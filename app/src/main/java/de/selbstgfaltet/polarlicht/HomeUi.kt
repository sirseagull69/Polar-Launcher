package de.selbstgfaltet.polarlicht

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Ein Eintrag der Startreihe: App oder Spiel. */
class HomeItem(
    @JvmField val key: String,
    @JvmField val label: String,
    @JvmField val sys: String,
    @JvmField val badge: String,
    @JvmField val fav: Boolean,
    @JvmField val last: Long,
    @JvmField val game: Boolean
)

/** Was der Startbildschirm von der Activity braucht. */
interface HomeHost {
    /** Bild für eine Kachel. Darf dauern, wird nie auf dem Haupt-Thread gerufen. */
    fun picture(key: String): Bitmap?
    fun stateJson(): String
    fun launch(key: String)
    fun open(screen: String)
    fun details(key: String, screen: String)
    fun selected(key: String)
    fun fav(key: String)
    fun android(what: String)
    fun brightness(percent: Int)
    fun volume(percent: Int)
    fun sound(name: String)
}

private val LocalFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private fun icon(path: String): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    return try {
        b.addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
    } catch (e: Exception) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes("M6 6h12v12H6z"), fill = SolidColor(Color.Black)).build()
    }
}

/* Symbole aus den Material Icons (Apache 2.0). */
private val IcWifi = icon("M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z")
private val IcBt = icon("M17.71 7.71L12 2h-1v7.59L6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 11 14.41V22h1l5.71-5.71-4.3-4.29 4.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.46L13 18.17v-3.76l1.88 1.88z")
private val IcPlane = icon("M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z")
private val IcApps = icon("M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z")
private val IcVol = icon("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z")
private val IcSun = icon("M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6zm0-10c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z")
private val IcSearch = icon("M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z")
private val IcStar = icon("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
private val IcPlay = icon("M8 5v14l11-7z")
private val IcTune = icon("M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z")
private val IcPad = icon("M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-10 7H8v3H6v-3H3v-2h3V8h2v3h3v2zm4.5 2c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4-3c-.83 0-1.5-.67-1.5-1.5S18.67 9 19.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z")
private val IcDown = icon("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")

private class DockItem(val label: String, val icon: ImageVector, val screen: String)

/**
 * Startbildschirm (oben, Controller) und Bedienfeld (unten, Touch) mit Jetpack Compose und Material 3.
 * Beide Ansichten lesen denselben Zustand, deshalb zieht unten sofort nach, wenn oben geblättert wird.
 */
class HomeUi(private val host: HomeHost) {
    private val slot = 184f   // Platz je Kachel, die Kachel selbst ist 168 groß
    private val gap = 4f
    private val pad = 28f
    private val stageW = 960f

    private var items by mutableStateOf<List<HomeItem>>(emptyList())
    private var total by mutableIntStateOf(0)
    private var index by mutableIntStateOf(0)
    private var zone by mutableIntStateOf(0)   // 0 = Kachelreihe, 1 = Leiste
    private var dock by mutableIntStateOf(0)
    private var off by mutableFloatStateOf(0f)
    private var gen by mutableIntStateOf(0)
    private var st by mutableStateOf(JSONObject())
    private val pics = mutableStateMapOf<String, ImageBitmap>()
    private var font: FontFamily? = null

    /* ---------- Von der Activity ---------- */

    fun set(list: List<HomeItem>, all: Int, selKey: String) {
        val keep = items.getOrNull(index)?.key ?: selKey
        items = list
        total = all
        val at = list.indexOfFirst { it.key == keep }
        index = if (at >= 0) at else min(index, list.size)
        gen++
        place()
        refresh()
    }

    fun refresh() {
        try {
            st = JSONObject(host.stateJson())
        } catch (e: Exception) {
            // alter Stand bleibt
        }
    }

    /** Eine Taste vom Controller. Namen wie in der Web-Oberfläche: left, right, up, down, A, B, X, Y, L1 … */
    fun press(name: String) {
        val games = st.optInt("games", 0) > 0
        val lib = if (games) "3.1" else "3.7"
        when (name) {
            "left", "right", "L2", "R2" -> {
                val d = if (name == "right" || name == "R2") 1 else -1
                if (zone == 0 || name.length == 2) {
                    zone = 0
                    val n = (index + d).coerceIn(0, items.size)
                    if (n != index) {
                        index = n
                        place()
                        host.sound("fokus")
                        items.getOrNull(n)?.let { host.selected(it.key) }
                    }
                } else {
                    val n = (dock + d).coerceIn(0, dockItems().size - 1)
                    if (n != dock) {
                        dock = n
                        host.sound("fokus")
                    }
                }
            }
            "down" -> if (zone == 0) { zone = 1; dock = dock.coerceIn(0, dockItems().size - 1); host.sound("fokus") }
            "up" -> if (zone == 1) { zone = 0; host.sound("fokus") }
            "A" -> if (zone == 0) activate(index) else dockItems().getOrNull(dock)?.let { go(it.screen) }
            "X" -> items.getOrNull(index)?.let { host.sound("ok"); host.details(it.key, "3.5") }
            "Y", "R1" -> go(lib)
            "L1" -> go("3.4")
            "START" -> go("5.1")
            "SELECT" -> go("7.2")
        }
    }

    fun topView(owner: ComponentActivity): View = ComposeView(owner).apply {
        setContent { Stage(owner, stageW, 540f) { Top() } }
    }

    /** decor ist die oberste Ansicht des Fensters auf dem zweiten Bildschirm: dort hängt Compose seinen Lebenslauf an. */
    fun panelView(context: Context, owner: ComponentActivity, decor: View): View {
        decor.setViewTreeLifecycleOwner(owner)
        decor.setViewTreeViewModelStoreOwner(owner)
        decor.setViewTreeSavedStateRegistryOwner(owner)
        return ComposeView(context).apply {
            setContent { Stage(context, 620f, 540f) { Panel() } }
        }
    }

    /* ---------- Logik ---------- */

    private fun go(screen: String) {
        host.sound("ok")
        host.open(screen)
    }

    private fun activate(i: Int) {
        val a = items.getOrNull(i)
        if (a == null) {
            go(if (st.optInt("games", 0) > 0) "3.1" else "3.7")
        } else {
            host.sound("start")
            host.selected(a.key)
            host.launch(a.key)
        }
    }

    /** Wie auf einer Konsole: die Reihe bewegt sich erst, wenn die Auswahl an den Rand kommt. */
    private fun place() {
        val l = pad + index * (slot + gap)
        val r = l + slot
        var o = off
        if (l - o < pad + 30f) o = l - pad - 30f else if (r - o > stageW - pad - 120f) o = r - (stageW - pad - 120f)
        val max = (pad * 2 + (items.size + 1) * (slot + gap) - gap - stageW).coerceAtLeast(0f)
        off = o.coerceIn(0f, max)
    }

    private fun dockItems(): List<DockItem> {
        val games = st.optInt("games", 0) > 0
        val out = ArrayList<DockItem>()
        out.add(DockItem(if (games) "Spiele" else "Apps", IcApps, if (games) "3.1" else "3.7"))
        out.add(DockItem("Favoriten", IcStar, "3.4"))
        out.add(DockItem("Suche", IcSearch, "3.3"))
        if (games) out.add(DockItem("Apps", IcPad, "3.7"))
        out.add(DockItem("System", IcTune, "5.1"))
        if (st.optJSONObject("upd")?.optBoolean("available") == true) out.add(DockItem("Update", IcDown, "6.2"))
        return out
    }

    private fun sub(a: HomeItem?): String {
        if (a == null) return "Bibliothek"
        var s = if (a.game) a.sys else "App"
        if (a.fav) s += " · Favorit"
        if (a.last > 0) s += " · zuletzt am " + SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(a.last))
        return s
    }

    /* ---------- Bausteine ---------- */

    @Composable
    private fun Stage(context: Context, w: Float, h: Float, content: @Composable () -> Unit) {
        val ctx = LocalContext.current
        val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else darkColorScheme()
        var f = font
        if (f == null) {
            f = try {
                FontFamily(listOf(400, 500, 600, 700, 800).map { Font("Outfit.ttf", context.assets, FontWeight(it)) })
            } catch (e: Exception) {
                FontFamily.Default
            }
            font = f
        }
        val family: FontFamily = f ?: FontFamily.Default
        MaterialTheme(colorScheme = scheme) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                // Fester Entwurfsmaßstab wie bisher: 1 dp ist ein Entwurfs-Pixel, egal wie dicht der Bildschirm ist.
                val real = LocalDensity.current
                val s = with(real) { min(maxWidth.toPx() / w, maxHeight.toPx() / h) }.coerceAtLeast(0.1f)
                CompositionLocalProvider(LocalDensity provides Density(s, 1f), LocalFont provides family) {
                    Box(Modifier.size(w.dp, h.dp)) { content() }
                }
            }
        }
    }

    @Composable
    private fun T(
        text: String, size: Int, weight: Int = 500, color: Color = LocalContentColor.current,
        modifier: Modifier = Modifier, spacing: Float = 0f, align: TextAlign? = null
    ) {
        Text(
            text = text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = FontWeight(weight),
            fontFamily = LocalFont.current, letterSpacing = spacing.sp, textAlign = align,
            lineHeight = (size * 1.2f).sp, overflow = TextOverflow.Ellipsis, maxLines = 1
        )
    }

    @Composable
    private fun pic(k: String): ImageBitmap? {
        val have = pics[k]
        val g = gen
        LaunchedEffect(k, g) {
            if (pics[k] == null) {
                val b = withContext(Dispatchers.IO) {
                    try {
                        host.picture(k)?.asImageBitmap()
                    } catch (e: Throwable) {
                        null
                    }
                }
                if (b != null) pics[k] = b
            }
        }
        return have
    }

    /** Bild einer App oder eines Spiels, sonst eine Farbfläche mit Kürzel. */
    @Composable
    private fun Art(a: HomeItem, n: Int, letter: Int) {
        val cs = MaterialTheme.colorScheme
        val bmp = pic(a.key)
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val bg = when (n % 3) { 0 -> cs.primaryContainer; 1 -> cs.secondaryContainer; else -> cs.tertiaryContainer }
            val fg = when (n % 3) { 0 -> cs.onPrimaryContainer; 1 -> cs.onSecondaryContainer; else -> cs.onTertiaryContainer }
            Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                T(if (a.badge.isNotEmpty()) a.badge else a.label.take(1).uppercase(), letter, 800, fg)
            }
        }
    }

    /* ---------- Oben ---------- */

    @Composable
    private fun Top() {
        val cs = MaterialTheme.colorScheme
        LaunchedEffect(Unit) {
            while (true) {
                delay(5000)
                refresh()
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.surfaceContainer, cs.surface)))) {
            Row(
                Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(st.optString("time"), 22, 700, cs.onSurface)
                Spacer(Modifier.width(12.dp))
                T(st.optString("weekday") + ", " + st.optString("date"), 15, 500, cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (st.optBoolean("wifi")) {
                    Icon(IcWifi, null, Modifier.size(18.dp), cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                T(st.optInt("battery").toString() + " %", 15, 600, cs.onSurfaceVariant)
            }

            val a = items.getOrNull(index)
            Column(Modifier.padding(start = 36.dp, top = 74.dp).width(820.dp)) {
                T(sub(a).uppercase(), 13, 600, cs.primary, spacing = 1.2f)
                Spacer(Modifier.height(2.dp))
                T(a?.label ?: "Alles anzeigen", 38, 700, cs.onSurface)
            }

            val x by animateFloatAsState(off, spring(dampingRatio = 0.9f, stiffness = 420f), label = "reihe")
            Box(Modifier.fillMaxWidth().padding(top = 158.dp).height(240.dp)) {
                Box(Modifier.graphicsLayer { translationX = -x * density }) {
                    for (i in 0..items.size) {
                        Tile(i, Modifier.offset(x = (pad + i * (slot + gap)).dp, y = 28.dp))
                    }
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                dockItems().forEachIndexed { i, d ->
                    val on = zone == 1 && dock == i
                    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, label = "leiste")
                    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, label = "leiste")
                    Row(
                        Modifier.height(56.dp).clip(CircleShape).background(bg)
                            .clickable { zone = 1; dock = i; go(d.screen) }
                            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
                            .padding(horizontal = if (on) 22.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(d.icon, null, Modifier.size(24.dp), fg)
                        if (on) {
                            Spacer(Modifier.width(10.dp))
                            T(d.label, 16, 600, fg)
                        }
                    }
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Hint("A", "Starten")
                Hint("X", "Details")
                Hint("Y", "Bibliothek")
            }
        }
    }

    @Composable
    private fun Hint(k: String, t: String) {
        val cs = MaterialTheme.colorScheme
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).border(1.5.dp, cs.outline, CircleShape), contentAlignment = Alignment.Center) {
                T(k, 11, 700, cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            T(t, 13, 500, cs.onSurfaceVariant)
        }
    }

    @Composable
    private fun Tile(i: Int, modifier: Modifier) {
        val cs = MaterialTheme.colorScheme
        val on = zone == 0 && index == i
        val scale by animateFloatAsState(if (on) 1.14f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "kachel")
        val ring by animateColorAsState(if (on) cs.primary else Color.Transparent, label = "ring")
        Box(
            modifier.size(slot.dp).zIndex(if (on) 1f else 0f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(3.dp, ring, RoundedCornerShape(46.dp))
                .padding(8.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(cs.surfaceContainerHighest)
                .clickable { zone = 0; index = i; place(); activate(i) },
            contentAlignment = Alignment.Center
        ) {
            val a = items.getOrNull(i)
            if (a == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(IcApps, null, Modifier.size(40.dp), cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    T("Alle $total", 14, 600, cs.onSurfaceVariant)
                }
            } else {
                Art(a, i, 30)
            }
        }
    }

    /* ---------- Unten ---------- */

    @Composable
    private fun Panel() {
        val cs = MaterialTheme.colorScheme
        val a = items.getOrNull(index)
        Column(
            Modifier.fillMaxSize().background(cs.surface).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(36.dp)).background(cs.surfaceContainerHigh)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    if (a != null) Art(a, index, 16) else Icon(IcApps, null, Modifier.size(30.dp), cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(sub(a).uppercase(), 12, 600, cs.primary, spacing = 0.8f)
                    Spacer(Modifier.height(2.dp))
                    T(a?.label ?: "Alles anzeigen", 21, 700, cs.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { activate(index) }, modifier = Modifier.height(64.dp),
                    contentPadding = PaddingValues(horizontal = 26.dp)
                ) {
                    Icon(IcPlay, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    T(if (a != null && a.game) "Spielen" else "Öffnen", 17, 700)
                }
            }

            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (a != null && a.fav) "★ Favorit" else "☆ Favorit") { a?.let { host.sound("an"); host.fav(it.key) } }
                Chip("Details") { a?.let { host.sound("ok"); host.details(it.key, "3.5") } }
                if (a != null && a.game) {
                    Chip("Emulator") { host.sound("ok"); host.details(a.key, "3.6") }
                } else {
                    Chip("App-Info") { a?.let { host.android("app:" + it.key) } }
                }
            }

            T("SCHNELL STARTEN", 12, 600, cs.onSurfaceVariant, Modifier.padding(start = 6.dp), spacing = 1.2f)
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val others = items.filter { a == null || it.key != a.key }.take(4)
                others.forEachIndexed { n, b ->
                    Box(
                        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainerHighest)
                            .clickable { host.sound("start"); host.selected(b.key); host.launch(b.key) }
                    ) {
                        Art(b, n + 1, 16)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB0000000)))))
                        T(b.label, 12, 600, Color.White, Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
                repeat(4 - others.size) { Spacer(Modifier.weight(1f)) }
            }

            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SliderBox(IcSun, "brightness") { host.brightness(it) }
                SliderBox(IcVol, "volume") { host.volume(it) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val games = st.optInt("games", 0) > 0
                Toggle(IcWifi, "WLAN", st.optBoolean("wifi")) { host.android("wifi") }
                Toggle(IcBt, "Bluetooth", st.optBoolean("bt")) { host.android("bt") }
                Toggle(IcPlane, "Flugmodus", st.optBoolean("airplane")) { host.android("airplane") }
                Toggle(IcSearch, "Suche", false) { go("3.3") }
                Toggle(IcApps, if (games) "Spiele" else "Apps", false) { go(if (games) "3.1" else "3.7") }
                Toggle(IcStar, "Favoriten", false) { go("3.4") }
                Toggle(IcTune, "System", false) { go("5.1") }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                T("Akku " + st.optInt("battery") + " % · " + st.optString("temp"), 12, 500, cs.onSurfaceVariant)
                T(st.optString("free") + " frei", 12, 500, cs.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun RowScope.Chip(label: String, click: () -> Unit) {
        FilledTonalButton(onClick = click, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            T(label, 15, 600)
        }
    }

    @Composable
    private fun RowScope.SliderBox(ic: ImageVector, field: String, send: (Int) -> Unit) {
        val cs = MaterialTheme.colorScheme
        val base = st.optInt(field, 0)
        var v by remember(base) { mutableFloatStateOf(base.toFloat()) }
        Row(
            Modifier.weight(1f).height(60.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ic, null, Modifier.size(22.dp), cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Slider(
                value = v, onValueChange = { v = it; send(it.roundToInt()) },
                valueRange = 0f..100f, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            T(v.roundToInt().toString(), 14, 700, cs.onSurface, Modifier.width(28.dp), align = TextAlign.End)
        }
    }

    /** Wie die Schnelleinstellungen am Pixel: aus = Kreis, an = gefülltes, eckigeres Feld. */
    @Composable
    private fun Toggle(ic: ImageVector, label: String, on: Boolean, click: () -> Unit) {
        val cs = MaterialTheme.colorScheme
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(if (on) RoundedCornerShape(22.dp) else CircleShape)
                    .background(if (on) cs.primary else cs.surfaceContainerHigh).clickable(onClick = click),
                contentAlignment = Alignment.Center
            ) {
                Icon(ic, null, Modifier.size(26.dp), if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            T(label, 11, 600, cs.onSurfaceVariant)
        }
    }
}
