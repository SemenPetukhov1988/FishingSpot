package ru.semen.fishingspot.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ru.semen.fishingspot.R
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.FragmentSpotDetailsBinding
import ru.semen.fishingspot.utils.NetworkMonitor
import ru.semen.fishingspot.utils.UserSessionManager
import ru.semen.fishingspot.viewmodel.SpotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpotDetailsFragment : Fragment() {

    private var _binding: FragmentSpotDetailsBinding? = null
    private val binding get() = _binding!!
    private val spotViewModel: SpotViewModel by activityViewModels()
    private lateinit var networkMonitor: NetworkMonitor

    private var documentId: String = ""
    private var currentSpot: FishingSpot? = null
    private var sourceTab: Int = R.id.nav_my_places

    companion object {
        private const val TAG = "SPOT_DETAILS"
        const val ARG_DOCUMENT_ID = "document_id"
        const val ARG_SOURCE_TAB = "source_tab"

        fun newInstance(documentId: String, sourceTab: Int = R.id.nav_my_places): SpotDetailsFragment {
            return SpotDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOCUMENT_ID, documentId)
                    putInt(ARG_SOURCE_TAB, sourceTab)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        networkMonitor = NetworkMonitor.getInstance(requireContext())

        documentId = arguments?.getString(ARG_DOCUMENT_ID) ?: ""
        sourceTab = arguments?.getInt(ARG_SOURCE_TAB, R.id.nav_my_places) ?: R.id.nav_my_places

        if (documentId.isBlank()) {
            Toast.makeText(requireContext(), "Не передан ID точки", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        Log.d(TAG, "открываем documentId=$documentId")

        setupClickListeners()

        spotViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (_binding == null) return@observe
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        spotViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (_binding == null || message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }

        spotViewModel.currentSpotDetails.observe(viewLifecycleOwner) { spot ->
            if (_binding == null || spot == null) return@observe
            currentSpot = spot
            render(spot)
        }

        spotViewModel.isSpotSaved.observe(viewLifecycleOwner) {
            if (_binding != null) updateFavoriteButton()
        }

        networkMonitor.observe(viewLifecycleOwner) { online ->
            if (_binding == null) return@observe
            binding.tvOfflineBanner.visibility = if (online) View.GONE else View.VISIBLE
            currentSpot?.let { render(it) }
        }

        spotViewModel.loadSpotDetails(documentId)
    }

    private fun render(spot: FishingSpot) {
        val online = networkMonitor.value == true
        val me = UserSessionManager.getCurrentUserId(requireContext()) ?: ""
        val isAuthor = me.isNotEmpty() && me == spot.authorId

        binding.tvSpotName.text = spot.name
        binding.tvDescription.text = spot.description.ifEmpty { "Нет описания" }
        binding.tvCatchWeight.text = weightText(spot.catchWeight)
        binding.tvAuthorName.text = if (isAuthor) "Вы (Автор)" else spot.authorName
        binding.tvFishingDate.text = "📅 Рыбалка: " +
                SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(Date(spot.createdAt))

        binding.tvOfflineBanner.visibility = if (online) View.GONE else View.VISIBLE
        binding.btnNavigate.visibility = View.VISIBLE

        val canManage = online && isAuthor

        binding.btnDeleteSpot.visibility = if (canManage) View.VISIBLE else View.GONE

        binding.btnToggleFriendsVisibility.visibility =
            if (canManage && spot.visibility != "public") View.VISIBLE else View.GONE
        binding.btnToggleFriendsVisibility.text =
            if (spot.visibility == "friends") "🙈 Скрыть от друзей" else "👥 Показать точку друзьям"

        binding.btnTogglePublicMap.visibility = if (canManage) View.VISIBLE else View.GONE
        binding.btnTogglePublicMap.text =
            if (spot.visibility == "public") "🌍 Скрыть с публичной карты" else "🌍 Показать на публичной карте"

        updateFavoriteButton()
    }

    private fun updateFavoriteButton() {
        val spot = currentSpot
        val me = UserSessionManager.getCurrentUserId(requireContext()) ?: ""
        val isAuthor = spot != null && me.isNotEmpty() && me == spot.authorId
        val isSaved = spotViewModel.isSpotSaved.value == true

        // Скрываем кнопку для автора своей точки или если нет интернета
        if (spot == null || isAuthor || networkMonitor.value != true) {
            binding.btnSaveToLocal.visibility = View.GONE
            return
        }

        // ✅ МЕНЯЕМ ТЕКСТ КНОПКИ В ЗАВИСИМОСТИ ОТ СОСТОЯНИЯ
        if (isSaved) {
            binding.btnSaveToLocal.text = "⭐ Убрать из избранного"
            binding.btnSaveToLocal.visibility = View.VISIBLE
            binding.btnSaveToLocal.setOnClickListener {
                spotViewModel.unsaveFromFavorites(spot)
            }
        } else {
            binding.btnSaveToLocal.text = "💾 Сохранить в избранное"
            binding.btnSaveToLocal.visibility = View.VISIBLE
            binding.btnSaveToLocal.setOnClickListener {
                spotViewModel.saveToFavorites(spot)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            spotViewModel.setSelectedTab(sourceTab)
            findNavController().navigateUp()
        }

        binding.btnNavigate.setOnClickListener {
            val spot = currentSpot ?: return@setOnClickListener
            val uri = Uri.parse("geo:${spot.latitude},${spot.longitude}?q=${spot.latitude},${spot.longitude}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteSpot.setOnClickListener {
            val id = currentSpot?.documentId ?: return@setOnClickListener
            confirm("Удалить точку?", "Точка будет удалена с сервера навсегда.", "Удалить") {
                spotViewModel.deleteSpot(id)
                findNavController().navigateUp()
            }
        }

        binding.btnToggleFriendsVisibility.setOnClickListener {
            val spot = currentSpot ?: return@setOnClickListener
            val target = if (spot.visibility == "friends") "private" else "friends"
            confirm("Изменить доступ для друзей?", "Новый статус: $target", "Применить") {
                spotViewModel.updateSpotVisibility(spot.documentId, target)
            }
        }

        binding.btnTogglePublicMap.setOnClickListener {
            val spot = currentSpot ?: return@setOnClickListener
            val target = if (spot.visibility == "public") "private" else "public"
            confirm("Изменить публичность?", "Новый статус: $target", "Применить") {
                spotViewModel.updateSpotVisibility(spot.documentId, target)
            }
        }

        binding.btnSaveToLocal.setOnClickListener {
            val spot = currentSpot ?: return@setOnClickListener
            spotViewModel.saveToFavorites(spot)
        }
    }

    private fun confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) {
        if (!isAdded || _binding == null) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { dialog, _ -> dialog.dismiss(); onConfirm() }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun weightText(weight: Double): String = when {
        weight <= 0.0 -> "🐟 Улов не указан"
        weight <= 1.0 -> "⚖️ Вес улова: До 1 кг"
        weight <= 5.0 -> "⚖️ Вес улова: 1–5 кг"
        else -> "⚖️ Вес улова: Больше 5 кг"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}