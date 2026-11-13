package ru.semen.fishingspot.messages.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.semen.fishingspot.messages.data.FishingSignal
import ru.semen.fishingspot.messages.repository.FishingSignalRepository

sealed class SignalsUiState {
    object Loading : SignalsUiState()
    data class Success(val signals: List<FishingSignal>) : SignalsUiState()
    data class Error(val message: String) : SignalsUiState()
}

sealed class SendSignalState {
    object Idle : SendSignalState()
    object Sending : SendSignalState()
    object Success : SendSignalState()
    data class Error(val message: String) : SendSignalState()
}

class FishingSignalsViewModel(
    private val repository: FishingSignalRepository = FishingSignalRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignalsUiState>(SignalsUiState.Loading)
    val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

    private val _sendState = MutableStateFlow<SendSignalState>(SendSignalState.Idle)
    val sendState: StateFlow<SendSignalState> = _sendState.asStateFlow()

    fun loadSignals(userId: String) {
        viewModelScope.launch {
            Log.d("SIGNAL_LOAD", "📤 Вызов репозитория для userId='$userId'")
            _uiState.value = SignalsUiState.Loading

            try {
                val result = repository.getSignals(userId)
                Log.d("SIGNAL_LOAD", "📥 Репозиторий вернул Result")

                result.fold(
                    onSuccess = { signals ->
                        Log.d("SIGNAL_LOAD", "✅ Успех: ${signals.size} сигналов")
                        val sorted = signals.sortedByDescending { it.createdAt }
                        _uiState.value = SignalsUiState.Success(sorted)
                    },
                    onFailure = { error ->
                        Log.e("SIGNAL_LOAD", "❌ Ошибка репозитория: ${error.message}")
                        _uiState.value = SignalsUiState.Error(error.message ?: "Неизвестная ошибка")
                    }
                )
            } catch (e: Exception) {
                Log.e("SIGNAL_LOAD", "💥 Критическое исключение: ${e.message}", e)
                _uiState.value = SignalsUiState.Error(e.message ?: "Критическая ошибка")
            }
        }
    }

    fun sendSignal(
        senderId: String,
        senderName: String,
        receiverId: String,
        type: String,
        lat: Double = 0.0,
        lon: Double = 0.0,
        zoom: Double = 0.0,
        messageText: String? = null,
        anchorId: String = ""
    ) {
        viewModelScope.launch {
            _sendState.value = SendSignalState.Sending

            repository.sendSignal(
                senderId = senderId,
                senderName = senderName,
                receiverId = receiverId,
                type = type,
                lat = lat,
                lon = lon,
                zoom = zoom,
                messageText = messageText,
                anchorId = anchorId
            ).fold(
                onSuccess = {
                    _sendState.value = SendSignalState.Success
                    // ✅ ФИКС: Перезагружаем ленту ОТПРАВИТЕЛЯ (senderId), где лежат якоря
                    loadSignals(senderId)
                },
                onFailure = { error ->
                    _sendState.value = SendSignalState.Error(error.message ?: "Ошибка отправки")
                }
            )
        }
    }

    fun resetSendState() {
        _sendState.value = SendSignalState.Idle
    }
}