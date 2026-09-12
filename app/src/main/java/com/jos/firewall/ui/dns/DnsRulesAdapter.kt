package com.jos.firewall.ui.dns

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jos.firewall.data.DnsRuleEntity
import com.jos.firewall.databinding.ItemDnsRuleBinding

class DnsRulesAdapter(
    private val onToggleBlock: (DnsRuleEntity) -> Unit
) : RecyclerView.Adapter<DnsRulesAdapter.DnsViewHolder>() {

    private var items: List<DnsRuleEntity> = emptyList()

    fun submitList(newItems: List<DnsRuleEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DnsViewHolder {
        val binding = ItemDnsRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DnsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DnsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DnsViewHolder(private val binding: ItemDnsRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DnsRuleEntity) {
            binding.tvDomain.text = item.domain
            binding.tvDomainCategory.text = "Category: ${item.category} • Hits: ${item.hitCount}"
            binding.switchDnsBlock.isChecked = item.isBlocked

            binding.switchDnsBlock.setOnCheckedChangeListener { _, isChecked ->
                onToggleBlock(item.copy(isBlocked = isChecked))
            }
        }
    }
}
