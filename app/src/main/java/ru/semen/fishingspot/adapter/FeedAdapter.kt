package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot // ✅ Импортируем нашу главную модель

class FeedAdapter(
    private val onSaveClick: (FishingSpot) -> Unit,
    private val onCommentClick: (FishingSpot) -> Unit,
    private val onConfirmClick: (FishingSpot) -> Unit,
    private val onDisputeClick: (FishingSpot) -> Unit
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    // ✅ Теперь используем List<FishingSpot>
    private var items: List<FishingSpot> = emptyList()

    fun submitList(newList: List<FishingSpot>) {
        items = newList
        notifyDataSetChanged()
    }

    class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivFeedPhoto)
        val ivPlaceholder: ImageView = view.findViewById(R.id.ivFeedPhotoPlaceholder)
        val tvName: TextView = view.findViewById(R.id.tvFeedSpotName)
        val tvCoords: TextView = view.findViewById(R.id.tvFeedCoordinates)
        val tvWeight: TextView = view.findViewById(R.id.tvFeedWeight)
        val tvFish: TextView = view.findViewById(R.id.tvFeedFish)

        val cardSuspiciousWarning: MaterialCardView = view.findViewById(R.id.cardSuspiciousWarning)
        val btnConfirmSpot: MaterialButton = view.findViewById(R.id.btnConfirmSpot)
        val btnDisputeSpot: MaterialButton = view.findViewById(R.id.btnDisputeSpot)

        val btnSaveToMyMap: View = view.findViewById(R.id.btnSaveToMyMap)
        val btnFeedComments: View = view.findViewById(R.id.btnFeedComments)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed_spot, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val spot = items[position]

        // Заполняем текстовые поля
        holder.tvName.text = spot.name
        holder.tvCoords.text = "📍 %.4f, %.4f".format(spot.latitude, spot.longitude)

        holder.tvWeight.text = when {
            spot.catchWeight <= 0.0 -> "⚖️ Не указан"
            spot.catchWeight <= 1.0 -> "⚖️ До 1 кг"
            spot.catchWeight <= 5.0 -> "⚖️ 1–5 кг"
            else -> "⚖️ Больше 5 кг"
        }

        // ✅ УЛУЧШЕНИЕ: Теперь берем рыбу из отдельного поля fishType, а не парсим описание!
        holder.tvFish.text = if (!spot.fishType.isNullOrEmpty()) {
            "🐟 ${spot.fishType}"
        } else {
            "🐟 Не указана"
        }

        // Фото (пока заглушка, потом подключим Glide/Coil)
        holder.ivPhoto.visibility = View.GONE
        holder.ivPlaceholder.visibility = View.VISIBLE

        // Предупреждение о сомнительной точке
        holder.cardSuspiciousWarning.visibility = if (spot.isVerified) View.GONE else View.VISIBLE

        // Клики
        holder.btnSaveToMyMap.setOnClickListener { onSaveClick(spot) }
        holder.btnFeedComments.setOnClickListener { onCommentClick(spot) }
        holder.btnConfirmSpot.setOnClickListener { onConfirmClick(spot) }
        holder.btnDisputeSpot.setOnClickListener { onDisputeClick(spot) }
    }

    override fun getItemCount() = items.size
}