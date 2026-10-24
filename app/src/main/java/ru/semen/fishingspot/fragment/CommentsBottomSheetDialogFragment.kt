package ru.semen.fishingspot.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.adapter.CommentsAdapter
import ru.semen.fishingspot.databinding.FragmentCommentsBottomSheetBinding
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.CommentViewModel

class CommentsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCommentsBottomSheetBinding? = null
    private val binding get() = _binding!!

    // Используем общий ViewModel для комментариев
    private val commentViewModel: CommentViewModel by activityViewModels()

    // Переменная для хранения ID комментария, на который отвечаем
    private var replyingToCommentId: String? = null

    companion object {
        private const val ARG_SPOT_ID = "spot_id"

        fun newInstance(spotId: String): CommentsBottomSheetDialogFragment {
            return CommentsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SPOT_ID, spotId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString(ARG_SPOT_ID) ?: ""

        // ✅ Инициализация адаптера с обработчиком клика "Ответить"
        val adapter = CommentsAdapter(onReplyClick = { comment ->
            startReplyMode(comment.id, comment.userName)
        })

        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = adapter

        // Наблюдатели ViewModel (LiveData)
        commentViewModel.comments.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            updateEmptyState(list.size)
        }

        commentViewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
            }
        }

        // Загружаем комментарии при открытии
        commentViewModel.loadComments(spotId)

        // ✅ Отправка комментария или ответа
        binding.btnSendComment.setOnClickListener {
            val text = binding.etCommentInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            commentViewModel.addComment(
                context = requireContext(),
                spotId = spotId,
                text = text,
                parentId = replyingToCommentId // Передаем ID родителя, если есть
            )

            binding.etCommentInput.text?.clear()
            cancelReplyMode() // Сбрасываем режим после отправки
        }

        // ✅ Кнопка отмены ответа
        binding.btnCancelReply.setOnClickListener {
            cancelReplyMode()
        }
    }

    // ✅ Метод включения режима ответа
    private fun startReplyMode(commentId: String, userName: String) {
        replyingToCommentId = commentId
        binding.layoutReplyIndicator.visibility = View.VISIBLE
        binding.tvReplyingTo.text = "Отвечаю @${userName}"
        binding.etCommentInput.hint = "Ваш ответ..."
        binding.etCommentInput.requestFocus()
    }

    // ✅ Метод выключения режима ответа
    private fun cancelReplyMode() {
        replyingToCommentId = null
        binding.layoutReplyIndicator.visibility = View.GONE
        binding.etCommentInput.hint = "Написать комментарий..."
    }

    private fun updateEmptyState(count: Int) {
        if (count == 0) {
            binding.tvEmptyComments.visibility = View.VISIBLE
            binding.rvComments.visibility = View.GONE
            binding.tvCommentsCount.text = "Нет комментариев. Будьте первым!"
        } else {
            binding.tvEmptyComments.visibility = View.GONE
            binding.rvComments.visibility = View.VISIBLE
            val word = when {
                count % 10 == 1 && count % 100 != 11 -> "комментарий"
                count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "комментария"
                else -> "комментариев"
            }
            binding.tvCommentsCount.text = "$count $word"
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
        _binding = null
        super.onDestroyView()
    }
}