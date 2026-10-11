@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.novelreader

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent { App(store) }
    }
}

// ---------- Темы ----------
class Pal(val key: String, val light: Boolean, val bg: Color, val bg2: Color, val fg: Color, val glass: Color, val stroke: Color)

val pals = listOf(
    Pal("th0", false, Color(0xFF0E0E16), Color(0xFF24184A), Color.White, Color(0x33FFFFFF), Color(0x66FFFFFF)),
    Pal("th1", true, Color(0xFFF2F2F7), Color(0xFFD9D4F5), Color.Black, Color(0x88FFFFFF), Color(0x33000000)),
    Pal("th2", false, Color(0xFF2D1B4E), Color(0xFF6A2C70), Color.White, Color(0x33FFFFFF), Color(0x66FFFFFF)),
    Pal("th3", false, Color(0xFF06202B), Color(0xFF0B7A75), Color.White, Color(0x33FFFFFF), Color(0x66FFFFFF)),
    Pal("th4", false, Color(0xFF3A0F2E), Color(0xFFC2512B), Color.White, Color(0x33FFFFFF), Color(0x66FFFFFF))
)

fun Modifier.glass(p: Pal, shape: Shape = RoundedCornerShape(28.dp)) =
    this.clip(shape).background(p.glass).border(1.dp, p.stroke, shape)

sealed class Screen {
    object Main : Screen()
    data class Chapters(val id: String) : Screen()
    data class Read(val id: String, val idx: Int) : Screen()
}

@Composable
fun App(st: Store) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    val p = pals[st.theme.coerceIn(0, pals.lastIndex)]
    LaunchedEffect(Unit) {
        if (st.user != null) try { Cloud.sync(st, false) } catch (e: Exception) { }
    }
    MaterialTheme(colorScheme = if (p.light) lightColorScheme() else darkColorScheme()) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bg, p.bg2)))
                .systemBarsPadding()
        ) {
            when (val s = screen) {
                is Screen.Main -> MainScreen(st, p) { screen = it }
                is Screen.Chapters -> ChaptersScreen(st, p, s.id) { screen = it }
                is Screen.Read -> ReadScreen(st, p, s.id, s.idx) { screen = it }
            }
        }
        BackHandler(screen !is Screen.Main) {
            screen = when (val s = screen) {
                is Screen.Read -> Screen.Chapters(s.id)
                else -> Screen.Main
            }
        }
    }
}

// ---------- Главный экран (3 страницы: Библиотека / Сайты / Настройки) ----------
@Composable
fun MainScreen(st: Store, p: Pal, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(st.tabs.first()) }
    var addTab by remember { mutableStateOf(false) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    fun syncLater() {
        if (st.user != null) scope.launch { try { Cloud.sync(st, false) } catch (e: Exception) { } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                st.import(uri, Parser.nameOf(ctx, uri), tab, false)
                syncLater()
            } catch (e: Exception) { toast("${st.t("error")}: ${e.message}") }
        }
    }

    // вызывается, когда в браузере нажали скачивание файла
    val onDownload: (String, String, String, String) -> Unit = { url, ua, cd, mime ->
        scope.launch {
            toast(st.t("downloading"))
            try {
                val name = URLUtil.guessFileName(url, cd, mime)
                val f = withContext(Dispatchers.IO) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.setRequestProperty("User-Agent", ua)
                    CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                    val out = File(File(ctx.cacheDir, "dl").apply { mkdirs() }, name)
                    c.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    out
                }
                st.import(Uri.fromFile(f), name, "@downloaded", true)   // true = склеивать главы одной серии
                f.delete()
                toast(st.t("added"))
                syncLater()
            } catch (e: Exception) { toast("${st.t("error")}: ${e.message}") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (page) {
            0 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(st.t("library"), color = p.fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { st.theme = (st.theme + 1) % pals.size; st.save() }) {
                        Icon(Icons.Rounded.Palette, st.t("theme"), tint = p.fg)
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(st.tabs.toList()) { t ->
                        val shape = RoundedCornerShape(20.dp)
                        Text(
                            st.tabName(t), color = p.fg,
                            modifier = Modifier.clip(shape)
                                .background(if (t == tab) Color(0x886C5CE7) else p.glass)
                                .border(1.dp, p.stroke, shape)
                                .clickable { tab = t }
                                .padding(16.dp, 8.dp)
                        )
                    }
                    item { IconButton(onClick = { addTab = true }) { Icon(Icons.Rounded.Add, st.t("new_tab"), tint = p.fg) } }
                }
                val list = st.novels.filter { it.tab == tab }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.id }) { n -> Banner(n, st, p) { go(Screen.Chapters(n.id)) } }
                }
            }
            1 -> SitesPage(st, p, onDownload)
            else -> SettingsScreen(st, p)
        }

        // нижняя стеклянная панель
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                .glass(p, RoundedCornerShape(34.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavItem(Icons.Rounded.Home, st.t("library"), page == 0, p) { page = 0 }
            NavItem(Icons.Rounded.Language, st.t("sites"), page == 1, p) { page = 1 }
            NavItem(Icons.Rounded.Settings, st.t("settings"), page == 2, p) { page = 2 }
        }

        if (page == 0) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).glass(p, CircleShape)
                    .clickable { picker.launch(arrayOf("*/*")) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, st.t("add"), tint = p.fg, modifier = Modifier.size(30.dp)) }
        }
    }

    if (addTab) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTab = false },
            title = { Text(st.t("new_tab")) },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, label = { Text(st.t("name")) }) },
            confirmButton = {
                TextButton(onClick = {
                    val x = t.trim()
                    if (x.isNotEmpty() && !x.startsWith("@") && x !in st.tabs) { st.tabs.add(x); st.save() }
                    addTab = false
                }) { Text(st.t("add")) }
            },
            dismissButton = { TextButton(onClick = { addTab = false }) { Text(st.t("cancel")) } }
        )
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, sel: Boolean, p: Pal, click: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(26.dp))
            .background(if (sel) Color(0x556C5CE7) else Color.Transparent)
            .clickable { click() }.padding(14.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = p.fg)
        AnimatedVisibility(sel) { Text(label, color = p.fg, modifier = Modifier.padding(start = 8.dp)) }
    }
}

