package com.jos.firewall.ui.applications

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.data.AppRuleEntity
import com.jos.firewall.databinding.FragmentApplicationsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationsFragment : Fragment() {

    private var _binding: FragmentApplicationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppRuleEntity> = emptyList()
    private var isAscending = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val repo = app.ruleRepository

        adapter = AppListAdapter { updatedRule ->
            lifecycleScope.launch(Dispatchers.IO) {
                repo.updateAppRule(updatedRule)
            }
        }

        binding.rvApplications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApplications.adapter = adapter

        // Search text watcher
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Sort button
        binding.btnSort.setOnClickListener {
            isAscending = !isAscending
            binding.btnSort.text = if (isAscending) "A-Z" else "Z-A"
            filterApps(binding.etSearch.text.toString())
        }

        loadInstalledApps(app)
        observeAppRules(repo)
    }

    private fun loadInstalledApps(app: JosFirewallApp) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireContext().packageManager
            val installed = pm.getInstalledApplications(0)
            val currentRules = app.database.firewallDao().getAllAppRules()
            val ruleMap = currentRules.associateBy { it.packageName }

            val appEntities = installed
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.contains("chrome") }
                .map { info ->
                    val pkg = info.packageName
                    val name = pm.getApplicationLabel(info).toString()
                    ruleMap[pkg] ?: AppRuleEntity(
                        packageName = pkg,
                        uid = info.uid,
                        appName = name,
                        isWifiAllowed = true,
                        isMobileAllowed = true,
                        isBlocked = false
                    )
                }

            app.database.firewallDao().insertAppRules(appEntities)
        }
    }

    private fun observeAppRules(repo: com.jos.firewall.firewall.RuleRepository) {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.getAllAppRulesFlow().collectLatest { rules ->
                withContext(Dispatchers.Main) {
                    allApps = rules
                    binding.tvAppCount.text = "Installed Apps: ${rules.size}"
                    filterApps(binding.etSearch.text.toString())
                }
            }
        }
    }

    private fun filterApps(query: String) {
        var filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }

        filtered = if (isAscending) {
            filtered.sortedBy { it.appName.lowercase() }
        } else {
            filtered.sortedByDescending { it.appName.lowercase() }
        }

        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
