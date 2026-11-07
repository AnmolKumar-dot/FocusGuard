package com.focusguard.app

import android.text.Html
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Channel search through the official YouTube Data API v3, using the user's own free key.
 * The key goes only to Google; nothing else is sent anywhere.
 */
object YouTubeApi {

    sealed class Result {
        data class Ok(val channels: List<Channel>) : Result()
        data class Error(val message: String) : Result()
    }

    fun searchChannels(query: String, key: String): Result {
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
            return Result.Ok(parse(body))
        } catch (e: IOException) {
            return Result.Error("Could not reach YouTube. Check your internet connection.")
        } catch (e: Exception) {
            return Result.Error("Something went wrong: " + (e.message ?: "unknown error"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(body: String): List<Channel> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Channel>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val sn = item.optJSONObject("snippet") ?: continue
            var id = sn.optString("channelId")
            if (id.isEmpty()) id = item.optJSONObject("id")?.optString("channelId").orEmpty()
            // Search results come back HTML-escaped (&amp;, &#39; ...), so decode them to match
            // exactly what YouTube shows on screen.
            val title = Html.fromHtml(sn.optString("title"), Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (id.isEmpty() || title.isEmpty()) continue
            val thumbs = sn.optJSONObject("thumbnails")
            var thumb = thumbs?.optJSONObject("medium")?.optString("url").orEmpty()
            if (thumb.isEmpty()) thumb = thumbs?.optJSONObject("default")?.optString("url").orEmpty()
            if (thumb.startsWith("//")) thumb = "https:$thumb"
            out.add(Channel(id, title, thumb))
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
                "That API key is not valid. Check it and try again."
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
