package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentLoginBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.AuthUiState
import ru.semen.fishingspot.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Инициализируем ViewModel (она сама создаст Repository с контекстом приложения)
    private val viewModel: AuthViewModel by viewModels()

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
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnEnter.setOnClickListener {
            val nickname = binding.etNickname.text.toString().trim()

            when {
                nickname.isEmpty() -> {
                    showError("Введите никнейм!")
                }
                nickname.length < 3 -> {
                    showError("Никнейм слишком короткий (мин. 3 символа)")
                }
                else -> {
                    // Запускаем процесс входа через ViewModel
                    viewModel.login(nickname)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthUiState.Loading -> {
                    binding.btnEnter.isEnabled = false
                    binding.btnEnter.text = "Создание аккаунта..."
                }
                is AuthUiState.Success -> {
                    navigateToMainTabs()
                }
                is AuthUiState.Error -> {
                    showError(state.message)
                    viewModel.resetState() // Сбрасываем состояние, чтобы можно было попробовать снова
                }
                is AuthUiState.Idle -> {
                    binding.btnEnter.isEnabled = true
                    binding.btnEnter.text = "Войти"
                }
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun navigateToMainTabs() {
        findNavController().navigate(R.id.action_login_to_mainTabs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}