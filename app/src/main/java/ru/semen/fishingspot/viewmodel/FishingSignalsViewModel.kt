package ru.semen.fishingspot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.semen.fishingspot.data.FishingSignal
import ru.semen.fishingspot.repository.FishingSignalRepository

/**
 * Состояние загрузки ленты сигналов
 */
sealed class SignalsUiState {
    object Loading : SignalsUiState()
    data class Success(val signals: List<FishingSignal>) : SignalsUiState()
    data class Error(val message: String) : SignalsUiState()
}

/**
 * Состояние операции отправки сигнала
 */
sealed class SendSignalState {
    object Idle : SendSignalState()
    object Sending : SendSignalState()
    object Success : SendSignalState()
    data class Error(val message: String) : SendSignalState()
}

class FishingSignalsViewModel(
    private val repository: FishingSignalRepository = FishingSignalRepository()
) : ViewModel() {

    // --- СОСТОЯНИЕ ЛЕНТЫ ---
    private val _uiState = MutableStateFlow<SignalsUiState>(SignalsUiState.Loading)
    val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

    // --- СОСТОЯНИЕ ОТПРАВКИ (ОТДЕЛЬНЫЙ ПОТОК) ---
    private val _sendState = MutableStateFlow<SendSignalState>(SendSignalState.Idle)
    val sendState: StateFlow<SendSignalState> = _sendState.asStateFlow()

    /**
     * Загружает сигналы для указанного пользователя
     */
    fun loadSignals(userId: String) {
        viewModelScope.launch {
            android.util.Log.d("SIGNAL_LOAD", "📤 Вызов репозитория для userId='$userId'")

            _uiState.value = SignalsUiState.Loading

            try {
                // ✅ ПРИНУДИТЕЛЬНЫЙ ТАЙМАУТ 15 СЕКУНД
                val result = withTimeout(15_000L) {
                    repository.getSignals(userId)
                }

                android.util.Log.d("SIGNAL_LOAD", "📥 Репозиторий вернул Result за <15с")

                result.fold(
                    onSuccess = { signals ->
                        android.util.Log.d("SIGNAL_LOAD", "✅ Успех: ${signals.size} сигналов")
                        val sorted = signals.sortedByDescending { it.createdAt }
                        _uiState.value = SignalsUiState.Success(sorted)
                    },
                    onFailure = { error ->
                        android.util.Log.e("SIGNAL_LOAD", "❌ Ошибка репозитория: ${error.message}")
                        _uiState.value = SignalsUiState.Error(error.message ?: "Неизвестная ошибка")
                    }
                )
            } catch (e: TimeoutCancellationException) {
                // ✅ ЛОВИМ ЗАВИСАНИЕ APPWRITE
                android.util.Log.e("SIGNAL_LOAD", "⏱️ ТАЙМАУТ 15с! Appwrite не ответил.")
                android.util.Log.e("SIGNAL_LOAD", "   Проверь: 1) Permissions коллекции 'any' на read")
                android.util.Log.e("SIGNAL_LOAD", "   2) Правильность collectionId в репозитории")
                android.util.Log.e("SIGNAL_LOAD", "   3) Интернет-соединение")
                _uiState.value = SignalsUiState.Error("Сервер не отвечает. Проверьте соединение и права доступа.")
            } catch (e: Exception) {
                android.util.Log.e("SIGNAL_LOAD", "💥 Критическое исключение: ${e.message}", e)
                _uiState.value = SignalsUiState.Error(e.message ?: "Критическая ошибка")
            }
        }
    }

    /**
     * Отправляет новый сигнал
     */
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
                    // Обновляем ленту после успешной отправки
                    loadSignals(receiverId)
                },
                onFailure = { error ->
                    _sendState.value = SendSignalState.Error(error.message ?: "Ошибка отправки")
                }
            )
        }
    }

    /**
     * Сбрасывает состояние отправки в Idle.
     * Вызывать из фрагмента после обработки успеха/ошибки.
     */
    fun resetSendState() {
        _sendState.value = SendSignalState.Idle
    }
}