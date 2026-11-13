package ru.semen.fishingspot.messages.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.databinding.ItemFriendRecipientBinding

// ✅ Модель данных для адаптера
data class FriendItem(val id: String, val name: String)

class FriendRecipientAdapter(
    private val onFriendClick: (String) -> Unit // Всё ещё возвращаем только ID при клике
) : androidx.recyclerview.widget.ListAdapter<FriendItem, FriendRecipientAdapter.FriendViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendRecipientBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(private val binding: ItemFriendRecipientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: FriendItem) {
            // ✅ ТЕПЕРЬ ПОКАЗЫВАЕМ ИМЯ
            binding.tvFriendName.text = friend.name

            binding.root.setOnClickListener { onFriendClick(friend.id) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FriendItem>() {
        override fun areItemsTheSame(oldItem: FriendItem, newItem: FriendItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FriendItem, newItem: FriendItem) =
            oldItem == newItem
    }
}