package com.rocchio.nuvix

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Ch(val name: String, val group: String, val logo: String, val url: String, val epgId: String)

/* ---------- stato e caricamento liste ---------- */
object Iptv {
    val channels = mutableStateListOf<Ch>()
    var fullscreen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var status by mutableStateOf("")

    suspend fun reload(ctx: Context) {
        val p = ctx.getSharedPreferences("nuvix", Context.MODE_PRIVATE)
        val src = p.getString("src", "") ?: ""
        if (src.isEmpty()) return
        loading = true; status = "Caricamento in corso…"
        try {
            val list = withContext(Dispatchers.IO) {
                if (src == "m3u") parseM3u(fetch((p.getString("m3u", "") ?: "").trim()))
                else loadXtream(p.getString("xs", "") ?: "", p.getString("xu", "") ?: "", p.getString("xp", "") ?: "")
            }
            channels.clear(); channels.addAll(list)
            status = "${list.size} canali caricati"
        } catch (e: Exception) {
            status = "Errore: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            loading = false
        }
    }
}

fun fetch(u: String): String {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 60000; c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Nuvix")
    return c.inputStream.bufferedReader().use { it.readText() }
}

private val ATTR = Regex("([\\w-]+)=\"([^\"]*)\"")

fun parseM3u(t: String): List<Ch> {
    val out = ArrayList<Ch>()
    var name = ""; var grp = ""; var logo = ""; var id = ""
    for (raw in t.lineSequence()) {
        val l = raw.trim()
        if (l.startsWith("#EXTINF")) {
            val at = ATTR.findAll(l).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            grp = at["group-title"] ?: ""; logo = at["tvg-logo"] ?: ""; id = at["tvg-id"] ?: ""
            name = l.substringAfterLast(",").trim().ifEmpty { at["tvg-name"] ?: "" }
        } else if (l.isNotEmpty() && !l.startsWith("#")) {
            out.add(Ch(name.ifEmpty { l }, grp, logo, l, id))
            name = ""; grp = ""; logo = ""; id = ""
        }
    }
    return out
}

fun loadXtream(server: String, user: String, pass: String): List<Ch> {
    var base = server.trim().trimEnd('/')
    if (!base.startsWith("http")) base = "http://$base"
    val u = URLEncoder.encode(user.trim(), "UTF-8"); val p = URLEncoder.encode(pass.trim(), "UTF-8")
    val q = "$base/player_api.php?username=$u&password=$p"
    val cats = HashMap<String, String>()
    JSONArray(fetch("$q&action=get_live_categories")).let { a ->
        for (i in 0 until a.length()) a.getJSONObject(i).let { cats[it.optString("category_id")] = it.optString("category_name") }
    }
    val arr = JSONArray(fetch("$q&action=get_live_streams"))
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Ch(o.optString("name"), cats[o.optString("category_id")] ?: "", o.optString("stream_icon"),
            "$base/live/${user.trim()}/${pass.trim()}/${o.optInt("stream_id")}.m3u8", o.optString("epg_channel_id"))
    }
}

/* ---------- guida (pannello + schermo intero) ---------- */
@Composable
fun ChannelRow(c: Ch, i: Int, full: Boolean, mod: Modifier, onClick: () -> Unit) {
    val k = if (full) 1.25f else 1f
    Row(mod.fillMaxWidth().tv(RoundedCornerShape(r(.75f)), onClick).background(Color.White.copy(alpha = .035f))
        .padding(horizontal = r(.6f * k), vertical = r(.5f * k)), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(r(.7f))) {
        Box(Modifier.size(r(2f * k)).clip(RoundedCornerShape(r(.6f))).background(Color(0xFF12233C)),
            contentAlignment = Alignment.Center) {
            T(c.name.take(1).uppercase(), .8f * k, Color(0xFFCFE0F5), FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            T(c.name, .95f * k, w = FontWeight.SemiBold)
            if (c.group.isNotEmpty()) T(c.group, .8f * k, Color(0xFF94A3B8))
        }
        T("${i + 1}", .78f * k, Color(0xFF93A6BC))
    }
}

@Composable
fun GuideList(mod: Modifier, full: Boolean, fr: FocusRequester? = null, onClick: (Ch) -> Unit) {
    if (Iptv.channels.isEmpty()) {
        Box(mod, contentAlignment = Alignment.Center) {
            T(if (Iptv.loading) "Caricamento…" else "Nessuna lista caricata. Apri le impostazioni dal profilo (M).",
                .9f, Color(0xFF94A3B8), lines = 2)
        }
        return
    }
    LazyColumn(mod, verticalArrangement = Arrangement.spacedBy(r(.38f))) {
        itemsIndexed(Iptv.channels) { i, c ->
            ChannelRow(c, i, full, if (i == 0 && fr != null) Modifier.focusRequester(fr) else Modifier) { onClick(c) }
        }
    }
}

@Composable
fun FullGuide() {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    BackHandler { Iptv.fullscreen = false }
    Column(Modifier.fillMaxSize().background(Color(0xFF060911)).padding(horizontal = r(3f), vertical = r(2f)),
        verticalArrangement = Arrangement.spacedBy(r(1f))) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(r(1f))) {
            T("Guida TV", 1.8f, w = FontWeight.Bold)
            T("${Iptv.channels.size} canali · Indietro per chiudere", .9f, Color(0xFF94A3B8))
        }
        GuideList(Modifier.weight(1f).fillMaxWidth(), true, fr) { /* prossimo passo: riproduzione */ }
    }
}

