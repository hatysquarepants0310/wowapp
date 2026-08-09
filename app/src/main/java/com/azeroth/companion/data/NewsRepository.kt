package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.datastore.LanguagePref
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Una noticia en el listado. */
data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val publishedAt: Instant?,
    val path: String,
)

/** Un trozo de artículo ya interpretado, listo para pintar en nativo. */
sealed interface NewsBlock {
    data class Heading(val text: String) : NewsBlock
    data class Paragraph(val text: String) : NewsBlock
    data class Image(val url: String) : NewsBlock
    data class Link(val text: String, val url: String) : NewsBlock
    data object Rule : NewsBlock
}

data class NewsArticle(
    val title: String,
    val publishedAt: Instant?,
    val blocks: List<NewsBlock>,
    val url: String,
)

/**
 * Noticias oficiales de World of Warcraft, leídas dentro de la app.
 *
 * Blizzard no publica ni RSS ni API de noticias —lo comprobé endpoint por
 * endpoint: `/feed`, `/rss`, `/news/blog/rss` y compañía devuelven 404—, así que
 * la única vía es su propia web. A cambio, esa web se sirve renderizada en el
 * servidor con marcado estable (`NewsBlog-title`, `NewsBlog-date`, y el cuerpo
 * dentro de `<div class="detail">`), y tiene un endpoint de fragmento,
 * `/news/river.frag`, pensado para paginar: 30 kB en vez de los 800 kB de la
 * página entera.
 *
 * Solo se leen las noticias oficiales de Blizzard sobre su propio juego, en el
 * idioma del usuario, y cada artículo conserva el enlace a la fuente. No se
 * toca ningún otro sitio.
 */
@Singleton
class NewsRepository @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    private val locale: String
        get() {
            val tag = LanguagePref.read(context) ?: java.util.Locale.getDefault().language
            return if (tag.startsWith("es")) "es-mx" else "en-us"
        }

    private val base get() = "https://worldofwarcraft.blizzard.com/$locale"

    suspend fun headlines(page: Int = 1): Result<List<NewsItem>> = runCatching {
        NewsHtml.parseHeadlines(get("$base/news/river.frag?page=$page"))
    }

    suspend fun article(path: String): Result<NewsArticle> = runCatching {
        val url = if (path.startsWith("http")) path else "$base$path"
        NewsHtml.parseArticle(get(url), url)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            // Sin User-Agent de navegador el CDN de Blizzard responde una página
            // de bloqueo en lugar del contenido.
            .header("User-Agent", NewsHtml.USER_AGENT)
            .header("Accept-Language", if (locale == "es-mx") "es-MX,es;q=0.9" else "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} al leer las noticias")
            response.body?.string() ?: error("Respuesta vacía")
        }
    }

}

/**
 * Intérprete del HTML de la web oficial. Vive aparte de [NewsRepository] para
 * poder probarse en la JVM sin Android ni red: el contrato con el marcado de
 * Blizzard es justo lo que hay que fijar con pruebas.
 */
object NewsHtml {

    // ---- Interpretación del HTML ------------------------------------------
    //
    // Es deliberadamente tolerante: si Blizzard cambia una clase, se pierde ese
    // campo concreto y el resto sigue funcionando. Nada aquí puede lanzar por
    // un atributo que falte.

