package com.example.pingmonitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HostAdapter(private val ctx: Context, private val onToggle: (HostEntry) -> Unit)
    : RecyclerView.Adapter<HostAdapter.VH>() {

    private val items = mutableListOf<HostEntry>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ipText: TextView = view.findViewById(R.id.ipText)
        val activeSwitch: Switch = view.findViewById(R.id.activeSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_host, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.ipText.text = item.ip
        holder.activeSwitch.isChecked = item.active
        holder.activeSwitch.setOnCheckedChangeListener(null)
        holder.activeSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            item.active = isChecked
            onToggle(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setHosts(list: List<HostEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun updateFromService(data: ArrayList<HashMap<String, Any>>) {
        // update items' history and active status
        for (d in data) {
            val ip = d["ip"] as? String ?: continue
            val idx = items.indexOfFirst { it.ip == ip }
            if (idx >= 0) {
                val hist = d["history"] as? ArrayList<Double> ?: arrayListOf()
                items[idx].history.clear()
                items[idx].history.addAll(hist)
                items[idx].active = d["active"] as? Boolean ?: items[idx].active
            }
        }
        notifyDataSetChanged()
    }
}
