package ru.semen.fishingspot.utils

import android.content.Context
import androidx.core.content.edit

object UserSessionManager {
    private const val PREFS_NAME = "user_session_prefs"
    private const val KEY_USER_ID = "current_user_id"
    private const val KEY_NICKNAME = "current_nickname"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"

    // Проверка: был ли пользователь здесь раньше?
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null) != null
    }

    // Сохранение данных после успешной регистрации
    fun saveUserSession(context: Context, userId: String, nickname: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_NICKNAME, nickname)
            putBoolean(KEY_IS_FIRST_LAUNCH, false)
        }
    }

    // Получение текущего UID (для запросов к Room/Firestore)
    fun getCurrentUserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null)
    }

    // Получение ника (для отображения в профиле)
    fun getCurrentNickname(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NICKNAME, null)
    }

    // Очистка сессии (если вдруг понадобится для тестов или сброса)
    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }
}