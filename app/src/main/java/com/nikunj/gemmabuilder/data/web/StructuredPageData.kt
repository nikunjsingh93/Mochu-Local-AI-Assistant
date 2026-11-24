package com.nikunj.gemmabuilder

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun structuredPriceOnPage(price: Double, page: PageText): Boolean =
    page.structuredData.lineSequence().any {
        it.startsWith("price: ") && it.removePrefix("price: ").toDoubleOrNull() == price
    }

/** Keep only fields used by cards. JSON-LD is page-supplied data and can be malformed. */
internal fun productAndRecipeData(scripts: List<String>): String {
    val fields = mutableListOf<String>()
    fun add(label: String, value: Any?) {
        if (value is String || value is Number) {
            val text = value.toString().replace(whitespace, " ").trim().take(240)
            if (text.isNotEmpty()) fields += "$label: $text"
        }
    }
    fun addSteps(value: Any?, depth: Int) {
        if (depth > 4 || fields.size >= 60) return
        when (value) {
            is String -> add("Step", value)
            is JSONArray -> (0 until minOf(value.length(), 20)).forEach { addSteps(value.opt(it), depth + 1) }
            is JSONObject -> {
                add("Step", value.opt("text"))
                addSteps(value.opt("itemListElement"), depth + 1)
            }
        }
    }
    fun visit(value: Any?, depth: Int) {
        if (depth > 6 || fields.size >= 60) return
        when (value) {
            is JSONArray -> (0 until minOf(value.length(), 30)).forEach { visit(value.opt(it), depth + 1) }
            is JSONObject -> {
                val types = when (val type = value.opt("@type")) {
                    is JSONArray -> (0 until minOf(type.length(), 8)).map { type.optString(it) }
                    is String -> listOf(type)
                    else -> emptyList()
                }.map { it.substringAfterLast('/').substringAfterLast(':') }
                if ("Product" in types) {
                    add("Product", value.opt("name"))
                    val offers = value.opt("offers")
                    val offer = (offers as? JSONObject) ?: (offers as? JSONArray)?.optJSONObject(0)
                    offer?.let {
                        add("priceCurrency", it.opt("priceCurrency"))
                        add("price", it.opt("price"))
                    }
                }
                if ("Recipe" in types) {
                    add("Recipe", value.opt("name"))
                    add("totalTime", value.opt("totalTime"))
                    add("recipeYield", value.opt("recipeYield"))
                    value.optJSONArray("recipeIngredient")?.let { ingredients ->
                        (0 until minOf(ingredients.length(), 20)).forEach { index ->
                            val ingredient = (ingredients.opt(index) as? String).orEmpty().replace(whitespace, " ").trim().take(160)
                            if (ingredient.isNotEmpty()) fields += "Ingredient: $ingredient"
                        }
                    }
                    addSteps(value.opt("recipeInstructions"), 0)
                }
                visit(value.opt("@graph"), depth + 1)
            }
        }
    }
    scripts.take(16).filter { it.length <= 32_000 }.forEach { script ->
        runCatching { JSONTokener(script).nextValue() }.getOrNull()?.let { visit(it, 0) }
    }
    return fields.distinct().joinToString("\n").take(2400)
}

private val whitespace = Regex("""\s+""")
