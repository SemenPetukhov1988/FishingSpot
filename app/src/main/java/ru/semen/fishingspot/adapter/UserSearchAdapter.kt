package ru.semen.fishingspot.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.data.User
import ru.semen.fishingspot.databinding.ItemUserSearchBinding // Создадим эту разметку ниже

class UserSearchAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, UserSearchAdapter.SearchViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchViewHolder(private val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvUserName.text = user.name
            binding.tvAvatarLetter.text = user.name.firstOrNull()?.uppercase() ?: "?"

            // Если уже друг - меняем текст кнопки или скрываем её
            if (user.isAlreadyFriend) {
                binding.btnAddFriend.text = "В друзьях"
                binding.btnAddFriend.isEnabled = false
                binding.btnAddFriend.alpha = 0.5f
            } else {
                binding.btnAddFriend.text = "Добавить"
                binding.btnAddFriend.isEnabled = true
                binding.btnAddFriend.alpha = 1.0f
                binding.btnAddFriend.setOnClickListener { onUserClick(user) }
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}