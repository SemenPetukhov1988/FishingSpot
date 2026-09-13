package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import ru.netology.fishingspot.ui.stats.StatsFragment
import ru.netology.fishingspot.ui.user.FishermenFragment

import ru.semen.fishingspot.R
import ru.semen.fishingspot.databinding.FragmentMainTabsBinding
import ru.semen.fishingspot.ui.map.GlobalMapFragment


class MainTabsFragment : Fragment() {

    private var _binding: FragmentMainTabsBinding? = null
    private val binding get() = _binding!!

    // Текущий активный фрагмент (чтобы не пересоздавать его каждый раз)
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

        // 1. Отступы под статус-бар
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }

        // 2. Загружаем первый экран (Мои места) при старте
        if (savedInstanceState == null) {
            switchFragment(MyMapFragment(), R.id.nav_my_places)
        } else {
            // Восстанавливаем состояние после поворота экрана
            binding.bottomNavigation.selectedItemId = savedInstanceState.getInt("SELECTED_ITEM", R.id.nav_my_places)
        }

        // 3. Обработчики кнопок в шапке
        binding.menuButton.setOnClickListener {
            // TODO: Открыть боковое меню
        }

        binding.profileButton.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_fishermen
        }

        // 4. Навигация через нижнее меню
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_my_places -> switchFragment(MyMapFragment(), item.itemId)
                R.id.nav_global_map -> switchFragment(GlobalMapFragment(), item.itemId)
                R.id.nav_stats -> switchFragment(StatsFragment(), item.itemId)
                R.id.nav_fishermen -> switchFragment(FishermenFragment(), item.itemId)
                else -> false
            }
        }
    }

    /**
     * Метод замены фрагмента без анимации свайпа
     */
    private fun switchFragment(fragment: Fragment, itemId: Int): Boolean {
        // Если это тот же самый фрагмент - ничего не делаем
        if (fragment::class.java == currentFragment?.javaClass) {
            return true
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        currentFragment = fragment

        // Сохраняем выбранный пункт для восстановления после поворота
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