package app.myradio

import android.Manifest
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var searchField: EditText
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowSubtitle: TextView
    private lateinit var playPause: ImageButton
    private lateinit var buffering: ProgressBar
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var shuffleButton: Button

    /** Список, который сейчас на экране. */
    private var shown: List<Station> = emptyList()
    private lateinit var filterRow: View
    private lateinit var countryButton: Button
    private lateinit var genreButton: Button

    private lateinit var filterPrefs: SharedPreferences
    /** Код страны (например "RU") или null — все страны. */
    private var countryCode: String? = null
    /** Жанр в каталоге (например "rock") или null — все жанры. */
    private var genreTag: String? = null

    /** Название для пользователя → тег в каталоге Radio Browser. */
    private val genres = listOf(
        "Поп" to "pop",
        "Рок" to "rock",
        "Классический рок" to "classic rock",
        "Метал" to "metal",
        "Альтернатива" to "alternative",
        "Джаз" to "jazz",
        "Блюз" to "blues",
        "Классическая музыка" to "classical",
        "Электроника" to "electronic",
        "Танцевальная" to "dance",
        "Хип-хоп" to "hip hop",
        "Ретро" to "oldies",
        "Хиты 80-х" to "80s",
        "Хиты 90-х" to "90s",
        "Шансон" to "chanson",
        "Фолк" to "folk",
        "Кантри" to "country",
        "Регги" to "reggae",
        "Латино" to "latin",
        "Чилаут" to "chillout",
        "Новости" to "news",
        "Разговорное" to "talk",
        "Спорт" to "sports",
        "Детское" to "children",
    )

    private lateinit var favorites: FavoritesStore
    private lateinit var adapter: StationAdapter

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var catalog: List<Station> = emptyList()
    private var catalogFailed = false
    private var searchJob: Job? = null
    private var showingFavorites = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlayerBar()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root: View = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        searchField = findViewById(R.id.search)
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.empty)
        nowTitle = findViewById(R.id.nowTitle)
        nowSubtitle = findViewById(R.id.nowSubtitle)
        playPause = findViewById(R.id.playPause)
        buffering = findViewById(R.id.buffering)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        prevButton.setOnClickListener { skip(forward = false) }
        nextButton.setOnClickListener { skip(forward = true) }
        shuffleButton = findViewById(R.id.shuffleButton)
        shuffleButton.setOnClickListener { playFavoritesShuffled() }
        filterRow = findViewById(R.id.filterRow)
        countryButton = findViewById(R.id.countryButton)
        genreButton = findViewById(R.id.genreButton)

        // Фильтры запоминаются; при первом запуске — страна из настроек телефона.
        filterPrefs = getSharedPreferences("filters", MODE_PRIVATE)
        countryCode = if (filterPrefs.contains("country")) {
            filterPrefs.getString("country", "").orEmpty().ifEmpty { null }
        } else {
            Locale.getDefault().country.takeIf { it.length == 2 }
        }
        genreTag = filterPrefs.getString("genre", "").orEmpty().ifEmpty { null }
        updateFilterButtons()
        countryButton.setOnClickListener { showCountryDialog() }
        genreButton.setOnClickListener { showGenreDialog() }

        favorites = FavoritesStore(this)
        adapter = StationAdapter(
            onPlay = { play(it) },
            onToggleFavorite = {
                favorites.toggle(it)
                refreshList()
            },
            isFavorite = { favorites.contains(it) },
        )
        val list: RecyclerView = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.addTab(tabs.newTab().setText(R.string.tab_catalog))
        tabs.addTab(tabs.newTab().setText(R.string.tab_favorites))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingFavorites = tab.position == 1
                filterRow.visibility = if (showingFavorites) View.GONE else View.VISIBLE
                refreshList()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        searchField.doAfterTextChanged {
            if (showingFavorites) refreshList() else loadCatalog(delayMs = 500)
        }
        findViewById<ImageButton>(R.id.addButton).setOnClickListener { showAddStationDialog() }
        playPause.setOnClickListener { togglePlayback() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        updatePlayerBar()
        loadCatalog(delayMs = 0)
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                c.addListener(playerListener)
                controller = c
                updatePlayerBar()
            } catch (e: Exception) {
                // Сервис недоступен или экран уже закрыт — подключимся при следующем открытии.
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    // ---------- Каталог ----------

    private fun loadCatalog(delayMs: Long) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val query = searchField.text.toString().trim()
            progress.visibility = View.VISIBLE
            try {
                catalog = RadioBrowserApi.search(query, countryCode, genreTag)
                catalogFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                catalog = emptyList()
                catalogFailed = true
            }
            progress.visibility = View.GONE
            refreshList()
        }
    }

    private fun refreshList() {
        val query = searchField.text.toString().trim()
        val items = if (showingFavorites) {
            favorites.all().filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
        } else {
            catalog
        }
        shown = items
        shuffleButton.visibility =
            if (showingFavorites && items.size > 1) View.VISIBLE else View.GONE
        adapter.submit(items, controller?.currentMediaItem?.mediaId)

        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(
            when {
                showingFavorites -> R.string.empty_favorites
                catalogFailed -> R.string.error_network
                else -> R.string.empty_catalog
            }
        )
    }

    // ---------- Фильтры ----------

    private fun countryName(code: String): String =
        Locale("", code).displayCountry.ifBlank { code }

    private fun updateFilterButtons() {
        val country = countryCode?.let { countryName(it) } ?: getString(R.string.all_countries)
        val genre = genres.firstOrNull { it.second == genreTag }?.first
            ?: genreTag
            ?: getString(R.string.all_genres)
        countryButton.text = getString(R.string.filter_country, country)
        genreButton.text = getString(R.string.filter_genre, genre)
    }

    private fun applyFilters() {
        filterPrefs.edit()
            .putString("country", countryCode.orEmpty())
            .putString("genre", genreTag.orEmpty())
            .apply()
        updateFilterButtons()
        loadCatalog(delayMs = 0)
    }

    private fun showCountryDialog() {
        val collator = Collator.getInstance()
        val countries = Locale.getISOCountries()
            .map { it to countryName(it) }
            .sortedWith { a, b -> collator.compare(a.second, b.second) }
        val codes: List<String?> = listOf<String?>(null) + countries.map { it.first }
        val names = (listOf(getString(R.string.all_countries)) + countries.map { it.second })
            .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.all_countries)
            .setSingleChoiceItems(names, codes.indexOf(countryCode).coerceAtLeast(0)) { dialog, which ->
                countryCode = codes[which]
                dialog.dismiss()
                applyFilters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGenreDialog() {
        val tags: List<String?> = listOf<String?>(null) + genres.map { it.second }
        val names = (listOf(getString(R.string.all_genres)) + genres.map { it.first })
            .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.all_genres)
            .setSingleChoiceItems(names, tags.indexOf(genreTag).coerceAtLeast(0)) { dialog, which ->
                genreTag = tags[which]
                dialog.dismiss()
                applyFilters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Воспроизведение ----------

    private fun toMediaItem(station: Station): MediaItem {
        val uri = Uri.parse(station.url)
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.country)
                    .build()
            )
            .build()
    }

    /** Запускает станцию; весь показанный список становится очередью для кнопок «назад/вперёд». */
    private fun play(station: Station) {
        val c = controller
        if (c == null) {
            Toast.makeText(this, R.string.error_player_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val queue = if (shown.any { it.id == station.id }) shown else listOf(station)
        val index = queue.indexOfFirst { it.id == station.id }
        c.shuffleModeEnabled = false
        c.setMediaItems(queue.map { toMediaItem(it) }, index, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    /** Избранное в случайном порядке: случайная первая станция, дальше «вперёд/назад» — тоже вразнобой. */
    private fun playFavoritesShuffled() {
        val c = controller
        if (c == null) {
            Toast.makeText(this, R.string.error_player_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val list = shown
        if (list.isEmpty()) return
        c.setMediaItems(list.map { toMediaItem(it) }, list.indices.random(), C.TIME_UNSET)
        c.shuffleModeEnabled = true
        c.prepare()
        c.play()
    }

    private fun skip(forward: Boolean) {
        val c = controller ?: return
        if (c.mediaItemCount < 2) return
        if (forward) c.seekToNextMediaItem() else c.seekToPreviousMediaItem()
        c.prepare()
        c.play()
    }

    private fun isActive(c: Player): Boolean =
        c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)

    private fun togglePlayback() {
        val c = controller ?: return
        if (c.currentMediaItem == null) return
        if (isActive(c)) {
            c.pause()
        } else {
            // Прямой эфир: после паузы возвращаемся к текущему моменту, а не к старому буферу.
            c.seekToDefaultPosition()
            c.prepare()
            c.play()
        }
    }

    private fun updatePlayerBar() {
        val c = controller
        val item = c?.currentMediaItem
        if (c == null || item == null) {
            nowTitle.setText(R.string.now_nothing)
            nowSubtitle.text = ""
            nowSubtitle.visibility = View.GONE
            buffering.visibility = View.GONE
            playPause.isEnabled = false
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            playPause.setImageResource(R.drawable.ic_play)
            adapter.setCurrent(null)
            return
        }

        val stationName = item.mediaMetadata.title?.toString().orEmpty()
        val song = c.mediaMetadata.title?.toString().orEmpty()
        val isBuffering = c.playWhenReady && c.playbackState == Player.STATE_BUFFERING

        nowTitle.text = if (c.shuffleModeEnabled && c.mediaItemCount > 1) {
            "\uD83D\uDD00 " + stationName
        } else {
            stationName
        }
        val subtitle = when {
            c.playerError != null -> getString(R.string.error_playback)
            isBuffering -> getString(R.string.connecting)
            song.isNotBlank() && song != stationName -> song
            else -> item.mediaMetadata.artist?.toString().orEmpty()
        }
        nowSubtitle.text = subtitle
        nowSubtitle.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE

        buffering.visibility = if (isBuffering) View.VISIBLE else View.GONE
        playPause.isEnabled = true
        prevButton.isEnabled = c.mediaItemCount > 1
        nextButton.isEnabled = c.mediaItemCount > 1
        playPause.setImageResource(if (isActive(c)) R.drawable.ic_pause else R.drawable.ic_play)
        adapter.setCurrent(item.mediaId)
    }

    // ---------- Своя станция ----------

    private fun showAddStationDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val nameField = EditText(this).apply {
            setHint(R.string.add_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val urlField = EditText(this).apply {
            setHint(R.string.add_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(nameField)
            addView(urlField)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_save) { _, _ ->
                val url = urlField.text.toString().trim()
                val name = nameField.text.toString().trim().ifEmpty { url }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, R.string.add_bad_url, Toast.LENGTH_LONG).show()
                } else {
                    favorites.add(Station(id = "custom:$url", name = name, url = url))
                    refreshList()
                    Toast.makeText(this, R.string.add_done, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
