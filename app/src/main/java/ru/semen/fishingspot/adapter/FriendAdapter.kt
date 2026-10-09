package ru.semen.fishingspot.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.data.Friend
import ru.semen.fishingspot.databinding.ItemFriendBinding

class FriendAdapter(
    private val onShareClick: (Friend) -> Unit,
    private val onRequestClick: (Friend) -> Unit,
    private val onRemoveClick: (Friend) -> Unit
) : ListAdapter<Friend, FriendAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(private val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: Friend) {
            binding.tvFriendName.text = friend.name
            binding.tvFriendStatus.text = friend.status

            // Первая буква имени для аватара-заглушки
            val firstLetter = if (friend.name.isNotEmpty()) {
                friend.name[0].uppercaseChar().toString()
            } else {
                "?"
            }
            binding.tvAvatarLetter.text = firstLetter

            // Клики по кнопкам
            binding.btnShareSpot.setOnClickListener { onShareClick(friend) }
            binding.btnRequestSpot.setOnClickListener { onRequestClick(friend) }
            binding.btnRemoveFriend.setOnClickListener { onRemoveClick(friend) }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(oldItem: Friend, newItem: Friend): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Friend, newItem: Friend): Boolean {
            return oldItem == newItem
        }
    }
}