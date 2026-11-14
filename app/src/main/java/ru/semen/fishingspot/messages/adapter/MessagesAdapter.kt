package ru.semen.fishingspot.messages.adapter

import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.*
import ru.semen.fishingspot.messages.data.AnchorGroupItem
import ru.semen.fishingspot.messages.data.FeedItem
import ru.semen.fishingspot.messages.data.FriendMessageItem
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
    private val onDeleteMessageClick: ((messageId: String) -> Unit)? = null
) : ListAdapter<FeedItem, RecyclerView.ViewHolder>(FeedItemDiffCallback()) {

    companion object {
        const val VIEW_TYPE_FRIEND_MESSAGE = 0
        const val VIEW_TYPE_INCOMING_SIGNAL = 1
        const val VIEW_TYPE_ANCHOR_GROUP = 2
        const val VIEW_TYPE_FRIEND_REQUEST = 3
    }

    // ✅ ПУБЛИЧНЫЙ МЕТОД ДЛЯ БЕЗОПАСНОГО ПОЛУЧЕНИЯ ЭЛЕМЕНТА ИЗ ФРАГМЕНТА
    fun getItemAt(position: Int): FeedItem? {
        return if (position in 0 until itemCount) {
            getItem(position)
        } else null
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is FriendMessageItem ->
                if (item.message.type == "NEW_FRIEND") VIEW_TYPE_FRIEND_REQUEST
                else VIEW_TYPE_FRIEND_MESSAGE
            is SignalItem -> VIEW_TYPE_INCOMING_SIGNAL
            is AnchorGroupItem -> VIEW_TYPE_ANCHOR_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_FRIEND_MESSAGE -> FriendMsgViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_FRIEND_REQUEST -> FriendRequestViewHolder(ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_INCOMING_SIGNAL -> IncomingSignalViewHolder(ItemOnFishingSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_ANCHOR_GROUP -> AnchorGroupViewHolder(ItemAnchorSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is FriendMsgViewHolder -> holder.bind(item as FriendMessageItem)
            is FriendRequestViewHolder -> holder.bind(item as FriendMessageItem)
            is IncomingSignalViewHolder -> holder.bind(item as SignalItem, onShowMapClick, onWishGoodFishingClick)
            is AnchorGroupViewHolder -> holder.bind(item as AnchorGroupItem)
        }
    }

    inner class FriendMsgViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendMessageItem) {
            val msg = item.message
            if (msg.type == "FRIEND_ACCEPTED") {
                binding.tvSenderName.text = msg.senderName.ifEmpty { "Рыбак" }
                binding.tvMessageAction.apply {
                    text = msg.contentText.ifEmpty { "Принял(а) вашу заявку в друзья" }
                    visibility = View.VISIBLE
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.gray_600))
                }
                binding.tvMessagePreview.visibility = View.GONE
            } else {
                binding.tvSenderName.text = msg.senderName.ifEmpty { "Рыбак" }
                binding.tvMessageAction.text = msg.contentText.ifEmpty { "(без текста)" }
                binding.tvMessagePreview.visibility = View.GONE
            }
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
            Log.d("LESHA_DEBUG", "👁️ Входящий сигнал type='${sig.type}' anchorId='${sig.anchorId}'")
            binding.tvSenderName.text = "${sig.senderName} сейчас на рыбалке! 🎣"
            binding.tvTime.text = getTimeAgo(sig.createdAt)
            binding.containerReplies.removeAllViews()
            binding.containerReplies.visibility = View.GONE
            binding.btnShowOnMap.setOnClickListener { onShowMap?.invoke(item) }
            binding.btnWishGoodFishing.setOnClickListener {
                onWish?.invoke(sig.anchorId, sig.senderId)
            }
            binding.tvSenderName.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    inner class AnchorGroupViewHolder(private val binding: ItemAnchorSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AnchorGroupItem) {
            binding.tvAnchorText.text = item.anchorMessage.contentText
            binding.tvAnchorTime.text = getTimeAgo(item.anchorMessage.timestamp)
            binding.containerReplies.removeAllViews()
            binding.containerReplies.visibility = if (item.replies.isEmpty()) View.GONE else View.VISIBLE
            item.replies.forEach { reply ->
                val replyView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.item_reply_signal, binding.containerReplies, false)
                val tvText = replyView.findViewById<TextView>(R.id.tvReplyText)
                val tvTime = replyView.findViewById<TextView>(R.id.tvReplyTime)
                tvText.text = "${reply.senderName} пожелал хорошей рыбалки! 🎣"
                tvTime.text = getTimeAgo(reply.createdAt)
                binding.containerReplies.addView(replyView)
            }
            binding.root.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    class FeedItemDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
        override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
            return when {
                oldItem is FriendMessageItem && newItem is FriendMessageItem -> oldItem.message.id == newItem.message.id
                oldItem is SignalItem && newItem is SignalItem -> oldItem.signal.id == newItem.signal.id
                oldItem is AnchorGroupItem && newItem is AnchorGroupItem -> oldItem.anchorMessage.id == newItem.anchorMessage.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
            return oldItem == newItem
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