    fun parseHeadlines(html: String): List<NewsItem> =
        ARTICLE.findAll(html).mapNotNull { match ->
            val block = match.value
            val title = TITLE.find(block)?.groupValues?.get(1)?.let(::unescape)?.trim()
                ?: return@mapNotNull null
            val path = LINK.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            NewsItem(
                id = path.substringAfter("/news/").substringBefore('/'),
                title = title,
                summary = DESC.find(block)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty(),
                imageUrl = IMAGE.find(block)?.groupValues?.get(1)?.let(::absoluteUrl),
                publishedAt = ISO_DATE.find(block)?.groupValues?.get(1)?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                },
                path = path,
            )
        }.distinctBy { it.id }.toList()

    fun parseArticle(html: String, url: String): NewsArticle {
        val title = H1.find(html)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()
        val published = ISO_DATE.find(html)?.groupValues?.get(1)?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }
        return NewsArticle(title, published, parseBlocks(extractDetail(html)), url)
    }

    /**
     * Recorta el cuerpo del artículo contando la profundidad de `<div>`. Con una
     * expresión regular no basta: dentro del cuerpo hay divs anidados (cada
     * imagen va en el suyo), así que un `.*?</div>` cortaría en la primera
     * imagen y se perdería medio artículo.
     */
    fun extractDetail(html: String): String {
        val start = html.indexOf(DETAIL_MARKER)
        if (start < 0) return ""
        val from = start + DETAIL_MARKER.length
        var depth = 1
        var index = from
        while (index < html.length) {
            val open = html.indexOf("<div", index)
            val close = html.indexOf("</div", index)
            if (close < 0) break
            if (open in 0 until close) {
                depth++
                index = open + 4
            } else {
                depth--
                if (depth == 0) return html.substring(from, close)
                index = close + 5
            }
        }
        return html.substring(from)
    }

    fun parseBlocks(body: String): List<NewsBlock> {
        val blocks = mutableListOf<NewsBlock>()
        // Un solo recorrido en orden de aparición: así el artículo mantiene la
        // secuencia original de texto e imágenes en vez de agrupar por tipo.
        for (match in BLOCK.findAll(body)) {
            val tag = match.groupValues[1].lowercase()
            when {
                tag == "hr" -> blocks += NewsBlock.Rule
                tag == "img" -> {
                    IMG_SRC.find(match.value)?.groupValues?.get(1)?.let {
                        blocks += NewsBlock.Image(absoluteUrl(it))
                    }
                }
                tag.startsWith("h") -> {
                    val text = plainText(match.groupValues[2])
                    if (text.isNotBlank()) blocks += NewsBlock.Heading(text)
                }
                tag == "p" -> {
                    val inner = match.groupValues[2]
                    val button = BUTTON.find(inner)
                    if (button != null) {
                        val label = plainText(button.groupValues[2])
                        if (label.isNotBlank()) {
                            blocks += NewsBlock.Link(label, absoluteUrl(button.groupValues[1]))
                            continue
                        }
                    }
                    val text = plainText(inner)
                    if (text.isNotBlank()) blocks += NewsBlock.Paragraph(text)
                }
            }
        }
        // Dos separadores seguidos no aportan nada y se ven como un hueco raro.
        return blocks.filterIndexed { i, b ->
            b !is NewsBlock.Rule || (i > 0 && blocks.getOrNull(i - 1) !is NewsBlock.Rule)
        }
    }

    private fun absoluteUrl(raw: String): String = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://worldofwarcraft.blizzard.com$raw"
        else -> raw
    }

    private fun plainText(html: String): String = unescape(html.replace(TAG, "")).trim()

    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    private const val DETAIL_MARKER = """<div class="detail">"""

    private val ARTICLE = Regex("""<article class="NewsBlog".*?</article>""", RegexOption.DOT_MATCHES_ALL)
    private val TITLE = Regex("""<div class="NewsBlog-title">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val DESC = Regex("""<p class="NewsBlog-desc[^"]*">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val IMAGE = Regex("""NewsBlog-image"[^>]*data-src="([^"]+)"""")
    private val LINK = Regex("""class="Link NewsBlog-link" href="([^"]+)"""")
    private val ISO_DATE = Regex("""iso8601(?:&quot;|"):(?:&quot;|")([^"&]+)""")
    private val H1 = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK = Regex(
        """<(h[1-6]|p|hr|img)\b[^>]*>(?:(.*?)</\1>)?""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val IMG_SRC = Regex("""src="([^"]+)"""")
    private val BUTTON = Regex(
        """<a[^>]*class="btn[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val TAG = Regex("""<[^>]+>""")
}
