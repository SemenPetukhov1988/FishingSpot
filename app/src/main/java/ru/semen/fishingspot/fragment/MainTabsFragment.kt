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

        // ✅ УНИВЕРСАЛЬНОЕ ВОССТАНОВЛЕНИЕ СОСТОЯНИЯ
        val requestedTab = arguments?.getInt("active_tab")
        val targetTab = requestedTab
            ?: savedInstanceState?.getInt("SELECTED_ITEM", R.id.nav_my_places)
            ?: spotViewModel.selectedTabId

        applyTab(targetTab)
        arguments?.clear()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // ✅ ЗАЩИТА ОТ РЕКУРСИИ: если кликнули на уже активный таб — ничего не делаем
            if (item.itemId == currentSelectedItemId) return@setOnItemSelectedListener true

            currentSelectedItemId = item.itemId
            spotViewModel.selectedTabId = item.itemId

            applyTab(item.itemId)

            // Анимация иконки
            val iconView = binding.bottomNavigation.findViewById<View>(item.itemId)
                ?.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view)
            iconView?.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(150)
                ?.withEndAction { iconView.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }?.start()

            true
        }
    }

    private fun applyTab(tabId: Int) {
        // ✅ ЗАЩИТА ОТ РЕКУРСИИ ВНУТРИ МЕТОДА: не обновляем UI, если он уже правильный
        if (currentSelectedItemId == tabId && binding.bottomNavigation.selectedItemId == tabId) {
            // Если это первый запуск или восстановление после поворота,
            // всё равно нужно убедиться, что фрагмент загружен
            if (currentFragment == null) {
                loadFragmentForTab(tabId)
            }
            return
        }

        currentSelectedItemId = tabId
        binding.bottomNavigation.selectedItemId = tabId

        loadFragmentForTab(tabId)
    }

    private fun loadFragmentForTab(tabId: Int) {
        when (tabId) {
            R.id.nav_my_places -> { ensureMapFragmentIsVisible(); spotViewModel.switchToMySpots() }
            R.id.nav_global_map -> { ensureMapFragmentIsVisible(); spotViewModel.switchToPublicSpots() }
            R.id.nav_feed -> switchFragment(FeedFragment(), tabId)
            R.id.nav_stats -> switchFragment(MessagesFragment(), tabId)
            R.id.nav_fishermen -> switchFragment(FishermenFragment(), tabId)
        }
    }

    private fun ensureMapFragmentIsVisible() {
        if (currentFragment !is MyMapFragment) {
            switchFragment(MyMapFragment(), currentSelectedItemId)
        }
    }

    private fun switchFragment(fragment: Fragment, itemId: Int): Boolean {
        if (fragment::class.java == currentFragment?.javaClass) return true
        childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
        currentFragment = fragment
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