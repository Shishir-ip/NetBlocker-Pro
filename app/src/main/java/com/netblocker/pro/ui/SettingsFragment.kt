package com.netblocker.pro.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import com.netblocker.pro.firewall.RuleApplier
import com.netblocker.pro.firewall.RootShell
import com.netblocker.pro.util.Backup
import com.netblocker.pro.util.NotifHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var container: LinearLayout? = null
    private var rootStatusValue: TextView? = null

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doExport(uri)
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }
    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && isAdded) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    Engine.startVpn(requireContext())
                }
            } else if (isAdded) {
                Toast.makeText(requireContext(), R.string.toast_vpn_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(28))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        this.container = content
        buildSettings()
        return scroll
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshRootStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshRootStatus()
    }

    // ------------------------------------------------------------------ build

    private fun buildSettings() {
        val content = container ?: return
        content.removeAllViews()
        val ctx = requireContext()

        // ---- Firewall ----
        content.addView(sectionTitle(getString(R.string.s_firewall)))

        val protectionSwitch = makeSwitch(ctx)
        protectionSwitch.isChecked = Prefs.protectionOn(ctx)
        protectionSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setProtection(ctx, checked)
            NotifHelper.update(ctx)
            if (checked) RootShell.invalidateRoot()
            applyInBackground()
        }

        val modeSegment = segmentedRow(
            ctx,
            getString(R.string.s_mode),
            getString(R.string.s_mode_sub),
            listOf(getString(R.string.s_mode_blacklist), getString(R.string.s_mode_whitelist)),
            if (Prefs.whitelistMode(ctx)) 1 else 0
        ) { index ->
            Prefs.setWhitelistMode(ctx, index == 1)
            applyInBackground()
        }

        val methodSegment = segmentedRow(
            ctx,
            getString(R.string.s_method),
            getString(R.string.s_method_sub),
            listOf(getString(R.string.method_auto), getString(R.string.method_root), getString(R.string.method_vpn)),
            Prefs.blockingMethod(ctx)
        ) { index ->
            Prefs.setBlockingMethod(ctx, index)
            if (Prefs.protectionOn(ctx)) {
                switchMethodAndRestart()
            }
        }

        content.addView(
            card(
                row(ctx, title = getString(R.string.s_protection), subtitle = getString(R.string.s_protection_sub), switchView = protectionSwitch),
                methodSegment.first,
                modeSegment.first,
                row(ctx, title = getString(R.string.s_boot), subtitle = getString(R.string.s_boot_sub),
                    switchView = bindSwitch(ctx, Prefs.applyOnBoot(ctx)) { Prefs.setApplyOnBoot(ctx, it) }),
                row(ctx, title = getString(R.string.s_notification), subtitle = getString(R.string.s_notification_sub),
                    switchView = bindSwitch(ctx, Prefs.showNotification(ctx)) {
                        Prefs.setShowNotification(ctx, it)
                        NotifHelper.update(ctx)
                    }),
                row(ctx, title = getString(R.string.s_ipv6), subtitle = getString(R.string.s_ipv6_sub),
                    switchView = bindSwitch(ctx, Prefs.blockIpv6(ctx)) {
                        Prefs.setBlockIpv6(ctx, it)
                        applyInBackground()
                    }),
                row(ctx, title = getString(R.string.s_sys_whitelist), subtitle = getString(R.string.s_sys_whitelist_sub),
                    switchView = bindSwitch(ctx, Prefs.allowSystemWhitelist(ctx)) {
                        Prefs.setAllowSystemWhitelist(ctx, it)
                        applyInBackground()
                    })
            )
        )

        // ---- Appearance ----
        content.addView(sectionTitle(getString(R.string.s_appearance)))
        content.addView(
            card(
                segmentedRow(
                    ctx,
                    getString(R.string.s_theme),
                    null,
                    listOf(getString(R.string.theme_auto), getString(R.string.theme_light), getString(R.string.theme_dark)),
                    Prefs.theme(ctx)
                ) { index ->
                    Prefs.setTheme(ctx, index)
                    AppCompatDelegate.setDefaultNightMode(
                        when (index) {
                            1 -> AppCompatDelegate.MODE_NIGHT_NO
                            2 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                }.first
            )
        )

        // ---- Tools ----
        content.addView(sectionTitle(getString(R.string.s_tools)))
        content.addView(
            card(
                rowAction(ctx, getString(R.string.s_apply_now), getString(R.string.s_apply_now_sub), R.drawable.ic_bolt) { applyRulesNow() },
                rowAction(ctx, getString(R.string.s_view_rules), getString(R.string.s_view_rules_sub), R.drawable.ic_terminal) { showRulesDialog() },
                rowAction(ctx, getString(R.string.s_export), getString(R.string.s_export_sub), R.drawable.ic_export) {
                    exportLauncher.launch("netblocker-rules.json")
                },
                rowAction(ctx, getString(R.string.s_import), getString(R.string.s_import_sub), R.drawable.ic_import) {
                    importLauncher.launch(arrayOf("*/*"))
                },
                rowAction(ctx, getString(R.string.s_reset), getString(R.string.s_reset_sub), R.drawable.ic_trash, danger = true) { confirmReset() }
            )
        )

        // ---- About ----
        content.addView(sectionTitle(getString(R.string.s_about)))
        val version = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        val rootValue = TextView(ctx)
        rootStatusValue = rootValue
        content.addView(
            card(
                infoActionRow(ctx, getString(R.string.s_root_status), rootValue),
                rowAction(ctx, getString(R.string.s_github), null, R.drawable.ic_link) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Shishir-ip/NetBlocker-Pro")))
                    } catch (ignored: Exception) {
                    }
                }
            )
        )
        rootValue.text = getString(R.string.checking)

        content.addView(TextView(ctx).apply {
            text = getString(R.string.disclaimer)
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary))
            setPadding(dp(24), dp(12), dp(24), 0)
        })

        refreshRootStatus()
    }

    // ------------------------------------------------------------------ rows

    private fun sectionTitle(text: String): TextView = TextView(requireContext()).apply {
        this.text = text.uppercase()
        textSize = 13f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_secondary))
        setPadding(dp(32), dp(18), dp(32), dp(6))
    }

    private fun card(vararg rows: View): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), 0) }
        }
        rows.forEachIndexed { index, row ->
            card.addView(row)
            if (index < rows.size - 1) {
                card.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(16)
                    }
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator))
                })
            }
        }
        return card
    }

    private fun row(
        ctx: android.content.Context,
        title: String,
        subtitle: String?,
        iconRes: Int? = null,
        danger: Boolean = false,
        switchView: MaterialSwitch? = null,
        valueView: TextView? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        if (iconRes != null) {
            row.addView(ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setImageResource(iconRes)
                setColorFilter(ContextCompat.getColor(ctx, if (danger) R.color.ios_red else R.color.ios_blue))
            })
        }
        val textBlock = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(if (iconRes != null) 12 else 0)
                marginEnd = dp(8)
            }
        }
        textBlock.addView(TextView(ctx).apply {
            text = title
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, if (danger) R.color.ios_red else R.color.ios_label))
        })
        if (subtitle != null) {
            textBlock.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary))
            })
        }
        row.addView(textBlock)
        switchView?.let { row.addView(it) }
        valueView?.let { row.addView(it) }
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            val tv = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            row.setBackgroundResource(tv.resourceId)
            row.setOnClickListener { onClick() }
        }
        return row
    }

    private fun rowAction(ctx: android.content.Context, title: String, subtitle: String?, iconRes: Int, danger: Boolean = false, onClick: () -> Unit): View =
        row(ctx, title, subtitle, iconRes = iconRes, danger = danger, onClick = onClick)

    private fun infoActionRow(ctx: android.content.Context, title: String, valueView: TextView): View =
        row(ctx, title, null, valueView = valueView)

    /** Returns (row, restyler) so the mode segment can restyle after imports. */
    private fun segmentedRow(
        ctx: android.content.Context,
        title: String,
        subtitle: String?,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): Pair<View, ((Int) -> Unit)?> {
        val block = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        block.addView(row(ctx, title, subtitle, switchView = null))
        var current = selected
        val segment = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_segment)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(14)) }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val items = ArrayList<TextView>()
        fun restyle() {
            items.forEachIndexed { i, tv ->
                if (i == current) {
                    tv.setBackgroundResource(R.drawable.bg_segment_selected)
                    tv.setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
                } else {
                    tv.background = null
                    tv.setTextColor(ContextCompat.getColor(ctx, R.color.ios_secondary))
                }
            }
        }
        for (i in options.indices) {
            val tv = TextView(ctx).apply {
                text = options[i]
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    current = i
                    restyle()
                    onSelect(i)
                }
            }
            items.add(tv)
            segment.addView(tv)
        }
        restyle()
        block.addView(segment)
        val restyler: (Int) -> Unit = { newSelected ->
            current = newSelected
            restyle()
        }
        return block to restyler
    }

    // ------------------------------------------------------------------ helpers

    private fun makeSwitch(ctx: android.content.Context): MaterialSwitch = MaterialSwitch(ctx).apply {
        thumbTintList = ContextCompat.getColorStateList(ctx, R.color.switch_thumb)
        trackTintList = ContextCompat.getColorStateList(ctx, R.color.switch_track)
    }

    private fun bindSwitch(ctx: android.content.Context, initial: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val sw = makeSwitch(ctx)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        return sw
    }

    /** Switch between the root and VPN engines while protection stays on. */
    private fun switchMethodAndRestart() {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            Engine.stop(appCtx)
            val result = Engine.startBlocking(appCtx)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (result == Engine.StartResult.VPN_CONSENT_NEEDED) {
                    val consent = VpnService.prepare(appCtx)
                    if (consent != null) vpnConsent.launch(consent)
                } else if (result == Engine.StartResult.FAILED) {
                    toast(R.string.toast_failed)
                }
            }
        }
    }

    private fun applyInBackground() {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = Engine.applyNow(appCtx)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                toast(
                    when {
                        ok && Prefs.protectionOn(appCtx) -> R.string.toast_applied
                        ok -> R.string.toast_cleared
                        else -> R.string.toast_failed
                    }
                )
            }
        }
    }

    private fun applyRulesNow() {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = Engine.applyNow(appCtx)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                toast(
                    when {
                        ok && Prefs.protectionOn(appCtx) -> R.string.toast_applied
                        ok -> R.string.toast_cleared
                        else -> R.string.toast_failed
                    }
                )
            }
        }
    }

    private fun showRulesDialog() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val root = RootShell.isRootAvailable()
            val dump = if (root) RuleApplier.dumpRules() else ""
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (!root) {
                    toast(R.string.toast_no_root)
                    return@withContext
                }
                val ctx = requireContext()
                val tv = TextView(ctx).apply {
                    text = dump
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                    setTextColor(ContextCompat.getColor(ctx, R.color.ios_label))
                }
                val scroll = ScrollView(ctx).apply { addView(tv) }
                AlertDialog.Builder(ctx)
                    .setTitle(getString(R.string.s_view_rules))
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun confirmReset() {
        if (!isAdded) return
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.reset_confirm_title))
            .setMessage(getString(R.string.reset_confirm_msg))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.reset)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    Prefs.clearRules(ctx)
                    RuleApplier.clearRules(ctx)
                    val ok = if (Prefs.protectionOn(ctx)) RuleApplier.applyRules(ctx) else true
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        toast(if (ok) R.string.toast_rules_reset else R.string.toast_failed)
                        buildSettings()
                    }
                }
            }
            .show()
    }

    private fun doExport(uri: Uri) {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                appCtx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Backup.exportJson(appCtx).toByteArray())
                    out.flush()
                }
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                if (isAdded) toast(if (ok) R.string.toast_exported else R.string.toast_import_failed)
            }
        }
    }

    private fun doImport(uri: Uri) {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val count = Backup.importJson(appCtx, uri)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (count < 0) {
                    toast(R.string.toast_import_failed)
                } else {
                    toast(getString(R.string.toast_imported, count))
                    buildSettings()
                    applyInBackground()
                }
            }
        }
    }

    private fun refreshRootStatus() {
        val valueView = rootStatusValue ?: return
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val root = RootShell.isRootAvailable()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                valueView.text = getString(if (root) R.string.root_granted else R.string.root_denied)
                valueView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (root) R.color.ios_green else R.color.ios_red
                    )
                )
            }
        }
    }

    private fun toast(res: Int) {
        if (!isAdded) return
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }

    private fun toast(text: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
