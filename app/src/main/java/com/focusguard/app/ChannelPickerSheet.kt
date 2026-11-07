package com.focusguard.app

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
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Search a YouTube channel by name, see its logo, and add it to the allowed list.
 * Matching during focus then uses the channel's exact title as YouTube shows it.
 */
class ChannelPickerSheet : BaseSheet() {

    private lateinit var prefs: Prefs
    private lateinit var tvStatus: TextView
    private lateinit var tvListTitle: TextView
    private lateinit var btnKey: TextView
    private lateinit var btnAddTyped: MaterialButton
    private lateinit var rv: RecyclerView

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val adapter = Adapter()
    private var token = 0
    private var query = ""
    private var results: List<Channel> = emptyList()
    private var searchMessage = ""
    private val searchRunnable = Runnable { runSearch() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        prefs = Prefs(ctx)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvListTitle = view.findViewById(R.id.tvListTitle)
        btnKey = view.findViewById(R.id.btnKey)
        btnAddTyped = view.findViewById(R.id.btnAddTyped)
        rv = view.findViewById(R.id.rvChannels)
        rv.layoutManager = LinearLayoutManager(ctx)
        rv.adapter = adapter

        view.findViewById<EditText>(R.id.etSearch).doAfterTextChanged { text ->
            query = text?.toString()?.trim().orEmpty()
            handler.removeCallbacks(searchRunnable)
            token++ // anything still in flight is now out of date
            results = emptyList()
            searchMessage = ""
            if (query.length >= 2) {
                if (prefs.apiKey.isNotBlank()) searchMessage = "Searching..."
                handler.postDelayed(searchRunnable, 500)
            }
            render()
        }
        btnKey.setOnClickListener {
            ctx.promptText("YouTube API key", prefs.apiKey, "Paste your key here") {
                prefs.apiKey = it
                render()
            }
        }
        btnAddTyped.setOnClickListener {
            if (query.length >= 2) addChannel(Channel("typed:" + query.lowercase(), query, ""))
        }
        render()
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroyView()
    }

    private fun runSearch() {
        val key = prefs.apiKey
        if (query.length < 2) return
        if (key.isBlank()) {
            render()
            return
        }
        val q = query
        val myToken = ++token
        searchMessage = "Searching..."
        render()
        try {
            executor.execute {
                val result = YouTubeApi.searchChannels(q, key)
                handler.post {
                    if (!isAdded || myToken != token) return@post
                    when (result) {
                        is YouTubeApi.Result.Ok -> {
                            results = result.channels
                            searchMessage = if (result.channels.isEmpty()) "No channels found for that name." else ""
                        }
                        is YouTubeApi.Result.Error -> {
                            results = emptyList()
                            searchMessage = result.message
                        }
                    }
                    render()
                }
            }
        } catch (e: Exception) {
            // the sheet was closed while the search was starting
        }
    }

    private fun addChannel(c: Channel) {
        if (prefs.isLocked()) {
            requireContext().toast("Locked during a strict session")
            return
        }
        val list = prefs.channels().toMutableList()
        if (list.none { it.id == c.id || it.title.equals(c.title, ignoreCase = true) }) {
            list.add(c)
            prefs.saveChannels(list)
        }
        render()
    }

    private fun removeChannel(c: Channel) {
        if (prefs.isLocked()) {
            requireContext().toast("Locked during a strict session")
            return
        }
        prefs.saveChannels(prefs.channels().filter { it.id != c.id })
        render()
    }

    private fun render() {
        if (!isAdded || view == null) return
        val saved = prefs.channels()
        val hasKey = prefs.apiKey.isNotBlank()
        btnKey.text = if (hasKey) "API key" else "Add API key"

        val searching = query.length >= 2
        val items = if (searching) results else saved
        tvListTitle.text = when {
            searching && results.isNotEmpty() -> "Search results"
            searching -> ""
            saved.isEmpty() -> ""
            else -> "Your study channels (${saved.size})"
        }
        tvListTitle.visibility = if (tvListTitle.text.isEmpty()) View.GONE else View.VISIBLE

        val message = when {
            searching && !hasKey ->
                "To search with logos, add a free YouTube API key (button above, see the README). " +
                    "You can still add the channel by its exact name below."
            searching -> searchMessage
            saved.isEmpty() -> "No channels yet. Search for one above."
            else -> ""
        }
        tvStatus.text = message
        tvStatus.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE

        btnAddTyped.visibility = if (searching) View.VISIBLE else View.GONE
        btnAddTyped.text = "Add \"" + query + "\" by name"

        adapter.items = items
        adapter.savedIds = saved.map { it.id }.toSet()
        adapter.notifyDataSetChanged()
    }

    private inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.chLogo)
        val title: TextView = v.findViewById(R.id.chTitle)
        val action: TextView = v.findViewById(R.id.chAction)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        var items: List<Channel> = emptyList()
        var savedIds: Set<String> = emptySet()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = items[position]
            holder.title.text = c.title
            if (c.thumb.isNotEmpty()) {
                Glide.with(holder.logo).load(c.thumb).circleCrop()
                    .placeholder(R.drawable.ic_channel_placeholder).into(holder.logo)
            } else {
                Glide.with(holder.logo).clear(holder.logo)
                holder.logo.setImageResource(R.drawable.ic_channel_placeholder)
            }
            val isSaved = c.id in savedIds
            holder.action.text = if (isSaved) "Remove" else "Add"
            val toggle = View.OnClickListener { if (isSaved) removeChannel(c) else addChannel(c) }
            holder.action.setOnClickListener(toggle)
            holder.itemView.setOnClickListener(toggle)
        }
    }
}