/* ---------- impostazioni ---------- */
@Composable
fun Field(label: String, v: String, mod: Modifier = Modifier, secret: Boolean = false, onChange: (String) -> Unit) {
    val a = LocalAccent.current
    var f by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(r(.7f))
    Column(verticalArrangement = Arrangement.spacedBy(r(.3f))) {
        T(label, .8f, Color(0xFF94A3B8))
        BasicTextField(v, onChange, singleLine = true, textStyle = TextStyle(color = Color(0xFFEEF2F8), fontSize = fs(1f)),
            cursorBrush = SolidColor(a),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = mod.fillMaxWidth().onFocusChanged { f = it.isFocused }.clip(sh)
                .background(Color.White.copy(alpha = .06f)).border(r(.15f), if (f) a else Color.White.copy(alpha = .09f), sh)
                .padding(r(.8f)))
    }
}

@Composable
fun Action(label: String, primary: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.tv(RoundedCornerShape(r(.95f)), onClick)
        .background(if (primary) LocalAccent.current else Color.White.copy(alpha = .08f))
        .padding(horizontal = r(1.35f), vertical = r(.72f))) {
        T(label, 1f, if (primary) Color(0xFF07101C) else Color(0xFFEEF2F8), FontWeight.SemiBold)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val p = remember { ctx.getSharedPreferences("nuvix", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var m3u by remember { mutableStateOf(p.getString("m3u", "") ?: "") }
    var xs by remember { mutableStateOf(p.getString("xs", "") ?: "") }
    var xu by remember { mutableStateOf(p.getString("xu", "") ?: "") }
    var xp by remember { mutableStateOf(p.getString("xp", "") ?: "") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    BackHandler { Iptv.settingsOpen = false }
    Column(Modifier.fillMaxSize().background(Color(0xFF060911)).padding(horizontal = r(4f), vertical = r(2.5f)),
        verticalArrangement = Arrangement.spacedBy(r(1.2f))) {
        T("Impostazioni IPTV", 1.8f, w = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(r(2.5f))) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(r(.8f))) {
                T("Lista M3U", 1.1f, LocalAccent.current, FontWeight.SemiBold)
                Field("URL della lista", m3u, Modifier.focusRequester(fr)) { m3u = it }
                Action("Salva e carica M3U") {
                    p.edit().putString("src", "m3u").putString("m3u", m3u.trim()).apply()
                    scope.launch { Iptv.reload(ctx) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(r(.8f))) {
                T("Xtream Codes", 1.1f, LocalAccent.current, FontWeight.SemiBold)
                Field("Server (es. http://dominio:porta)", xs) { xs = it }
                Field("Username", xu) { xu = it }
                Field("Password", xp, secret = true) { xp = it }
                Action("Salva e carica Xtream") {
                    p.edit().putString("src", "xt").putString("xs", xs.trim()).putString("xu", xu.trim())
                        .putString("xp", xp.trim()).apply()
                    scope.launch { Iptv.reload(ctx) }
                }
            }
        }
        T(Iptv.status, 1f, Color(0xFFA9BBD2), lines = 2)
        Action("Chiudi", primary = false) { Iptv.settingsOpen = false }
    }
}

@Composable
fun IptvOverlays() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Iptv.reload(ctx) }
    if (Iptv.fullscreen) FullGuide()
    if (Iptv.settingsOpen) SettingsScreen()
}
