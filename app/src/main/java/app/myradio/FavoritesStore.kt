package app.myradio

import android.content.Context
import org.json.JSONArray

/** Избранные станции; хранятся на телефоне в SharedPreferences. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val items = LinkedHashMap<String, Station>()

    init {
        try {
            val array = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until array.length()) {
                val s = Station.fromJson(array.getJSONObject(i))
                if (s.id.isNotBlank() && s.url.isNotBlank()) items[s.id] = s
            }
        } catch (e: org.json.JSONException) {
            items.clear()
        }
    }

    fun all(): List<Station> = items.values.toList()

    fun contains(station: Station): Boolean = items.containsKey(station.id)

    fun add(station: Station) {
        items[station.id] = station
        save()
    }

    fun toggle(station: Station) {
        if (items.remove(station.id) == null) items[station.id] = station
        save()
    }

    private fun save() {
        val array = JSONArray()
        items.values.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}
