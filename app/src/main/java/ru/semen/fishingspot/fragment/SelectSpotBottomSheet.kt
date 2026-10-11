package ru.semen.fishingspot.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.semen.fishingspot.data.FishingSpot
import ru.semen.fishingspot.databinding.BottomSheetSelectSpotBinding
import ru.semen.fishingspot.databinding.ItemSelectSpotBinding
import ru.semen.fishingspot.viewmodel.SpotViewModel

class SelectSpotBottomSheet(
    private val spotViewModel: SpotViewModel,
    private val requesterName: String,
    private val onSpotSelected: (FishingSpot) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectSpotBinding? = null
    private val binding get() = _binding!!

    private var selectedSpot: FishingSpot? = null
    private var spotsAdapter: SpotsAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSelectSpotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeSpots()
        setupSendButton()
    }

    private fun setupRecyclerView() {
        spotsAdapter = SpotsAdapter { spot ->
            selectedSpot = spot
            spotsAdapter?.setSelectedSpot(spot)
            binding.btnSendSpot.isEnabled = true
        }
        binding.rvSpots.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSpots.adapter = spotsAdapter
    }

    private fun observeSpots() {
        // ✅ НАБЛЮДАЕМ ЗА ТВОИМИ РЕАЛЬНЫМИ ТОЧКАМИ ИЗ ROOM ЧЕРЕЗ SpotViewModel
        spotViewModel.allSpots.observe(viewLifecycleOwner) { spots ->
            if (spots.isNullOrEmpty()) {
                binding.rvSpots.visibility = View.GONE
                // Можно добавить TextView "У вас пока нет сохраненных точек"
            } else {
                binding.rvSpots.visibility = View.VISIBLE
                spotsAdapter?.submitList(spots)
            }
        }
    }

    private fun setupSendButton() {
        binding.btnSendSpot.setOnClickListener {
            selectedSpot?.let { spot ->
                onSpotSelected(spot)
                dismiss()
            }
        }
    }

    inner class SpotsAdapter(
        private val onItemClick: (FishingSpot) -> Unit
    ) : RecyclerView.Adapter<SpotsAdapter.SpotViewHolder>() {

        private var items: List<FishingSpot> = emptyList()
        private var currentSelected: FishingSpot? = null

        fun submitList(newList: List<FishingSpot>) {
            items = newList
            notifyDataSetChanged()
        }

        fun setSelectedSpot(spot: FishingSpot) {
            currentSelected = spot
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
            val binding = ItemSelectSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SpotViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
            holder.bind(items[position], items[position] == currentSelected)
            holder.itemView.setOnClickListener { onItemClick(items[position]) }
        }

        override fun getItemCount() = items.size

        inner class SpotViewHolder(private val binding: ItemSelectSpotBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(spot: FishingSpot, isSelected: Boolean) {
                // ✅ Используем реальные поля из твоего класса FishingSpot
                binding.tvSpotName.text = spot.name
                binding.tvSpotCoords.text = "${spot.latitude}, ${spot.longitude}"
                binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}