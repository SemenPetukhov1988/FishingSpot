package ru.semen.fishingspot.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                    // 🔧 ЗАГЛУШКА: пока просто имитируем вход
                    simulateLogin(nickname)
                }
            }
        }
    }

    /**
     * Заглушка для входа без Firebase.
     * Позже здесь будет вызов FirebaseAuth.signInAnonymously()
     */
    private fun simulateLogin(nickname: String) {
        // Блокируем кнопку, чтобы не нажать дважды
        binding.btnEnter.isEnabled = false
        binding.btnEnter.text = "Загрузка..."

        // Имитация задержки сети (1 секунда)
        view?.postDelayed({
            // TODO: Здесь будет реальная авторизация через Firebase
            // 1. signInAnonymously()
            // 2. Сохранение никнейма в Firestore
            // 3. Навигация на карту

            Snackbar.make(binding.root, "Добро пожаловать, $nickname!", Snackbar.LENGTH_LONG).show()

            // Временная навигация (заменить на реальный маршрут после подключения Firebase)
            // findNavController().navigate(R.id.action_login_to_map)

            binding.btnEnter.isEnabled = true
            binding.btnEnter.text = "Начать рыбалку"
        }, 1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}