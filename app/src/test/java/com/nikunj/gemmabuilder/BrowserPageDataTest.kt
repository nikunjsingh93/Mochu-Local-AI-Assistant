package com.nikunj.gemmabuilder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageDataTest {
    @Test fun wikipediaActionsUseOnlyTheVerifiedMobileOrigin() {
        val result = JSONObject.quote("""[
            {"kind":"WikipediaMenu","label":"Wikipedia main menu","index":0,"documentTime":42.5}
        ]""")
        val wikipedia = "https://en.m.wikipedia.org/wiki/Android"
        assertEquals(listOf(BrowserActionKind.WikipediaSearch, BrowserActionKind.WikipediaMenu),
            browserActions(result, wikipedia).map { it.kind })
        assertTrue(browserActions(result, "https://en.m.wikipedia.org.example.com/wiki/Android").isEmpty())
        assertEquals("https://en.m.wikipedia.org/w/index.php?search=tea+%26+coffee",
            wikipediaSearchUrl(wikipedia, "tea & coffee"))
        assertNull(wikipediaSearchUrl("https://en.m.wikipedia.org:8443/wiki/Android", "tea"))
        assertNull(wikipediaSearchUrl(wikipedia, " "))
    }

    @Test fun extractsKnownTypesFromJsonLdGraph() {
        val data = productAndRecipeData(listOf(
            "invalid JSON",
            """{"@graph":[{"@type":["Thing","Product"],"name":"Tea","offers":{"price":"4.99","priceCurrency":"USD"}},
                {"@type":"https://schema.org/Recipe","name":"Iced tea","recipeIngredient":["2 cups water"],
                 "recipeInstructions":[{"@type":"HowToStep","text":"Brew the tea."}]},
                {"@type":"Person","name":"Private name"}]}"""
        ))
        assertTrue("Product: Tea" in data)
        assertTrue("Ingredient: 2 cups water" in data)
        assertTrue("Step: Brew the tea." in data)
        assertFalse("Private name" in data)
        val page = PageText("Tea", "https://example.com/tea", "2 cups water", data)
        assertTrue(structuredPriceOnPage(4.99, page))
        assertFalse(structuredPriceOnPage(2.0, page))
        assertTrue(page.sourceText(800).startsWith("Structured page data:"))
    }

    @Test fun onlyReturnsOrdinarySameOriginLinksAndRequiresExactSelection() {
        val result = JSONObject.quote("""[
            {"label":"Shipping","url":"https://example.com/shipping"},
            {"label":"Continue","url":"https://example.com/%63heckout"},
            {"label":"Pay now","url":"https://example.com/next"},
            {"label":"Results","url":"https://example.com/search?q=tea"},
            {"label":"Other origin","url":"https://other.example.com/shipping"},
            {"label":"Different port","url":"https://example.com:1234/shipping"}
        ]""")
        val links = browserActions(result, "https://example.com/home")
        assertEquals(listOf(BrowserAction("Shipping", "https://example.com/shipping")), links)
        assertEquals(links.single(), selectedBrowserAction("1", links))
        assertNull(selectedBrowserAction("Open 1", links))
        assertNull(selectedBrowserAction("0", links))
    }

    @Test fun allowsNativeControlsAndRejectsCredentialOrDestructiveLabels() {
        val page = "https://example.com/"
        val result = JSONObject.quote("""[
            {"kind":"Search","label":"Search articles","hint":"q search","index":0,"documentTime":42.5},
            {"kind":"Search","label":"Search","hint":"one-time-code","index":1,"documentTime":42.5},
            {"kind":"Disclosure","label":"Shipping information","index":2,"documentTime":42.5},
            {"kind":"Disclosure","label":"Delete account","index":3,"documentTime":42.5},
            {"kind":"Disclosure","label":"Old section","index":4},
            {"kind":"Submit","label":"Send","index":5,"documentTime":42.5}
        ]""")
        val actions = browserActions(result, page)
        assertEquals(listOf(BrowserActionKind.Search, BrowserActionKind.Disclosure), actions.map { it.kind })
        assertEquals(listOf(0, 2), actions.map { it.index })
        val value = """tea "leaves" \ (search)"""
        val script = browserControlScript(actions.first(), value)
        assertTrue(script.contains(JSONObject.quote(value)))
        assertTrue(script.contains(JSONObject.quote(page)))
    }
}
