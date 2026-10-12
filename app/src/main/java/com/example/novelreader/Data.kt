package com.example.novelreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

val coverColors = listOf(0xFF6C5CE7, 0xFF00B894, 0xFFE17055, 0xFF0984E3, 0xFFD63031)

data class Novel(
    val id: String, val title: String, val tab: String,
    val cover: String?, val color: Long, val rev: Long = 0L
)

data class Chapter(val title: String, val text: String)
data class Site(val name: String, val url: String)
data class ReaderCfg(
    val font: Int = 0, val size: Float = 18f, val line: Float = 1.4f,
    val margin: Float = 16f, val spacing: Float = 0f,
    val align: Int = 0, val bold: Boolean = false, val bg: Int = 0,
    val autoSpeed: Float = 60f, val autoNext: Boolean = false, val autoDelay: Float = 2f,
    val keepOn: Boolean = false
)

// ---- объединение глав одной серии ----
fun seriesOf(s: String): String {
    val x = s.replace('_', ' ')
        .replace(Regex("(?iu)[\\s\\-–—.,(\\[]+(chapter|chap|ch|глава|гл|розділ|том|volume|vol)\\.?\\s*\\d+.*$"), "")
        .replace(Regex("[\\s\\-–—.,]*\\d+\\s*$"), "")
        .trim()
    return x.ifEmpty { s.trim() }
}

fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

fun sortChapters(l: List<Chapter>): List<Chapter> {
    val n = l.map {
        (Regex("(?iu)(?:chapter|chap|ch|глава|гл|розділ)\\.?\\s*(\\d+(?:\\.\\d+)?)").find(it.title)?.groupValues?.get(1)
            ?: Regex("\\d+(?:\\.\\d+)?").find(it.title)?.value)?.toDoubleOrNull()
    }
    return if (n.all { it != null }) l.indices.sortedBy { n[it] }.map { l[it] } else l
}

class Store(private val ctx: Context) {
    private val file = File(ctx.filesDir, "data.json")
    val dir: File get() = ctx.filesDir

    val novels = mutableStateListOf<Novel>()
    val tabs = mutableStateListOf<String>()
    val sites = mutableStateListOf<Site>()
    var theme by mutableStateOf(0)
    var lang by mutableStateOf(0)            // 0 = English (по умолчанию)
    var user by mutableStateOf<String?>(null)
    var cfg by mutableStateOf(ReaderCfg())
    var syncing by mutableStateOf(false)

    fun t(k: String) = L.t(k, lang)
    fun tabName(x: String) = if (x.startsWith("@")) t(x.drop(1)) else x

