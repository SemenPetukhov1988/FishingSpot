package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }

        if (savedInstanceState == null) {
            switchFragment(MyMapFragment(), R.id.nav_my_places)
        } else {
            binding.bottomNavigation.selectedItemId =
                savedInstanceState.getInt("SELECTED_ITEM", R.id.nav_my_places)
        }

        binding.menuButton.setOnClickListener {
            // TODO: Открыть боковое меню
        }

        binding.profileButton.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_fishermen
        }

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
