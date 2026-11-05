package ru.semen.fishingspot.fragment

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import io.appwrite.models.Document
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentFriendMapBinding
import ru.semen.fishingspot.utils.AppwriteManager

class FriendMapFragment : Fragment() {

    private var _binding: FragmentFriendMapBinding? = null
    private val binding get() = _binding!!

    // ⚠️ ЗАМЕНИ НА СВОИ РЕАЛЬНЫЕ ID ИЗ APPWRITE CONSOLE
    private  val DB_ID = "6ad5021a0029a2c88786"
    private  val SPOTS_COL_ID = "6ad524e3001c9a28c3e5"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val friendId = arguments?.getString("arg_friend_id") ?: return
        val friendName = arguments?.getString("arg_friend_name", "Друг") ?: "Друг"

        binding.tvTitle.text = "Точки $friendName"

        // 1. Настройка карты
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(10.0)
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        // Дефолтная позиция, чтобы не улететь в океан при пустом ответе
        val defaultLocation = GeoPoint(55.7558, 37.6173)
        mapView.controller.setCenter(defaultLocation)

        // ✅ ВОЗВРАЩАЕМ КНОПКУ НАЗАД
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 2. Загрузка точек друга
        loadFriendSpots(friendId, friendName, mapView)
    }

    private fun loadFriendSpots(friendId: String, friendName: String, mapView: MapView) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = SPOTS_COL_ID,
                    queries = listOf(
                        io.appwrite.Query.equal("authorId", friendId),
                        io.appwrite.Query.equal("visibility", "friends")
                    )
                )

                val spots: List<Document<Map<String, Any>>> = result.documents

                if (spots.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "У $friendName пока нет точек для друзей 🎣",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.d("FRIEND_MAP", "Загружено ${spots.size} точек друга")
                    drawMarkers(spots, mapView)

                    val firstLat = spots.first().data["latitude"] as? Double ?: 0.0
                    val firstLon = spots.first().data["longitude"] as? Double ?: 0.0
                    if (firstLat != 0.0 && firstLon != 0.0) {
                        mapView.controller.animateTo(GeoPoint(firstLat, firstLon))
                        mapView.controller.setZoom(12.0)
                    }
                }
            } catch (e: Exception) {
                Log.e("FRIEND_MAP", "Ошибка загрузки точек: ${e.message}")
                Toast.makeText(requireContext(), "Не удалось загрузить точки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawMarkers(documents: List<Document<Map<String, Any>>>, mapView: MapView) {
        mapView.overlays.removeAll { it is Marker }

        documents.forEach { doc ->
            val lat = doc.data["latitude"] as? Double ?: return@forEach
            val lon = doc.data["longitude"] as? Double ?: return@forEach

            val point = GeoPoint(lat, lon)
            val marker = Marker(mapView)
            marker.position = point

            // ✅ УБРАЛИ colorFilter! Используем родную иконку без изменений
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.metka4567))

            marker.title = doc.data["catchWeight"]?.toString() ?: "Точка"

            // ✅ ВОЗВРАЩАЕМ ЛОГИКУ КЛИКА КАК В MYMAPFRAGMENT
            marker.setOnMarkerClickListener { _, _ ->
                val documentId = doc.id // или doc.data["\$id"] если используется другое поле
                val detailsBundle = Bundle().apply {
                    putString("document_id", documentId)
                    // Можно добавить флаг источника, если нужно
                    putInt("source_tab", R.id.nav_fishermen)
                }

                // Навигация к деталям точки
                requireActivity().findNavController(R.id.fragmentContainer)
                    .navigate(R.id.action_map_to_details, detailsBundle)

                true
            }

            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        binding.mapView.onDetach()
        _binding = null
        super.onDestroyView()
    }
}