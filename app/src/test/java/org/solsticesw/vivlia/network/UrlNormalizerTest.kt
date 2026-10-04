package org.solsticesw.vivlia.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.solsticesw.vivlia.data.network.UrlNormalizer

class UrlNormalizerTest {

    @Test
    fun `normalize github repository main page`() {
        val input = "https://github.com/keiyoushi/extensions"
        val result = UrlNormalizer.normalize(input)

        assertEquals("https://github.com/keiyoushi/extensions", result.originalUrl)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json", result.indexUrl)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/main/", result.rawBaseUrl)
        assertTrue(result.repoId.contains("keiyoushi"))
    }

    @Test
    fun `normalize github repository tree page`() {
        val input = "https://github.com/user/repo/tree/master/extensions"
        val result = UrlNormalizer.normalize(input)

        assertEquals("https://raw.githubusercontent.com/user/repo/master/extensions/index.json", result.indexUrl)
        assertEquals("https://raw.githubusercontent.com/user/repo/master/extensions/", result.rawBaseUrl)
    }

    @Test
    fun `normalize github repository blob page with plugins min json`() {
        val input = "https://github.com/lnreader/lnreader-sources/blob/main/plugins.min.json"
        val result = UrlNormalizer.normalize(input, defaultIndexFile = "plugins.min.json")

        assertEquals("https://raw.githubusercontent.com/lnreader/lnreader-sources/main/plugins.min.json", result.indexUrl)
        assertEquals("https://raw.githubusercontent.com/lnreader/lnreader-sources/main/", result.rawBaseUrl)
    }

    @Test
    fun `normalize direct raw url`() {
        val input = "https://raw.githubusercontent.com/user/repo/main/index.min.json"
        val result = UrlNormalizer.normalize(input)

        assertEquals("https://raw.githubusercontent.com/user/repo/main/index.min.json", result.indexUrl)
        assertEquals("https://raw.githubusercontent.com/user/repo/main/", result.rawBaseUrl)
    }

    @Test
    fun `normalize url without http scheme and trailing slash`() {
        val input = "github.com/user/repo/"
        val result = UrlNormalizer.normalize(input)

        assertEquals("https://raw.githubusercontent.com/user/repo/main/index.json", result.indexUrl)
    }
}
