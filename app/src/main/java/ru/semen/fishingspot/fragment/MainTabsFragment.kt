package ru.semen.fishingspot.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.yandex.mapkit.MapKitFactory
import ru.netology.fishingspot.ui.stats.StatsFragment
import ru.netology.fishingspot.ui.user.FishermenFragment
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentMainTabsBinding
import ru.semen.fishingspot.ui.map.GlobalMapFragment
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MainTabsFragment : Fragment() {

    private var _binding: FragmentMainTabsBinding? = null
    private val binding get() = _binding!!
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var currentFragment: Fragment? = null

    // ✅ НОВОЕ: Храним ID выбранной вкладки в переменной, чтобы не лезть в binding при уничтожении
    private var currentSelectedItemId = R.id.nav_my_places

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)

        // Синхронизация вкладки при возврате из SpotDetailsFragment
        spotViewModel.selectedTabId.observe(viewLifecycleOwner) { tabId ->
            currentSelectedItemId = tabId // ✅ Обновляем и нашу переменную
            binding.bottomNavigation.selectedItemId = tabId
        }

        if (savedInstanceState == null) {
            switchFragment(MyMapFragment(), R.id.nav_my_places)
        } else {
            currentSelectedItemId = savedInstanceState.getInt("SELECTED_ITEM", R.id.nav_my_places)
            binding.bottomNavigation.selectedItemId = currentSelectedItemId
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            currentSelectedItemId = item.itemId // ✅ Запоминаем выбор

            val iconView = binding.bottomNavigation.findViewById<View>(item.itemId)
                ?.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view)

            iconView?.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(150)
                ?.withEndAction {
                    iconView.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }?.start()

            when (item.itemId) {
                R.id.nav_my_places -> switchFragment(MyMapFragment(), item.itemId)
                R.id.nav_global_map -> switchFragment(GlobalMapFragment(), item.itemId)
                R.id.nav_stats -> switchFragment(StatsFragment(), item.itemId)
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), item.itemId)
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
    }

    private fun switchFragment(fragment: Fragment, itemId: Int): Boolean {
        if (fragment::class.java == currentFragment?.javaClass) return true

        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        currentFragment = fragment

        requireActivity().runOnUiThread {
            binding.bottomNavigation.selectedItemId = itemId
        }
        return true
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД: больше не обращаемся к binding, который может быть null
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SELECTED_ITEM", currentSelectedItemId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}