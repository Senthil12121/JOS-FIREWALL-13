package com.jos.firewall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.jos.firewall.databinding.ActivityMainBinding
import com.jos.firewall.ui.applications.ApplicationsFragment
import com.jos.firewall.ui.dashboard.DashboardFragment
import com.jos.firewall.ui.dns.DnsFragment
import com.jos.firewall.ui.logs.LogsFragment
import com.jos.firewall.ui.rules.RulesFragment
import com.jos.firewall.ui.settings.SettingsFragment
import com.jos.firewall.vpn.FirewallVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnServiceInternal()
        } else {
            Toast.makeText(this, "VPN permission required to inspect traffic", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission granted/denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        requestNotificationPermission()
        setupNavigation()

        if (savedInstanceState == null) {
            switchFragment(DashboardFragment())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            switchFragment(SettingsFragment())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    switchFragment(DashboardFragment())
                    true
                }
                R.id.nav_applications -> {
                    switchFragment(ApplicationsFragment())
                    true
                }
                R.id.nav_rules -> {
                    switchFragment(RulesFragment())
                    true
                }
                R.id.nav_logs -> {
                    switchFragment(LogsFragment())
                    true
                }
                R.id.nav_dns -> {
                    switchFragment(DnsFragment())
                    true
                }
                else -> false
            }
        }
    }

    fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun openDetailFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun toggleFirewall() {
        val isRunning = FirewallVpnService.isRunningFlow.value
        if (isRunning) {
            FirewallVpnService.stopService(this)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent)
            } else {
                startVpnServiceInternal()
            }
        }
    }

    private fun startVpnServiceInternal() {
        FirewallVpnService.startService(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
