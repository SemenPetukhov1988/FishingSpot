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
import ru.semen.fishingspot.data.MessagesRepository // ✅ Добавили репозиторий
import ru.semen.fishingspot.data.Review
import ru.semen.fishingspot.data.ReviewsRepository
import ru.semen.fishingspot.databinding.FragmentReviewsBottomSheetDialogBinding
import ru.semen.fishingspot.utils.UserSessionManager

class ReviewsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReviewsBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private val reviewsRepository = ReviewsRepository()
    private val messagesRepository = MessagesRepository() // ✅ Инициализируем
    private lateinit var adapter: ReviewsAdapter
    private var reviewsListener: ListenerRegistration? = null

    private var firebaseSpotId: String = ""
    private var spotAuthorId: String = "" // ✅ ID автора точки
    private var spotName: String = ""     // ✅ Название точки

    companion object {
        private const val ARG_SPOT_ID = "firebase_spot_id"
        private const val ARG_SPOT_AUTHOR_ID = "spot_author_id" // ✅ Новый аргумент
        private const val ARG_SPOT_NAME = "spot_name"           // ✅ Новый аргумент

        fun newInstance(
            firebaseSpotId: String,
            spotAuthorId: String,
            spotName: String
        ): ReviewsBottomSheetDialogFragment {
            return ReviewsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SPOT_ID, firebaseSpotId)
                    putString(ARG_SPOT_AUTHOR_ID, spotAuthorId)
                    putString(ARG_SPOT_NAME, spotName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseSpotId = arguments?.getString(ARG_SPOT_ID) ?: ""
        spotAuthorId = arguments?.getString(ARG_SPOT_AUTHOR_ID) ?: ""
        spotName = arguments?.getString(ARG_SPOT_NAME) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewsBottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReviewsAdapter()
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        subscribeToReviews()

        binding.btnWriteReview.setOnClickListener {
            showWriteReviewDialog()
        }
    }

    private fun subscribeToReviews() {
        reviewsListener = reviewsRepository.subscribeToReviews(firebaseSpotId) { reviews ->
            adapter.submitList(reviews)
            updateEmptyState(reviews.size)
        }
    }

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

    private fun showWriteReviewDialog() {
        val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: "unknown"

        viewLifecycleOwner.lifecycleScope.launch {
            val hasReviewed = reviewsRepository.hasUserReviewed(firebaseSpotId, currentUserId)
                .getOrDefault(false)

            if (hasReviewed) {
                Toast.makeText(requireContext(), "⚠️ Вы уже оставляли отзыв к этой точке", Toast.LENGTH_SHORT).show()
                return@launch
            }

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

    private fun submitReview(text: String, reviewerId: String) {
        val userName = UserSessionManager.getCurrentNickname(requireContext()) ?: "Рыбак"

        android.util.Log.d("DEBUG_SPOT", "📝 Попытка сохранить отзыв. reviewerId=$reviewerId, spotAuthorId=$spotAuthorId")

        val review = Review(
            authorId = reviewerId,
            authorName = userName,
            text = text,
            createdAt = System.currentTimeMillis()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = reviewsRepository.addReview(firebaseSpotId, review)
            result.fold(
                onSuccess = {
                    android.util.Log.d("DEBUG_SPOT", "✅ Отзыв успешно сохранен в Firebase!")
                    Toast.makeText(requireContext(), "✅ Отзыв опубликован!", Toast.LENGTH_SHORT).show()

                    // ПРОВЕРКА: нужно ли отправлять уведомление
                    if (spotAuthorId.isNotEmpty() && reviewerId != spotAuthorId) {
                        android.util.Log.d("DEBUG_SPOT", "🔔 Автор точки ($spotAuthorId) и автор отзыва ($reviewerId) РАЗНЫЕ. Отправляем уведомление...")

                        viewLifecycleOwner.lifecycleScope.launch {
                            val msgResult = messagesRepository.sendReviewNotification(
                                recipientId = spotAuthorId,
                                senderId = reviewerId,
                                senderName = userName,
                                spotId = firebaseSpotId,
                                spotName = spotName,
                                reviewText = text
                            )

                            msgResult.fold(
                                onSuccess = { android.util.Log.d("DEBUG_SPOT", "🚀 Уведомление УСПЕШНО отправлено в коллекцию messages!") },
                                onFailure = { e -> android.util.Log.e("DEBUG_SPOT", "💥 ОШИБКА отправки уведомления", e) }
                            )
                        }
                    } else {
                        android.util.Log.d("DEBUG_SPOT", "⏭️ Уведомление НЕ отправлено (автор точки и автор отзыва совпадают, или spotAuthorId пуст)")
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("DEBUG_SPOT", "❌ Ошибка сохранения отзыва", e)
                    Toast.makeText(requireContext(), "❌ Ошибка публикации", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

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
        reviewsListener?.remove()
        reviewsListener = null
        _binding = null
        super.onDestroyView()
    }
}