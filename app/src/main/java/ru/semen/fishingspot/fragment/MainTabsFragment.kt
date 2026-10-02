package ru.semen.fishingspot.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.yandex.mapkit.MapKitFactory
import ru.netology.fishingspot.ui.stats.StatsFragment
import ru.netology.fishingspot.ui.user.FishermenFragment
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentMainTabsBinding
import ru.semen.fishingspot.ui.map.GlobalMapFragment
import androidx.core.graphics.toColorInt

class MainTabsFragment : Fragment() {

    private var _binding: FragmentMainTabsBinding? = null
    private val binding get() = _binding!!

    private var currentFragment: Fragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        // ✅ Отключаем стандартную перекраску и серый круг
        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)


        if (savedInstanceState == null) {
            switchFragment(MyMapFragment(), R.id.nav_my_places)
        } else {
            binding.bottomNavigation.selectedItemId =
                savedInstanceState.getInt("SELECTED_ITEM", R.id.nav_my_places)
        }





        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // ✅ АНИМАЦИЯ "ПОДПРЫГИВАНИЯ" ИКОНКИ
            val iconView = binding.bottomNavigation.findViewById<View>(item.itemId)
                ?.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view)

            iconView?.animate()
                ?.scaleX(1.25f)
                ?.scaleY(1.25f)
                ?.setDuration(150)
                ?.withEndAction {
                    iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                ?.start()

            // Переключаем фрагменты как раньше
            when (item.itemId) {
                R.id.nav_my_places -> switchFragment(MyMapFragment(), item.itemId)
                R.id.nav_global_map -> switchFragment(GlobalMapFragment(), item.itemId)
                R.id.nav_stats -> switchFragment(StatsFragment(), item.itemId)
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), item.itemId)
                else -> false
            }
        }
    }

    // ✅ MapKit рантайм запускается ОДИН РАЗ для всех дочерних фрагментов
    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    // ✅ И останавливается один раз — при уходе с экрана вкладок
    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
    }

    private fun switchFragment(fragment: Fragment, itemId: Int): Boolean {
        if (fragment::class.java == currentFragment?.javaClass) {
            return true
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        currentFragment = fragment

        requireActivity().runOnUiThread {
            binding.bottomNavigation.selectedItemId = itemId
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SELECTED_ITEM", binding.bottomNavigation.selectedItemId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
