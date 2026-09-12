package com.jos.firewall.ui.locations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.jos.firewall.databinding.FragmentLocationsBinding
import com.jos.firewall.vpn.LocationManager
import com.jos.firewall.vpn.LocationNode

class LocationsFragment : Fragment() {

    private var _binding: FragmentLocationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locationManager = LocationManager.getInstance(requireContext())
        val currentLocation = locationManager.currentLocationFlow.value

        val adapter = LocationsAdapter(
            locations = LocationNode.DEFAULT_LOCATIONS,
            selectedId = currentLocation.id
        ) { selectedNode ->
            locationManager.setLocation(selectedNode)
        }

        binding.rvLocations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
