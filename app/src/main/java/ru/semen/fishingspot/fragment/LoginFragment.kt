package ru.semen.fishingspot.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentLoginBinding
import ru.semen.fishingspot.utils.UserSessionManager

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ ПРОВЕРКА СЕССИИ: если уже залогинен — сразу на карту
        if (UserSessionManager.isLoggedIn(requireContext())) {
            navigateToMainTabs()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnEnter.setOnClickListener {
            val nickname = binding.etNickname.text.toString().trim()

            when {
                nickname.isEmpty() -> {
                    Snackbar.make(binding.root, "Введите никнейм!", Snackbar.LENGTH_SHORT).show()
                }
                nickname.length < 3 -> {
                    Snackbar.make(binding.root, "Никнейм слишком короткий", Snackbar.LENGTH_SHORT).show()
                }
                else -> {
                    performFirebaseLogin(nickname)
                }
            }
        }
    }

    /**
     * Реальная анонимная авторизация через Firebase
     */
    private fun performFirebaseLogin(nickname: String) {
        binding.btnEnter.isEnabled = false
        binding.btnEnter.text = "Проверка..."

        // ✅ ШАГ 1: Проверяем, занят ли ник
        firestore.collection("users")
            .whereEqualTo("nickname", nickname)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Ник занят!
                    showError("Ник \"$nickname\" уже занят. Попробуйте другой.")
                    return@addOnSuccessListener
                }

                // ✅ ШАГ 2: Ник свободен — создаём аккаунт
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        val userId = result.user?.uid ?: run {
                            showError("Не удалось создать аккаунт")
                            return@addOnSuccessListener
                        }

                        // Сохраняем профиль
                        val userProfile = hashMapOf(
                            "nickname" to nickname,
                            "createdAt" to Timestamp.now(),
                            "isProfileDeleted" to false
                        )

                        firestore.collection("users").document(userId)
                            .set(userProfile)
                            .addOnSuccessListener {
                                UserSessionManager.saveUserSession(requireContext(), userId, nickname)
                                navigateToMainTabs()
                            }
                            .addOnFailureListener { e ->
                                // Даже если запись в Firestore не удалась,
                                // локальная сессия создана — пользователь сможет работать оффлайн
                                UserSessionManager.saveUserSession(requireContext(), userId, nickname)
                                navigateToMainTabs()
                            }
                    }
                    .addOnFailureListener { e ->
                        showError("Ошибка входа: ${e.localizedMessage}")
                    }
            }
            .addOnFailureListener { e ->
                showError("Ошибка проверки ника: ${e.localizedMessage}")
            }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        binding.btnEnter.isEnabled = true
        binding.btnEnter.text = "Войти"
    }

    private fun navigateToMainTabs() {
        findNavController().navigate(R.id.action_login_to_mainTabs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}