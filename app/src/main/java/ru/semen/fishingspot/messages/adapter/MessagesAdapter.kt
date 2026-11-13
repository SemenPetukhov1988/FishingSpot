package ru.semen.fishingspot.messages.adapter

import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private val onMessageClick: ((FeedItem) -> Unit)? = null,
    private val onShowMapClick: ((FeedItem) -> Unit)? = null,
    // ✅ ИЗМЕНЁННАЯ СИГНАТУРА: принимаем anchorId и receiverId как строки
    private val onWishGoodFishingClick: ((String, String) -> Unit)? = null,

    // ✅ НОВЫЕ КОЛБЭКИ ДЛЯ ЗАПРОСОВ В ДРУЗЬЯ
    private val onAcceptFriendClick: ((senderId: String, messageId: String) -> Unit)? = null,
    private val onDeleteMessageClick: ((messageId: String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<FeedItem> = emptyList()

    companion object {
        const val VIEW_TYPE_FRIEND_MESSAGE = 0
        const val VIEW_TYPE_INCOMING_SIGNAL = 1
        const val VIEW_TYPE_ANCHOR_GROUP = 2
        const val VIEW_TYPE_FRIEND_REQUEST = 3 // ✅ НОВЫЙ ТИП ДЛЯ NEW_FRIEND
    }

    fun submitList(newList: List<FeedItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
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
            VIEW_TYPE_FRIEND_REQUEST -> FriendRequestViewHolder(ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)) // ✅ СПЕЦИАЛЬНЫЙ ХОЛДЕР
            VIEW_TYPE_INCOMING_SIGNAL -> IncomingSignalViewHolder(ItemOnFishingSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            VIEW_TYPE_ANCHOR_GROUP -> AnchorGroupViewHolder(ItemAnchorSignalBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is FriendMsgViewHolder -> holder.bind(item as FriendMessageItem)
            is FriendRequestViewHolder -> holder.bind(item as FriendMessageItem) // ✅ БИНДИМ ЧЕРЕЗ СПЕЦ. ХОЛДЕР
            is IncomingSignalViewHolder -> holder.bind(item as SignalItem, onShowMapClick, onWishGoodFishingClick)
            is AnchorGroupViewHolder -> holder.bind(item as AnchorGroupItem)
        }
    }

    override fun getItemCount() = items.size

    // --- ОБЫЧНОЕ СООБЩЕНИЕ ---
    inner class FriendMsgViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendMessageItem) {
            val msg = item.message

            // ✅ СПЕЦИАЛЬНАЯ ОБРАБОТКА ДЛЯ УВЕДОМЛЕНИЙ О ПРИНЯТИИ ДРУЖБЫ
            if (msg.type == "FRIEND_ACCEPTED") {
                // Имя отправителя уведомления (кто принял)
                binding.tvSenderName.text = msg.senderName.ifEmpty { "Рыбак" }

                // Текст уведомления переносим во вторую строку или делаем основным контентом
                // Если у тебя есть tvMessageAction - используем его, если нет - можно поменять цвет/размер tvSenderName
                binding.tvMessageAction.apply {
                    text = msg.contentText.ifEmpty { "Принял(а) вашу заявку в друзья" }
                    visibility = View.VISIBLE
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.gray_600)) // Серый цвет для вторичного текста
                }

                // Скрываем превью точки, так как это системное сообщение
                binding.tvMessagePreview.visibility = View.GONE

            } else {
                // ✅ СТАНДАРТНОЕ ПОВЕДЕНИЕ ДЛЯ ВСЕХ ОСТАЛЬНЫХ СООБЩЕНИЙ
                binding.tvSenderName.text = msg.senderName.ifEmpty { "Рыбак" }
                binding.tvMessageAction.text = msg.contentText.ifEmpty { "(без текста)" }
                binding.tvMessagePreview.visibility = View.GONE
            }

            binding.tvMessageTime.text = getTimeAgo(msg.timestamp)
            binding.viewUnreadIndicator.visibility = if (msg.isRead) View.GONE else View.VISIBLE

            binding.root.setOnClickListener { onMessageClick?.invoke(item) }
            binding.tvSenderName.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    // ✅ СПЕЦИАЛЬНЫЙ ХОЛДЕР ДЛЯ ЗАПРОСА В ДРУЗЬЯ
    inner class FriendRequestViewHolder(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendMessageItem) {
            val msg = item.message

            // Заполняем шапку
            binding.tvFriendReqAvatar.text = msg.senderName.firstOrNull()?.uppercase() ?: "?"
            binding.tvFriendReqAuthorName.text = msg.senderName.ifEmpty { "Рыбак" }
            binding.tvFriendReqTime.text = getTimeAgo(msg.timestamp)

            // Кнопки действий
            binding.btnAcceptFriend.setOnClickListener {
                onAcceptFriendClick?.invoke(msg.senderId, msg.id)
            }

            binding.btnDeclineFriend.setOnClickListener {
                onDeleteMessageClick?.invoke(msg.id)
            }
        }
    }

    // --- ВХОДЯЩИЙ СИГНАЛ (КАРТА) ---
    inner class IncomingSignalViewHolder(private val binding: ItemOnFishingSignalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SignalItem, onShowMap: ((FeedItem) -> Unit)?, onWish: ((String, String) -> Unit)?) {
            val sig = item.signal

            Log.d("LESHA_DEBUG", "👁️ Входящий сигнал type='${sig.type}'")
            Log.d("LESHA_DEBUG", "   signal.id = ${sig.id}")
            Log.d("LESHA_DEBUG", "   signal.anchorId = '${sig.anchorId}'")

            binding.tvSenderName.text = "${sig.senderName} сейчас на рыбалке! 🎣"
            binding.tvTime.text = getTimeAgo(sig.createdAt)

            binding.containerReplies.removeAllViews()
            binding.containerReplies.visibility = View.GONE

            binding.btnShowOnMap.setOnClickListener { onShowMap?.invoke(item) }

            // ✅ ЯВНАЯ ПЕРЕДАЧА ANCHOR_ID И SENDER_ID ПРИ КЛИКЕ
            binding.btnWishGoodFishing.setOnClickListener {
                val anchorId = sig.anchorId
                val senderId = sig.senderId
                Log.d("LESHA_DEBUG", "👆 Клик 'Хорошей рыбалки'. Отправляю anchorId='$anchorId' receiverId='$senderId'")
                onWish?.invoke(anchorId, senderId)
            }

            binding.tvSenderName.setOnClickListener { onSenderClick?.invoke(item) }
        }
    }

    // --- ГРУППА ЯКОРЬ + ОТВЕТЫ ---
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

            binding.root.setOnClickListener { onMessageClick?.invoke(item) }
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

fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()