package ru.semen.fishingspot.profile_avatar

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.DialogEditProfileBinding

class EditProfileDialog : DialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    // ✅ ViewModel привязана к жизненному циклу диалога
    private val viewModel: EditProfileViewModel by viewModels()

    // Лаунчер выбора фото из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadAvatar(requireContext(), uri)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (requireContext().resources.displayMetrics.widthPixels * 0.85).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Клик по большой аватарке → открытие галереи
        binding.ivProfileAvatarLarge.apply {
            isClickable = true
            setOnClickListener {
                pickImageLauncher.launch("image/*")
            }
        }

        // Закрытие диалога
        binding.btnCloseDialog.setOnClickListener { dismiss() }

        // Кнопка сохранения (пока просто закрывает, т.к. никнейм ещё не реализован)
        binding.btnSaveProfile.setOnClickListener { dismiss() }

        // ✅ Реакция на состояние загрузки
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnSaveProfile.isEnabled = !loading
            binding.btnSaveProfile.text = if (loading) "Загрузка..." else "Сохранить изменения"

            // Блокируем клик по аватарке во время загрузки, чтобы не запустить вторую
            binding.ivProfileAvatarLarge.isClickable = !loading
        }

        // ✅ Реакция на результат загрузки
        viewModel.uploadResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { url ->
                Log.d("AVATAR_DIALOG", "🖼️ Загрузка в ImageView: $url")

                // ✅ Используем application context для надежности внутри DialogFragment
                Glide.with(requireContext().applicationContext)
                    .load(url)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.bg_avatar_shape)
                    .error(R.drawable.bg_avatar_shape)
                    .listener(object :
                        com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("AVATAR_DIALOG", "❌ Glide ошибка: ${e?.message}")
                            return false
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("AVATAR_DIALOG", "✅ Glide успешно загрузил!")
                            return false
                        }
                    })
                    .into(binding.ivProfileAvatarLarge)

                Toast.makeText(requireContext(), "Аватарка обновлена!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}