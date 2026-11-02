package com.netblocker.pro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.materialswitch.MaterialSwitch
import com.netblocker.pro.R
import com.netblocker.pro.data.AppRepository
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.RuleApplier
import com.netblocker.pro.firewall.RootShell
import com.netblocker.pro.model.AppEntry
import com.netblocker.pro.util.NotifHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment(), AppDetailSheet.Host {

    private var allApps: List<AppEntry> = emptyList()
    private var query = ""
    private var filter = 0 // 0 all, 1 user, 2 system, 3 blocked
    private var adapter: AppAdapter? = null
    private var applyJob: Job? = null

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var emptyView: LinearLayout
    private lateinit var switchProtection: MaterialSwitch
    private lateinit var frameStatusIcon: FrameLayout
    private lateinit var ivShield: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var segments: List<TextView>

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { NotifHelper.update(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipe = view.findViewById(R.id.swipe)
        emptyView = view.findViewById(R.id.emptyView)
        switchProtection = view.findViewById(R.id.switchProtection)
        frameStatusIcon = view.findViewById(R.id.frameStatusIcon)
        ivShield = view.findViewById(R.id.ivShield)
        tvStatusTitle = view.findViewById(R.id.tvStatusTitle)
        tvStatusSub = view.findViewById(R.id.tvStatusSub)
        segments = listOf(
            view.findViewById(R.id.segAll),
            view.findViewById(R.id.segUser),
            view.findViewById(R.id.segSystem),
            view.findViewById(R.id.segBlocked)
        )

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        adapter = AppAdapter(
            onToggle = { app, blocked -> toggleBlockAll(app, blocked) },
            onDetail = { showDetail(it) },
            onDismissHint = {
                Prefs.setHintDismissed(requireContext())
                applyFilter()
            }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        swipe.setOnRefreshListener {
            loadApps()
            refreshStatus()
        }

        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })

        for (i in segments.indices) {
            segments[i].setOnClickListener { setFilter(i) }
        }
        styleSegments()

        switchProtection.isChecked = Prefs.protectionOn(requireContext())
        switchProtection.setOnCheckedChangeListener { _, checked ->
            onProtectionChanged(checked)
        }

        loadApps()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        switchProtection.setOnCheckedChangeListener(null)
        switchProtection.isChecked = Prefs.protectionOn(requireContext())
        switchProtection.setOnCheckedChangeListener { _, checked -> onProtectionChanged(checked) }
        loadApps()
        refreshStatus()
    }

    // ----- Data -----

    private fun loadApps() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val list = AppRepository.loadApps(requireContext())
            if (!isAdded) return@launch
            allApps = list
            applyFilter()
        }
    }

    private fun applyFilter() {
        if (!isAdded) return
        val ctx = requireContext()
        val q = query.trim().lowercase()
        val whitelist = Prefs.whitelistMode(ctx)
        val filtered = allApps.filter { app ->
            val matchQuery = q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            val matchFilter = when (filter) {
                FILTER_USER -> !app.isSystem
                FILTER_SYSTEM -> app.isSystem
                FILTER_BLOCKED -> app.policy != AppEntry.POLICY_ALLOW
                else -> true
            }
            matchQuery && matchFilter
        }
        val hint = !Prefs.hintDismissed(ctx) && filter == FILTER_ALL && q.isEmpty()
        adapter?.submit(filtered, hint, whitelist)
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setFilter(index: Int) {
        filter = index
        styleSegments()
        applyFilter()
    }

    private fun styleSegments() {
        for (i in segments.indices) {
            val tv = segments[i]
            if (i == filter) {
                tv.setBackgroundResource(R.drawable.bg_segment_selected)
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label))
            } else {
                tv.background = null
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_secondary))
            }
        }
    }

    // ----- Actions -----

    private fun toggleBlockAll(app: AppEntry, blocked: Boolean) {
        val newPolicy = if (blocked) AppEntry.POLICY_BLOCK_ALL else AppEntry.POLICY_ALLOW
        Prefs.setRule(requireContext(), app.uid, newPolicy)
        allApps = allApps.map { if (it.uid == app.uid) it.copy(policy = newPolicy) else it }
        applyFilter()
        requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scheduleApply()
    }

    private fun onProtectionChanged(on: Boolean) {
        val ctx = requireContext()
        Prefs.setProtection(ctx, on)
        if (on && Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        NotifHelper.update(ctx)
        if (on) RootShell.invalidateRoot()
        refreshStatus()
        applyFilter()
    }

    private fun showDetail(app: AppEntry) {
        val sheet = AppDetailSheet.newInstance(app.uid)
        sheet.show(childFragmentManager, "detail")
    }

    override fun entryFor(uid: Int): AppEntry? = allApps.firstOrNull { it.uid == uid }

    override fun onPolicyChanged(uid: Int, policy: Int) {
        if (!isAdded) return
        Prefs.setRule(requireContext(), uid, policy)
        allApps = allApps.map { if (it.uid == uid) it.copy(policy = policy) else it }
        applyFilter()
        scheduleApply()
    }

    // ----- Firewall -----

    private fun scheduleApply() {
        if (!isAdded) return
        applyJob?.cancel()
        val appCtx = requireContext().applicationContext
        applyJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (Prefs.protectionOn(appCtx) && RootShell.isRootAvailable()) {
                RuleApplier.applyRules(appCtx)
            }
            withContext(Dispatchers.Main) {
                if (isAdded) refreshStatus()
            }
        }
    }

    private fun refreshStatus() {
        if (!isAdded) return
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val root = RootShell.isRootAvailable()
            var status = if (root) RuleApplier.status(appCtx) else RuleApplier.Status.NO_ROOT
            if (root && Prefs.protectionOn(appCtx) && status == RuleApplier.Status.INACTIVE) {
                RuleApplier.applyRules(appCtx)
                status = RuleApplier.status(appCtx)
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                updateStatusCard(root, status)
                swipe.isRefreshing = false
            }
        }
    }

    private fun updateStatusCard(root: Boolean, status: RuleApplier.Status) {
        val ctx = requireContext()
        val on = Prefs.protectionOn(ctx)
        val (colorRes, title, sub) = when {
            !on -> Triple(R.color.ios_tertiary, "Protection is off", "Turn on the switch to enable the firewall")
            !root -> Triple(R.color.ios_red, "Protection is on", "Root access is required to enforce rules")
            status == RuleApplier.Status.ACTIVE -> {
                val count = allApps.count { it.policy != AppEntry.POLICY_ALLOW }
                val detail = if (Prefs.whitelistMode(ctx)) {
                    "$count apps whitelisted"
                } else {
                    "$count apps restricted"
                }
                Triple(R.color.ios_green, "Protection is on", "Firewall active · $detail")
            }
            else -> Triple(R.color.ios_orange, "Protection is on", "Applying firewall rules…")
        }

        frameStatusIcon.background = GradientDrawable().apply {
            cornerRadius = dp(12f)
            setColor(ContextCompat.getColor(ctx, colorRes))
        }
        ivShield.setColorFilter(Color.WHITE)
        tvStatusTitle.text = title
        tvStatusSub.text = sub
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val FILTER_ALL = 0
        const val FILTER_USER = 1
        const val FILTER_SYSTEM = 2
        const val FILTER_BLOCKED = 3
    }
}