    init {
        load()
        // перенос старых названий вкладок
        val fix = mapOf("Читаю" to "@reading", "Заброшено" to "@dropped", "Буду читать" to "@planned")
        for (i in tabs.indices) fix[tabs[i]]?.let { tabs[i] = it }
        for (i in novels.indices) fix[novels[i].tab]?.let { novels[i] = novels[i].copy(tab = it) }
        if (tabs.isEmpty()) tabs.addAll(listOf("@reading", "@dropped", "@planned"))
        if ("@downloaded" !in tabs) tabs.add("@downloaded")
        if ("@all" !in tabs) tabs.add(0, "@all")
        if (sites.isEmpty()) sites.addAll(
            listOf(
                Site("Project Gutenberg", "https://www.gutenberg.org"),
                Site("Standard Ebooks", "https://standardebooks.org"),
                Site("Wikisource", "https://wikisource.org"),
                Site("Internet Archive", "https://archive.org")
            )
        )
        user = runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            val ta = o.getJSONArray("tabs")
            for (i in 0 until ta.length()) tabs.add(ta.getString(i))
            theme = o.optInt("theme", 0)
            lang = o.optInt("lang", 0)
            o.optJSONObject("cfg")?.let { c ->
                cfg = ReaderCfg(
                    c.optInt("font", 0), c.optDouble("size", 18.0).toFloat(), c.optDouble("line", 1.4).toFloat(),
                    c.optDouble("margin", 16.0).toFloat(), c.optDouble("spacing", 0.0).toFloat(),
                    c.optInt("align", 0), c.optBoolean("bold", false), c.optInt("bg", 0),
                    c.optDouble("autoSpeed", 60.0).toFloat(), c.optBoolean("autoNext", false),
                    c.optDouble("autoDelay", 2.0).toFloat(), c.optBoolean("keepOn", false)
                )
            }
            val na = o.getJSONArray("novels")
            for (i in 0 until na.length()) {
                val n = na.getJSONObject(i)
                novels.add(
                    Novel(
                        n.getString("id"), n.getString("title"), n.getString("tab"),
                        n.optString("cover", "").ifEmpty { null }, n.optLong("color", 0xFF6C5CE7), n.optLong("rev", 0L)
                    )
                )
            }
            val sa = o.optJSONArray("sites")
            if (sa != null) for (i in 0 until sa.length()) {
                val s = sa.getJSONObject(i)
                sites.add(Site(s.getString("n"), s.getString("u")))
            }
        } catch (e: Exception) {
        }
    }

    fun save() {
        val o = JSONObject()
        o.put("tabs", JSONArray(tabs.toList()))
        o.put("theme", theme)
        o.put("lang", lang)
        o.put(
            "cfg", JSONObject().put("font", cfg.font).put("size", cfg.size.toDouble())
                .put("line", cfg.line.toDouble()).put("margin", cfg.margin.toDouble())
                .put("spacing", cfg.spacing.toDouble()).put("align", cfg.align).put("bold", cfg.bold)
                .put("bg", cfg.bg).put("autoSpeed", cfg.autoSpeed.toDouble()).put("autoNext", cfg.autoNext)
                .put("autoDelay", cfg.autoDelay.toDouble()).put("keepOn", cfg.keepOn)
        )
        val a = JSONArray()
        novels.forEach { n ->
            a.put(
                JSONObject().put("id", n.id).put("title", n.title).put("tab", n.tab)
                    .put("cover", n.cover ?: "").put("color", n.color).put("rev", n.rev)
            )
        }
        o.put("novels", a)
        val sa = JSONArray()
        sites.forEach { sa.put(JSONObject().put("n", it.name).put("u", it.url)) }
        o.put("sites", sa)
        file.writeText(o.toString())
    }

    fun update(old: Novel, new: Novel) {
        val i = novels.indexOfFirst { it.id == old.id }
        if (i >= 0) novels[i] = new
        save()
    }

    fun delete(n: Novel) {
        chapters(n.id).filter { it.text.startsWith("IMG\n") }.forEach { c ->
            c.text.lines().getOrNull(1)?.let { File(it).parentFile?.deleteRecursively() }
        }
        novels.removeAll { it.id == n.id }
        File(dir, "ch_${n.id}.json").delete()
        n.cover?.let { File(it).delete() }
        save()
        if (user != null) CoroutineScope(Dispatchers.IO).launch {
            try { Cloud.deleteNovel(n.id) } catch (e: Exception) { }
        }
    }

    fun saveChapters(id: String, ch: List<Chapter>) {
        val a = JSONArray()
        ch.forEach { a.put(JSONObject().put("t", it.title).put("x", it.text)) }
        File(dir, "ch_$id.json").writeText(a.toString())
    }

    fun chapters(id: String): List<Chapter> = try {
        val a = JSONArray(File(dir, "ch_$id.json").readText())
        List(a.length()) { val o = a.getJSONObject(it); Chapter(o.getString("t"), o.getString("x")) }
    } catch (e: Exception) {
        emptyList()
    }

    // Добавить скачанные главы в "Скачанные": главы одной серии сливаются в одну запись
    suspend fun addDownloaded(series: String, ch: List<Chapter>, cover: String? = null) {
        val key = norm(series)
        val cv = cover ?: ch.firstOrNull { it.text.startsWith("IMG\n") }?.text?.lines()?.getOrNull(1)
        val ex = novels.firstOrNull { it.tab == "@downloaded" && norm(it.title) == key }
        val now = System.currentTimeMillis()
        if (ex != null) {
            withContext(Dispatchers.IO) {
                val old = chapters(ex.id)
                saveChapters(ex.id, sortChapters(old + ch.filter { c -> old.none { it.title == c.title } }))
            }
            update(ex, ex.copy(rev = now, cover = ex.cover ?: cv))
        } else {
            val id = UUID.randomUUID().toString()
            withContext(Dispatchers.IO) { saveChapters(id, ch) }
            novels.add(Novel(id, series, "@downloaded", cv, coverColors[Random.nextInt(coverColors.size)], now))
            save()
        }
    }

    // Импорт файла. group = true: главы одной серии сливаются в одну запись.
    suspend fun import(uri: Uri, name: String, tab: String, group: Boolean) {
        val id = UUID.randomUUID().toString()
        val (t, ch0, cv) = withContext(Dispatchers.IO) { Parser.parse(ctx, uri, name, id) }
        val base = name.substringBeforeLast('.').replace('_', ' ').trim()
        val ch = if (ch0.size == 1) listOf(Chapter(base, ch0[0].text)) else ch0
        val series = if (group) seriesOf(if (ch0.size == 1) base else t) else t
        val key = norm(series)
        val ex = if (group) novels.firstOrNull { it.tab == tab && norm(it.title) == key } else null
        val now = System.currentTimeMillis()
        if (ex != null) {
            withContext(Dispatchers.IO) {
                val old = chapters(ex.id)
                saveChapters(ex.id, sortChapters(old + ch.filter { c -> old.none { it.title == c.title } }))
            }
            update(ex, ex.copy(rev = now, cover = ex.cover ?: cv))
        } else {
            withContext(Dispatchers.IO) { saveChapters(id, ch) }
            novels.add(Novel(id, series, tab, cv, coverColors[Random.nextInt(coverColors.size)], now))
            save()
        }
    }
}

