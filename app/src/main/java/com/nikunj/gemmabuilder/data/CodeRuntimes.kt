package com.nikunj.gemmabuilder

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Code projects beyond a single index.html: React + TypeScript (Vite layout) and Python. Both run
// offline in the preview WebView from runtimes bundled in assets/runtime (React + Babel, Pyodide).

enum class ProjectKind { Web, React, Python }

fun projectKind(root: File): ProjectKind {
    if (File(root, "src/main.tsx").exists() || File(root, "src/main.jsx").exists()) return ProjectKind.React
    val pkg = File(root, "package.json")
    if (pkg.exists() && runCatching { pkg.readText() }.getOrDefault("").contains("\"react\"")) return ProjectKind.React
    if (File(root, "main.py").exists() && !hasOwnIndexHtml(root)) return ProjectKind.Python
    return ProjectKind.Web
}

/** True when index.html is a page of the project's own, not the starter page every project begins with. */
private fun hasOwnIndexHtml(root: File): Boolean {
    val index = File(root, "index.html")
    return index.exists() && runCatching { index.readText() }.getOrDefault("").trim() != welcomeHtml().trim()
}

/**
 * For a project still on its starter page, sets up a React or Python project when the request asks
 * for one, so the model edits a working project instead of writing one from nothing. Returns the kind
 * it set up, or null when nothing changed.
 */
fun seedProjectForRequest(root: File, request: String): ProjectKind? {
    if (projectKind(root) != ProjectKind.Web || hasOwnIndexHtml(root)) return null
    val lower = request.lowercase(Locale.US)
    val files = when {
        Regex("""\breact\b|\btsx\b|\btypescript\b""").containsMatchIn(lower) -> reactStarterFiles()
        Regex("""\bpython\b|\.py\b|\bpy script\b""").containsMatchIn(lower) -> pythonStarterFiles()
        else -> return null
    }
    files.forEach { (path, content) -> File(root, path).apply { parentFile?.mkdirs() }.writeText(content) }
    return projectKind(root)
}

fun reactStarterFiles(): Map<String, String> = mapOf(
    "index.html" to """
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Mochu App</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
""".trimStart(),
    "src/main.tsx" to """
import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
""".trimStart(),
    "src/App.tsx" to """
import { useState } from 'react'

export default function App() {
  const [count, setCount] = useState(0)
  return (
    <main className="app">
      <h1>Mochu React app</h1>
      <button onClick={() => setCount(count + 1)}>Tapped {count} times</button>
    </main>
  )
}
""".trimStart(),
    "src/index.css" to """
* { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, sans-serif; background: #f6f7fb; color: #1d2130; }
.app { min-height: 100vh; display: grid; place-content: center; gap: 16px; text-align: center; padding: 24px; }
button { font: inherit; padding: 12px 20px; border: 0; border-radius: 999px; background: #5b5fe8; color: white; }
""".trimStart(),
    "package.json" to """
{
  "name": "mochu-app",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.4.0"
  }
}
""".trimStart(),
    "vite.config.ts" to """
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './'
})
""".trimStart(),
    "tsconfig.json" to """
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["src"]
}
""".trimStart()
)

fun pythonStarterFiles(): Map<String, String> = mapOf(
    "main.py" to """
def main():
    name = "Mochu"
    print(f"Hello from {name}!")
    for i in range(1, 4):
        print(f"{i} squared is {i * i}")


if __name__ == "__main__":
    main()
""".trimStart()
)

