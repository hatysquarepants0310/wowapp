package com.azeroth.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El lector de noticias interpreta el HTML de la web oficial de World of
 * Warcraft, porque Blizzard no publica RSS ni API de noticias. Estas pruebas
 * fijan el contrato con ese marcado: si Blizzard lo cambia, falla aquí y no en
 * el móvil del usuario.
 *
 * El HTML de las pruebas es una reducción del real, con la misma estructura de
 * clases.
 */
class NewsParsingTest {

    private val riverHtml = """
        <div class="List-item"><article class="NewsBlog"><div class="NewsBlog-content">
        <img class="NewsBlog-image" src="data:image/gif;base64,R0lGOD" data-src="//cms.example/thumb.png"/>
        <div class="NewsBlog-title">WoW Weekly: Curse of Ula&#39;tek</div>
        <p class="NewsBlog-desc color-beige-medium font-size-xSmall">Cada semana trae aventuras.</p>
        <div class="NewsBlog-date LocalizedDateMount" data-props="{&quot;iso8601&quot;:&quot;2026-08-07T17:00:00.000Z&quot;,&quot;relative&quot;:true}">2 days ago</div>
        <a class="Link NewsBlog-link" href="/news/24295089/wow-weekly-curse-of-ulatek"></a>
        </div></article></div>
        <div class="List-item"><article class="NewsBlog"><div class="NewsBlog-content">
        <div class="NewsBlog-title">Segunda noticia</div>
        <a class="Link NewsBlog-link" href="/news/24290000/segunda"></a>
        </div></article></div>
    """.trimIndent()

    @Test
    fun `lee el listado de noticias`() {
        val items = NewsHtml.parseHeadlines(riverHtml)
        assertEquals(2, items.size)
        val first = items.first()
        assertEquals("24295089", first.id)
        assertEquals("WoW Weekly: Curse of Ula'tek", first.title)
        assertEquals("Cada semana trae aventuras.", first.summary)
        assertEquals("https://cms.example/thumb.png", first.imageUrl)
        assertEquals("2026-08-07T17:00:00Z", first.publishedAt.toString())
    }

    /**
     * El cuerpo del artículo lleva divs anidados (cada imagen va en el suyo),
     * así que recortarlo con una expresión regular perezosa cortaba en la
     * primera imagen y se perdía medio artículo. Se recorta contando
     * profundidad.
     */
    @Test
    fun `recorta el cuerpo con divs anidados`() {
        val html = """
            <div class="detail"><p>Primero</p>
            <aside><div class="image"><img src="https://cms.example/a.png" /></div></aside>
            <h2>Un titular</h2><p>Segundo</p></div><div class="footer"><p>Pie</p></div>
        """.trimIndent()
        val body = NewsHtml.extractDetail(html)
        assertTrue(body.contains("Segundo"))
        assertTrue("no debe arrastrar el pie de página", !body.contains("Pie"))
    }

    @Test
    fun `convierte el artículo en bloques en orden`() {
        val body = """
            <p>Intro del artículo</p>
            <hr class="image-divider" />
            <h2><span style="color:#DAA520;">Un titular</span></h2>
            <div class="image"><img src="https://cms.example/a.png" /></div>
            <p>Cuerpo con &amp; entidades y &rsquo;comillas&rsquo;.</p>
            <p style="text-align: center;"><a class="btn btn-alternate" href="/news/1">Saber más</a></p>
        """.trimIndent()
        val blocks = NewsHtml.parseBlocks(body)

        assertEquals(NewsBlock.Paragraph("Intro del artículo"), blocks[0])
        assertEquals(NewsBlock.Rule, blocks[1])
        assertEquals(NewsBlock.Heading("Un titular"), blocks[2])
        assertEquals(NewsBlock.Image("https://cms.example/a.png"), blocks[3])
        assertEquals(
            NewsBlock.Paragraph("Cuerpo con & entidades y ’comillas’."),
            blocks[4],
        )
        assertEquals(
            NewsBlock.Link("Saber más", "https://worldofwarcraft.blizzard.com/news/1"),
            blocks[5],
        )
    }

    /**
     * Los artículos de Blizzard empiezan con un `<p></p>` vacío y separan las
     * secciones con `<hr>`. Ni los párrafos en blanco ni un separador que no
     * tiene nada encima aportan nada al lector, así que se caen.
     */
    @Test
    fun `descarta bloques vacíos y separadores sin contenido encima`() {
        val blocks = NewsHtml.parseBlocks("<p></p><hr /><hr /><p>   </p><p>Algo</p>")
        assertEquals(listOf(NewsBlock.Paragraph("Algo")), blocks)
    }

    /** Un separador entre dos secciones sí se conserva: ahí sí ordena. */
    @Test
    fun `conserva el separador entre dos secciones`() {
        val blocks = NewsHtml.parseBlocks("<p>Uno</p><hr /><hr /><p>Dos</p>")
        assertEquals(
            listOf(
                NewsBlock.Paragraph("Uno"),
                NewsBlock.Rule,
                NewsBlock.Paragraph("Dos"),
            ),
            blocks,
        )
    }
}