@Composable
fun Banner(n: Novel, st: Store, p: Pal, open: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf(false) }
    val bmp = remember(n.cover) {
        n.cover?.let { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.padding(6.dp).height(220.dp).clip(shape).border(1.dp, p.stroke, shape)
            .combinedClickable(onClick = open, onLongClick = { menu = true })
    ) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Color(n.color)))
        Text(
            n.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(8.dp)
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false; sub = false }) {
            DropdownMenuItem(text = { Text(st.t("delete")) }, onClick = { menu = false; st.delete(n) })
            DropdownMenuItem(text = { Text(st.t("add_to") + "  ▸") }, onClick = { sub = !sub })
            if (sub) st.tabs.filter { it != n.tab }.forEach { t ->
                DropdownMenuItem(text = { Text("      → " + st.tabName(t)) }, onClick = {
                    menu = false; sub = false
                    st.update(n, n.copy(tab = t, rev = n.rev))
                })
            }
        }
    }
}

// ---------- Сайты (встроенный браузер) ----------
@Composable
fun SitesPage(st: Store, p: Pal, onDownload: (String, String, String, String) -> Unit) {
    var open by remember { mutableStateOf<Site?>(null) }
    var add by remember { mutableStateOf(false) }
    val web = remember { arrayOfNulls<WebView>(1) }

    BackHandler(open != null) {
        val w = web[0]
        if (w != null && w.canGoBack()) w.goBack() else open = null
    }

    val o = open
    if (o != null) {
        Column(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.Rounded.Close, null, tint = p.fg) }
                Text(o.name, color = p.fg, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        setDownloadListener { u, ua, cd, m, _ -> onDownload(u, ua, cd, m) }
                        loadUrl(o.url)
                        web[0] = this
                    }
                }
            )
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp, 16.dp, 16.dp, 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.t("sites"), color = p.fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { add = true }) { Icon(Icons.Rounded.Add, st.t("add_site"), tint = p.fg) }
            }
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(st.sites.toList()) { s ->
                    Row(
                        Modifier.fillMaxWidth().glass(p, RoundedCornerShape(20.dp)).clickable { open = s }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = p.fg)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(s.name, color = p.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(s.url, color = p.fg.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { st.sites.remove(s); st.save() }) { Icon(Icons.Rounded.Delete, st.t("delete"), tint = p.fg) }
                    }
                }
            }
        }
    }

    if (add) {
        var n by remember { mutableStateOf("") }
        var u by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text(st.t("add_site")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it }, singleLine = true, label = { Text(st.t("name")) })
                    OutlinedTextField(u, { u = it }, singleLine = true, label = { Text(st.t("url")) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = u.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    if (u.isNotBlank()) { st.sites.add(Site(n.trim().ifEmpty { url }, url)); st.save() }
                    add = false
                }) { Text(st.t("add")) }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text(st.t("cancel")) } }
        )
    }
}

