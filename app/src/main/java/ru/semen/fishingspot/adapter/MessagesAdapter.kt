package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.databinding.ItemMessageBinding
import ru.semen.fishingspot.databinding.ItemSpotRequestBinding
import ru.semen.fishingspot.databinding.ItemSpotSharedBinding // ✅ Новый импорт
import ru.semen.fishingspot.data.AppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MessagesAdapter(
    private val onSenderClick: (AppMessage) -> Unit,
    private val onMessageClick: (AppMessage) -> Unit,
    private val onShareRequestClick: ((AppMessage) -> Unit)? = null,
    private val onDeclineRequestClick: ((AppMessage) -> Unit)? = null,
    private val onTakeSpotClick: ((AppMessage) -> Unit)? = null // ✅ Новый колбэк
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<AppMessage> = emptyList()

    companion object {
        const val VIEW_TYPE_NEW_REVIEW = 0
        const val VIEW_TYPE_SPOT_REQUEST = 1
        const val VIEW_TYPE_SPOT_SHARED = 2 // ✅ Теперь он обрабатывается!
        const val VIEW_TYPE_NEW_FRIEND = 3
        const val VIEW_TYPE_SYSTEM = 4
    }

    fun submitList(newList: List<AppMessage>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val typeStr = items[position].type.uppercase()
        return when {
            typeStr == "NEW_REVIEW" -> VIEW_TYPE_NEW_REVIEW
            typeStr == "SPOT_REQUEST" || typeStr == "REQUEST_SPOT" -> VIEW_TYPE_SPOT_REQUEST
            typeStr == "SPOT_SHARED" -> VIEW_TYPE_SPOT_SHARED // ✅
            typeStr == "NEW_FRIEND" -> VIEW_TYPE_NEW_FRIEND
            typeStr == "SYSTEM" -> VIEW_TYPE_SYSTEM
            else -> VIEW_TYPE_NEW_REVIEW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_NEW_REVIEW -> {
                val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                NewReviewViewHolder(binding)
            }
            VIEW_TYPE_SPOT_REQUEST -> {
                val binding = ItemSpotRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SpotRequestViewHolder(binding)
            }
            VIEW_TYPE_SPOT_SHARED -> { // ✅ Создаем ViewHolder для полученной точки
                val binding = ItemSpotSharedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SpotSharedViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        when (holder) {
            is NewReviewViewHolder -> holder.bind(message)
            is SpotRequestViewHolder -> holder.bind(message)
            is SpotSharedViewHolder -> holder.bind(message) // ✅
        }
    }

    override fun getItemCount() = items.size

    inner class NewReviewViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: AppMessage) {
            val authorName = message.senderName.ifEmpty { "Рыбак" }
            binding.tvSenderName.text = authorName
            binding.tvMessageAction.text = "оставил отзыв"
            binding.tvMessagePreview.text = message.contentText.ifEmpty { "(без текста)" }
            binding.tvMessageTime.text = "На точке '${message.spotName}' • ${getTimeAgo(message.timestamp)}"
            binding.viewUnreadIndicator.visibility = if (message.isRead) View.GONE else View.VISIBLE
            binding.tvSenderName.setOnClickListener { onSenderClick(message) }
            binding.root.setOnClickListener { onMessageClick(message) }
        }
    }

    inner class SpotRequestViewHolder(private val binding: ItemSpotRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: AppMessage) {
            val authorName = message.senderName.ifEmpty { "Рыбак" }
            binding.tvRequestAuthorName.text = authorName
            binding.tvRequestAvatar.text = authorName.firstOrNull()?.uppercase() ?: "?"
            binding.tvRequestTime.text = getTimeAgo(message.timestamp)
            binding.tvRequestMessage.text = message.customMessage.ifEmpty { "(без текста)" }
            binding.tvRequestAuthorName.setOnClickListener { onSenderClick(message) }
            binding.btnShareSpot.setOnClickListener { onShareRequestClick?.invoke(message) }
            binding.btnDeclineRequest.setOnClickListener { onDeclineRequestClick?.invoke(message) }
        }
    }

    // ✅ НОВЫЙ ViewHolder для отображения полученной точки
    inner class SpotSharedViewHolder(private val binding: ItemSpotSharedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: AppMessage) {
            val authorName = message.senderName.ifEmpty { "Рыбак" }
            binding.tvSharedAuthorName.text = authorName
            binding.tvSharedAvatar.text = authorName.firstOrNull()?.uppercase() ?: "?"
            binding.tvSharedTime.text = getTimeAgo(message.timestamp)
            binding.tvSharedSpotName.text = message.spotName.ifEmpty { "Без названия" }

            binding.tvSharedAuthorName.setOnClickListener { onSenderClick(message) }
            binding.btnTakeSpot.setOnClickListener { onTakeSpotClick?.invoke(message) }
            binding.root.setOnClickListener { onMessageClick(message) }
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} мин назад"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} ч назад"
            diff < TimeUnit.DAYS.toMillis(2) -> "вчера"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }
}