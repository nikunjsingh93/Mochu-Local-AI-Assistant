package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichCardsTest {
    @Test
    fun picksTheCardForTheRequest() {
        assertEquals(CardKind.Stock, cardKindFor("every day at 4pm check NVDA", hasPages = true, hasDocs = false))
        assertEquals(CardKind.Stock, cardKindFor("what's the bitcoin price today", hasPages = true, hasDocs = false))
        assertEquals(CardKind.News, cardKindFor("check the latest ai news", hasPages = true, hasDocs = false))
        assertEquals(CardKind.Sports, cardKindFor("did the Leafs win last night?", hasPages = true, hasDocs = false))
        assertEquals(CardKind.Place, cardKindFor("is Costco open now?", hasPages = true, hasDocs = false))
        assertEquals(CardKind.Recipe, cardKindFor("give me a recipe for banana bread", hasPages = false, hasDocs = false))
        assertEquals(CardKind.Document, cardKindFor("summarize this", hasPages = false, hasDocs = true))
        assertEquals(
            CardKind.Compare,
            cardKindFor("compare https://bestbuy.com/a and https://amazon.com/dp/b", hasPages = true, hasDocs = false)
        )
        assertNull(cardKindFor("tell me a joke", hasPages = false, hasDocs = false))
        // Without pages there's nothing to check a stock price against.
        assertNull(cardKindFor("check NVDA", hasPages = false, hasDocs = false))
    }

    @Test
    fun tickersNeedCapitalsAndAreNotCommonWords() {
        assertEquals("NVDA", tickerIn("check NVDA"))
        assertEquals("AAPL", tickerIn("price of \$AAPL"))
        assertNull(tickerIn("check the AI news"))
        assertNull(tickerIn("check nvda"))
    }

    @Test
    fun readsJsonInsideChatter() {
        val o = firstJsonObject("""Sure! Here it is: {"title": "Pancakes", "ingredients": ["2 eggs", "1 cup {flour}"], "x": {"y": 1}} hope it helps""")
        assertEquals("Pancakes", o?.getString("title"))
        assertEquals(2, o?.getJSONArray("ingredients")?.length())
        assertNull(firstJsonObject("no json here"))
    }

    @Test
    fun numbersMustBeOnThePage() {
        assertTrue(numberOnPage(4.5, "Rated 4.5 out of 5"))
        assertTrue(numberOnPage(1234.5, "NVDA 1,234.50 +2.1%"))
        assertFalse(numberOnPage(4.5, "Rated 14.5"))
        assertTrue(numberOnPage(3.0, "Leafs 3, Bruins 2"))
    }
}