// ---------- Настройки ----------
@Composable
fun Section(p: Pal, title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    Column(Modifier.fillMaxWidth().glass(p, RoundedCornerShape(20.dp)).animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.fg)
            Text(title, color = p.fg, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = p.fg, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(16.dp, 0.dp, 16.dp, 16.dp), content = content)
        }
    }
}

@Composable
fun ThemeCard(pal: Pal, name: String, sel: Boolean, click: () -> Unit) {
    val sc by animateFloatAsState(if (sel) 1.08f else 1f, label = "sc")
    val bw by animateDpAsState(if (sel) 3.dp else 1.dp, label = "bw")
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.scale(sc).width(96.dp).clip(shape)
            .border(bw, if (sel) Color(0xFF6C5CE7) else pal.stroke, shape).clickable(onClick = click)
    ) {
        Box(
            Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(pal.bg, pal.bg2))),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(60.dp, 26.dp).glass(pal, RoundedCornerShape(13.dp)))
            if (sel) Icon(Icons.Rounded.Check, null, tint = pal.fg, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(name, color = pal.fg, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(pal.bg).padding(8.dp))
    }
}

@Composable
fun SettingsScreen(st: Store, p: Pal) {
    val act = requireNotNull(LocalActivity.current) { "Activity not found" }
    val scope = rememberCoroutineScope()
    fun toast(s: String) = Toast.makeText(act, s, Toast.LENGTH_LONG).show()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp, 24.dp, 24.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(st.t("settings"), color = p.fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        // аккаунт
        Column(Modifier.fillMaxWidth().glass(p, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountCircle, null, tint = p.fg)
                Text(st.t("account"), color = p.fg, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
            }
            if (st.user == null) {
                Text(st.t("about_cloud"), color = p.fg.copy(alpha = 0.7f), fontSize = 13.sp)
                Button(onClick = {
                    scope.launch {
                        try {
                            Cloud.signIn(act)
                            st.user = Cloud.auth.currentUser?.email
                            Cloud.sync(st, true)
                            toast(st.t("synced"))
                        } catch (e: Exception) { toast("${st.t("error")}: ${e.message}") }
                    }
                }) { Text(st.t("sign_in")) }
            } else {
                Text("${st.t("signed_as")}: ${st.user}", color = p.fg)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !st.syncing, onClick = {
                        scope.launch {
                            try { Cloud.sync(st, false); toast(st.t("synced")) }
                            catch (e: Exception) { toast("${st.t("error")}: ${e.message}") }
                        }
                    }) { Text(st.t("sync_now")) }
                    OutlinedButton(onClick = { Cloud.auth.signOut(); st.user = null }) { Text(st.t("sign_out")) }
                }
                if (st.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        // темы: раскрывается с анимацией, внутри карточки-превью
        Section(p, st.t("theme"), Icons.Rounded.Palette) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                itemsIndexed(pals) { i, pal ->
                    ThemeCard(pal, st.t(pal.key), st.theme == i) { st.theme = i; st.save() }
                }
            }
        }

        // язык
        Section(p, st.t("language"), Icons.Rounded.Language) {
            L.names.forEachIndexed { i, nm ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { st.lang = i; st.save() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(nm, color = p.fg, modifier = Modifier.weight(1f))
                    if (st.lang == i) Icon(Icons.Rounded.Check, null, tint = p.fg)
                }
            }
        }

        // настройки текста
        Section(p, st.t("reader"), Icons.Rounded.TextFields) { ReaderOptions(st, p) }
    }
}

