package com.netblocker.pro.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.model.AppEntry

/**
 * iOS-style per-app options: block everything, Wi-Fi only or mobile data only.
 * In whitelist mode the same controls act as "allow" switches.
 */
class AppDetailSheet : BottomSheetDialogFragment() {

    interface Host {
        fun entryFor(uid: Int): AppEntry?
        fun onPolicyChanged(uid: Int, policy: Int)
    }

    private var host: Host? = null
    private var uid = -1
    private var entry: AppEntry? = null

    private lateinit var swAll: MaterialSwitch
    private lateinit var swWifi: MaterialSwitch
    private lateinit var swData: MaterialSwitch
    private lateinit var tvState: TextView
    private lateinit var tvAllLabel: TextView
    private lateinit var tvWifiLabel: TextView
    private lateinit var tvDataLabel: TextView
    private lateinit var rowWifi: LinearLayout
    private lateinit var rowData: LinearLayout

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = parentFragment as? Host
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        uid = requireArguments().getInt(ARG_UID)
        entry = host?.entryFor(uid)
        return buildView()
    }

    private fun buildView(): View {
        val ctx = requireContext()
        val app = entry

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(28))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dpF(20f), dpF(20f), dpF(20f), dpF(20f), 0f, 0f, 0f, 0f)
                setColor(ContextCompat.getColor(ctx, R.color.ios_bg))
            }
        }

        // Grabber
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = GradientDrawable().apply {
                cornerRadius = dpF(3f)
                setColor(ContextCompat.getColor(ctx, R.color.ios_tertiary))
            }
        })

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(6))
        }
        val icon = ShapeableImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(dpF(12f)).build()
            setImageDrawable(app?.icon)
        }
        header.addView(icon)
        val titleBlock = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        titleBlock.addView(TextView(ctx).apply {
            text = app?.label ?: ""
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
            maxLines = 1
            ellipsize = null
        })
        titleBlock.addView(TextView(ctx).apply {
            text = app?.packageName ?: ""
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary))
            maxLines = 1
        })
        header.addView(titleBlock)
        root.addView(header)

        tvState = TextView(ctx).apply {
            textSize = 14f
            setPadding(0, dp(2), 0, dp(14))
        }
        root.addView(tvState)

        // Switch card
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
        }

        tvAllLabel = TextView(ctx)
        tvWifiLabel = TextView(ctx)
        tvDataLabel = TextView(ctx)

        swAll = makeSwitch(ctx)
        swWifi = makeSwitch(ctx)
        swData = makeSwitch(ctx)

        card.addView(switchRow(ctx, R.drawable.ic_globe, tvAllLabel, swAll))
        card.addView(divider(ctx))
        rowWifi = switchRow(ctx, R.drawable.ic_wifi, tvWifiLabel, swWifi)
        card.addView(rowWifi)
        card.addView(divider(ctx))
        rowData = switchRow(ctx, R.drawable.ic_cell, tvDataLabel, swData)
        card.addView(rowData)
        root.addView(card)

        // Info card
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        app?.let {
            info.addView(infoRow(ctx, getString(R.string.package_label), it.packageName))
            info.addView(divider(ctx, indent = dp(16)))
            info.addView(infoRow(ctx, getString(R.string.uid_label), it.uid.toString()))
            if (it.versionName.isNotBlank()) {
                info.addView(divider(ctx, indent = dp(16)))
                info.addView(infoRow(ctx, getString(R.string.version_label), it.versionName))
            }
            info.addView(divider(ctx, indent = dp(16)))
            info.addView(
                infoRow(
                    ctx,
                    getString(R.string.type_label),
                    getString(if (it.isSystem) R.string.type_system else R.string.type_user)
                )
            )
        }
        root.addView(info)

        refresh()
        return root
    }

    private fun makeSwitch(ctx: Context): MaterialSwitch = MaterialSwitch(ctx).apply {
        thumbTintList = ContextCompat.getColorStateList(ctx, R.color.switch_thumb)
        trackTintList = ContextCompat.getColorStateList(ctx, R.color.switch_track)
    }

    private fun switchRow(ctx: Context, iconRes: Int, titleView: TextView, sw: MaterialSwitch): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        row.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(ctx, R.color.ios_blue))
        })
        titleView.apply {
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        row.addView(titleView)
        row.addView(sw)
        return row
    }

    private fun infoRow(ctx: Context, label: String, value: String): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = value
            textSize = 14f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
        })
        return row
    }

    private fun divider(ctx: Context, indent: Int = 0): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = indent
        }
        setBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_separator))
    }

    private fun refresh() {
        if (!isAdded) return
        val ctx = requireContext()
        val app = entry ?: return
        val whitelist = Prefs.whitelistMode(ctx)
        val verb = if (whitelist) getString(R.string.s_mode_whitelist) else getString(R.string.s_mode_blacklist)
        val allow = whitelist
        tvAllLabel.text = getString(if (allow) R.string.allow_internet else R.string.block_internet)
        tvWifiLabel.text = getString(if (allow) R.string.allow_wifi else R.string.block_wifi)
        tvDataLabel.text = getString(if (allow) R.string.allow_data else R.string.block_data)

        val all = app.policy == AppEntry.POLICY_BLOCK_ALL
        val wifi = app.affectsWifi
        val data = app.affectsData

        swAll.setOnCheckedChangeListener(null)
        swWifi.setOnCheckedChangeListener(null)
        swData.setOnCheckedChangeListener(null)

        swAll.isChecked = all
        swWifi.isChecked = wifi
        swData.isChecked = data
        swWifi.isEnabled = !all
        swData.isEnabled = !all
        rowWifi.alpha = if (all) 0.45f else 1f
        rowData.alpha = if (all) 0.45f else 1f

        swAll.setOnCheckedChangeListener { _, checked ->
            emit(if (checked) AppEntry.POLICY_BLOCK_ALL else AppEntry.POLICY_ALLOW)
        }
        swWifi.setOnCheckedChangeListener { _, checked ->
            if (!all) emit(if (checked) AppEntry.POLICY_BLOCK_WIFI else AppEntry.POLICY_ALLOW)
        }
        swData.setOnCheckedChangeListener { _, checked ->
            if (!all) emit(if (checked) AppEntry.POLICY_BLOCK_DATA else AppEntry.POLICY_ALLOW)
        }

        val (text, colorRes) = if (whitelist) {
            when (app.policy) {
                AppEntry.POLICY_BLOCK_ALL -> "Allowed on all networks" to R.color.ios_green
                AppEntry.POLICY_BLOCK_WIFI -> "Allowed on Wi-Fi only" to R.color.ios_blue
                AppEntry.POLICY_BLOCK_DATA -> "Allowed on mobile data only" to R.color.ios_blue
                else -> "Not whitelisted" to R.color.ios_red
            }
        } else {
            when (app.policy) {
                AppEntry.POLICY_BLOCK_ALL -> "Internet blocked on all networks" to R.color.ios_red
                AppEntry.POLICY_BLOCK_WIFI -> "Blocked on Wi-Fi" to R.color.ios_orange
                AppEntry.POLICY_BLOCK_DATA -> "Blocked on mobile data" to R.color.ios_orange
                else -> "Internet allowed" to R.color.ios_green
            }
        }
        tvState.text = text
        tvState.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }

    private fun emit(policy: Int) {
        host?.onPolicyChanged(uid, policy)
        entry = host?.entryFor(uid)
        refresh()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dpF(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val ARG_UID = "uid"

        fun newInstance(uid: Int): AppDetailSheet {
            val sheet = AppDetailSheet()
            sheet.arguments = Bundle().apply { putInt(ARG_UID, uid) }
            return sheet
        }
    }
}
