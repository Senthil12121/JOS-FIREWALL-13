package com.jos.firewall.ui.applications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jos.firewall.R
import com.jos.firewall.data.AppRuleEntity
import com.jos.firewall.databinding.ItemApplicationBinding

class AppListAdapter(
    private val onRuleChanged: (AppRuleEntity) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var items: List<AppRuleEntity> = emptyList()

    fun submitList(newItems: List<AppRuleEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemApplicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(private val binding: ItemApplicationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppRuleEntity) {
            val context = binding.root.context
            binding.tvAppName.text = item.appName
            binding.tvPackageUid.text = "${item.packageName} • UID ${item.uid}"

            // Master switch: if blocked is true, switch is unchecked
            binding.switchAllow.isChecked = !item.isBlocked

            // Wi-Fi icon color
            val wifiTint = if (item.isWifiAllowed) R.color.secondary else R.color.error
            binding.btnToggleWifi.setColorFilter(ContextCompat.getColor(context, wifiTint))

            // Mobile icon color
            val mobileTint = if (item.isMobileAllowed) R.color.secondary else R.color.error
            binding.btnToggleMobile.setColorFilter(ContextCompat.getColor(context, mobileTint))

            // Event Listeners
            binding.switchAllow.setOnCheckedChangeListener { _, isChecked ->
                onRuleChanged(item.copy(isBlocked = !isChecked))
            }

            binding.btnToggleWifi.setOnClickListener {
                val updatedWifi = !item.isWifiAllowed
                onRuleChanged(item.copy(isWifiAllowed = updatedWifi))
            }

            binding.btnToggleMobile.setOnClickListener {
                val updatedMobile = !item.isMobileAllowed
                onRuleChanged(item.copy(isMobileAllowed = updatedMobile))
            }
        }
    }
}
