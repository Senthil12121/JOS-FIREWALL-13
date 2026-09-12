package com.jos.firewall.ui.rules

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jos.firewall.R
import com.jos.firewall.data.FirewallRuleEntity
import com.jos.firewall.data.RuleAction
import com.jos.firewall.databinding.ItemRuleBinding

class RulesAdapter(
    private val onToggleEnabled: (FirewallRuleEntity) -> Unit,
    private val onDeleteClicked: (FirewallRuleEntity) -> Unit
) : RecyclerView.Adapter<RulesAdapter.RuleViewHolder>() {

    private var items: List<FirewallRuleEntity> = emptyList()

    fun submitList(newItems: List<FirewallRuleEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RuleViewHolder(private val binding: ItemRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FirewallRuleEntity) {
            val context = binding.root.context

            binding.tvRuleAction.text = item.action.name
            binding.tvRuleAction.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.action == RuleAction.BLOCK) R.color.error else R.color.secondary
                )
            )

            binding.tvRuleType.text = "[${item.ruleType.name}: ${item.pattern} | ${item.protocol}]"
            binding.tvRulePattern.text = item.ruleName
            binding.tvRuleDetails.text = "${if (item.isIpv6) "IPv6" else "IPv4"} • Direction: ${item.direction.name}"

            binding.switchRuleEnabled.isChecked = item.isEnabled

            binding.switchRuleEnabled.setOnCheckedChangeListener { _, _ ->
                onToggleEnabled(item)
            }

            binding.btnDeleteRule.setOnClickListener {
                onDeleteClicked(item)
            }
        }
    }
}
