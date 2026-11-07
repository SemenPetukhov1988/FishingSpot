package ru.semen.fishingspot.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentMainTabsBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class MainTabsFragment : Fragment() {

    private var _binding: FragmentMainTabsBinding? = null
    private val binding get() = _binding!!

    // ✅ Общая ViewModel для управления состоянием точек
    private val spotViewModel: SpotViewModel by activityViewModels()

    private var currentFragment: Fragment? = null
    private var currentSelectedItemId = R.id.nav_my_places

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)

        // ✅ ОБРАБОТКА ЯВНОГО ЗАПРОСА НА ПЕРЕКЛЮЧЕНИЕ ТАБА (при возврате с карты сигнала)
        // ✅ ОБРАБОТКА ЯВНОГО ЗАПРОСА НА ПЕРЕКЛЮЧЕНИЕ ТАБА (при возврате с карты сигнала)
        val requestedTab = arguments?.getInt("active_tab")
        if (requestedTab != null && requestedTab != 0) {
            currentSelectedItemId = requestedTab
            binding.bottomNavigation.selectedItemId = requestedTab

            // ✅ ГЛАВНОЕ ИСПРАВЛЕНИЕ: Явно загружаем нужный фрагмент в контейнер
            when (requestedTab) {
                R.id.nav_my_places, R.id.nav_global_map -> ensureMapFragmentIsVisible()
                R.id.nav_feed -> switchFragment(FeedFragment(), requestedTab)
                R.id.nav_stats -> switchFragment(MessagesFragment(), requestedTab) // ← ЭТОГО НЕ ХВАТАЛО!
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), requestedTab)
            }

            arguments?.clear()
        } else if (savedInstanceState == null) {
            // Обычный первый запуск
            switchFragment(MyMapFragment(), R.id.nav_my_places)
            spotViewModel.switchToMySpots()
        } else {
            // Восстановление после поворота/убийства процесса
            currentSelectedItemId = savedInstanceState.getInt("SELECTED_ITEM", R.id.nav_my_places)
            binding.bottomNavigation.selectedItemId = currentSelectedItemId
            when (currentSelectedItemId) {
                R.id.nav_my_places, R.id.nav_global_map -> ensureMapFragmentIsVisible()
                R.id.nav_feed -> switchFragment(FeedFragment(), currentSelectedItemId)
                R.id.nav_stats -> switchFragment(MessagesFragment(), currentSelectedItemId)
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), currentSelectedItemId)
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            currentSelectedItemId = item.itemId

            // Анимация иконки
            val iconView = binding.bottomNavigation.findViewById<View>(item.itemId)
                ?.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view)

            iconView?.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(150)
                ?.withEndAction {
                    iconView.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }?.start()

            when (item.itemId) {
                R.id.nav_my_places -> {
                    ensureMapFragmentIsVisible()
                    spotViewModel.switchToMySpots()
                    true
                }
                R.id.nav_global_map -> {
                    ensureMapFragmentIsVisible()
                    spotViewModel.switchToPublicSpots()
                    true
                }
                R.id.nav_feed -> switchFragment(FeedFragment(), item.itemId)
                R.id.nav_stats -> switchFragment(MessagesFragment(), item.itemId)
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), item.itemId)
                else -> false
            }
        }
    }

    // ✅ НОВЫЙ МЕТОД: Переключает на MyMapFragment только если мы сейчас НЕ на нём.
    private fun ensureMapFragmentIsVisible() {
        if (currentFragment !is MyMapFragment) {
            switchFragment(MyMapFragment(), currentSelectedItemId)
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SELECTED_ITEM", currentSelectedItemId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}