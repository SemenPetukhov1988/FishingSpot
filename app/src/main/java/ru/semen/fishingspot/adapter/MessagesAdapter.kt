package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.AppMessage
import ru.semen.fishingspot.data.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Адаптер для списка сообщений.
 * Поддерживает разные типы карточек через ViewType.
 * Сейчас реализован только тип NEW_REVIEW, но структура готова для расширения.
 */
class MessagesAdapter(
    private val onSenderClick: (AppMessage) -> Unit,   // Клик по имени → профиль
    private val onMessageClick: (AppMessage) -> Unit   // Клик по карточке → точка
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<AppMessage> = emptyList()

    // Константы для разных типов View (для будущих расширений)
    companion object {
        const val VIEW_TYPE_NEW_REVIEW = 0
        const val VIEW_TYPE_SPOT_REQUEST = 1   // Зарезервировано на будущее
        const val VIEW_TYPE_SPOT_SHARED = 2    // Зарезервировано на будущее
        const val VIEW_TYPE_NEW_FRIEND = 3     // Зарезервировано на будущее
        const val VIEW_TYPE_SYSTEM = 4         // Зарезервировано на будущее
    }

    fun submitList(newList: List<AppMessage>) {
        items = newList
        notifyDataSetChanged()
    }

    /**
     * Определяет тип карточки по типу сообщения.
     * Сейчас все сообщения — это NEW_REVIEW, но когда добавим другие типы,
     * просто расширим этот when.
     */
    override fun getItemViewType(position: Int): Int {
        return when (items[position].type) {
            MessageType.NEW_REVIEW -> VIEW_TYPE_NEW_REVIEW
            MessageType.SPOT_REQUEST -> VIEW_TYPE_SPOT_REQUEST
            MessageType.SPOT_SHARED -> VIEW_TYPE_SPOT_SHARED
            MessageType.NEW_FRIEND -> VIEW_TYPE_NEW_FRIEND
            MessageType.SYSTEM -> VIEW_TYPE_SYSTEM
        }
    }

    /**
     * Создает ViewHolder в зависимости от типа.
     * Сейчас только один тип, но структура готова для расширения.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_NEW_REVIEW -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message, parent, false)
                NewReviewViewHolder(view)
            }
            // Когда добавим другие типы карточек, просто добавим сюда новые ветки:
            // VIEW_TYPE_SPOT_REQUEST -> SpotRequestViewHolder(...)
            // VIEW_TYPE_SPOT_SHARED -> SpotSharedViewHolder(...)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        when (holder) {
            is NewReviewViewHolder -> holder.bind(message)
            // Когда добавим другие типы, добавим сюда:
            // is SpotRequestViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount() = items.size

    /**
     * ViewHolder для карточки "Новый отзыв".
     * Именно здесь мы обрабатываем клики и форматируем данные.
     */
    inner class NewReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivMessageIcon)
        private val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
        private val tvMessageAction: TextView = view.findViewById(R.id.tvMessageAction)
        private val tvPreview: TextView = view.findViewById(R.id.tvMessagePreview)
        private val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
        private val viewUnread: View = view.findViewById(R.id.viewUnreadIndicator)

        fun bind(message: AppMessage) {
            // Имя отправителя (кликабельное → профиль)
            tvSenderName.text = message.senderName.ifEmpty { "Рыбак" }
            tvSenderName.setOnClickListener {
                onSenderClick(message)
            }

            // Действие (статичный текст)
            tvMessageAction.text = "оставил отзыв"

            // Превью текста отзыва
            tvPreview.text = message.contentText.ifEmpty { "(без текста)" }

            // Время и название точки
            val timeAgo = getTimeAgo(message.timestamp)
            tvTime.text = "На вашей точке '${message.spotName}' • $timeAgo"

            // Индикатор непрочитанного
            viewUnread.visibility = if (message.isRead) View.GONE else View.VISIBLE

            // Клик по всей карточке → переход к точке
            itemView.setOnClickListener {
                onMessageClick(message)
            }
        }
    }

    /**
     * Красиво форматирует время: "только что", "5 мин назад", "2 часа назад", "вчера", "5 окт"
     */
    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$mins мин назад"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours ч назад"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> "вчера"
            else -> {
                val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}