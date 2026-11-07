package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.appwrite.Query
import kotlinx.coroutines.launch
import ru.semen.fishingspot.R
import ru.semen.fishingspot.adapter.FriendItem
import ru.semen.fishingspot.adapter.FriendRecipientAdapter
import ru.semen.fishingspot.databinding.BottomSheetRecipientSelectionBinding
import ru.semen.fishingspot.utils.AppwriteManager
import ru.semen.fishingspot.utils.UserSessionManager

class RecipientSelectionBottomSheet(
    private val signalType: String,
    private val senderName: String = "Рыбак", // ✅ Новое поле с дефолтным значением
    private val senderId: String = ""         // ✅ Новое поле с дефолтным значением
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecipientSelectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FriendRecipientAdapter

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

        adapter = FriendRecipientAdapter { friendId ->
            dismiss()
            openMapForSignal(friendId)
        }

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter

        binding.btnAllFriends.setOnClickListener {
            dismiss()
            openMapForSignal("all")
        }

        loadFriends()
    }

    private fun loadFriends() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch

                val userDoc = AppwriteManager.databases.getDocument(
                    databaseId = DB_ID,
                    collectionId = USERS_COL_ID,
                    documentId = currentUserId
                )

                val friendIds = (userDoc.data["friends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                if (friendIds.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@launch
                }

                val friendsWithNames = friendIds.mapNotNull { id ->
                    try {
                        val doc = AppwriteManager.databases.getDocument(
                            databaseId = DB_ID,
                            collectionId = USERS_COL_ID,
                            documentId = id
                        )
                        val nickname = doc.data["nickname"] as? String ?: "Рыбак"
                        FriendItem(id = id, name = nickname)
                    } catch (e: Exception) {
                        null
                    }
                }

                adapter.submitList(friendsWithNames)

            } catch (e: Exception) {
                android.util.Log.e("RECIPIENT_SHEET", "Ошибка: ${e.message}")
            }
        }
    }

    private fun openMapForSignal(receiverId: String) {
        // ✅ ПЕРЕДАЕМ ИМЯ ОТПРАВИТЕЛЯ В СЛЕДУЮЩИЙ ФРАГМЕНТ
        val bundle = Bundle().apply {
            putString("signal_type", signalType)
            putString("receiver_id", receiverId)
            putString("sender_name", senderName)   // ✅ Добавлено
            putString("sender_id", senderId)       // ✅ Добавлено
        }
        findNavController().navigate(R.id.sendFishingSignalFragment, bundle)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}