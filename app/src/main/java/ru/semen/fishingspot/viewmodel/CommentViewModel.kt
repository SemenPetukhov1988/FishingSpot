package ru.semen.fishingspot.viewmodel



import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.models.Document // ← Убедись, что это есть
    // ← ИСПРАВЛЕНО: добавлено .models
import kotlinx.coroutines.launch
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

// Простой data class для комментария
data class Comment(
    val id: String,
    val spotId: String,
    val parentId: String?,
    val userId: String,
    val userName: String,
    val text: String,
    val createdAt: Long
)

class CommentViewModel : ViewModel() {

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // ⚠️ ЗАМЕНИ НА СВОЙ ID БАЗЫ ДАННЫХ
    private val DATABASE_ID = "6ad5021a0029a2c88786"
    private val COLLECTION_ID = "6adcf7ad002d061647dc"

    /**
     * Загружает корневые комментарии для точки (без ответов)
     */
    fun loadComments(spotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DATABASE_ID,
                    collectionId = COLLECTION_ID,
                    queries = listOf(
                        Query.equal("spotId", spotId),
                        Query.orderAsc("createdAt"),
                        Query.limit(100)
                    )
                )

                val flatList = result.documents.map { doc ->
                    val data = doc.data
                    Comment(
                        id = doc.id,
                        spotId = (data["spotId"] as? String) ?: "",
                        parentId = (data["parentId"] as? String)?.takeIf { it != "null" && it.isNotEmpty() },
                        userId = (data["userId"] as? String) ?: "",
                        userName = (data["userName"] as? String) ?: "Рыбак",
                        text = (data["text"] as? String) ?: "",
                        createdAt = (data["createdAt"] as? Long) ?: 0L
                    )
                }

                // ✅ ПРЕВРАЩАЕМ ПЛОСКИЙ СПИСОК В ДЕРЕВО
                val threadedComments = buildThreadedComments(flatList)

                _comments.value = threadedComments
                Log.d("COMMENT_VM", "✅ Загружено ${threadedComments.size} комментариев (дерево построено)")
                _error.value = null

            } catch (e: Exception) {
                Log.e("COMMENT_VM", "Ошибка загрузки: ${e.message}", e)
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ✅ ФУНКЦИЯ ПОСТРОЕНИЯ ДЕРЕВА
    private fun buildThreadedComments(comments: List<Comment>): List<Comment> {
        val commentMap = comments.associateBy { it.id }
        val roots = mutableListOf<Comment>()
        val childrenMap = mutableMapOf<String, MutableList<Comment>>()

        // Разделяем на корни и детей
        for (comment in comments) {
            if (comment.parentId == null || !commentMap.containsKey(comment.parentId)) {
                roots.add(comment)
            } else {
                childrenMap.getOrPut(comment.parentId) { mutableListOf() }.add(comment)
            }
        }

        // Рекурсивно собираем дерево
        val result = mutableListOf<Comment>()
        fun addWithChildren(parent: Comment) {
            result.add(parent)
            childrenMap[parent.id]?.forEach { child ->
                addWithChildren(child)
            }
        }

        roots.forEach { root -> addWithChildren(root) }
        return result
    }

    /**
     * Добавляет новый комментарий или ответ
     * @param spotId ID точки
     * @param text Текст комментария
     * @param parentId ID родителя (null если это корневой комментарий)
     */
    fun addComment(context: Context, spotId: String, text: String, parentId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = UserSessionManager.getCurrentUserId(context) ?: throw Exception("Не авторизован")
                val userName = UserSessionManager.getCurrentNickname(context) ?: "Рыбак"

                AppwriteManager.databases.createDocument(
                    databaseId = DATABASE_ID,
                    collectionId = COLLECTION_ID,
                    documentId = ID.unique(),
                    data = mapOf(
                        "spotId" to spotId,
                        "parentId" to (parentId ?: "null"), // Appwrite требует строку "null" или реальное значение
                        "userId" to userId,
                        "userName" to userName,
                        "text" to text,
                        "createdAt" to System.currentTimeMillis()
                    )
                )

                // После успешного добавления перезагружаем список
                loadComments(spotId)
                _error.value = null

            } catch (e: Exception) {
                _error.value = "Ошибка отправки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}