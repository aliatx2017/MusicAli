package com.musicali.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapingRepositoryParserTest {

    private fun loadFixture(filename: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$filename")!!
            .bufferedReader()
            .readText()

    @Test
    fun parseArtists_indietronica_extractsAllNames() {
        val html = loadFixture("everynoise-indietronica.html")
        val artists = ScrapingRepositoryImpl.parseArtists(html)
        assertEquals(5, artists.size)
        assertEquals("Ellie Goulding", artists[0])
        assertEquals("Glass Animals", artists[1])
        assertEquals("MGMT", artists[2])
        assertEquals("Tame Impala", artists[3])
        assertEquals("Washed Out", artists[4])
    }

    @Test
    fun parseArtists_nudisco_extractsAllNames() {
        val html = loadFixture("everynoise-nudisco.html")
        val artists = ScrapingRepositoryImpl.parseArtists(html)
        assertEquals(4, artists.size)
        assertEquals("Dua Lipa", artists[0])
        assertEquals("Jessie Ware", artists[1])
        assertEquals("Purple Disco Machine", artists[2])
        assertEquals("Roisin Murphy", artists[3])
    }

    @Test
    fun parseArtists_indiesoul_extractsAllNames() {
        val html = loadFixture("everynoise-indiesoul.html")
        val artists = ScrapingRepositoryImpl.parseArtists(html)
        assertEquals(3, artists.size)
        assertEquals("Hiatus Kaiyote", artists[0])
        assertEquals("Erykah Badu", artists[1])
        assertEquals("Jordan Rakei", artists[2])
    }

    @Test
    fun parseArtists_emptyHtml_returnsEmptyList() {
        val html = "<html><body><div class=\"other\">No genre divs here</div></body></html>"
        val artists = ScrapingRepositoryImpl.parseArtists(html)
        assertTrue(artists.isEmpty())
    }

    @Test
    fun parseArtists_neverIncludesNavlinkSymbol() {
        val html = loadFixture("everynoise-indietronica.html")
        val artists = ScrapingRepositoryImpl.parseArtists(html)
        assertFalse("No artist name should contain >>", artists.any { it.contains(">>") })
        assertFalse("No artist name should contain raquo entity", artists.any { it.contains("»") })
    }
}
