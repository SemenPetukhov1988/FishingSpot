package ru.semen.fishingspot.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import io.appwrite.models.RealtimeSubscription
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.friends.adapter.FriendAdapter
import ru.semen.fishingspot.friends.data.Friend
import ru.semen.fishingspot.databinding.FragmentUserBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.ProfileCache
import ru.semen.fishingspot.utils.UserSessionManager

class FishermenFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private val DB_ID = "6ad5021a0029a2c88786"
    private val USERS_COL_ID = "6ad5022b003770d2864c"

    private val viewModel: FriendsViewModel by viewModels()
    private lateinit var adapter: FriendAdapter

    private var realtimeSubscription: RealtimeSubscription? = null

    // ✅ Переменные для поиска
    private var searchJob: Job? = null
    private var allFriendsList: List<Friend> = emptyList() // Храним полный список

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
        setupFab()
        setupSwipeRefresh()
        setupSearch() // ✅ Настраиваем поиск
        viewModel.loadFriends()
    }

    private fun setupSearch() {
        binding.etSearchFriends.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                searchJob?.cancel()

                if (query.isEmpty()) {
                    // Если пусто — показываем весь список
                    adapter.submitList(allFriendsList)
                    updateEmptyState(allFriendsList)
                    return
                }

                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300) // Небольшая задержка для плавности
                    filterFriends(query)
                }
            }
        })
    }

    private fun filterFriends(query: String) {
        val filtered = allFriendsList.filter {
            it.name.contains(query, ignoreCase = true)
        }
        adapter.submitList(filtered)
        updateEmptyState(filtered)
    }

    private fun updateEmptyState(list: List<Friend>) {
        if (list.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.etSearchFriends.setText("") // Очищаем поиск при обновлении
            viewModel.loadFriends()
        }
    }

    private fun setupRecyclerView() {
        adapter = FriendAdapter(
            onFriendClick = { friend -> checkIfStillFriend(friend) },
            onRemoveClick = { friend ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Удалить из друзей?")
                    .setMessage("Вы уверены, что хотите удалить ${friend.name}?")
                    .setPositiveButton("Удалить") { _, _ ->
                        viewModel.removeFriend(friend.id, friend.name)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter
    }

    private fun checkIfStillFriend(friend: Friend) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val myId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch
                val friendDoc = AppwriteManager.databases.getDocument(
                    databaseId = DB_ID,
                    collectionId = USERS_COL_ID,
                    documentId = friend.id
                )
                val friendFriendsList = friendDoc.data["friends"] as? List<String> ?: emptyList()
                val isStillFriend = friendFriendsList.contains(myId)

                if (!isStillFriend) {
                    Toast.makeText(requireContext(), "${friend.name} удалил(а) вас из друзей", Toast.LENGTH_LONG).show()
                    viewModel.loadFriends()
                } else {
                    val bundle = Bundle().apply {
                        putString("arg_friend_id", friend.id)
                        putString("arg_friend_name", friend.name)
                    }
                    findNavController().navigate(R.id.friendMapFragment, bundle)
                }
            } catch (e: Exception) {
                Log.e("FRIEND_CHECK", "Ошибка проверки дружбы: ${e.message}")
                Toast.makeText(requireContext(), "Не удалось проверить статус друга.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        viewModel.friends.observe(viewLifecycleOwner) { friendsList ->
            allFriendsList = friendsList // ✅ Сохраняем полный список
            adapter.submitList(friendsList)
            updateEmptyState(friendsList)
            binding.swipeRefresh.isRefreshing = false
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        ProfileCache.avatarUpdated.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun setupFab() {
        binding.fabAddFriend.setOnClickListener {
            AddFriendBottomSheet().show(childFragmentManager, "add_friend_sheet")
        }
    }

    override fun onResume() {
        super.onResume()
        startRealtimeAvatarUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeAvatarUpdates()
    }

    private fun startRealtimeAvatarUpdates() {
        realtimeSubscription?.close()
        realtimeSubscription = AppwriteManager.realtime.subscribe(
            channels = arrayOf("databases.$DB_ID.collections.$USERS_COL_ID.documents")
        ) { message ->
            val data = message.payload as? Map<*, *>
            val userId = data?.get("\$id") as? String
            val avatarUrl = data?.get("avatarUrl") as? String
            val newNickname = data?.get("nickname") as? String

            if (!userId.isNullOrEmpty()) {
                requireActivity().runOnUiThread {
                    if (!avatarUrl.isNullOrEmpty()) ProfileCache.putAvatar(userId, avatarUrl)

                    if (!newNickname.isNullOrEmpty()) {
                        val currentList = allFriendsList.toMutableList()
                        val index = currentList.indexOfFirst { it.id == userId }
                        if (index != -1) {
                            currentList[index] = currentList[index].copy(name = newNickname)
                            allFriendsList = currentList
                            // Если сейчас идет поиск, фильтруем заново, иначе показываем всё
                            val query = binding.etSearchFriends.text?.toString()?.trim().orEmpty()
                            if (query.isNotEmpty()) filterFriends(query) else adapter.submitList(currentList)
                        }
                    }
                }
            }
        }
    }

    private fun stopRealtimeAvatarUpdates() {
        realtimeSubscription?.close()
        realtimeSubscription = null
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        stopRealtimeAvatarUpdates()
        super.onDestroyView()
        _binding = null
    }
}