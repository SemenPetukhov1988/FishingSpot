package ru.semen.fishingspot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.semen.fishingspot.data.Friend
import ru.semen.fishingspot.data.FriendsRepository
import ru.semen.fishingspot.data.User

class FriendsViewModel(application: Application) : AndroidViewModel(application) {

    // ✅ Передаём контекст приложения в репозиторий
    private val repository = FriendsRepository(application.applicationContext)

    private val _friends = MutableLiveData<List<Friend>>()
    val friends: LiveData<List<Friend>> = _friends

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    // ✅ Убрали init блок. Загрузка теперь происходит явно по запросу из Fragment

    /**
     * Загружает список друзей из Appwrite
     */
    fun loadFriends() {
        viewModelScope.launch {
            val list = repository.getFriends()
            _friends.value = list
        }
    }

    /**
     * Отправляет запрос в друзья через коллекцию messages
     */
    fun sendFriendRequest(friendId: String, friendName: String) {
        viewModelScope.launch {
            val result = repository.sendFriendRequest(friendId, friendName)

            result.onSuccess {
                _message.value = "✅ Запрос в друзья отправлен!"
                // Опционально: можно перезагрузить список, если нужно обновить статусы
                // loadFriends()
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка: ${error.message}"
            }
        }
    }

    /**
     * Поиск пользователей по никнейму через индекс Appwrite
     */
    suspend fun searchUsers(query: String): List<User> {
        return repository.searchUsers(query)
    }

    /**
     * Принятие заявки в друзья (для фрагмента сообщений)
     */
    fun acceptFriendRequest(friendId: String, messageId: String) { // ✅ Убрали friendName из параметров
        viewModelScope.launch {
            val result = repository.acceptFriendRequest(friendId, messageId) // ✅ Передаем только 2 аргумента

            result.onSuccess {
                _message.value = "✅ Теперь вы друзья!"
                loadFriends() // Обновляем список друзей после принятия
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка: ${error.message}"
            }
        }
    }

    fun removeFriend(friendId: String, friendName: String) {
        viewModelScope.launch {
            val result = repository.removeFriend(friendId)

            result.onSuccess {
                _message.value = "❌ $friendName удален из друзей"
                // ✅ ОБЯЗАТЕЛЬНО перезагружаем список после удаления
                loadFriends()
            }
            result.onFailure { error ->
                _message.value = "❌ Ошибка удаления: ${error.message}"
            }
        }
    }

    // ❌ Методы sendSpotRequest, sendSharedSpot удалены, так как логика точек убрана из этого экрана
}