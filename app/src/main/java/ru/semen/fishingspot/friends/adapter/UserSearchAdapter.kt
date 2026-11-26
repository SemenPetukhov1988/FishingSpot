package ru.semen.fishingspot.friends.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import ru.semen.fishingspot.databinding.ItemUserSearchBinding
import ru.semen.fishingspot.friends.data.User
import ru.semen.fishingspot.utils.loadUserAvatar

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

            // ✅ ЗАГРУЗКА АВАТАРКИ ЧЕРЕЗ УМНЫЙ EXTENSION
            binding.ivUserAvatar.loadUserAvatar(user.id)

            if (user.isAlreadyFriend) {
                binding.btnAddFriend.text = "В друзьях"
                binding.btnAddFriend.isEnabled = false
                binding.btnAddFriend.alpha = 0.5f
            } else {
                binding.btnAddFriend.text = "Добавить"
                binding.btnAddFriend.isEnabled = true
                binding.btnAddFriend.alpha = 1.0f

                binding.btnAddFriend.setOnClickListener {
                    // ✅ ПОКАЗЫВАЕМ ТОСТ ПЕРЕД ВЫЗОВОМ ЛОГИКИ
                    Toast.makeText(binding.root.context, "Запрос в друзья отправлен!", Toast.LENGTH_SHORT).show()
                    onUserClick(user)
                }
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}