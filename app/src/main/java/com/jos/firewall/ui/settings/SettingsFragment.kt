package com.jos.firewall.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        binding.switchBoot.isChecked = prefs.getBoolean("start_on_boot", true)
        binding.switchKillswitch.isChecked = prefs.getBoolean("kill_switch", false)

        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("start_on_boot", isChecked).apply()
        }

        binding.switchKillswitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("kill_switch", isChecked).apply()
            Toast.makeText(requireContext(), "Kill-switch ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.switchScreenOffBlock.isChecked = prefs.getBoolean("block_on_screen_off", false)
        binding.switchScreenOffBlock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_on_screen_off", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Internet will pause when screen turns off" else "Screen-off blocking disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rowAlwaysOn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Always-on VPN Setup")
                .setMessage("To prevent leaks during device boot or connection switches:\n\n1. Open Android Settings\n2. Search for 'VPN'\n3. Tap the Gear icon beside 'JOS Firewall'\n4. Enable 'Always-on VPN' and 'Block connections without VPN'")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Open Android Settings -> VPN manually", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnClearAllLogs.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to delete all stored connection audit logs?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.clearLogs()
                    }
                    Toast.makeText(requireContext(), "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnResetRules.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Rules")
                .setMessage("Reset all per-app and firewall rules to default settings?")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.deleteAllFirewallRules()
                    }
                    Toast.makeText(requireContext(), "Rules reset to default", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
