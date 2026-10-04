package org.solsticesw.vivlia.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.solsticesw.vivlia.data.network.LnReaderCatalogParser
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType

class LnReaderCatalogParserTest {

    private val sampleLnJson = """
        [
          {
            "id": "wuxiaworld",
            "name": "WuxiaWorld",
            "site": "https://www.wuxiaworld.com",
            "lang": "English",
            "version": "1.2.0",
            "icon": "src/en/wuxiaworld/icon.png",
            "url": "src/en/wuxiaworld/plugin.ts",
            "type": "novel"
          },
          {
            "id": "royalroad",
            "name": "RoyalRoad",
            "site": "https://www.royalroad.com",
            "lang": "English",
            "version": "2.0.1",
            "icon": "src/en/royalroad/icon.png",
            "path": "src/en/royalroad/plugin.js",
            "type": "novel"
          }
        ]
    """.trimIndent()

    @Test
    fun `parse valid lnreader plugin catalog`() {
        val dtos = LnReaderCatalogParser.parseCatalog(sampleLnJson)
        assertEquals(2, dtos.size)
        assertEquals("wuxiaworld", dtos[0].id)
        assertEquals("WuxiaWorld", dtos[0].name)
    }

    @Test
    fun `parse and map lnreader catalog into entities`() {
        val repoId = "lnreader_repo"
        val rawBaseUrl = "https://raw.githubusercontent.com/lnreader/lnreader-sources/main/"

        val (extensions, sources) = LnReaderCatalogParser.parseAndMap(sampleLnJson, repoId, rawBaseUrl)

        assertEquals(2, extensions.size)
        assertEquals(2, sources.size)

        val wuxiaExt = extensions.find { it.pkgName == "wuxiaworld" }
        assertNotNull(wuxiaExt)
        assertEquals("WuxiaWorld", wuxiaExt!!.name)
        assertEquals("1.2.0", wuxiaExt.versionName)
        assertEquals("https://raw.githubusercontent.com/lnreader/lnreader-sources/main/src/en/wuxiaworld/icon.png", wuxiaExt.iconUrl)
        assertEquals("https://raw.githubusercontent.com/lnreader/lnreader-sources/main/src/en/wuxiaworld/plugin.ts", wuxiaExt.apkUrl)

        val wuxiaSource = sources.find { it.extensionId == wuxiaExt.id }
        assertNotNull(wuxiaSource)
        assertEquals("WuxiaWorld", wuxiaSource!!.name)
        assertEquals("https://www.wuxiaworld.com", wuxiaSource.baseUrl)
        assertEquals(ProviderType.LN_READER.name, wuxiaSource.providerType)
        assertEquals(MediaType.NOVEL.name, wuxiaSource.mediaType)
    }

    @Test
    fun `parse empty or malformed json`() {
        val emptyResult = LnReaderCatalogParser.parseCatalog("[]")
        assertTrue(emptyResult.isEmpty())

        val malformedResult = LnReaderCatalogParser.parseCatalog("invalid json")
        assertTrue(malformedResult.isEmpty())
    }
}
