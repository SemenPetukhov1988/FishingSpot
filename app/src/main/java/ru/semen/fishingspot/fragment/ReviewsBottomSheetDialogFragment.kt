package ru.semen.fishingspot.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.adapter.ReviewsAdapter
import ru.semen.fishingspot.data.Review
import ru.semen.fishingspot.data.ReviewsRepository
import ru.semen.fishingspot.databinding.FragmentReviewsBottomSheetDialogBinding // ✅ ИСПРАВЛЕНО
import ru.semen.fishingspot.utils.UserSessionManager

class ReviewsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    // ✅ ИСПРАВЛЕНО ИМЯ КЛАССА БИНДИНГА
    private var _binding: FragmentReviewsBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private val reviewsRepository = ReviewsRepository()
    private lateinit var adapter: ReviewsAdapter
    private var reviewsListener: ListenerRegistration? = null

    private var firebaseSpotId: String = ""

    companion object {
        private const val ARG_SPOT_ID = "firebase_spot_id"

        fun newInstance(firebaseSpotId: String): ReviewsBottomSheetDialogFragment {
            return ReviewsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SPOT_ID, firebaseSpotId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseSpotId = arguments?.getString(ARG_SPOT_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ✅ ИСПРАВЛЕНО ИМЯ КЛАССА ПРИ ИНФЛЕЙТЕ
        _binding = FragmentReviewsBottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Настраиваем RecyclerView
        adapter = ReviewsAdapter()
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        // Подписываемся на отзывы в реальном времени
        subscribeToReviews()

        // Кнопка "Написать отзыв"
        binding.btnWriteReview.setOnClickListener {
            showWriteReviewDialog()
        }
    }

    /**
     * Подписка на изменения отзывов в реальном времени
     */
    private fun subscribeToReviews() {
        reviewsListener = reviewsRepository.subscribeToReviews(firebaseSpotId) { reviews ->
            adapter.submitList(reviews)
            updateEmptyState(reviews.size)
        }
    }

    /**
     * Обновляет UI в зависимости от количества отзывов
     */
    private fun updateEmptyState(count: Int) {
        if (count == 0) {
            binding.tvEmptyReviews.visibility = View.VISIBLE
            binding.rvReviews.visibility = View.GONE
            binding.tvReviewsCount.text = "Нет отзывов. Будьте первым!"
        } else {
            binding.tvEmptyReviews.visibility = View.GONE
            binding.rvReviews.visibility = View.VISIBLE
            val word = when {
                count % 10 == 1 && count % 100 != 11 -> "отзыв"
                count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "отзыва"
                else -> "отзывов"
            }
            binding.tvReviewsCount.text = "$count $word"
        }
    }

    /**
     * Показывает диалог для написания отзыва
     */
    private fun showWriteReviewDialog() {
        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: "unknown"

        // Проверяем, не оставлял ли пользователь уже отзыв
        viewLifecycleOwner.lifecycleScope.launch {
            val hasReviewed = reviewsRepository.hasUserReviewed(firebaseSpotId, currentUserId)
                .getOrDefault(false)

            if (hasReviewed) {
                Toast.makeText(
                    requireContext(),
                    "⚠️ Вы уже оставляли отзыв к этой точке",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Создаём диалог с полем ввода
            val editText = EditText(requireContext()).apply {
                hint = "Расскажите о месте..."
                setPadding(48, 32, 48, 32)
                maxLines = 5
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("✍️ Новый отзыв")
                .setView(editText)
                .setPositiveButton("Отправить") { _, _ ->
                    val text = editText.text.toString().trim()
                    if (text.isNotEmpty()) {
                        submitReview(text, currentUserId)
                    } else {
                        Toast.makeText(requireContext(), "Введите текст отзыва", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    /**
     * Отправляет отзыв в Firebase
     */
    /**
     * Отправляет отзыв в Firebase
     */
    private fun submitReview(text: String, authorId: String) {
        // ✅ ПОЛУЧАЕМ РЕАЛЬНЫЙ НИКНЕЙМ ПОЛЬЗОВАТЕЛЯ ИЗ СЕССИИ
        // Если по какой-то причине он null, используем запасной вариант "Рыбак"
        val userName = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"

        val review = Review(
            authorId = authorId,
            authorName = userName, // ✅ Теперь здесь реальный никнейм!
            text = text,
            createdAt = System.currentTimeMillis()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = reviewsRepository.addReview(firebaseSpotId, review)
            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "✅ Отзыв опубликован!", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(requireContext(), "❌ Ошибка публикации", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * Делаем BottomSheet более высоким по умолчанию
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        // Отписываемся от Firebase, чтобы не тратить батарею
        reviewsListener?.remove()
        reviewsListener = null
        _binding = null
        super.onDestroyView()
    }
}