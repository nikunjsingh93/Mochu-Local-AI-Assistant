package com.nikunj.gemmabuilder

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebToolsTest {
    private val ddgHtml = """
        <div class="result results_links result--ad">
          <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fduckduckgo.com%2Fy.js%3Fad_domain%3Dads.example&amp;rut=1">Sponsored deal</a>
          <a class="result__snippet">Buy now</a>
        </div>
        <div class="result results_links">
          <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.bestbuy.com%2Fsite%2Flaptops%3Fid%3D1&amp;rut=2">Laptops on Sale</a>
          <a class="result__snippet">Shop laptop deals.</a>
        </div>
        <div class="result results_links">
          <a class="result__a" href="https://example.org/review">Best laptops reviewed</a>
          <a class="result__snippet">Our picks.</a>
        </div>
    """.trimIndent()

    @Test
    fun parsesOrganicResultsAndDropsAds() {
        val results = parseDuckDuckGoResults(ddgHtml)
        assertEquals(2, results.size)
        assertEquals(SearchResult("Laptops on Sale", "https://www.bestbuy.com/site/laptops?id=1", "Shop laptop deals."), results[0])
        assertEquals("https://example.org/review", results[1].url)
    }

    @Test
    fun unwrapsRedirectLinks() {
        assertEquals("https://a.com/x?y=1", unwrapDuckDuckGoUrl("//duckduckgo.com/l/?uddg=https%3A%2F%2Fa.com%2Fx%3Fy%3D1&amp;rut=abc"))
        assertEquals(null, unwrapDuckDuckGoUrl("javascript:alert(1)"))
    }

    @Test
    fun policyAllowsPublicWebOnly() {
        assertTrue(WebPolicy.isAllowed("https://www.bestbuy.com/site/laptops"))
        assertTrue(WebPolicy.isAllowed("http://example.org"))
        assertFalse(WebPolicy.isAllowed("file:///sdcard/secret.txt"))
        assertFalse(WebPolicy.isAllowed("content://com.android.contacts/data"))
        assertFalse(WebPolicy.isAllowed("http://localhost:8080"))
        assertFalse(WebPolicy.isAllowed("http://127.0.0.1/admin"))
        assertFalse(WebPolicy.isAllowed("http://192.168.1.1"))
        assertFalse(WebPolicy.isAllowed("http://10.0.0.5:9000"))
        assertFalse(WebPolicy.isAllowed("http://[::1]/"))
        assertFalse(WebPolicy.isAllowed("http://printer.local"))
    }

    @Test
    fun readableTextKeepsContentAndDropsChrome() {
        val doc = Jsoup.parse(
            """
            <html><head><title>T</title><script>var x=1</script></head><body>
            <nav>Home | Deals</nav>
            <main><h1>Laptop deals</h1><p>MacBook Air $799</p><ul><li>Dell XPS $999</li></ul></main>
            <footer>Copyright</footer>
            </body></html>
            """.trimIndent()
        )
        val text = readableText(doc)
        assertEquals(listOf("Laptop deals", "MacBook Air \$799", "Dell XPS \$999"), text.lines())
    }

    @Test
    fun turnsRequestsIntoQueries() {
        assertEquals("best buy laptop prices", searchQueryFrom("Search for best buy laptop prices please"))
        assertEquals("weather in Toronto", searchQueryFrom("look up weather in Toronto?"))
        assertEquals("Pixel 10 review", searchQueryFrom("Pixel 10 review"))
    }

    @Test
    fun detectsSearchRequests() {
        assertTrue(looksLikeSearchRequest("search best buy laptop prices"))
        assertTrue(looksLikeSearchRequest("Can you search the web for flights to Lisbon"))
        assertFalse(looksLikeSearchRequest("Write a poem about the sea"))
    }

    @Test
    fun activityLogRoundTrips() {
        val steps = listOf(
            ActivityStep(ACTIVITY_SEARCH, "Searched \"laptops\" · 8 results"),
            ActivityStep(ACTIVITY_READ, "Read bestbuy.com", "https://www.bestbuy.com/site/laptops"),
            ActivityStep(ACTIVITY_SKIP, "Couldn't read example.org", "https://example.org")
        )
        assertEquals(steps, decodeActivity(encodeActivity(steps)))
        assertEquals(emptyList<ActivityStep>(), decodeActivity(""))
    }

    @Test
    fun flagsNumbersNotInSources() {
        val sources = listOf("MacBook Air M4 now \$799.00, Dell XPS 13 for \$1,099.99 (2026 model)")
        val answer = "- **MacBook Air**: \$799.00 [1]\n- **Dell XPS 13**: \$1,099.99 [1]\n- **HP OmniBook**: 1699.9999 [2]\n- Released in 2026, with 16 GB."
        assertEquals(listOf("1699.9999"), unverifiedNumbers(answer, sources))
    }

    @Test
    fun numberCheckMatchesWholeAmountsOnly() {
        val sources = listOf("Chromebooks from \$199. Save \$100 on gaming laptops. MacBook Air \$699.00, now 1,299.50 off.")
        val answer = "- Save \$10 on gaming [1]\n- MacBook Air: \$699 [1]\n- From \$19 [1]\n- Chromebooks from \$199\n- 1299.5 and 1,299.50"
        assertEquals(listOf("\$10", "\$19", "1299.5"), unverifiedNumbers(answer, sources))
    }

    @Test
    fun detectsBotCheckPages() {
        assertTrue(looksBlocked(PageText("Just a moment...", "https://x.com", "Checking your browser")))
        assertFalse(looksBlocked(PageText("Laptop deals", "https://x.com", "MacBook Air $799")))
    }
}
