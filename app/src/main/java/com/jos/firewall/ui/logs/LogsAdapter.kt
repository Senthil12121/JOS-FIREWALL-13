package com.jos.firewall.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jos.firewall.R
import com.jos.firewall.data.ConnectionLogEntity
import com.jos.firewall.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter(
    private val onEntryClicked: (com.jos.firewall.data.ConnectionLogEntity) -> Unit = {}
) : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {

    private var items: List<ConnectionLogEntity> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submitList(newItems: List<ConnectionLogEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LogViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConnectionLogEntity) {
            val context = binding.root.context
            val isAllowed = item.status == "ALLOWED"

            binding.tvLogStatus.text = item.status
            binding.tvLogStatus.setTextColor(
                ContextCompat.getColor(context, if (isAllowed) R.color.secondary else R.color.error)
            )

            binding.tvLogAppName.text = item.appName ?: "Unknown"
            binding.tvLogTimestamp.text = timeFormat.format(Date(item.timestamp))
            binding.tvLogConnection.text = "${item.srcIp}:${item.srcPort} → ${item.dstIp}:${item.dstPort}"
            binding.tvLogProto.text = item.protocol
            binding.tvLogReason.text = "Rule: ${item.reason} • UID ${item.uid}"

            binding.root.setOnClickListener {
                onEntryClicked(item)
            }
        }
    }
}
