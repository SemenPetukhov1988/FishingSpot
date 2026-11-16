package ru.semen.fishingspot.messages.friends

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.BottomSheetRecipientSelectionBinding
import ru.semen.fishingspot.messages.notifikation.DateSelectionBottomSheet
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class RecipientSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecipientSelectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FriendRecipientAdapter

    companion object {
        fun newInstance(signalType: String, senderName: String, senderId: String): RecipientSelectionBottomSheet {
            return RecipientSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("signal_type", signalType)
                    putString("sender_name", senderName)
                    putString("sender_id", senderId)
                }
            }
        }
    }

    private val USERS_COL_ID = "6ad5022b003770d2864c"
    private val DB_ID = "6ad5021a0029a2c88786"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRecipientSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val signalType = arguments?.getString("signal_type") ?: "on_fishing"
        val senderName = arguments?.getString("sender_name") ?: "Рыбак"
        val senderId = arguments?.getString("sender_id") ?: ""

        adapter = FriendRecipientAdapter { friendId ->
            handleFriendSelected(friendId, signalType, senderName, senderId)
        }

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter

        binding.btnAllFriends.setOnClickListener {
            handleFriendSelected("all", signalType, senderName, senderId)
        }

        loadFriends()
    }

    // ✅ ИСПРАВЛЕННАЯ ЛОГИКА ПЕРЕХОДА
    private fun handleFriendSelected(receiverId: String, signalType: String, senderName: String, senderId: String) {
        dismiss()

        if (signalType == "going_fishing") {
            // НОВАЯ ЦЕПОЧКА: Идем к выбору даты
            DateSelectionBottomSheet.newInstance().apply {
                arguments = Bundle().apply {
                    putString("signal_type", signalType)
                    putString("sender_name", senderName)
                    putString("sender_id", senderId)
                    putString("receiver_id", receiverId)
                }
            }.show(parentFragmentManager, "date_selection")

        } else {
            // СТАРАЯ ЛОГИКА: Сразу идем на карту для on_fishing и других типов
            val bundle = Bundle().apply {
                putString("signal_type", signalType)
                putString("receiver_id", receiverId)
                putString("sender_name", senderName)
                putString("sender_id", senderId)
            }

            try {
                findNavController().navigate(R.id.sendFishingSignalFragment, bundle)
            } catch (e: Exception) {
                Log.e("RECIPIENT_SHEET", "Ошибка навигации на карту: ${e.message}", e)
            }
        }
    }

    private fun loadFriends() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch
                val userDoc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, currentUserId)
                val friendIds = (userDoc.data["friends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                if (friendIds.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@launch
                }

                val friendsWithNames = friendIds.mapNotNull { id ->
                    try {
                        val doc = AppwriteManager.databases.getDocument(DB_ID, USERS_COL_ID, id)
                        val nickname = doc.data["nickname"] as? String ?: "Рыбак"
                        FriendItem(id = id, name = nickname)
                    } catch (e: Exception) { null }
                }
                adapter.submitList(friendsWithNames)
            } catch (e: Exception) {
                Log.e("RECIPIENT_SHEET", "Ошибка загрузки друзей: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}