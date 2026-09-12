package com.jos.firewall.ui.locations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jos.firewall.R
import com.jos.firewall.vpn.LocationNode

class LocationsAdapter(
    private val locations: List<LocationNode>,
    private var selectedId: String,
    private val onLocationSelected: (LocationNode) -> Unit
) : RecyclerView.Adapter<LocationsAdapter.LocationViewHolder>() {

    inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFlag: TextView = itemView.findViewById(R.id.tv_flag)
        val tvCityCountry: TextView = itemView.findViewById(R.id.tv_city_country)
        val tvProtocolIp: TextView = itemView.findViewById(R.id.tv_protocol_ip)
        val tvLatency: TextView = itemView.findViewById(R.id.tv_latency)
        val ivSelected: ImageView = itemView.findViewById(R.id.iv_selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val node = locations[position]

        holder.tvFlag.text = node.flagEmoji
        holder.tvCityCountry.text = if (node.isDirect) {
            node.cityName
        } else {
            "${node.cityName}, ${node.countryName}"
        }
        holder.tvProtocolIp.text = "${node.protocol.name.replace("_", " ")} • ${node.exitIp}"
        holder.tvLatency.text = if (node.isDirect) "" else "${node.latencyMs} ms"
        holder.ivSelected.visibility = if (node.id == selectedId) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val previousSelectedId = selectedId
            selectedId = node.id
            onLocationSelected(node)
            notifyItemChanged(locations.indexOfFirst { it.id == previousSelectedId })
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = locations.size
}
