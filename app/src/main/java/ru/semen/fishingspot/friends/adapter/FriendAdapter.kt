package ru.semen.fishingspot.friends.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import ru.semen.fishingspot.R
import ru.semen.fishingspot.friends.data.Friend
import ru.semen.fishingspot.utils.loadUserAvatar // ✅ Импорт Extension

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
        // ✅ Ищем новый ImageView вместо TextView
        val ivFriendAvatar: ShapeableImageView = view.findViewById(R.id.ivFriendAvatar)
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

        // ✅ ЗАГРУЗКА АВАТАРКИ ЧЕРЕЗ УМНЫЙ EXTENSION
        holder.ivFriendAvatar.loadUserAvatar(friend.id)

        holder.tvFriendName.text = friend.name.ifEmpty { "Рыбак" }

        holder.itemView.setOnClickListener {
            onFriendClick(friend)
        }

        holder.btnRemoveFriend.setOnClickListener {
            onRemoveClick(friend)
        }
    }

    override fun getItemCount() = items.size
}