package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.Review
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewsAdapter : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    private var items: List<Review> = emptyList()

    fun submitList(newList: List<Review>) {
        items = newList
        notifyDataSetChanged()
    }

    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuthorAvatar: TextView = view.findViewById(R.id.tvAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val tvReviewText: TextView = view.findViewById(R.id.tvReviewText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = items[position]

        // Имя автора (если пусто — берём ID)
        val displayName = review.authorName.ifEmpty { review.authorId }
        holder.tvAuthorName.text = displayName

        // Первая буква для аватара
        val firstLetter = displayName.firstOrNull()?.uppercase() ?: "?"
        holder.tvAuthorAvatar.text = firstLetter

        // Дата
        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        holder.tvReviewDate.text = dateFormat.format(Date(review.createdAt))

        // Текст отзыва
        holder.tvReviewText.text = review.text
    }

    override fun getItemCount() = items.size
}