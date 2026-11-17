package app.myradio

import org.json.JSONObject

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val country: String = "",
    val tags: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("country", country)
        .put("tags", tags)
        .put("codec", codec)
        .put("bitrate", bitrate)

    companion object {
        fun fromJson(o: JSONObject): Station = Station(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            country = o.optString("country"),
            tags = o.optString("tags"),
            codec = o.optString("codec"),
            bitrate = o.optInt("bitrate"),
        )
    }
}
