package com.rocchio.nuvix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* 1 unità "rem" del mockup = 1% della larghezza dello schermo (16:9) */
val LocalUnit = compositionLocalOf { 9.6.dp }
val LocalAccent = compositionLocalOf { Color(0xFF58A6FF) }
private val Ink = Color(0xFFEEF2F8)
private val Muted = Color(0xFF94A3B8)
private val Line = Color.White.copy(alpha = .09f)
private val LineSoft = Color.White.copy(alpha = .055f)
private val Sub = Color(0xFFA9BBD2)

@Composable fun r(v: Float): Dp = LocalUnit.current * v
@Composable fun fs(v: Float): TextUnit = with(LocalDensity.current) { r(v).toSp() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NuvixApp() }
    }
}

data class AppItem(val label: String, val icon: ImageBitmap?, val intent: Intent)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    fun q(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
    return (q(Intent.CATEGORY_LEANBACK_LAUNCHER) + q(Intent.CATEGORY_LAUNCHER))
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
        .map { ri ->
            AppItem(
                ri.loadLabel(pm).toString(),
                runCatching { ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull(),
                Intent(Intent.ACTION_MAIN).setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
}

@Composable
fun NuvixApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nuvix", Context.MODE_PRIVATE) }
    var accent by remember { mutableStateOf(Color(prefs.getInt("accent", 0xFF58A6FF.toInt()))) }
    val apps by produceState(emptyList<AppItem>()) { value = withContext(Dispatchers.Default) { loadApps(ctx) } }
    BackHandler {}
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF03050A)), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * (16f / 9f))
        CompositionLocalProvider(LocalUnit provides w / 100, LocalAccent provides accent) {
            Screen(Modifier.size(w, w * 9f / 16f), apps) { c ->
                accent = c
                prefs.edit().putInt("accent", c.toArgb()).apply()
            }
        }
    }
}

/* ---------- helper ---------- */
@Composable
fun Modifier.tv(shape: Shape, onClick: () -> Unit = {}): Modifier {
    var f by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val a = LocalAccent.current
    val bw = r(.2f)
    return this.clip(shape)
        .onFocusChanged { f = it.isFocused }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
        .drawWithContent {
            drawContent()
            if (f) drawOutline(shape.createOutline(size, layoutDirection, this), a, style = Stroke(bw.toPx() * 2))
        }
}

