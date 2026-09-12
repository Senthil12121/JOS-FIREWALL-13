package com.jos.firewall.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jos.firewall.JosFirewallApp
import com.jos.firewall.R
import com.jos.firewall.data.FirewallRuleEntity
import com.jos.firewall.data.RuleAction
import com.jos.firewall.data.RuleDirection
import com.jos.firewall.data.RuleType
import com.jos.firewall.databinding.FragmentRulesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as JosFirewallApp
        val repo = app.ruleRepository

        adapter = RulesAdapter(
            onToggleEnabled = { rule ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.toggleFirewallRule(rule)
                }
            },
            onDeleteClicked = { rule ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.deleteFirewallRule(rule)
                }
            }
        )

        binding.rvRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRules.adapter = adapter

        binding.fabAddRule.setOnClickListener {
            showAddRuleDialog(repo)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repo.getAllFirewallRulesFlow().collectLatest { rules ->
                adapter.submitList(rules)
            }
        }
    }

    private fun showAddRuleDialog(repo: com.jos.firewall.firewall.RuleRepository) {
        val dialogView = layoutInflater.inflate(R.layout.fragment_settings, null) // simple dialog
        val inputName = EditText(requireContext()).apply { hint = "Rule Name (e.g. Block Port 80)" }
        val inputPattern = EditText(requireContext()).apply { hint = "Port or IP (e.g. 80 or 192.168.1.1)" }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(inputName)
            addView(inputPattern)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Firewall Rule")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = inputName.text.toString().trim()
                val pattern = inputPattern.text.toString().trim()
                if (name.isNotEmpty() && pattern.isNotEmpty()) {
                    val isNumericPort = pattern.all { it.isDigit() }
                    val newRule = FirewallRuleEntity(
                        ruleName = name,
                        ruleType = if (isNumericPort) RuleType.PORT else RuleType.IP,
                        pattern = pattern,
                        direction = RuleDirection.OUTBOUND,
                        action = RuleAction.BLOCK,
                        protocol = "TCP",
                        isEnabled = true
                    )
                    lifecycleScope.launch(Dispatchers.IO) {
                        repo.addFirewallRule(newRule)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