/** The project's index.html with its module entry swapped for the bundled React runner. */
fun reactPreviewHtml(indexHtml: String): String {
    val moduleScript = Regex("""<script[^>]*type=["']module["'][^>]*src=["']([^"']+)["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
    val entry = moduleScript.find(indexHtml)?.groupValues?.get(1)?.trimStart('.', '/') ?: "src/main.tsx"
    val runtime = "https://appassets.androidplatform.net/runtime/react"
    val scripts = """
<script src="$runtime/react.production.min.js"></script>
<script src="$runtime/react-dom.production.min.js"></script>
<script src="$runtime/babel.min.js"></script>
<script>window.__MOCHU_ENTRY = ${org.json.JSONObject.quote(entry)};</script>
<script src="$runtime/runner.js"></script>
""".trim()
    val withoutModule = moduleScript.replace(indexHtml, "")
    return if (Regex("(?i)</body>").containsMatchIn(withoutModule)) {
        withoutModule.replaceFirst(Regex("(?i)</body>"), "$scripts\n</body>")
    } else {
        "$withoutModule\n$scripts"
    }
}

/** Project files a Python project copies into Python's file system (everything but the starter page). */
fun pythonProjectFileList(root: File): String {
    val files = root.walkTopDown()
        .filter { it.isFile && !it.name.startsWith(".") }
        .map { it.relativeTo(root).path.replace('\\', '/') }
        .filterNot { it.equals("index.html", ignoreCase = true) }
        .toList()
    return JSONArray(files).toString()
}

/** Serves assets/runtime with the content types WebAssembly and modules need. */
class RuntimeAssetsHandler(private val context: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val clean = path.substringBefore('?').trimStart('/')
        if (clean.contains("..")) return null
        val mime = when (clean.substringAfterLast('.', "").lowercase(Locale.US)) {
            "js", "mjs" -> "text/javascript"
            "wasm" -> "application/wasm"
            "json" -> "application/json"
            "html" -> "text/html"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
        return runCatching { WebResourceResponse(mime, "utf-8", context.assets.open("runtime/$clean")) }.getOrNull()
    }
}

/** One line of the preview's console, shown on the Console tab and read back for automatic fixes. */
data class ConsoleEntry(val level: String, val message: String, val source: String = "")

object PreviewConsole {
    private val _entries = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val entries: StateFlow<List<ConsoleEntry>> = _entries.asStateFlow()

    fun add(entry: ConsoleEntry) = _entries.update { (it + entry).takeLast(300) }
    fun clear() = _entries.update { emptyList() }
    fun errors(): List<ConsoleEntry> = _entries.value.filter { it.level == "error" }
}

/**
 * Writes the project as a Capacitor project: the web app plus capacitor.config.json and the
 * packages, so on a computer `npx cap add android` turns it into an Android Studio project.
 */
fun writeCapacitorZip(root: File, appName: String, output: OutputStream) {
    val kind = projectKind(root)
    val appId = "com.mochu." + appName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }.take(30)
    ZipOutputStream(output).use { zip ->
        fun put(path: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
        val projectFiles = root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
        if (kind == ProjectKind.React) {
            projectFiles.filter { it.relativeTo(root).path.replace('\\', '/') != "package.json" }.forEach { file ->
                put(file.relativeTo(root).path.replace('\\', '/'), file.readBytes())
            }
            val pkg = runCatching { org.json.JSONObject(File(root, "package.json").readText()) }.getOrElse { org.json.JSONObject() }
            val deps = pkg.optJSONObject("dependencies") ?: org.json.JSONObject().also { pkg.put("dependencies", it) }
            val devDeps = pkg.optJSONObject("devDependencies") ?: org.json.JSONObject().also { pkg.put("devDependencies", it) }
            deps.put("@capacitor/core", "^6.1.2")
            deps.put("@capacitor/android", "^6.1.2")
            devDeps.put("@capacitor/cli", "^6.1.2")
            put("package.json", pkg.toString(2).toByteArray())
        } else {
            // A plain web app goes in www/, which Capacitor copies into the Android app as it is.
            projectFiles.forEach { file -> put("www/" + file.relativeTo(root).path.replace('\\', '/'), file.readBytes()) }
            put(
                "package.json",
                """
{
  "name": "${appId.substringAfterLast('.')}",
  "private": true,
  "version": "0.1.0",
  "dependencies": { "@capacitor/core": "^6.1.2", "@capacitor/android": "^6.1.2" },
  "devDependencies": { "@capacitor/cli": "^6.1.2" }
}
""".trimStart().toByteArray()
            )
        }
        val webDir = if (kind == ProjectKind.React) "dist" else "www"
        put(
            "capacitor.config.json",
            """
{
  "appId": "$appId",
  "appName": ${org.json.JSONObject.quote(appName)},
  "webDir": "$webDir"
}
""".trimStart().toByteArray()
        )
        val build = if (kind == ProjectKind.React) "npm run build\n" else ""
        put(
            "README-MOCHU.md",
            """
# $appName

Made with Mochu Code. To turn it into an Android app on a computer (Node.js and Android Studio installed):

```
npm install
${build}npx cap add android
npx cap sync
npx cap open android
```

Android Studio opens the app; press Run to install it on a phone, or Build > Generate Signed Bundle for Google Play.
After changing the web code later, run ${if (kind == ProjectKind.React) "`npm run build` and " else ""}`npx cap sync` again.
""".trimStart().toByteArray()
        )
    }
}

/** Code actions the work panel's Console and Files tabs need, provided by the app shell. */
data class CodeActions(val saveCapacitorZip: () -> Unit = {}, val fixErrors: () -> Unit = {})

val LocalCodeActions = androidx.compose.runtime.staticCompositionLocalOf { CodeActions() }
