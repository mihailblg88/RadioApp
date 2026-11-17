package app.myradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Открытый каталог интернет-радио: https://www.radio-browser.info */
object RadioBrowserApi {

    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    /**
     * Самые популярные станции, подходящие под все заданные условия:
     * [query] — часть названия, [countryCode] — двухбуквенный код страны,
     * [tag] — жанр. Пустое или null условие не применяется.
     */
    suspend fun search(query: String, countryCode: String?, tag: String?): List<Station> =
        withContext(Dispatchers.IO) {
            val path = StringBuilder("/json/stations/search?limit=100&hidebroken=true&order=clickcount&reverse=true")
            if (query.isNotBlank()) {
                path.append("&name=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            }
            if (!countryCode.isNullOrBlank()) {
                path.append("&countrycode=").append(URLEncoder.encode(countryCode, "UTF-8"))
            }
            if (!tag.isNullOrBlank()) {
                path.append("&tag=").append(URLEncoder.encode(tag, "UTF-8"))
            }

            var lastError: Exception = IOException("Нет доступных серверов")
            for (server in servers) {
                try {
                    return@withContext parse(get(server + path))
                } catch (e: IOException) {
                    lastError = e
                } catch (e: org.json.JSONException) {
                    lastError = e
                }
            }
            throw lastError
        }

    private fun get(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "MyRadioAndroid/1.0")
            if (conn.responseCode != 200) throw IOException("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): List<Station> {
        val array = JSONArray(json)
        val result = ArrayList<Station>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (url.isBlank() || name.isBlank()) continue
            result.add(
                Station(
                    id = o.optString("stationuuid").ifBlank { url },
                    name = name,
                    url = url,
                    country = o.optString("country"),
                    tags = o.optString("tags").split(',').map { it.trim() }
                        .filter { it.isNotEmpty() }.take(3).joinToString(", "),
                    codec = o.optString("codec"),
                    bitrate = o.optInt("bitrate"),
                )
            )
        }
        return result
    }
}
