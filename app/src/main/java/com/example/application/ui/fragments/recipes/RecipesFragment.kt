package com.example.application.ui.fragments.recipes

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.R
import com.example.application.R.id.action_search
import com.example.application.databinding.FragmentRecipesBinding
import com.example.application.ui.adapter.RecipesAdapter
import com.example.application.ui.viewmodels.MainViewModel
import com.example.application.ui.viewmodels.RecipesViewModel
import com.example.application.utils.network.NetworkListener
import com.example.application.utils.observeOnce
import com.example.application.utils.safeapi.NetworkResult
import com.example.application.utils.view.onQueryTextChanged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecipesFragment : Fragment(R.layout.fragment_recipes) {
    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!
    private val recipesAdapter by lazy { RecipesAdapter() }
    private val mainViewModel: MainViewModel by viewModels()
    private val recipesViewModel: RecipesViewModel by viewModels()

    private val args by navArgs<RecipesFragmentArgs>()
    private lateinit var menuHost: MenuHost

    @Inject
    lateinit var networkListener: NetworkListener
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.mainViewModel = mainViewModel
        menuHost = requireActivity()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        recipesViewModel.readBackOnline.observe(viewLifecycleOwner) {
            recipesViewModel.backOnline = it
        }

        lifecycleScope.launch {
            networkListener.checkNetworkAvailability().collectLatest { status ->
                recipesViewModel.networkStatus = status
                recipesViewModel.showNetworkStatus()
                readDatabase()
            }
        }

        with(binding) {
            floatingActionButton.setOnClickListener {
                if (recipesViewModel.networkStatus)
                    findNavController().navigate(RecipesFragmentDirections.actionRecipesFragmentToRecipesBottomSheet())
                else recipesViewModel.showNetworkStatus()
            }
        }
        menuHost.addMenuProvider(object : MenuProvider{
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_recipes, menu)
                val searchAction = menu.findItem(action_search)
                val searchView = searchAction.actionView as SearchView
                searchView.isSubmitButtonEnabled = true
                searchView.onQueryTextChanged {

                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    else -> false
                }
            }
        })
    }

    private fun readDatabase() {
        mainViewModel.getRecipe()
        mainViewModel.readRecipes.observeOnce(viewLifecycleOwner) {
            if (it.toMutableList().isNotEmpty() && !args.backFromBTS) {
                recipesAdapter.setData(it[0].foodRecipes);hideShimmerEffect()
            } else requestApiData()
        }
    }

    private fun requestApiData() {
        mainViewModel.getRecipes(recipesViewModel.applyQueries())
        mainViewModel.recipeResponse.observe(viewLifecycleOwner) { response ->
            when (response) {
                is NetworkResult.Error -> {
                    hideShimmerEffect()
                    // TODO: toast network error
                    loadDataFromCach()
                }

                is NetworkResult.Loading -> {
                    showShimmerEffect()
                }

                is NetworkResult.Success -> {
                    hideShimmerEffect()
                    response.data?.let { recipesAdapter.setData(it) }
                }
            }
        }
    }

    private fun loadDataFromCach() {
        lifecycleScope.launch {
            mainViewModel.readRecipes.observe(viewLifecycleOwner) {
                if (it.isNotEmpty()) recipesAdapter.setData(it[0].foodRecipes)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvRecipes.apply {
            adapter = recipesAdapter
            layoutManager = LinearLayoutManager(this.context)
            showShimmerEffect()
        }
    }

    private fun showShimmerEffect() {
        binding.rvRecipes.showShimmer()
    }

    private fun hideShimmerEffect() {
        binding.rvRecipes.hideShimmer()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}