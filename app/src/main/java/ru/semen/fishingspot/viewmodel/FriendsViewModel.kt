package ru.semen.fishingspot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.Friend
import ru.semen.fishingspot.data.FriendsRepository
import ru.semen.fishingspot.data.User

class FriendsViewModel : ViewModel() {

    private val repository = FriendsRepository()

    private val _friends = MutableLiveData<List<Friend>>()
    val friends: LiveData<List<Friend>> = _friends

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    init {
        // Запускаем постоянное слушание базы данных
        observeFriends()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            repository.getFriendsFlow().collect { friendsList ->
                _friends.value = friendsList
            }
        }
    }

    fun addFriend(friendId: String, friendName: String) {
        viewModelScope.launch {
            val result = repository.addFriend(friendId, friendName)

            result.onSuccess {
                _message.value = "✅ $friendName теперь в друзьях!"
                // Список обновится САМ благодаря Flow в observeFriends()
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
            // При успехе ничего не показываем, просто закрываем шторку (она сама закроется)
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
}