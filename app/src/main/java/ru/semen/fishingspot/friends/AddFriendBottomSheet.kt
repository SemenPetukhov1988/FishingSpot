package ru.semen.fishingspot.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.semen.fishingspot.friends.adapter.UserSearchAdapter
import ru.semen.fishingspot.databinding.FragmentAddFriendBottomSheetBinding
import ru.semen.fishingspot.friends.FriendsViewModel

class AddFriendBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddFriendBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendsViewModel by activityViewModels()
    private lateinit var searchAdapter: UserSearchAdapter
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddFriendBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
    }

    private fun setupRecyclerView() {
        searchAdapter = UserSearchAdapter { user ->
            // При клике отправляем запрос через ViewModel
            viewModel.sendFriendRequest(user.id, user.name)
            dismiss() // Закрываем шторку после отправки
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter
    }

    private fun setupSearch() {
        binding.etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                searchJob?.cancel()

                if (query.isEmpty()) {
                    searchAdapter.submitList(emptyList())
                    binding.tvEmptySearch.visibility = View.VISIBLE
                    binding.tvEmptySearch.text = "Введите имя для поиска"
                    binding.progressSearch.visibility = View.GONE
                    return
                }

                binding.progressSearch.visibility = View.VISIBLE
                binding.tvEmptySearch.visibility = View.GONE

                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(400) // Debounce
                    performSearch(query)
                }
            }
        })
    }

    private suspend fun performSearch(query: String) {
        val results = viewModel.searchUsers(query)
        binding.progressSearch.visibility = View.GONE

        if (results.isEmpty()) {
            binding.tvEmptySearch.visibility = View.VISIBLE
            binding.tvEmptySearch.text = "Ничего не найдено"
        } else {
            binding.tvEmptySearch.visibility = View.GONE
        }
        searchAdapter.submitList(results)
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}