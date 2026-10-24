package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.viewmodel.Comment
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

    // ✅ ДОБАВЛЕН tvReplyToIndicator В VIEWHOLDER
    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuthorAvatar: TextView = view.findViewById(R.id.tvAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val tvReviewText: TextView = view.findViewById(R.id.tvReviewText)
        val tvReplyButton: TextView = view.findViewById(R.id.tvReplyButton)
        val tvReplyToIndicator: TextView = view.findViewById(R.id.tvReplyToIndicator) // ✅ Новое поле
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = items[position]

        // Находим родителя в текущем списке (благодаря нашей сортировке он всегда будет найден)
        val parentComment = items.find { it.id == comment.parentId }

        holder.tvAuthorName.text = comment.userName.ifEmpty { "Рыбак" }
        holder.tvAuthorAvatar.text = comment.userName.firstOrNull()?.uppercase() ?: "?"

        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        holder.tvReviewDate.text = dateFormat.format(Date(comment.createdAt))
        holder.tvReviewText.text = comment.text

        // ✅ ПРАВИЛЬНАЯ ЛОГИКА СДВИГА (ТОЛЬКО КОНТЕНТ, НЕ РАМКА)
        if (parentComment != null) {
            // Показываем плашку "Семен отвечает Сергею"
            holder.tvReplyToIndicator.visibility = View.VISIBLE
            holder.tvReplyToIndicator.text = "${comment.userName} отвечает ${parentComment.userName}"

            // Сдвигаем ТОЛЬКО внутренний LinearLayout, рамка остается на месте!
            val startMarginPx = (48 * holder.itemView.context.resources.displayMetrics.density).toInt()
            val params = holder.itemView.findViewById<LinearLayout>(R.id.contentContainer)?.layoutParams as? ViewGroup.MarginLayoutParams
            params?.let {
                it.marginStart = startMarginPx
                holder.itemView.findViewById<LinearLayout>(R.id.contentContainer).layoutParams = it
            }

            // Визуально делаем ответ чуть меньше
            holder.tvAuthorName.textSize = 13f
            holder.tvReviewText.textSize = 13f
        } else {
            // Корневой комментарий: убираем отступ
            holder.tvReplyToIndicator.visibility = View.GONE
            val params = holder.itemView.findViewById<LinearLayout>(R.id.contentContainer)?.layoutParams as? ViewGroup.MarginLayoutParams
            params?.let {
                it.marginStart = 0
                holder.itemView.findViewById<LinearLayout>(R.id.contentContainer).layoutParams = it
            }
            holder.tvAuthorName.textSize = 14f
            holder.tvReviewText.textSize = 14f
        }

        holder.tvReplyButton.setOnClickListener {
            onReplyClick?.invoke(comment)
        }
    }

    override fun getItemCount() = items.size
}