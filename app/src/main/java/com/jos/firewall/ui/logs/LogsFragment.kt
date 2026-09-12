package com.jos.firewall.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.ConnectionLogEntity
import com.jos.firewall.data.FirewallRuleEntity
import com.jos.firewall.data.RuleAction
import com.jos.firewall.data.RuleType
import com.jos.firewall.databinding.FragmentLogsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LogsAdapter
    private var allLogs: List<ConnectionLogEntity> = emptyList()
    private var selectedFilter = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val dao = app.database.firewallDao()

        adapter = LogsAdapter { logEntry ->
            showLogActionDialog(logEntry, dao)
        }
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedFilter = when {
                checkedIds.contains(R.id.chip_allowed) -> "ALLOWED"
                checkedIds.contains(R.id.chip_blocked) -> "BLOCKED"
                else -> "ALL"
            }
            applyFilter()
        }

        binding.btnClearLogs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                dao.clearLogs()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            dao.getRecentLogsFlow().collectLatest { logs ->
                withContext(Dispatchers.Main) {
                    allLogs = logs
                    applyFilter()
                }
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (selectedFilter) {
            "ALLOWED" -> allLogs.filter { it.status == "ALLOWED" }
            "BLOCKED" -> allLogs.filter { it.status == "BLOCKED" }
            else -> allLogs
        }
        adapter.submitList(filtered)
    }

    private fun showLogActionDialog(logEntry: ConnectionLogEntity, dao: com.jos.firewall.data.FirewallDao) {
        val options = mutableListOf<String>()
        if (logEntry.packageName != null) options.add("Block app: ${logEntry.appName ?: logEntry.packageName}")
        options.add("Block destination IP: ${logEntry.dstIp}")
        options.add("Copy connection details")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Connection Details")
            .setMessage(
                "${logEntry.status} • ${logEntry.protocol}\n" +
                    "${logEntry.srcIp}:${logEntry.srcPort} → ${logEntry.dstIp}:${logEntry.dstPort}\n" +
                    "Reason: ${logEntry.reason}"
            )
            .setItems(options.toTypedArray()) { _, index ->
                val chosen = options[index]
                when {
                    chosen.startsWith("Block app") && logEntry.packageName != null -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val existing = dao.getAppRule(logEntry.packageName)
                            val updated = existing?.copy(isBlocked = true)
                                ?: com.jos.firewall.data.AppRuleEntity(
                                    packageName = logEntry.packageName,
                                    uid = logEntry.uid,
                                    appName = logEntry.appName ?: logEntry.packageName,
                                    isBlocked = true
                                )
                            dao.insertOrUpdateAppRule(updated)
                        }
                        Toast.makeText(requireContext(), "Blocked ${logEntry.appName ?: logEntry.packageName}", Toast.LENGTH_SHORT).show()
                    }
                    chosen.startsWith("Block destination IP") -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            dao.insertFirewallRule(
                                FirewallRuleEntity(
                                    ruleName = "Blocked from Logs: ${logEntry.dstIp}",
                                    ruleType = RuleType.IP,
                                    pattern = logEntry.dstIp,
                                    action = RuleAction.BLOCK
                                )
                            )
                        }
                        Toast.makeText(requireContext(), "Blocked IP ${logEntry.dstIp}", Toast.LENGTH_SHORT).show()
                    }
                    chosen.startsWith("Copy connection") -> {
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val text = "${logEntry.srcIp}:${logEntry.srcPort} -> ${logEntry.dstIp}:${logEntry.dstPort} [${logEntry.protocol}] ${logEntry.status}"
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Connection", text))
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
