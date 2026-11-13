package ru.semen.fishingspot.friends

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import io.appwrite.Query
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

    private val DB_ID = "6ad5021a0029a2c88786"
    private val SPOTS_COL_ID = "6ad524e3001c9a28c3e5"
    private val DEFAULT_ZOOM = 10.0
    private val FALLBACK_RETURN_ZOOM = 14.0

    private var needsRestore = false

    companion object {
        var returnLat: Double? = null
        var returnLon: Double? = null
        var returnZoom: Double? = null
    }

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

        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBackgroundColor(Color.parseColor("#E8E8E8"))

        needsRestore = returnLat != null

        if (needsRestore) {
            val lat = returnLat
            val lon = returnLon
            if (lat != null && lon != null) {
                mapView.controller.setZoom(returnZoom ?: FALLBACK_RETURN_ZOOM)
                mapView.controller.setCenter(GeoPoint(lat, lon))
            }
        } else {
            mapView.controller.setCenter(GeoPoint(55.7558, 37.6173))
            mapView.controller.setZoom(DEFAULT_ZOOM)
        }

        // ✅ ЕДИНЫЙ МЕТОД ВОЗВРАТА ДЛЯ ОБЕИХ КНОПОК
        val navigateBackToFishermen = Runnable {
            val bundle = Bundle().apply {
                putInt("active_tab", R.id.nav_fishermen)
            }

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mainTabsFragment, inclusive = false)
                .setEnterAnim(R.anim.slide_in_left)
                .setExitAnim(R.anim.slide_out_right)
                .build()

            findNavController().navigate(R.id.mainTabsFragment, bundle, navOptions)
        }

        // Кнопка в интерфейсе
        binding.btnBack.setOnClickListener { navigateBackToFishermen.run() }

        // ✅ СИСТЕМНАЯ КНОПКА НАЗАД: ИСПОЛЬЗУЕТ ТОТ ЖЕ МЕТОД
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBackToFishermen.run()
                }
            }
        )

        loadFriendSpots(friendId, friendName, mapView)
    }

    private fun applySavedCamera(mapView: MapView) {
        val lat = returnLat
        val lon = returnLon
        if (lat == null || lon == null) return

        mapView.controller.setZoom(returnZoom ?: FALLBACK_RETURN_ZOOM)
        mapView.controller.setCenter(GeoPoint(lat, lon))
        mapView.invalidate()

        returnLat = null
        returnLon = null
        returnZoom = null
    }

    private fun loadFriendSpots(friendId: String, friendName: String, mapView: MapView) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = AppwriteManager.databases.listDocuments(
                    databaseId = DB_ID,
                    collectionId = SPOTS_COL_ID,
                    queries = listOf(
                        Query.equal("authorId", friendId),
                        Query.equal("visibility", "friends")
                    )
                )

                val safeBinding = _binding ?: return@launch
                val safeMap = safeBinding.mapView
                val spots: List<Document<Map<String, Any>>> = result.documents

                if (spots.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "У $friendName пока нет точек 🎣",
                        Toast.LENGTH_LONG
                    ).show()
                    if (needsRestore) {
                        safeMap.post {
                            if (needsRestore && _binding != null) applySavedCamera(safeMap)
                        }
                    }
                } else {
                    drawMarkers(spots, safeMap)

                    if (needsRestore) {
                        safeMap.post {
                            if (needsRestore && _binding != null) applySavedCamera(safeMap)
                        }
                    } else {
                        val firstLat = spots.first().data["latitude"] as? Double ?: 0.0
                        val firstLon = spots.first().data["longitude"] as? Double ?: 0.0
                        if (firstLat != 0.0 && firstLon != 0.0) {
                            safeMap.controller.setCenter(GeoPoint(firstLat, firstLon))
                            safeMap.controller.setZoom(13.0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FRIEND_MAP", "Ошибка: ${e.message}")
                _binding?.mapView?.let { map ->
                    if (needsRestore) {
                        map.post { if (needsRestore && _binding != null) applySavedCamera(map) }
                    }
                }
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
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.metka4567))
            marker.title = doc.data["catchWeight"]?.toString() ?: "Точка"

            marker.setOnMarkerClickListener { _, _ ->
                returnLat = lat
                returnLon = lon
                returnZoom = mapView.zoomLevelDouble

                val bundle = Bundle().apply {
                    putString("document_id", doc.id)
                    putInt("source_tab", R.id.nav_fishermen)
                }

                val navOptions = NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
                    .build()

                findNavController().navigate(R.id.action_friend_map_to_details, bundle, navOptions)
                true
            }

            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let { it.mapView.onResume() }
    }

    override fun onPause() {
        super.onPause()
        _binding?.let { bind ->
            bind.mapView.onPause()
            if (returnLat != null && returnLon != null) {
                returnZoom = bind.mapView.zoomLevelDouble
            }
        }
    }

    override fun onDestroyView() {
        _binding?.let { it.mapView.onDetach() }
        _binding = null
        super.onDestroyView()
    }
}