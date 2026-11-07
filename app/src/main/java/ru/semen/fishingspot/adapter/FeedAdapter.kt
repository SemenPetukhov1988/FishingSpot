package ru.semen.fishingspot.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedAdapter(
    private val onMapClick: (String) -> Unit,
    private val onCommentsClick: (String) -> Unit
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private var items: List<FishingSpot> = emptyList()
    private val TAG = "FEED_ADAPTER"

    fun submitList(newList: List<FishingSpot>) {
        items = newList
        notifyDataSetChanged()
    }

    class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAuthorAvatar: ShapeableImageView = view.findViewById(R.id.ivFeedAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvFeedAuthorName)
        val tvSpotName: TextView = view.findViewById(R.id.tvFeedSpotName)
        val tvDate: TextView = view.findViewById(R.id.tvFeedDate)

        val tvMetaInfo: TextView = view.findViewById(R.id.tvFeedMetaInfo)
        val tvUserText: TextView = view.findViewById(R.id.tvFeedUserText) // ✅ Теперь точно найдет в новом XML

        val ivPhoto: ImageView = view.findViewById(R.id.ivFeedPhoto)
        val ivPhotoPlaceholder: ImageView = view.findViewById(R.id.ivFeedPhotoPlaceholder)

        val btnViewOnMap: View = view.findViewById(R.id.btnViewOnMap)
        val layoutLike: View = view.findViewById(R.id.layoutFeedLike)
        val tvLikesCount: TextView = view.findViewById(R.id.tvFeedLikesCount)
        val btnComments: View = view.findViewById(R.id.btnFeedComments)
        val tvCommentsCount: TextView = view.findViewById(R.id.tvFeedCommentsCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        // ✅ ИСПРАВЛЕНИЕ: Используем НОВЫЙ лейаут item_feed_post
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed_spot, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val spot = items[position]

        // 1. ШАПКА
        holder.tvAuthorName.text = spot.authorName.ifEmpty { "Рыбак" }
        holder.tvSpotName.text = spot.name.ifEmpty { "Без названия" }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
        holder.tvDate.text = "📅 ${dateFormat.format(Date(spot.createdAt))}"

        // 2. МЕТА-ИНФОРМАЦИЯ (Снасти/наживка)
        if (!spot.description.isNullOrEmpty()) {
            holder.tvMetaInfo.visibility = View.VISIBLE
            holder.tvMetaInfo.text = spot.description
        } else {
            holder.tvMetaInfo.visibility = View.GONE
        }

        // 3. ФОТО
        if (!spot.photoUrl.isNullOrEmpty()) {
            holder.ivPhoto.visibility = View.VISIBLE
            holder.ivPhotoPlaceholder.visibility = View.GONE
            Glide.with(holder.itemView.context)
                .load(spot.photoUrl)
                .centerCrop()
                .placeholder(R.drawable.add_a_photo_24dp_000000_fill0_wght400_grad0_opsz24__1_)
                .error(R.drawable.add_a_photo_24dp_000000_fill0_wght400_grad0_opsz24__1_)
                .into(holder.ivPhoto)
        } else {
            holder.ivPhoto.visibility = View.GONE
            holder.ivPhotoPlaceholder.visibility = View.VISIBLE
        }

        // ✅ 4. ТЕКСТ ПОСТА + ОТЛАДКА
        Log.d("FEED_DEBUG", "📝 postText='${spot.postText}' | isEmpty=${spot.postText.isEmpty()}")

        if (!spot.postText.isNullOrEmpty()) {
            holder.tvUserText.visibility = View.VISIBLE
            holder.tvUserText.text = spot.postText
        } else {
            holder.tvUserText.visibility = View.GONE
        }

        // 5. ПАНЕЛЬ ДЕЙСТВИЙ
        holder.tvLikesCount.text = "0"
        holder.tvCommentsCount.text = "Обсуждение"

        holder.btnViewOnMap.setOnClickListener { onMapClick(spot.documentId) }
        holder.btnComments.setOnClickListener { onCommentsClick(spot.documentId) }
        holder.layoutLike.setOnClickListener { /* TODO */ }
    }

    override fun getItemCount() = items.size
}