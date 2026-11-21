package ru.semen.fishingspot.messages.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.*
import ru.semen.fishingspot.messages.data.AnchorGroupItem
import ru.semen.fishingspot.messages.data.FeedItem
import ru.semen.fishingspot.messages.data.FriendMessageItem
import ru.semen.fishingspot.messages.data.GoingFishingAnchorItem
import ru.semen.fishingspot.messages.data.SignalItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MessagesAdapter(
    private val onSenderClick: ((FeedItem) -> Unit)? = null,
    private val onShowMapClick: ((FeedItem) -> Unit)? = null,
    private val onWishGoodFishingClick: ((String, String) -> Unit)? = null,
    private val onAcceptFriendClick: ((senderId: String, messageId: String) -> Unit)? = null,
    private val onDeleteMessageClick: ((messageId: String) -> Unit)? = null,
    private val onShowDetailsClick: ((lat: Double, lon: Double, dateMillis: Long?) -> Unit)? = null,
    private val onReplyToGoingFishingClick: ((lat: Double, lon: Double, anchorId: String, senderId: String) -> Unit)? = null,
    private val onRecommendSpotClick: ((anchorId: String, senderId: String) -> Unit)? = null,
    // ✅ НОВЫЙ КОЛБЕК ДЛЯ ОТКРЫТИЯ КАРТЫ РЕКОМЕНДАЦИИ
    private val onShowRecommendationMapClick: ((lat: Double, lon: Double, senderName: String) -> Unit)? = null
) : ListAdapter<FeedItem, RecyclerView.ViewHolder>(FeedItemDiffCallback()) {

    companion object {
        const val VIEW_TYPE_FRIEND_MESSAGE = 0
        const val VIEW_TYPE_INCOMING_SIGNAL = 1
        const val VIEW_TYPE_ANCHOR_GROUP = 2
        const val VIEW_TYPE_FRIEND_REQUEST = 3
        const val VIEW_TYPE_GOING_FISHING_ANCHOR_ITEM = 4
        const val VIEW_TYPE_GOING_FISHING_SIGNAL = 5
        const val VIEW_TYPE_SPOT_REQUEST_SIGNAL = 6
    }

    fun getItemAt(position: Int): FeedItem? =
        if (position in 0 until itemCount) getItem(position) else null

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is FriendMessageItem -> {
            val item = getItem(position) as FriendMessageItem
            if (item.message.type == "NEW_FRIEND") VIEW_TYPE_FRIEND_REQUEST else VIEW_TYPE_FRIEND_MESSAGE
        }
        is SignalItem -> {
            val signal = getItem(position) as SignalItem
            when (signal.signal.type) {
                "going_fishing" -> VIEW_TYPE_GOING_FISHING_SIGNAL
                "request_spot" -> VIEW_TYPE_SPOT_REQUEST_SIGNAL
                else -> VIEW_TYPE_INCOMING_SIGNAL
            }
        }
        is AnchorGroupItem -> VIEW_TYPE_ANCHOR_GROUP
        is GoingFishingAnchorItem -> VIEW_TYPE_GOING_FISHING_ANCHOR_ITEM
        else -> throw IllegalArgumentException("Unknown item type")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_FRIEND_MESSAGE -> FriendMsgViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_FRIEND_REQUEST -> FriendRequestViewHolder(ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_INCOMING_SIGNAL -> IncomingSignalViewHolder(ItemOnFishingSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_ANCHOR_GROUP -> AnchorGroupViewHolder(ItemAnchorSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_GOING_FISHING_ANCHOR_ITEM -> GoingFishingAnchorViewHolder(ItemGoingFishingAnchorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_GOING_FISHING_SIGNAL -> GoingFishingSignalViewHolder(ItemGoingFishingSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        VIEW_TYPE_SPOT_REQUEST_SIGNAL -> SpotRequestSignalViewHolder(ItemGoingFishingSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> throw IllegalArgumentException("Unknown view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is FriendMsgViewHolder -> holder.bind(item as FriendMessageItem)
            is FriendRequestViewHolder -> holder.bind(item as FriendMessageItem)
            is IncomingSignalViewHolder -> holder.bind(item as SignalItem, onShowMapClick, onWishGoodFishingClick)
            is AnchorGroupViewHolder -> holder.bind(item as AnchorGroupItem, onShowRecommendationMapClick)
            is GoingFishingAnchorViewHolder -> holder.bind(item as GoingFishingAnchorItem, onShowDetailsClick)
            is GoingFishingSignalViewHolder -> holder.bind(item as SignalItem, onReplyToGoingFishingClick)
            is SpotRequestSignalViewHolder -> holder.bind(item as SignalItem, onRecommendSpotClick)
        }
    }

    inner class FriendMsgViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendMessageItem) {
            val msg = item.message
            binding.tvSenderName.text = msg.senderName.ifEmpty { "Рыбак" }
            binding.tvMessageAction.text = msg.contentText.ifEmpty { "(без текста)" }
            binding.tvMessageTime.text = getTimeAgo(msg.timestamp)
            binding.viewUnreadIndicator.visibility = if (msg.isRead) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    inner class FriendRequestViewHolder(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendMessageItem) {
            val msg = item.message
            binding.tvFriendReqAvatar.text = msg.senderName.firstOrNull()?.uppercase() ?: "?"
            binding.tvFriendReqAuthorName.text = msg.senderName.ifEmpty { "Рыбак" }
            binding.tvFriendReqTime.text = getTimeAgo(msg.timestamp)
            binding.btnAcceptFriend.setOnClickListener { onAcceptFriendClick?.invoke(msg.senderId, msg.id) }
            binding.btnDeclineFriend.setOnClickListener { onDeleteMessageClick?.invoke(msg.id) }
        }
    }

    inner class IncomingSignalViewHolder(private val binding: ItemOnFishingSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SignalItem, onShowMap: ((FeedItem) -> Unit)?, onWish: ((String, String) -> Unit)?) {
            val sig = item.signal
            binding.btnShowOnMap.setOnClickListener(null)
            binding.btnWishGoodFishing.setOnClickListener(null)
            binding.tvSenderName.setOnClickListener(null)

            binding.tvSenderName.text = "${sig.senderName} сейчас на рыбалке! 🎣"
            binding.tvTime.text = getTimeAgo(sig.createdAt)
            binding.btnShowOnMap.visibility = View.VISIBLE
            binding.btnShowOnMap.setOnClickListener { onShowMap?.invoke(item) }
            binding.btnWishGoodFishing.setOnClickListener { onWish?.invoke(sig.anchorId, sig.senderId) }
            binding.tvSenderName.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    // ✅ ОБНОВЛЕННЫЙ ХОЛДЕР ЯКОРЯ С ПОДДЕРЖКОЙ РАЗНЫХ ТИПОВ ОТВЕТОВ
    inner class AnchorGroupViewHolder(private val binding: ItemAnchorSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AnchorGroupItem, onShowRecMap: ((Double, Double, String) -> Unit)?) {
            val baseText = item.anchorMessage.contentText
            val comment = try {
                val field = item.anchorMessage.javaClass.getDeclaredField("requestComment")
                field.isAccessible = true
                field.get(item.anchorMessage) as? String
            } catch (_: Exception) { null }

            binding.tvAnchorText.text = if (!comment.isNullOrEmpty() && item.anchorMessage.type == "ANCHOR_SPOT_REQUEST") {
                "$baseText\n\n💬 \"$comment\""
            } else {
                baseText
            }

            binding.tvAnchorTime.text = getTimeAgo(item.anchorMessage.timestamp)
            binding.containerReplies.removeAllViews()
            binding.containerReplies.visibility = if (item.replies.isEmpty()) View.GONE else View.VISIBLE

            item.replies.forEach { reply ->
                // ✅ ВЫБИРАЕМ LAYOUT В ЗАВИСИМОСТИ ОТ ТИПА ОТВЕТА
                val replyView = if (reply.type == "spot_recommendation") {
                    LayoutInflater.from(binding.root.context)
                        .inflate(R.layout.item_spot_recommendation_reply, binding.containerReplies, false).apply {

                            // ✅ ФОРМИРУЕМ ТЕКСТ С УЧЕТОМ НАЛИЧИЯ КОММЕНТАРИЯ
                            val recTextView = findViewById<TextView>(R.id.tvRecText)
                            val userComment = reply.messageText?.ifBlank { null }

                            recTextView.text = if (userComment != null) {
                                "${reply.senderName} рекомендует точку! 📍\n💬 \"$userComment\""
                            } else {
                                "${reply.senderName} рекомендует точку! 📍"
                            }

                            findViewById<MaterialButton>(R.id.btnShowRecMap).setOnClickListener {
                                onShowRecMap?.invoke(reply.lat, reply.lon, reply.senderName)
                            }
                        }
                } else {
                    // Стандартный layout для обычных ответов
                    LayoutInflater.from(binding.root.context)
                        .inflate(R.layout.item_reply_signal, binding.containerReplies, false).apply {
                            val tvText = findViewById<TextView>(R.id.tvReplyText)
                            val tvTime = findViewById<TextView>(R.id.tvReplyTime)

                            val replyContent = when (reply.type) {
                                "fishing_reply" -> "${reply.senderName}: \"${reply.messageText ?: ""}\""
                                "fishing_wish" -> "${reply.senderName} пожелал хорошей рыбалки! 🎣"
                                else -> "${reply.senderName}: ${reply.messageText ?: "(без текста)"}"
                            }
                            tvText.text = replyContent
                            tvTime.text = getTimeAgo(reply.createdAt)
                        }
                }

                binding.containerReplies.addView(replyView)
            }

            binding.root.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    inner class GoingFishingAnchorViewHolder(private val binding: ItemGoingFishingAnchorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GoingFishingAnchorItem, onClick: ((Double, Double, Long?) -> Unit)?) {
            binding.tvAnchorTitle.text = item.contentText
            val dateStr = if (item.plannedDate > 0) {
                "📅 ${SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(item.plannedDate))}"
            } else ""
            binding.tvPlannedDate.text = dateStr
            binding.tvPlannedDate.visibility = if (dateStr.isNotEmpty()) View.VISIBLE else View.GONE

            binding.containerReplies.removeAllViews()
            binding.containerReplies.visibility = if (item.replies.isEmpty()) View.GONE else View.VISIBLE

            item.replies.forEach { reply ->
                val replyView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.item_reply_signal, binding.containerReplies, false)
                val tvText = replyView.findViewById<TextView>(R.id.tvReplyText)
                val tvTime = replyView.findViewById<TextView>(R.id.tvReplyTime)
                val replyContent = when (reply.type) {
                    "fishing_reply" -> "${reply.senderName}: \"${reply.messageText ?: ""}\""
                    else -> "${reply.senderName}: ${reply.messageText ?: "(без текста)"}"
                }
                tvText.text = replyContent
                tvTime.text = getTimeAgo(reply.createdAt)
                binding.containerReplies.addView(replyView)
            }

            binding.tvRepliesCount.text = "${item.replies.size} ответ(ов)"
            binding.tvRepliesCount.visibility = if (item.replies.isEmpty()) View.GONE else View.VISIBLE
            binding.btnShowDetails.setOnClickListener { onClick?.invoke(item.spotLat, item.spotLon, item.plannedDate) }
        }
    }

    inner class GoingFishingSignalViewHolder(private val binding: ItemGoingFishingSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SignalItem, onReply: ((Double, Double, String, String) -> Unit)?) {
            val sig = item.signal
            val dateText = if (sig.plannedDate > 0) {
                val formattedDate = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(sig.plannedDate))
                "${sig.senderName} спрашивает: стоит ли туда ехать на рыбалку $formattedDate? 🎣"
            } else {
                "${sig.senderName} спрашивает: стоит ли туда ехать на рыбалку? 🎣"
            }
            binding.tvSenderName.text = dateText
            binding.tvTime.text = getTimeAgo(sig.createdAt)
            binding.btnShowDetails.text = "📍 Посмотреть на карте и ответить"
            binding.btnShowDetails.setOnClickListener { onReply?.invoke(sig.lat, sig.lon, sig.anchorId, sig.senderId) }
            binding.root.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    inner class SpotRequestSignalViewHolder(private val binding: ItemGoingFishingSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SignalItem, onRecommend: ((String, String) -> Unit)?) {
            val sig = item.signal
            val dateText = if (sig.plannedDate > 0) {
                val formattedDate = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(sig.plannedDate))
                "${sig.senderName} просит дать точку для рыбалки на $formattedDate 🎣"
            } else {
                "${sig.senderName} просит дать точку для рыбалки 🎣"
            }
            val fullText = if (!sig.messageText.isNullOrEmpty()) {
                "$dateText\n\n💬 \"${sig.messageText}\""
            } else {
                dateText
            }
            binding.tvSenderName.text = fullText
            binding.tvTime.text = getTimeAgo(sig.createdAt)
            binding.btnShowDetails.text = "📍 Предложить свое место"
            binding.btnShowDetails.setOnClickListener { onRecommend?.invoke(sig.anchorId, sig.senderId) }
            binding.root.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    class FeedItemDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
        override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean = when {
            oldItem is FriendMessageItem && newItem is FriendMessageItem -> oldItem.message.id == newItem.message.id
            oldItem is SignalItem && newItem is SignalItem -> oldItem.signal.id == newItem.signal.id
            oldItem is AnchorGroupItem && newItem is AnchorGroupItem -> oldItem.anchorMessage.id == newItem.anchorMessage.id
            oldItem is GoingFishingAnchorItem && newItem is GoingFishingAnchorItem -> oldItem.id == newItem.id
            else -> false
        }
        override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem) = oldItem == newItem
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