package ru.semen.fishingspot.map.point.comment

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.utils.loadUserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentsAdapter(
    private val onReplyClick: ((Comment) -> Unit)? = null
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    private var items: List<Comment> = emptyList()

    fun submitList(newList: List<Comment>) {
        items = newList
        notifyDataSetChanged()
    }

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAuthorAvatar: ShapeableImageView = view.findViewById(R.id.ivAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val tvReviewText: TextView = view.findViewById(R.id.tvReviewText)
        val tvReplyButton: TextView = view.findViewById(R.id.tvReplyButton)

        // ✅ Элементы для стиля VK
        val contentContainer: LinearLayout = view.findViewById(R.id.contentContainer)
        val viewThreadLine: View = view.findViewById(R.id.viewThreadLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = items[position]
        val parentComment = items.find { it.id == comment.parentId }

        holder.ivAuthorAvatar.loadUserAvatar(comment.userId)
        holder.tvAuthorName.text = comment.userName.ifEmpty { "Рыбак" }

        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        holder.tvReviewDate.text = dateFormat.format(Date(comment.createdAt))

        // ✅ ЛОГИКА ОТОБРАЖЕНИЯ ОТВЕТА (VK STYLE)
        if (parentComment != null) {
            // 1. Сдвигаем контент вправо
            val marginPx = (48 * holder.itemView.context.resources.displayMetrics.density).toInt()
            val params = holder.contentContainer.layoutParams as? ViewGroup.MarginLayoutParams
            params?.marginStart = marginPx
            holder.contentContainer.layoutParams = params

            // 2. Показываем линию связи
            holder.viewThreadLine.visibility = View.VISIBLE

            // 3. Формируем текст с синим именем адресата
            val targetName = parentComment.userName.ifEmpty { "Рыбак" }
            val fullText = "$targetName, ${comment.text}"

            val spannable = SpannableString(fullText)
            val colorBlue = Color.parseColor("#0288D1") // Синий цвет VK
            spannable.setSpan(
                ForegroundColorSpan(colorBlue),
                0,
                targetName.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            holder.tvReviewText.text = spannable

        } else {
            // Корневой комментарий: без отступа и линии
            val params = holder.contentContainer.layoutParams as? ViewGroup.MarginLayoutParams
            params?.marginStart = 0
            holder.contentContainer.layoutParams = params
            holder.viewThreadLine.visibility = View.GONE
            holder.tvReviewText.text = comment.text
        }

        holder.tvReplyButton.setOnClickListener {
            onReplyClick?.invoke(comment)
        }
    }

    override fun getItemCount() = items.size
}