object Parser {
    fun nameOf(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i)
        }
        return "novel.txt"
    }

    fun parse(ctx: Context, uri: Uri, fileName: String, id: String): Triple<String, List<Chapter>, String?> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        var title = fileName.substringBeforeLast('.')
        var cover: String? = null
        val ch = mutableListOf<Chapter>()

        ctx.contentResolver.openInputStream(uri)!!.use { s ->
            when (ext) {
                "fb2" -> {
                    val d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(s)
                    d.getElementsByTagName("book-title").item(0)?.textContent?.trim()
                        ?.takeIf { it.isNotEmpty() }?.let { title = it }
                    val body = d.getElementsByTagName("body").item(0) as Element
                    val kids = body.childNodes
                    var i = 1
                    for (k in 0 until kids.length) {
                        val e = kids.item(k) as? Element ?: continue
                        if (e.tagName != "section") continue
                        val t = e.getElementsByTagName("title").item(0)?.textContent?.trim()
                            .takeIf { !it.isNullOrEmpty() } ?: "Chapter $i"
                        val ps = e.getElementsByTagName("p")
                        val sb = StringBuilder()
                        for (j in 0 until ps.length) {
                            val p = ps.item(j)
                            if (p.parentNode.nodeName == "title") continue
                            sb.append(p.textContent.trim()).append("\n\n")
                        }
                        ch.add(Chapter(t, sb.toString().trim()))
                        i++
                    }
                    val cp = d.getElementsByTagName("coverpage").item(0) as? Element
                    val img = cp?.getElementsByTagName("image")?.item(0) as? Element
                    val h1 = img?.getAttribute("l:href") ?: ""
                    val h2 = img?.getAttribute("xlink:href") ?: ""
                    val href = h1.ifEmpty { h2 }.trimStart('#')
                    val bins = d.getElementsByTagName("binary")
                    for (b in 0 until bins.length) {
                        val be = bins.item(b) as Element
                        if (href.isNotEmpty() && be.getAttribute("id") == href) {
                            val bytes = android.util.Base64.decode(be.textContent.trim(), android.util.Base64.DEFAULT)
                            val f = File(ctx.filesDir, "cover_$id.jpg")
                            f.writeBytes(bytes)
                            cover = f.path
                        }
                    }
                }

                "epub" -> {
                    val items = mutableListOf<Pair<String, String>>()
                    ZipInputStream(s).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            if (!e.isDirectory && Regex("(?i).*\\.(x?html?)$").matches(e.name))
                                items.add(e.name to z.readBytes().toString(Charsets.UTF_8))
                            e = z.nextEntry
                        }
                    }
                    var k = 1
                    for ((_, h) in items.sortedBy { it.first }) {
                        val t = Regex("(?is)<title>(.*?)</title>").find(h)?.groupValues?.get(1)?.trim().orEmpty()
                        val clean = h.replace(Regex("(?is)<(script|style|head)[\\s\\S]*?</\\1>"), " ")
                        val txt = Html.fromHtml(clean, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        if (txt.length < 80) continue
                        ch.add(Chapter(if (t.isEmpty()) "Chapter $k" else t, txt))
                        k++
                    }
                }

                "cbz", "zip" -> { // манга: архив с картинками
                    val dir = File(ctx.filesDir, "pages_$id").apply { mkdirs() }
                    val files = mutableListOf<String>()
                    ZipInputStream(s).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            if (!e.isDirectory && Regex("(?i).*\\.(jpe?g|png|webp)$").matches(e.name)) {
                                val f = File(dir, e.name.replace('/', '_'))
                                f.outputStream().use { o -> z.copyTo(o) }
                                files.add(f.path)
                            }
                            e = z.nextEntry
                        }
                    }
                    val sorted = files.sortedWith(compareBy({ it.length }, { it }))
                    if (sorted.isNotEmpty()) {
                        ch.add(Chapter(title, "IMG\n" + sorted.joinToString("\n")))
                        cover = sorted.first()
                    }
                }

                else -> {
                    val t = s.readBytes().toString(Charsets.UTF_8)
                    var parts = Regex("(?imu)^(?=\\s*(Глава|Chapter|Часть)\\s)").split(t).filter { it.isNotBlank() }
                    if (parts.size < 2) parts = t.chunked(6000)
                    parts.forEachIndexed { i, p ->
                        val first = p.trim().lineSequence().first().trim()
                        ch.add(Chapter(if (first.length in 1..59) first else "Part ${i + 1}", p.trim()))
                    }
                }
            }
        }
        if (ch.isEmpty()) ch.add(Chapter("Text", "(empty or unsupported file)"))
        return Triple(title, ch, cover)
    }
}
