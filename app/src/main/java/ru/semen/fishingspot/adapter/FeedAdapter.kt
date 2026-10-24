package ru.semen.fishingspot.adapter

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
    private val onMapClick: (FishingSpot) -> Unit,
    private val onCommentsClick: (String) -> Unit // ✅ НОВЫЙ CALLBACK ДЛЯ КОММЕНТАРИЕВ
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private var items: List<FishingSpot> = emptyList()

    fun submitList(newList: List<FishingSpot>) {
        items = newList
        notifyDataSetChanged()
    }

    class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAuthorAvatar: ShapeableImageView = view.findViewById(R.id.ivFeedAuthorAvatar)
        val tvAuthorName: TextView = view.findViewById(R.id.tvFeedAuthorName)
        val tvDate: TextView = view.findViewById(R.id.tvFeedDate)
        val tvSpotName: TextView = view.findViewById(R.id.tvFeedSpotName)

        val ivPhoto: ImageView = view.findViewById(R.id.ivFeedPhoto)
        val ivPhotoPlaceholder: ImageView = view.findViewById(R.id.ivFeedPhotoPlaceholder)

        val tvWeight: TextView = view.findViewById(R.id.tvFeedWeight)
        val tvFish: TextView = view.findViewById(R.id.tvFeedFish)
        val tvCoordinates: TextView = view.findViewById(R.id.tvFeedCoordinates)

        val btnViewOnMap: View = view.findViewById(R.id.btnViewOnMap)
        val layoutLike: View = view.findViewById(R.id.layoutFeedLike)
        val btnComments: View = view.findViewById(R.id.btnFeedComments)
        val tvCommentsCount: TextView = view.findViewById(R.id.tvFeedCommentsCount) // ✅ НАШЛИ СЧЕТЧИК
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed_spot, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val spot = items[position]

        // Шапка
        holder.tvAuthorName.text = spot.authorName.ifEmpty { "Рыбак" }
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("ru"))
        holder.tvDate.text = "📅 ${dateFormat.format(Date(spot.createdAt))}"
        holder.tvSpotName.text = spot.name

        // Описание
        holder.tvWeight.text = when {
            spot.catchWeight <= 0.0 -> "⚖️ Улов не указан"
            spot.catchWeight <= 1.0 -> "⚖️ До 1 кг"
            spot.catchWeight <= 5.0 -> "⚖️ 1–5 кг"
            else -> "⚖️ Больше 5 кг"
        }
        holder.tvFish.text = if (!spot.fishType.isNullOrEmpty()) "🐟 ${spot.fishType}" else "🐟 Не указана"
        holder.tvCoordinates.text = "📍 %.4f, %.4f".format(spot.latitude, spot.longitude)

        // Фото
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

        // ✅ КЛИКИ
        holder.btnViewOnMap.setOnClickListener { onMapClick(spot) }

        // Открываем шторку комментариев по ID точки
        holder.btnComments.setOnClickListener {
            onCommentsClick(spot.documentId)
        }

        // Заглушка для лайков
        holder.layoutLike.setOnClickListener { /* TODO */ }
    }

    override fun getItemCount() = items.size
}