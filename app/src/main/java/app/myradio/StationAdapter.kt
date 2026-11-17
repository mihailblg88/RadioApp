package app.myradio

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val onPlay: (Station) -> Unit,
    private val onToggleFavorite: (Station) -> Unit,
    private val isFavorite: (Station) -> Boolean,
) : RecyclerView.Adapter<StationAdapter.Holder>() {

    private var items: List<Station> = emptyList()
    private var currentId: String? = null

    fun submit(newItems: List<Station>, playingId: String?) {
        items = newItems
        currentId = playingId
        notifyDataSetChanged()
    }

    fun setCurrent(playingId: String?) {
        if (playingId != currentId) {
            currentId = playingId
            notifyDataSetChanged()
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.stationName)
        val info: TextView = view.findViewById(R.id.stationInfo)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val station = items[position]
        holder.name.text = station.name
        holder.name.setTypeface(null, if (station.id == currentId) Typeface.BOLD else Typeface.NORMAL)

        val quality = buildString {
            append(station.codec)
            if (station.bitrate > 0) {
                if (isNotEmpty()) append(' ')
                append(station.bitrate).append(" kbps")
            }
        }
        val info = listOf(station.country, quality, station.tags)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        holder.info.text = info
        holder.info.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE

        holder.favorite.setImageResource(
            if (isFavorite(station)) R.drawable.ic_star else R.drawable.ic_star_border
        )
        holder.favorite.setOnClickListener { onToggleFavorite(station) }
        holder.itemView.setOnClickListener { onPlay(station) }
    }
}
