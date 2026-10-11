package ru.semen.fishingspot.viewmodel

import android.app.Application // ✅ Добавлен импорт
import androidx.lifecycle.AndroidViewModel // ✅ Изменено с ViewModel на AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.Friend
import ru.semen.fishingspot.data.FriendsRepository
import ru.semen.fishingspot.data.User

// ✅ Теперь принимаем Application в конструкторе
class FriendsViewModel(application: Application) : AndroidViewModel(application) {

    // ✅ Передаём контекст приложения в репозиторий
    private val repository = FriendsRepository(application.applicationContext)

    private val _friends = MutableLiveData<List<Friend>>()
    val friends: LiveData<List<Friend>> = _friends

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    init {
        observeFriends()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            repository.getFriendsFlow().collect { friendsList ->
                _friends.value = friendsList
            }
        }
    }

    fun sendFriendRequest(friendId: String, friendName: String) {
        viewModelScope.launch {
            val result = repository.sendFriendRequest(friendId, friendName)

            result.onSuccess {
                _message.value = "✅ Запрос в друзья отправлен!"
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка: ${error.message}"
            }
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        return repository.searchUsers(query)
    }

    fun sendSpotRequest(friendId: String, friendName: String, customMessage: String) {
        viewModelScope.launch {
            val result = repository.sendSpotRequest(friendId, friendName, customMessage)
            result.onFailure { error ->
                _message.value = "❌ Ошибка отправки: ${error.message}"
            }
        }
    }

    fun sendSharedSpot(
        requesterId: String,
        requesterName: String,
        spot: ru.semen.fishingspot.data.FishingSpot,
        originalMessageId: String
    ) {
        viewModelScope.launch {
            val result = repository.sendSharedSpot(requesterId, requesterName, spot, originalMessageId)
            result.onSuccess {
                _message.value = "✅ Точка успешно отправлена!"
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка отправки: ${error.message}"
            }
        }
    }

    fun acceptFriendRequest(friendId: String, friendName: String, messageId: String) {
        viewModelScope.launch {
            val result = repository.acceptFriendRequest(friendId, friendName, messageId)
            result.onSuccess {
                _message.value = "✅ Теперь вы друзья!"
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка: ${error.message}"
            }
        }
    }
}