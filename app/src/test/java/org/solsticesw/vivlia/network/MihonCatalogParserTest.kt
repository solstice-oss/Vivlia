package org.solsticesw.vivlia.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.solsticesw.vivlia.data.network.MihonCatalogParser
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType

class MihonCatalogParserTest {

    private val sampleJson = """
        [
          {
            "name": "MangaDex",
            "pkg": "eu.kanade.tachiyomi.extension.all.mangadex",
            "apk": "tachiyomi-all.mangadex-v1.4.1.apk",
            "lang": "all",
            "code": 100,
            "version": "1.4.1",
            "nsfw": 0,
            "icon": "icon.png",
            "sources": [
              {
                "id": "7583921849",
                "name": "MangaDex",
                "baseUrl": "https://mangadex.org",
                "lang": "en"
              }
            ]
          },
          {
            "name": "Nhentai",
            "pkg": "eu.kanade.tachiyomi.extension.en.nhentai",
            "apk": "nhentai.apk",
            "lang": "en",
            "code": 101,
            "version": "1.0.1",
            "nsfw": 1,
            "icon": "icon_nhentai.png",
            "sources": [
              {
                "id": "12345",
                "name": "Nhentai",
                "baseUrl": "https://nhentai.net",
                "lang": "en",
                "isNsfw": true
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `parse valid json catalog`() {
        val dtos = MihonCatalogParser.parseCatalog(sampleJson)
        assertEquals(2, dtos.size)
        assertEquals("MangaDex", dtos[0].name)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", dtos[0].pkg)
        assertEquals(1, dtos[0].sources.size)
    }

    @Test
    fun `parse and map catalog into entities`() {
        val repoId = "keiyoushi_repo"
        val rawBaseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/main/"

        val (extensions, sources) = MihonCatalogParser.parseAndMap(sampleJson, repoId, rawBaseUrl)

        assertEquals(2, extensions.size)
        assertEquals(2, sources.size)

        val mangadexExt = extensions.find { it.pkgName == "eu.kanade.tachiyomi.extension.all.mangadex" }
        assertNotNull(mangadexExt)
        assertEquals("MangaDex", mangadexExt!!.name)
        assertEquals("1.4.1", mangadexExt.versionName)
        assertEquals(100, mangadexExt.versionCode)
        assertFalse(mangadexExt.isNsfw)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/main/icon.png", mangadexExt.iconUrl)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/main/tachiyomi-all.mangadex-v1.4.1.apk", mangadexExt.apkUrl)

        val mangadexSource = sources.find { it.extensionId == mangadexExt.id }
        assertNotNull(mangadexSource)
        assertEquals("MangaDex", mangadexSource!!.name)
        assertEquals("https://mangadex.org", mangadexSource.baseUrl)
        assertEquals(ProviderType.MIHON.name, mangadexSource.providerType)
        assertEquals(MediaType.MANGA.name, mangadexSource.mediaType)

        val nhentaiExt = extensions.find { it.pkgName == "eu.kanade.tachiyomi.extension.en.nhentai" }
        assertNotNull(nhentaiExt)
        assertTrue(nhentaiExt!!.isNsfw)
    }

    @Test
    fun `parse empty or malformed json`() {
        val emptyResult = MihonCatalogParser.parseCatalog("[]")
        assertTrue(emptyResult.isEmpty())

        val malformedResult = MihonCatalogParser.parseCatalog("{ invalid json }")
        assertTrue(malformedResult.isEmpty())
    }
}
