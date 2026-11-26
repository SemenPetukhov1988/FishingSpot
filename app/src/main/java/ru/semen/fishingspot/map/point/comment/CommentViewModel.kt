package ru.semen.fishingspot.map.point.comment

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CommentViewModel : ViewModel() {

    private val repository = CommentRepository()

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadComments(spotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val list = repository.getCommentsForSpot(spotId)
            _comments.value = list
            _isLoading.value = false
        }
    }

    fun addComment(context: Context, spotId: String, text: String, parentId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.addComment(context, spotId, text, parentId)

            result.onSuccess {
                loadComments(spotId) // Перезагружаем список после успешной отправки
                _error.value = null
            }
            result.onFailure { e ->
                _error.value = "Ошибка отправки: ${e.message}"
            }
            _isLoading.value = false
        }
    }
}