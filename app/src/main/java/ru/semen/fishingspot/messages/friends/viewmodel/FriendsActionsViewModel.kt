package ru.semen.fishingspot.feature_messages.friends.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import ru.semen.fishingspot.messages.friends.repository.FriendsActionsRepository

sealed class FriendActionState {
    object Idle : FriendActionState()
    object Loading : FriendActionState()
    object Success : FriendActionState()
    data class Error(val message: String) : FriendActionState()
}

class FriendsActionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FriendsActionsRepository(application.applicationContext)

    private val _actionState = MutableStateFlow<FriendActionState>(FriendActionState.Idle)
    val actionState: StateFlow<FriendActionState> = _actionState.asStateFlow()

    fun acceptRequest(senderId: String, messageId: String) {
        viewModelScope.launch {
            _actionState.value = FriendActionState.Loading
            repository.acceptFriendRequest(senderId, messageId)
                .onSuccess { _actionState.value = FriendActionState.Success }
                .onFailure { _actionState.value = FriendActionState.Error(it.message ?: "Ошибка") }
        }
    }

    fun declineRequest(messageId: String) {
        viewModelScope.launch {
            _actionState.value = FriendActionState.Loading
            repository.declineFriendRequest(messageId)
                .onSuccess { _actionState.value = FriendActionState.Success }
                .onFailure { _actionState.value = FriendActionState.Error(it.message ?: "Ошибка") }
        }
    }

    fun resetState() {
        _actionState.value = FriendActionState.Idle
    }
}