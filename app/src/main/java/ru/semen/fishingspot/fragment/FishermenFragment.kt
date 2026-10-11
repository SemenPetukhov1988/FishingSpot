package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ru.semen.fishingspot.databinding.FragmentUserBinding
import ru.semen.fishingspot.viewmodel.FriendsViewModel

class FishermenFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    // ✅ Подключаем нашу ViewModel
    private val viewModel: FriendsViewModel by viewModels()

    private lateinit var adapter: FriendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData() // ✅ Начинаем слушать данные
        setupFab()
    }

    private fun setupRecyclerView() {
        // Адаптер принимает лямбды для кликов
        adapter = FriendAdapter(

            onRequestClick = { friend ->
                // ✅ ОТКРЫВАЕМ ШТОРКУ ЗАПРОСА ТОЧКИ
                RequestSpotBottomSheet(
                    friendId = friend.id,
                    friendName = friend.name
                ).show(childFragmentManager, "request_spot_sheet")
            },
            onRemoveClick = { friend ->
                Toast.makeText(requireContext(), "❌ Удалить ${friend.name}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter
    }

    // ✅ Метод наблюдения за данными из ViewModel
    private fun observeData() {
        // Слушаем список друзей
        viewModel.friends.observe(viewLifecycleOwner) { friendsList ->
            showFriends(friendsList)
        }

        // Слушаем сообщения об ошибках или успехе
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                // Сбрасываем сообщение после показа
               // viewModel.clearMessage() // (Если такого метода нет в твоей ViewModel, просто оставь как было, это не критично)
            }
        }
    }

    private fun setupFab() {
        binding.fabAddFriend.setOnClickListener {
            // Открываем шторку поиска
            AddFriendBottomSheet { user ->
                // ✅ ИЗМЕНЕНО: Теперь отправляем запрос в друзья, а не добавляем мгновенно
                viewModel.sendFriendRequest(user.id, user.name)
            }.show(childFragmentManager, "add_friend_sheet")
        }
    }

    private fun showFriends(friends: List<ru.semen.fishingspot.data.Friend>) {
        if (friends.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
            // Адаптер сам обновит список благодаря DiffUtil
            adapter.submitList(friends)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}