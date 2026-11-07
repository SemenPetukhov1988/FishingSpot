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
    private val signalType: String
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecipientSelectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FriendRecipientAdapter

    // ⚠️ ID КОЛЛЕКЦИИ ПОЛЬЗОВАТЕЛЕЙ (где хранится поле friends)
    private  val USERS_COL_ID = "6ad5022b003770d2864c" // ЗАМЕНИ НА СВОЙ ID ИЗ СКРИНШОТА
    private  val DB_ID = "6ad5021a0029a2c88786"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRecipientSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Инициализация адаптера
        adapter = FriendRecipientAdapter { friendId ->
            dismiss()
            openMapForSignal(friendId)
        }

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter

        // Клик "Всем друзьям"
        binding.btnAllFriends.setOnClickListener {
            dismiss()
            openMapForSignal("all")
        }

        // Загрузка списка друзей
        loadFriends()
    }

    private fun loadFriends() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUserId = UserSessionManager.getCurrentUserId(requireContext()) ?: return@launch

                // 1. Получаем текущего пользователя
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

                // 2. Загружаем документы друзей параллельно, чтобы получить их ники
                // ⚠️ В продакшене лучше сделать один запрос с Query.or(), но для шторки это ок
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
                        null // Если друг удален или ошибка - пропускаем
                    }
                }

                // 3. Отдаем в адаптер красивые объекты
                adapter.submitList(friendsWithNames)

            } catch (e: Exception) {
                android.util.Log.e("RECIPIENT_SHEET", "Ошибка: ${e.message}")
            }
        }
    }

    private fun openMapForSignal(receiverId: String) {
        val bundle = Bundle().apply {
            putString("signal_type", signalType)
            putString("receiver_id", receiverId)
        }
        findNavController().navigate(R.id.sendFishingSignalFragment, bundle)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}