@Composable
fun T(s: String, size: Float, c: Color = Ink, w: FontWeight = FontWeight.Normal, ls: Float = 0f,
      mod: Modifier = Modifier, lines: Int = 1) {
    Text(s, mod, color = c, fontSize = fs(size), fontWeight = w, letterSpacing = ls.em,
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Bar(p: Float, mod: Modifier = Modifier, h: Float = .2f, bg: Color = Color.White.copy(alpha = .14f)) {
    Box(mod.fillMaxWidth().height(r(h)).clip(CircleShape).background(bg)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(LocalAccent.current))
    }
}

class Art(val glow: Long, val x: Float, val y: Float, val c1: Long, val c2: Long)
private val ARTS = listOf(
    Art(0x8C58A6FF, .2f, .2f, 0xFF152C4A, 0xFF0A1424),
    Art(0x73FF6E8C, .8f, .3f, 0xFF33131F, 0xFF120A12),
    Art(0x666EF0BE, .3f, .8f, 0xFF0F2A28, 0xFF08161A),
    Art(0x6BFFC86E, .7f, .7f, 0xFF2E2413, 0xFF14100A),
    Art(0x80A082FF, .4f, .3f, 0xFF221C3D, 0xFF0D0B18),
    Art(0x6678DCFF, .6f, .2f, 0xFF0E2836, 0xFF07141B)
)
fun Modifier.art(i: Int): Modifier = drawBehind {
    val a = ARTS[i % ARTS.size]
    drawRect(Brush.linearGradient(listOf(Color(a.c1), Color(a.c2))))
    drawRect(Brush.radialGradient(listOf(Color(a.glow), Color.Transparent),
        Offset(size.width * a.x, size.height * a.y), size.width * .5f))
}

/* ---------- schermata ---------- */
@Composable
fun Screen(mod: Modifier, apps: List<AppItem>, onAccent: (Color) -> Unit) {
    Column(
        mod.background(Color(0xFF060911))
            .drawBehind {
                fun g(c: Long, x: Float, y: Float, rad: Float) = drawRect(
                    Brush.radialGradient(listOf(Color(c), Color.Transparent),
                        Offset(size.width * x, size.height * y), size.width * rad))
                g(0x2958A6FF, .12f, -.12f, .5f); g(0x217E5AFF, .96f, .04f, .42f); g(0x1F14788C, .6f, 1.08f, .5f)
            }
            .padding(start = r(2f), end = r(2f), top = r(1.5f), bottom = r(1.4f)),
        verticalArrangement = Arrangement.spacedBy(r(1.15f))
    ) {
        Header(onAccent)
        IptvPanel()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = r(9.6f)),
                verticalArrangement = Arrangement.spacedBy(r(.7f))
            ) {
                ShelfHead("Scelti per te", "aggiornato ora")
                Row(horizontalArrangement = Arrangement.spacedBy(r(.8f))) { PICKS.forEach { MediaCard(it) } }
                ShelfHead("I tuoi canali", "preferiti")
                Row(horizontalArrangement = Arrangement.spacedBy(r(.8f))) { CHANNELS.forEach { MediaCard(it) } }
            }
            Dock(apps, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun Header(onAccent: (Color) -> Unit) {
    val a = LocalAccent.current
    val time by produceState("") {
        while (true) { value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(10_000) }
    }
    Row(Modifier.fillMaxWidth().height(r(3.5f)), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(r(1.4f))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(r(.7f))) {
            Box(Modifier.size(r(2.3f)).clip(RoundedCornerShape(r(.85f)))
                .background(Brush.linearGradient(listOf(a, Color(0xD97850FF))))) {
                Canvas(Modifier.padding(r(.62f)).fillMaxSize()) {
                    val p = Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, size.height * .38f); lineTo(size.width * .48f, size.height * .56f)
                        lineTo(size.width * .48f, size.height); lineTo(0f, size.height * .62f); close()
                    }
                    drawPath(p, Color(0xFF05070C))
                }
            }
            T("NUVIX", 1.35f, w = FontWeight.Bold, ls = .16f)
        }
        val navShape = RoundedCornerShape(r(1.15f))
        Row(Modifier.clip(navShape).background(Color.White.copy(alpha = .04f)).border(1.dp, LineSoft, navShape)
            .padding(r(.34f)), horizontalArrangement = Arrangement.spacedBy(r(.35f))) {
            listOf("Home", "Guida", "Film e Serie", "App", "Libreria").forEachIndexed { i, t ->
                Box(Modifier.tv(RoundedCornerShape(r(.85f)))
                    .then(if (i == 0) Modifier.background(a) else Modifier)
                    .padding(horizontal = r(1f), vertical = r(.52f))) {
                    T(t, 1.02f, if (i == 0) Color(0xFF0A0E16) else Muted, FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val swShape = RoundedCornerShape(r(1f))
        Row(Modifier.clip(swShape).background(Color.White.copy(alpha = .04f)).border(1.dp, LineSoft, swShape)
            .padding(horizontal = r(.7f), vertical = r(.34f)), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(r(.42f))) {
            listOf(0xFF58A6FF, 0xFFB08CFF, 0xFF4FDBA0, 0xFFFFC15E, 0xFFFF7D8A, 0xFFB8C4D6).forEach { hex ->
                val col = Color(hex)
                Box(Modifier.size(r(.85f)).tv(CircleShape) { onAccent(col) }.background(col))
            }
        }
        T(time, 1.05f, w = FontWeight.SemiBold, ls = .04f)
        Box(Modifier.size(r(2.4f)).tv(RoundedCornerShape(r(.9f))).background(Color.White.copy(alpha = .05f)),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(r(1.15f))) {
                val s = size.width
                drawCircle(Ink, s * .33f, Offset(s * .46f, s * .46f), style = Stroke(s * .09f))
                drawLine(Ink, Offset(s * .7f, s * .7f), Offset(s * .92f, s * .92f), strokeWidth = s * .09f)
            }
        }
        Box(Modifier.size(r(2.4f)).tv(CircleShape).background(Brush.linearGradient(listOf(a, Color(0xFF9F8CFF)))),
            contentAlignment = Alignment.Center) { T("M", .95f, Color(0xFF0A0E16), FontWeight.SemiBold) }
    }
}

/* ---------- pannello IPTV ---------- */
class Guide(val l: String, val n: String, val s: String, val p: Float, val w: String)
private val GUIDE = listOf(
    Guide("S", "Sky Sport Uno", "Champions League · Milan – Inter", .64f, "21:00"),
    Guide("D", "DAZN 1", "Serie A · Juventus – Roma", .38f, "21:15"),
    Guide("R", "Rai 1", "Porta a Porta", .22f, "21:25"),
    Guide("E", "Eurosport 1", "ATP Finals · Sinner – Alcaraz", .71f, "20:45"),
    Guide("C", "Canale 5", "Film: Il Gladiatore", .12f, "21:20")
)

@Composable
fun IptvPanel() {
    val sh = RoundedCornerShape(r(1.6f))
    Row(Modifier.fillMaxWidth().height(r(23.5f)).clip(sh)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .075f), Color.White.copy(alpha = .025f))))
        .border(1.dp, Line, sh).padding(r(1f)), horizontalArrangement = Arrangement.spacedBy(r(1f))) {
        NowCard(Modifier.weight(1.42f).fillMaxHeight())
        Column(Modifier.weight(.98f).fillMaxHeight()
            .padding(start = r(.1f), top = r(.35f), end = r(.2f), bottom = r(.2f)),
            verticalArrangement = Arrangement.spacedBy(r(.55f))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = r(.35f)), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                T("IN ONDA ORA", .78f, LocalAccent.current, FontWeight.Bold, .18f)
                Row(horizontalArrangement = Arrangement.spacedBy(r(.3f))) {
                    listOf("Ora", "Canali", "Preferiti").forEachIndexed { i, t ->
                        Box(Modifier.clip(RoundedCornerShape(r(.55f)))
                            .background(Color.White.copy(alpha = if (i == 0) .12f else .045f))
                            .padding(horizontal = r(.6f), vertical = r(.28f))) {
                            T(t, .8f, if (i == 0) Ink else Muted, FontWeight.SemiBold)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(.78f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(r(.38f))) {
                GUIDE.forEachIndexed { i, g -> GuideRow(g, i == 0) }
            }
        }
    }
}

@Composable
fun GuideRow(g: Guide, hi: Boolean) {
    Row(Modifier.fillMaxWidth().tv(RoundedCornerShape(r(.75f)))
        .background(Color.White.copy(alpha = if (hi) .1f else .035f))
        .padding(horizontal = r(.6f), vertical = r(.5f)), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(r(.7f))) {
        Box(Modifier.size(r(2f)).clip(RoundedCornerShape(r(.6f))).background(Color(0xFF12233C)),
            contentAlignment = Alignment.Center) { T(g.l, .8f, Color(0xFFCFE0F5), FontWeight.ExtraBold) }
        Column(Modifier.weight(1f)) {
            T(g.n, .95f, w = FontWeight.SemiBold); T(g.s, .8f, Muted)
            Bar(g.p, Modifier.padding(top = r(.28f)))
        }
        T(g.w, .78f, Color(0xFF93A6BC))
    }
}

@Composable
fun Tag(s: String, bg: Color = Color.White.copy(alpha = .09f)) {
    val sh = RoundedCornerShape(r(.55f))
    Box(Modifier.clip(sh).background(bg).border(1.dp, Line, sh).padding(horizontal = r(.62f), vertical = r(.32f))) {
        T(s, .8f, Color(0xFFCFE0F5), FontWeight.SemiBold, .06f)
    }
}

@Composable
fun Btn(label: String, kind: Int) {
    val a = LocalAccent.current
    val (bg, fg) = when (kind) {
        0 -> a to Color(0xFF07101C)
        1 -> Color.White.copy(alpha = .07f) to Ink
        else -> Color.Transparent to Color(0xFFB5C6DA)
    }
    Row(Modifier.tv(RoundedCornerShape(r(.95f))).background(bg)
        .padding(horizontal = r(if (kind == 2) .6f else 1.35f), vertical = r(.72f)),
        verticalAlignment = Alignment.CenterVertically) {
        if (kind == 0) {
            Canvas(Modifier.size(r(1.05f))) {
                val p = Path().apply {
                    moveTo(size.width * .2f, size.height * .1f); lineTo(size.width * .9f, size.height * .5f)
                    lineTo(size.width * .2f, size.height * .9f); close()
                }
                drawPath(p, fg)
            }
            Spacer(Modifier.width(r(.55f)))
        }
        T(label, 1f, fg, FontWeight.SemiBold)
    }
}

@Composable
fun NowCard(mod: Modifier) {
    val a = LocalAccent.current
    Column(mod.clip(RoundedCornerShape(r(1.2f)))
        .background(Brush.linearGradient(listOf(Color(0xFF0B1A2E), Color(0xFF0A1020), Color(0xFF111A2C))))
        .drawBehind {
            drawRect(Brush.radialGradient(listOf(a.copy(alpha = .34f), Color.Transparent),
                Offset(size.width * .78f, size.height * .22f), size.width * .5f))
        }
        .padding(horizontal = r(1.4f), vertical = r(1.25f)),
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(r(.6f))) {
            Row(Modifier.clip(RoundedCornerShape(r(.55f))).background(Color(0xFFE4322B))
                .padding(horizontal = r(.68f), vertical = r(.32f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(r(.42f)).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(r(.45f)))
                T("LIVE", .82f, Color.White, FontWeight.Bold, .12f)
            }
            Tag("4K HDR"); Tag("Dolby 5.1")
            Spacer(Modifier.weight(1f))
            Tag("21:00 – 22:50", Color(0x80060A12))
        }
        Column(Modifier.widthIn(max = r(34f)), verticalArrangement = Arrangement.spacedBy(r(.55f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(r(1.9f)).clip(RoundedCornerShape(r(.6f)))
                    .background(Brush.linearGradient(listOf(Color(0xFF2C6BD6), Color(0xFF12325F)))),
                    contentAlignment = Alignment.Center) { T("S", .85f, Color.White, FontWeight.ExtraBold) }
                Spacer(Modifier.width(r(.6f)))
                T("Sky Sport Uno", .98f, Color(0xFFC7D6E8), FontWeight.SemiBold)
                Spacer(Modifier.width(r(.6f)))
                Box(Modifier.size(r(.42f)).clip(CircleShape).background(a))
            }
            T("Champions League\nMilan – Inter", 2.15f, Ink, FontWeight.Bold, -.015f, lines = 2)
            T("Secondo tempo · 61' · 2 – 1", 1f, Sub)
            Column {
                Bar(.64f, h = .28f, bg = Color.White.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth().padding(top = r(.4f)), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("21:00", "In onda ora", "22:50").forEach { T(it, .8f, Color(0xFF9FB2C9)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(r(.7f))) {
                Btn("Guarda ora", 0); Btn("Guida TV", 1); Btn("Multi-view", 2)
            }
        }
    }
}

/* ---------- scaffali ---------- */
class Media(val t: String, val s: String, val badge: String?, val art: Int)
private val PICKS = listOf(
    Media("Dark Waters", "Stagione 2 · 8 episodi", "SERIE", 4), Media("Orbita", "2026 · Fantascienza", "FILM", 0),
    Media("Notturno", "2025 · Thriller", "FILM", 1), Media("Verde Profondo", "Stagione 1 · 10 episodi", "SERIE", 2),
    Media("L'Ultima Estate", "2024 · Drammatico", "FILM", 3), Media("Frequenze", "Stagione 3 · 6 episodi", "SERIE", 5)
)
private val CHANNELS = listOf(
    Media("Sky Sport Uno", "Champions League", null, 0), Media("DAZN 1", "Serie A", null, 1),
    Media("Rai 1", "Porta a Porta", null, 2), Media("Eurosport 1", "ATP Finals", null, 3),
    Media("Canale 5", "Il Gladiatore", null, 4), Media("TV 2000", "Documentari", null, 5)
)

@Composable
fun ShelfHead(t: String, s: String) {
    Row(Modifier.padding(horizontal = r(.2f)), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(r(.8f))) {
        T(t, 1.32f, w = FontWeight.SemiBold, ls = -.01f); T(s, .86f, Muted)
    }
}

@Composable
fun MediaCard(m: Media) {
    Box(Modifier.width(r(13.2f)).height(r(7.1f)).tv(RoundedCornerShape(r(1f))).art(m.art)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Color(0xD904070D))))
        if (m.badge != null) {
            Box(Modifier.align(Alignment.TopStart).padding(r(.55f)).clip(RoundedCornerShape(r(.4f)))
                .background(Color(0xB8060A12)).padding(horizontal = r(.42f), vertical = r(.2f))) {
                T(m.badge, .66f, w = FontWeight.Bold, ls = .08f)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = r(.7f), end = r(.7f), bottom = r(.55f))) {
            T(m.t, .94f, w = FontWeight.SemiBold); T(m.s, .76f, Sub)
        }
    }
}

/* ---------- dock infinito ---------- */
sealed interface Dk
class DApp(val app: AppItem) : Dk
class DMini(val t: String, val s: String, val p: Float, val art: Int) : Dk
class DChip(val l: String, val n: String, val s: String) : Dk
private val MINIS = listOf(
    DMini("Dark Waters · S2E3", "12 min rimasti", .72f, 4), DMini("Orbita", "34 min rimasti", .41f, 0),
    DMini("Notturno", "1 h 02 min rimasti", .18f, 1), DMini("Verde Profondo · S1E7", "9 min rimasti", .85f, 2)
)
private val CHIPS = listOf(
    DChip("S", "Sky Sport Uno", "visto 21:04"), DChip("D", "DAZN 1", "visto 20:31"),
    DChip("R", "Rai 1", "visto ieri"), DChip("E", "Eurosport 1", "visto ieri")
)

@Composable
fun Dock(apps: List<AppItem>, mod: Modifier) {
    val a = LocalAccent.current
    val ctx = LocalContext.current
    val items: List<Dk> = remember(apps) { apps.map { DApp(it) } + MINIS + CHIPS }
    val n = items.size
    val state = key(n) { rememberLazyListState(1_000_000 - 1_000_000 % n) }
    var focused by remember { mutableStateOf(false) }
    val unit = with(LocalDensity.current) { r(1f).toPx() }
    LaunchedEffect(focused, state) {
        if (!focused) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                state.scrollBy((now - last) / 1e9f * unit)   // ~1 rem al secondo
                last = now
            }
        }
    }
    val sh = RoundedCornerShape(r(1.45f))
    Row(mod.fillMaxWidth().height(r(8.6f)).clip(sh)
        .background(Color(0xB30A101C))
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .13f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .09f))))
        .border(1.dp, Color.White.copy(alpha = .13f), sh).padding(horizontal = r(1.15f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(r(1.2f))) {
        Column(Modifier.width(r(10f)).height(r(5.6f)).drawBehind {
            drawLine(Line, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 2f)
        }.padding(end = r(1f)), verticalArrangement = Arrangement.Center) {
            T("Dock", 1.02f, w = FontWeight.Bold, ls = .02f)
            T("scorre all'infinito", .78f, Muted)
            Row(Modifier.padding(top = r(.28f)), horizontalArrangement = Arrangement.spacedBy(r(.5f))) {
                listOf("App" to a, "Continua" to Color(0xFF9F8CFF), "Recenti" to Color(0xFF4FDBA0)).forEach { (t, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(r(.34f)).clip(CircleShape).background(c)); Spacer(Modifier.width(r(.25f)))
                        T(t, .68f, Color(0xFF93A6BC))
                    }
                }
            }
        }
        LazyRow(
            state = state,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.hasFocus }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, .03f to Color.Black, .97f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn)
                },
            horizontalArrangement = Arrangement.spacedBy(r(.85f)), verticalAlignment = Alignment.CenterVertically
        ) {
            items(Int.MAX_VALUE) { i ->
                when (val d = items[i % n]) {
                    is DApp -> Column(Modifier.width(r(4.9f)), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(r(.38f))) {
                        Box(Modifier.size(r(4.1f)).tv(RoundedCornerShape(r(1.15f))) { ctx.startActivity(d.app.intent) }
                            .background(Color(0xFF14263F))) {
                            d.app.icon?.let { Image(it, d.app.label, Modifier.fillMaxSize()) }
                        }
                        T(d.app.label, .72f, Color(0xFFC2D0E0))
                    }
                    is DMini -> Box(Modifier.width(r(11f)).height(r(5.9f)).tv(RoundedCornerShape(r(.95f))).art(d.art)) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, 1f to Color(0xE604070D))))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = r(.6f), end = r(.6f), bottom = r(.5f))) {
                            T(d.t, .82f, w = FontWeight.SemiBold); T(d.s, .68f, Sub)
                        }
                        Bar(d.p, Modifier.align(Alignment.BottomStart), h = .22f)
                    }
                    is DChip -> Column(Modifier.width(r(8.6f)).height(r(5.9f)).tv(RoundedCornerShape(r(.95f)))
                        .background(Color.White.copy(alpha = .05f)).padding(r(.5f)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(r(.34f), Alignment.CenterVertically)) {
                        Box(Modifier.size(r(2.2f)).clip(RoundedCornerShape(r(.65f))).background(Color(0xFF14263F)),
                            contentAlignment = Alignment.Center) { T(d.l, .85f, Color(0xFFCFE0F5), FontWeight.ExtraBold) }
                        T(d.n, .78f, w = FontWeight.SemiBold); T(d.s, .66f, Muted)
                    }
                }
            }
        }
    }
}