// ---------- Список глав ----------
@Composable
fun ChaptersScreen(st: Store, p: Pal, id: String, go: (Screen) -> Unit) {
    val n = st.novels.firstOrNull { it.id == id }
    if (n == null) {
        LaunchedEffect(Unit) { go(Screen.Main) }
        return
    }
    val ctx = LocalContext.current
    val chapters = remember(id, n.rev) { st.chapters(id) }
    var menu by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val f = File(ctx.filesDir, "cover_${id}_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            n.cover?.let { File(it).delete() }
            st.update(n, n.copy(cover = f.path, rev = System.currentTimeMillis()))
        } catch (e: Exception) {
            Toast.makeText(ctx, st.t("error"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { go(Screen.Main) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, st.t("back"), tint = p.fg) }
            Text(n.title, color = p.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = p.fg) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(st.t("change_cover")) }, onClick = { menu = false; coverPicker.launch(arrayOf("image/*")) })
                    DropdownMenuItem(text = { Text(st.t("delete_novel")) }, onClick = { menu = false; st.delete(n); go(Screen.Main) })
                }
            }
        }
        LazyColumn {
            itemsIndexed(chapters) { i, c ->
                Text(
                    c.title, color = p.fg, fontSize = 16.sp, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().clickable { go(Screen.Read(id, i)) }.padding(16.dp)
                )
                HorizontalDivider(color = p.stroke)
            }
        }
    }
}

// ---------- Чтение ----------
val fonts = listOf(
    "Default" to FontFamily.Default, "Serif" to FontFamily.Serif, "Sans" to FontFamily.SansSerif,
    "Mono" to FontFamily.Monospace, "Cursive" to FontFamily.Cursive
)

@Composable
fun Sl(name: String, v: Float, r: ClosedFloatingPointRange<Float>, col: Color, set: (Float) -> Unit) {
    Text("$name: ${"%.1f".format(v)}", color = col)
    Slider(value = v, onValueChange = set, valueRange = r)
}

@Composable
fun ReaderOptions(st: Store, p: Pal) {
    val c = st.cfg
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(st.t("font").uppercase(), color = p.fg.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fonts.forEachIndexed { i, f ->
                FilterChip(selected = c.font == i, onClick = { st.cfg = c.copy(font = i) }, label = { Text(f.first) })
            }
        }
        Sl(st.t("size"), c.size, 10f..40f, p.fg) { st.cfg = c.copy(size = it) }
        Sl(st.t("letter"), c.spacing, 0f..5f, p.fg) { st.cfg = c.copy(spacing = it) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.stroke)
        Text(st.t("paragraph").uppercase(), color = p.fg.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Sl(st.t("line"), c.line, 1f..2.5f, p.fg) { st.cfg = c.copy(line = it) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.stroke)
        Text(st.t("page").uppercase(), color = p.fg.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Sl(st.t("margin"), c.margin, 0f..80f, p.fg) { st.cfg = c.copy(margin = it) }
    }
}

@Composable
fun PageImg(path: String) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
            catch (e: Exception) { null }
        }
    }
    bmp?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
}

@Composable
fun ReadScreen(st: Store, p: Pal, id: String, start: Int, go: (Screen) -> Unit) {
    val chapters = remember(id) { st.chapters(id) }
    var idx by remember { mutableIntStateOf(start.coerceIn(0, maxOf(0, chapters.size - 1))) }
    var sheet by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(idx) { scroll.scrollTo(0) }
    if (chapters.isEmpty()) { Text("—", color = p.fg); return }
    val c = st.cfg
    val cur = chapters[idx]

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { go(Screen.Chapters(id)) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, st.t("back"), tint = p.fg) }
            Text(cur.title, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { sheet = true }) { Icon(Icons.Rounded.TextFields, st.t("reader"), tint = p.fg) }
        }
        if (cur.text.startsWith("IMG\n")) {
            // манга: страницы-картинки сверху вниз
            key(idx) { LazyColumn(Modifier.weight(1f)) { items(cur.text.lines().drop(1)) { PageImg(it) } } }
        } else {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = c.margin.dp, vertical = 8.dp)) {
                Text(
                    cur.text, color = p.fg, fontSize = c.size.sp, lineHeight = (c.size * c.line).sp,
                    letterSpacing = c.spacing.sp, fontFamily = fonts[c.font.coerceIn(0, fonts.lastIndex)].second
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = idx > 0, onClick = { idx-- }) { Text("◀ " + st.t("back")) }
            Text("${idx + 1} / ${chapters.size}", color = p.fg)
            TextButton(enabled = idx < chapters.size - 1, onClick = { idx++ }) { Text(st.t("next") + " ▶") }
        }
    }

    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false; st.save() }) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            ReaderOptions(st, p)
            Spacer(Modifier.height(24.dp))
        }
    }
}