package com.music.bitchord

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NavidromeLocaleStringsTest {
    private val resources = File(".").canonicalFile.let { root ->
        File(root, "app/src/main/res").takeIf(File::exists) ?: File(root, "src/main/res")
    }

    private fun keys(relativePath: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(resources, relativePath))
        val strings = document.getElementsByTagName("string")
        return (0 until strings.length).map { (strings.item(it) as Element).getAttribute("name") }.toSet()
    }

    @Test
    fun `Navidrome resources have Portuguese coverage and unique base keys`() {
        val forkKeys = keys("values/strings_navidrome.xml")
        val portugueseKeys = keys("values-pt/strings.xml") + keys("values-pt-rBR/strings.xml")
        assertTrue("Missing Portuguese Navidrome keys: ${forkKeys - portugueseKeys}", portugueseKeys.containsAll(forkKeys))
        val upstreamKeys = keys("values/strings.xml")
        assertTrue("Duplicate base keys: ${forkKeys intersect upstreamKeys}", (forkKeys intersect upstreamKeys).isEmpty())
    }
}
