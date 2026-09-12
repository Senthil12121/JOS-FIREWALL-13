package com.jos.firewall.ui.dns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.DnsRuleEntity
import com.jos.firewall.databinding.FragmentDnsBinding
import com.jos.firewall.dns.DnsProvider
import com.jos.firewall.dns.DnsProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.net.HttpURLConnection
import java.net.URL

class DnsFragment : Fragment() {

    private var _binding: FragmentDnsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DnsRulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        adapter = DnsRulesAdapter { updatedRule ->
            lifecycleScope.launch(Dispatchers.IO) {
                dao.updateDnsRule(updatedRule)
            }
        }

        binding.rvDnsRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDnsRules.adapter = adapter

        binding.btnAddDomain.setOnClickListener {
            showAddDomainDialog(dao)
        }

        binding.btnImportBlocklist.setOnClickListener {
            showImportBlocklistDialog(dao)
        }

        setupDnsProviderPicker()

        viewLifecycleOwner.lifecycleScope.launch {
            dao.getAllDnsRulesFlow().collectLatest { rules ->
                adapter.submitList(rules)
                binding.tvBlockedCount.text = "BLOCKED DOMAINS (${rules.count { it.isBlocked }})"
            }
        }
    }

    private fun setupDnsProviderPicker() {
        val dnsManager = DnsProviderManager.getInstance(requireContext())
        val current = dnsManager.currentProviderFlow.value

        val radioIdFor = mapOf(
            DnsProvider.CLOUDFLARE.id to R.id.rb_cloudflare,
            DnsProvider.ADGUARD.id to R.id.rb_adguard,
            DnsProvider.GOOGLE.id to R.id.rb_google,
            DnsProvider.QUAD9.id to R.id.rb_quad9
        )
        radioIdFor[current.id]?.let { binding.rgDnsServers.check(it) }

        binding.rgDnsServers.setOnCheckedChangeListener { _, checkedId ->
            val provider = when (checkedId) {
                R.id.rb_cloudflare -> DnsProvider.CLOUDFLARE
                R.id.rb_adguard -> DnsProvider.ADGUARD
                R.id.rb_google -> DnsProvider.GOOGLE
                R.id.rb_quad9 -> DnsProvider.QUAD9
                else -> return@setOnCheckedChangeListener
            }
            dnsManager.setProvider(provider)
            Toast.makeText(
                requireContext(),
                "DNS provider set to ${provider.displayName}. Restart the firewall to apply.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showAddDomainDialog(dao: com.jos.firewall.data.FirewallDao) {
        val input = EditText(requireContext()).apply {
            hint = "e.g. tracker.example.com"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Domain to Blocklist")
            .setView(input)
            .setPositiveButton("Block Domain") { _, _ ->
                val domain = input.text.toString().trim().lowercase()
                if (domain.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.insertDnsRules(listOf(DnsRuleEntity(domain, isBlocked = true, category = "CUSTOM")))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImportBlocklistDialog(dao: com.jos.firewall.data.FirewallDao) {
        val input = EditText(requireContext()).apply {
            hint = "Paste a hosts-file URL, or paste domains directly (one per line)"
            minLines = 3
            maxLines = 6
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Blocklist")
            .setMessage("Paste a hosts-format blocklist URL (e.g. StevenBlack's hosts file) or paste raw domains, one per line.")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton

                lifecycleScope.launch(Dispatchers.IO) {
                    val domains = if (text.startsWith("http://") || text.startsWith("https://")) {
                        fetchAndParseHostsList(text)
                    } else {
                        parseDomainLines(text)
                    }

                    if (domains.isNotEmpty()) {
                        dao.insertDnsRules(
                            domains.map { DnsRuleEntity(it, isBlocked = true, category = "IMPORTED") }
                        )
                    }

                    withContextMain {
                        Toast.makeText(
                            requireContext(),
                            if (domains.isNotEmpty()) "Imported ${domains.size} domains" else "No valid domains found",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun withContextMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
    }

    /**
     * Downloads a hosts-format blocklist (lines like "0.0.0.0 domain.com") and extracts domains.
     * Runs on IO dispatcher; caller is responsible for switching context.
     */
    private fun fetchAndParseHostsList(urlString: String): List<String> {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            parseDomainLines(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDomainLines(text: String): List<String> {
        val domainRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        val results = mutableSetOf<String>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore("#").trim()
            if (line.isEmpty()) return@forEach

            // Hosts-file format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
            val parts = line.split(Regex("\\s+"))
            val candidate = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> null
            }

            candidate?.lowercase()?.let {
                if (it != "localhost" && domainRegex.matches(it)) {
                    results.add(it)
                }
            }
        }
        return results.toList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
