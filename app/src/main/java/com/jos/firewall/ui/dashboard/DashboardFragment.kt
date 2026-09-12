package com.jos.firewall.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jos.firewall.MainActivity
import com.jos.firewall.R
import com.jos.firewall.databinding.FragmentDashboardBinding
import com.jos.firewall.ui.applications.ApplicationsFragment
import com.jos.firewall.ui.locations.LocationsFragment
import com.jos.firewall.vpn.FirewallVpnService
import com.jos.firewall.vpn.LocationManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToggleFirewall.setOnClickListener {
            (activity as? MainActivity)?.toggleFirewall()
        }

        binding.btnViewAllApps.setOnClickListener {
            (activity as? MainActivity)?.switchFragment(ApplicationsFragment())
        }

        binding.cardLocation.setOnClickListener {
            (activity as? MainActivity)?.openDetailFragment(LocationsFragment())
        }

        populateSampleQuickApps()
        observeVpnState()
        observeTrafficStats()
        observeLocation()
    }

    private fun observeLocation() {
        val locationManager = LocationManager.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            locationManager.currentLocationFlow.collectLatest { node ->
                binding.tvLocationFlag.text = node.flagEmoji
                binding.tvLocationName.text = if (node.isDirect) {
                    node.cityName
                } else {
                    "${node.cityName}, ${node.countryName}"
                }
                binding.tvLocationDetail.text = if (node.isDirect) {
                    node.exitIp
                } else {
                    "${node.protocol.name.replace("_", " ")} • ${node.latencyMs} ms"
                }
            }
        }
    }

    private fun observeVpnState() {
        viewLifecycleOwner.lifecycleScope.launch {
            FirewallVpnService.isRunningFlow.collectLatest { isRunning ->
                if (isRunning) {
                    binding.tvStatus.text = getString(R.string.firewall_on)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_connected))
                    binding.btnToggleFirewall.text = getString(R.string.btn_stop_firewall)
                    binding.btnToggleFirewall.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.error)
                    )
                    binding.ivShield.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.primary)
                    )
                } else {
                    binding.tvStatus.text = getString(R.string.firewall_off)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected))
                    binding.btnToggleFirewall.text = getString(R.string.btn_start_firewall)
                    binding.btnToggleFirewall.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.primary)
                    )
                    binding.ivShield.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.status_disconnected)
                    )
                }
            }
        }
    }

    private fun observeTrafficStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            FirewallVpnService.statsManager.statsFlow.collectLatest { stats ->
                binding.tvDownloadVal.text = stats.formattedDownload
                binding.tvUploadVal.text = stats.formattedUpload
                binding.tvAllowedVal.text = stats.packetsAllowed.toString()
                binding.tvBlockedVal.text = stats.packetsBlocked.toString()
            }
        }
    }

    private fun populateSampleQuickApps() {
        binding.llQuickApps.removeAllViews()

        val sampleApps = listOf(
            Triple("Chrome", "ALLOW", R.color.secondary),
            Triple("YouTube", "BLOCK", R.color.error),
            Triple("WhatsApp", "ALLOW", R.color.secondary),
            Triple("Instagram", "BLOCK", R.color.error),
            Triple("Camera", "BLOCK", R.color.error)
        )

        val inflater = LayoutInflater.from(requireContext())
        for ((name, status, colorRes) in sampleApps) {
            val itemView = inflater.inflate(R.layout.item_application, binding.llQuickApps, false)
            val tvName = itemView.findViewById<TextView>(R.id.tv_app_name)
            val tvPkg = itemView.findViewById<TextView>(R.id.tv_package_uid)
            val switchAllow = itemView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_allow)

            tvName.text = name
            tvPkg.text = "Sample Rule: $status"
            switchAllow.isChecked = (status == "ALLOW")

            binding.llQuickApps.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
