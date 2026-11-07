package com.focusguard.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

/** Search and toggle which installed apps get blocked during focus. */
class AppPickerSheet : BaseSheet() {

    private class AppInfo(val pkg: String, val label: String, val icon: Drawable)

    private class Row(val pkg: String?, val label: String, val icon: Drawable?, val header: Boolean)

    private lateinit var prefs: Prefs
    private lateinit var selected: MutableSet<String>
    private var apps: List<AppInfo> = emptyList()
    private val adapter = Adapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        prefs = Prefs(ctx)
        selected = prefs.blockedPackages.toMutableSet()

        val rv = view.findViewById<RecyclerView>(R.id.rvApps)
        rv.layoutManager = LinearLayoutManager(ctx)
        rv.adapter = adapter
        view.findViewById<EditText>(R.id.etSearch).doAfterTextChanged { applyFilter(it?.toString() ?: "") }

        // Reading every app icon can take a moment, so do it off the main thread.
        val appContext = ctx.applicationContext
        val self = ctx.packageName
        val loading = view.findViewById<TextView>(R.id.tvLoading)
        val main = Handler(Looper.getMainLooper())
        Thread {
            val pm = appContext.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = pm.queryIntentActivities(launcher, 0)
                .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .filter { it.pkg != self && it.pkg != YOUTUBE }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            main.post {
                if (!isAdded || view.parent == null) return@post
                apps = found
                loading.visibility = View.GONE
                applyFilter(view.findViewById<EditText>(R.id.etSearch).text.toString())
            }
        }.start()
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val matches = apps.filter { q.isEmpty() || it.label.lowercase().contains(q) }
        val suggested = matches.filter { it.pkg in SUGGESTED }
        val others = matches.filter { it.pkg !in SUGGESTED }
        val rows = ArrayList<Row>()
        if (suggested.isNotEmpty()) {
            rows.add(Row(null, "Suggested distracting apps", null, true))
            suggested.forEach { rows.add(Row(it.pkg, it.label, it.icon, false)) }
        }
        if (others.isNotEmpty()) {
            rows.add(Row(null, "All apps", null, true))
            others.forEach { rows.add(Row(it.pkg, it.label, it.icon, false)) }
        }
        adapter.rows = rows
        adapter.notifyDataSetChanged()
    }

    private inner class AppHolder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val label: TextView = v.findViewById(R.id.appLabel)
        val sw: MaterialSwitch = v.findViewById(R.id.appSwitch)
    }

    private inner class HeaderHolder(v: View) : RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()

        override fun getItemViewType(position: Int): Int = if (rows[position].header) 0 else 1

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
            } else {
                AppHolder(inflater.inflate(R.layout.item_app, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (holder is HeaderHolder) {
                (holder.itemView as TextView).text = row.label
                return
            }
            holder as AppHolder
            val pkg = row.pkg ?: return
            holder.icon.setImageDrawable(row.icon)
            holder.label.text = row.label
            holder.sw.setOnCheckedChangeListener(null)
            holder.sw.isChecked = pkg in selected
            holder.sw.setOnCheckedChangeListener { _, checked ->
                if (prefs.isLocked()) {
                    requireContext().toast("Locked during a strict session")
                    notifyItemChanged(holder.bindingAdapterPosition)
                } else {
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                    prefs.blockedPackages = selected
                }
            }
            holder.itemView.setOnClickListener { holder.sw.toggle() }
        }
    }

    companion object {
        private const val YOUTUBE = "com.google.android.youtube"

        // Apps that tend to eat study time. They are listed first so they are quick to switch on.
        private val SUGGESTED = setOf(
            "com.instagram.android", "com.snapchat.android", "com.facebook.katana",
            "com.facebook.orca", "com.twitter.android", "com.zhiliaoapp.musically",
            "in.startv.hotstar", "com.netflix.mediaclient", "com.reddit.frontpage",
            "com.pinterest", "com.whatsapp", "org.telegram.messenger", "com.discord",
            "com.amazon.avod.thirdpartyclient", "com.mxtech.videoplayer.ad",
            "com.jio.media.ondemand", "com.google.android.apps.youtube.music"
        )
    }
}
