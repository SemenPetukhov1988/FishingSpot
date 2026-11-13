package ru.semen.fishingspot.friends.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.Friend

class FriendAdapter(
    private val onFriendClick: (Friend) -> Unit,
    private val onRemoveClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    private var items: List<Friend> = emptyList()

    fun submitList(newList: List<Friend>) {
        items = newList
        notifyDataSetChanged()
    }

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val tvFriendName: TextView = view.findViewById(R.id.tvFriendName)
        val btnRemoveFriend: ImageButton = view.findViewById(R.id.btnRemoveFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = items[position]

        // Привязываем данные
        holder.tvFriendName.text = friend.name.ifEmpty { "Рыбак" }
        holder.tvAvatarLetter.text = friend.name.firstOrNull()?.uppercase() ?: "?"

        // ✅ ОБРАБОТЧИКИ КЛИКОВ ПРИВЯЗЫВАЮТСЯ ЗДЕСЬ, ГДЕ ЕСТЬ ДОСТУП К СПИСКУ И ЛАМБДАМ
        holder.itemView.setOnClickListener {
            onFriendClick(friend)
        }

        holder.btnRemoveFriend.setOnClickListener {
            onRemoveClick(friend)
        }
    }

    override fun getItemCount() = items.size
}