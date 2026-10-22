package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceTrackingTest {
    private val page = PageText("Kindle Paperwhite", "https://www.amazon.com/dp/B0GVTPKJVT", "Kindle Paperwhite 16GB. Price: $1,159.99 with free delivery. Other: $24.99")

    @Test
    fun readsPriceThatIsOnThePage() {
        val p = parseProductReply("""Sure! {"title": "Kindle Paperwhite", "price": 1159.99, "currency": "usd"}""", page)
        assertEquals(1159.99, p!!.price, 0.001)
        assertEquals("USD", p.currency)
        assertEquals("Amazon", p.store)
    }

    @Test
    fun dropsMadeUpPrices() {
        assertNull(parseProductReply("""{"title": "Kindle", "price": 99.99}""", page))
        assertNull(parseProductReply("""{"price": null}""", page))
        assertFalse(priceOnPage(159.99, "costs 1159.99"))
        assertTrue(priceOnPage(24.99, page.text))
    }

    @Test
    fun onlyPriceQuestionsWithLinks() {
        assertTrue(wantsPrice("https://www.amazon.com/dp/B0GVTPKJVT show its current price"))
        assertFalse(wantsPrice("what is the price of gold"))
        assertFalse(wantsPrice("summarize https://example.com/article"))
    }

    @Test
    fun readsTheTypicalPriceFromThePage() {
        val deal = PageText("Fire HD 10", "https://www.amazon.com/dp/B0GVTPKJVT", "-53% $72.99 Typical price: $154.99 Buy new")
        assertEquals(154.99, parseProductReply("""{"title": "Fire HD 10", "price": 72.99}""", deal)?.listPrice ?: 0.0, 0.001)
        assertNull(parseProductReply("""{"title": "Kindle Paperwhite", "price": 1159.99}""", page)?.listPrice)
    }
}
