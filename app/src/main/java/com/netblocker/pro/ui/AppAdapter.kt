package com.netblocker.pro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.netblocker.pro.R
import com.netblocker.pro.model.AppEntry

class AppAdapter(
    private val onToggle: (AppEntry, Boolean) -> Unit,
    private val onDetail: (AppEntry) -> Unit,
    private val onDismissHint: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<AppEntry> = emptyList()
    private var showHint = false
    private var whitelist = false

    fun submit(list: List<AppEntry>, hint: Boolean, whitelistMode: Boolean) {
        items = list
        showHint = hint
        whitelist = whitelistMode
        notifyDataSetChanged()
    }

    class AppVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ShapeableImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val pkg: TextView = view.findViewById(R.id.tvPkg)
        val state: TextView = view.findViewById(R.id.tvState)
        val sw: MaterialSwitch = view.findViewById(R.id.switchBlock)
    }

    class HintVH(view: View) : RecyclerView.ViewHolder(view) {
        val dismiss: TextView = view.findViewById(R.id.btnDismiss)
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0 && showHint) TYPE_HINT else TYPE_APP

    override fun getItemCount(): Int = items.size + if (showHint) 1 else 0

    private fun appAt(position: Int): AppEntry = items[position - if (showHint) 1 else 0]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HINT) {
            HintVH(inflater.inflate(R.layout.item_hint, parent, false))
        } else {
            AppVH(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HintVH) {
            holder.dismiss.setOnClickListener { onDismissHint() }
            return
        }
        holder as AppVH
        val app = appAt(position)
        val ctx = holder.itemView.context

        holder.name.text = app.label
        holder.pkg.text = app.packageName
        holder.icon.setImageDrawable(app.icon)

        val (text, colorRes) = if (!whitelist) {
            when (app.policy) {
                AppEntry.POLICY_BLOCK_ALL -> "Internet blocked" to R.color.ios_red
                AppEntry.POLICY_BLOCK_WIFI -> "Wi-Fi blocked" to R.color.ios_orange
                AppEntry.POLICY_BLOCK_DATA -> "Mobile data blocked" to R.color.ios_orange
                else -> "Allowed" to R.color.ios_green
            }
        } else {
            when (app.policy) {
                AppEntry.POLICY_BLOCK_ALL -> "Whitelisted · all networks" to R.color.ios_green
                AppEntry.POLICY_BLOCK_WIFI -> "Whitelisted · Wi-Fi only" to R.color.ios_blue
                AppEntry.POLICY_BLOCK_DATA -> "Whitelisted · mobile data only" to R.color.ios_blue
                else -> "Not whitelisted" to R.color.ios_red
            }
        }
        holder.state.text = text
        holder.state.setTextColor(ContextCompat.getColor(ctx, colorRes))

        holder.sw.setOnCheckedChangeListener(null)
        holder.sw.isChecked = app.blockAll
        holder.sw.setOnCheckedChangeListener { _, checked -> onToggle(app, checked) }

        holder.itemView.setOnClickListener { onToggle(app, !app.blockAll) }
        holder.itemView.setOnLongClickListener {
            onDetail(app)
            true
        }
    }

    companion object {
        private const val TYPE_HINT = 0
        private const val TYPE_APP = 1
    }
}
