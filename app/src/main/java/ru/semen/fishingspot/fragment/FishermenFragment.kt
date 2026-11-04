package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ru.semen.fishingspot.adapter.FriendAdapter
import ru.semen.fishingspot.databinding.FragmentUserBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.FriendsViewModel

class FishermenFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    // ⚠️ ЗАМЕНИ НА СВОИ РЕАЛЬНЫЕ ID ИЗ APPWRITE CONSOLE
    private  val DB_ID = "6ad5021a0029a2c88786"
    private  val USERS_COL_ID = "6ad5022b003770d2864c"

    private val viewModel: FriendsViewModel by viewModels()
    private lateinit var adapter: FriendAdapter

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

        viewModel.loadFriends()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFriends()
        }
    }

    private fun setupRecyclerView() {
        adapter = FriendAdapter(
            onRequestClick = { friend ->
                RequestSpotBottomSheet(friend.id, friend.name)
                    .show(childFragmentManager, "request_spot_sheet")
            },
            onRemoveClick = { friend ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Удалить из друзей?")
                    .setMessage("Вы уверены, что хотите удалить ${friend.name}?")
                    .setPositiveButton("Удалить") { _, _ ->
                        viewModel.removeFriend(friend.id, friend.name)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            },
            onItemClick = { friend ->
                checkIfStillFriend(friend)
            }
        )

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter
    }

    /**
     * Проверяет СЕРВЕРНОЕ состояние дружбы перед открытием шторки.
     * Смотрит, есть ли МОЙ ID в списке друзей того, на кого кликнули.
     */
    private fun checkIfStillFriend(friend: ru.semen.fishingspot.data.Friend) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val myId = UserSessionManager.getCurrentUserId(requireContext())
                    ?: return@launch

                // ✅ Запрашиваем документ ДРУГА с сервера
                val friendDoc = AppwriteManager.databases.getDocument(
                    databaseId = DB_ID,
                    collectionId = USERS_COL_ID,
                    documentId = friend.id
                )

                val friendFriendsList = friendDoc.data["friends"] as? List<String> ?: emptyList()

                // ✅ Проверяем: есть ли я всё ещё в ЕГО списке друзей?
                val isStillFriend = friendFriendsList.contains(myId)

                if (!isStillFriend) {
                    Toast.makeText(
                        requireContext(),
                        "${friend.name} удалил(а) вас из друзей",
                        Toast.LENGTH_LONG
                    ).show()

                    // Удаляем из локального списка мгновенно
                    val currentList = viewModel.friends.value?.filter { it.id != friend.id } ?: emptyList()
                    adapter.submitList(currentList)

                    // Обновляем данные с сервера в фоне
                    viewModel.loadFriends()
                } else {
                    // Всё ок, открываем шторку запроса точки
                    RequestSpotBottomSheet(friend.id, friend.name)
                        .show(childFragmentManager, "request_spot_sheet")
                }
            } catch (e: Exception) {
                // Если сеть недоступна или ошибка - просто открываем шторку, не блокируем UX
                android.util.Log.e("FRIEND_CHECK", "Ошибка проверки дружбы: ${e.message}")
                RequestSpotBottomSheet(friend.id, friend.name)
                    .show(childFragmentManager, "request_spot_sheet")
            }
        }
    }

    private fun observeData() {
        viewModel.friends.observe(viewLifecycleOwner) { friendsList ->
            showFriends(friendsList)
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFab() {
        binding.fabAddFriend.setOnClickListener {
            AddFriendBottomSheet().show(childFragmentManager, "add_friend_sheet")
        }
    }

    private fun showFriends(friends: List<ru.semen.fishingspot.data.Friend>) {
        if (friends.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
            adapter.submitList(friends)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}