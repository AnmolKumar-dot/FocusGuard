package com.focusguard.app

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Channel search.
 *
 * 1. Works with no key: asks the same YouTube web address the YouTube website itself uses
 *    (this is how apps like NewPipe do it). It is unofficial, so YouTube can change it.
 * 2. If that fails and the user has saved a free YouTube Data API key, falls back to the
 *    official API.
 */
object YouTubeApi {

    sealed class Result {
        data class Ok(val channels: List<Channel>) : Result()
        data class Error(val message: String) : Result()
    }

    // If the no-key search ever stops working, these are the first things to try updating.
    private const val WEB_CLIENT_VERSION = "2.20251001.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
    private const val CHANNEL_FILTER = "EgIQAg=="

    fun searchChannels(query: String, key: String): Result {
        val web = searchWeb(query)
        if (web is Result.Ok && web.channels.isNotEmpty()) return web
        if (key.isBlank()) return web
        return searchApi(query, key)
    }

    // ---------------------------------------------------------------- no-key web search

    private fun searchWeb(query: String): Result {
        var conn: HttpURLConnection? = null
        try {
            val body = JSONObject()
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().put("clientName", "WEB")
                            .put("clientVersion", WEB_CLIENT_VERSION)
                            .put("hl", "en")
                    )
                )
                .put("query", query)
                .put("params", CHANNEL_FILTER)
                .toString()

            conn = URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("X-YouTube-Client-Name", "1")
            conn.setRequestProperty("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.Error("YouTube search is not available right now (error $code).")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (!text.contains("itemSectionRenderer")) {
                return Result.Error("YouTube changed how its search works, so channel search is down for now.")
            }
            val found = ArrayList<Channel>()
            collect(JSONObject(text), found)
            return Result.Ok(found.distinctBy { it.id }.take(15))
        } catch (e: IOException) {
            return Result.Error("Could not reach YouTube. Check your internet connection.")
        } catch (e: Exception) {
            return Result.Error("Something went wrong with the search: " + (e.message ?: "unknown error"))
        } finally {
            conn?.disconnect()
        }
    }

    /** Walks the whole response and picks out every channel result, whatever the nesting. */
    private fun collect(node: Any?, out: MutableList<Channel>) {
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("channelRenderer")
                if (renderer != null) parseChannelRenderer(renderer)?.let { out.add(it) }
                val lockup = node.optJSONObject("lockupViewModel")
                if (lockup != null) parseLockup(lockup)?.let { out.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), out)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collect(node.opt(i), out)
            }
        }
    }

    private fun parseChannelRenderer(o: JSONObject): Channel? {
        val id = o.optString("channelId")
        val titleObj = o.optJSONObject("title")
        var title = titleObj?.optString("simpleText").orEmpty()
        if (title.isEmpty()) {
            title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
        }
        title = title.trim()
        if (id.isEmpty() || title.isEmpty()) return null
        val thumbs = o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        var thumb = ""
        if (thumbs != null && thumbs.length() > 0) {
            thumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        }
        return Channel(id, title, fixUrl(thumb))
    }

    /** YouTube is moving search results to a newer layout; this reads that one too. */
    private fun parseLockup(o: JSONObject): Channel? {
        val id = o.optString("contentId")
        if (!id.startsWith("UC") || id.length != 24) return null
        val title = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            ?.optJSONObject("title")?.optString("content").orEmpty().trim()
        if (title.isEmpty()) return null
        val sources = o.optJSONObject("contentImage")?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")?.optJSONObject("avatarViewModel")?.optJSONObject("image")
            ?.optJSONArray("sources")
        var thumb = ""
        if (sources != null && sources.length() > 0) {
            thumb = sources.optJSONObject(sources.length() - 1)?.optString("url").orEmpty()
        }
        return Channel(id, title, fixUrl(thumb))
    }

    private fun fixUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

    // ---------------------------------------------------------------- official API (backup)

    private fun searchApi(query: String, key: String): Result {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=channel&maxResults=15" +
                    "&q=" + URLEncoder.encode(query, "UTF-8") +
                    "&key=" + URLEncoder.encode(key, "UTF-8")
            )
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return Result.Error(friendlyError(code, body))
            return Result.Ok(parseApi(body))
        } catch (e: IOException) {
            return Result.Error("Could not reach YouTube. Check your internet connection.")
        } catch (e: Exception) {
            return Result.Error("Something went wrong: " + (e.message ?: "unknown error"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseApi(body: String): List<Channel> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Channel>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val sn = item.optJSONObject("snippet") ?: continue
            var id = sn.optString("channelId")
            if (id.isEmpty()) id = item.optJSONObject("id")?.optString("channelId").orEmpty()
            // The official API returns HTML-escaped titles (&amp;, &#39; ...); decode them to
            // match exactly what YouTube shows on screen.
            val title = Html.fromHtml(sn.optString("title"), Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (id.isEmpty() || title.isEmpty()) continue
            val thumbs = sn.optJSONObject("thumbnails")
            var thumb = thumbs?.optJSONObject("medium")?.optString("url").orEmpty()
            if (thumb.isEmpty()) thumb = thumbs?.optJSONObject("default")?.optString("url").orEmpty()
            out.add(Channel(id, title, fixUrl(thumb)))
        }
        return out
    }

    private fun friendlyError(code: Int, body: String): String {
        var reason = ""
        var message = ""
        try {
            val err = JSONObject(body).optJSONObject("error")
            message = err?.optString("message").orEmpty()
            reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        } catch (e: Exception) {
            // keep the defaults
        }
        return when {
            reason == "keyInvalid" || message.contains("API key not valid", true) ->
                "The backup API key is not valid. Check it, or remove it."
            reason == "quotaExceeded" || reason == "dailyLimitExceeded" ->
                "Today's free search limit is used up. Try tomorrow, or add the channel by name."
            reason == "accessNotConfigured" || message.contains("has not been used", true) ||
                message.contains("is disabled", true) ->
                "Turn on YouTube Data API v3 for your key's project, then try again."
            code == 403 && (reason == "ipRefererBlocked" || message.contains("restrictions", true)) ->
                "This key has app restrictions. Create a key without app restrictions."
            else -> "YouTube said: " + (if (message.isEmpty()) "error $code" else message)
        }
    }
}
