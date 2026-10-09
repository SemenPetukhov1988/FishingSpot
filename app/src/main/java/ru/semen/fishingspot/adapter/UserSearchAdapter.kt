package ru.semen.fishingspot.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.data.User
import ru.semen.fishingspot.databinding.ItemUserSearchBinding

class UserSearchAdapter(
    private val onAddClick: (User) -> Unit
) : ListAdapter<User, UserSearchAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(private val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvUserName.text = user.name

            // Первая буква имени для аватара
            val firstLetter = if (user.name.isNotEmpty()) {
                user.name[0].uppercaseChar().toString()
            } else {
                "?"
            }
            binding.tvAvatarLetter.text = firstLetter

            if (user.isAlreadyFriend) {
                // Если уже в друзьях — показываем текст вместо кнопки
                binding.btnAddFriend.visibility = View.GONE
                binding.tvAlreadyFriend.visibility = View.VISIBLE
            } else {
                // Иначе показываем кнопку добавления
                binding.btnAddFriend.visibility = View.VISIBLE
                binding.tvAlreadyFriend.visibility = View.GONE
                binding.btnAddFriend.setOnClickListener { onAddClick(user) }
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}