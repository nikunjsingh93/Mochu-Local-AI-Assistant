package com.nikunj.gemmabuilder

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.graphics.pdf.PdfRenderer
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewAssetLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.coroutines.resume


// System prompt and starter page templates.

const val BUILD_SYSTEM_PROMPT = """
You are an offline Android coding agent.

You build small apps: web apps (HTML, CSS, JavaScript), React + TypeScript apps, and Python programs.

You MUST respond only with XML actions.

The host app may include extracted local file contents in the prompt using markers like:
<<<FILE:path>>>
...content...
<<<END_FILE>>>
Treat that as authoritative local file content (including text the app extracted from PDF/image/DOCX/audio). Do not claim you cannot read local files.

The first message of a chat contains a project snapshot. Later messages may contain only the user's request; the project files are then exactly what your earlier write_file actions produced.

Allowed actions:

<action name="write_file">
<path>relative/path/from/project/root</path>
<content>
FULL FILE CONTENT HERE
</content>
</action>

<action name="edit_file">
<path>relative/path/from/project/root</path>
<find>
EXACT LINES COPIED FROM THE CURRENT FILE
</find>
<replace>
THE NEW LINES THAT REPLACE THEM
</replace>
</action>

<action name="reply">
<content>
ASSISTANT REPLY HERE
</content>
</action>

Rules:
- For a small change to a file that already exists, use edit_file instead of rewriting the whole file. Copy each <find> block exactly from the current file, keep it short (just the lines that change plus a little context), and repeat <find>/<replace> pairs for several changes.
- Use write_file for new files, a different app, or changes to most of a file.
- You may return one or more write_file or edit_file actions.
- Use reply action when the user is asking a normal question and no file changes are required; do not modify files then.
- For edits, update only the needed files and keep paths relative to the project root.
- If the user asks for a different app, replace the current app completely. Do not keep the previous UI unless the user asks.
Project types (the snapshot shows which files exist):
- React + TypeScript project (it has src/main.tsx): a Vite project. Put the UI in src/App.tsx and more components in src/*.tsx, styles in .css files imported from TSX. Use function components, hooks and typed props. Imports allowed: 'react', 'react-dom/client' and relative project files only; no other npm packages. Leave index.html, src/main.tsx and package.json as they are unless asked.
- Python project (it has main.py): Python 3 in main.py, plus other .py modules if useful. It runs in a terminal panel: print() shows output and input() asks the user. Standard library only: no pip packages, no network, no GUI windows.
- Web project (otherwise): the rules below.
- For web projects, by default generate a single self-contained index.html that includes CSS in <style> and JavaScript in <script>.
- Only split into separate files (for example styles.css/app.js) when the user explicitly asks for separate files.
- If index.html references local files (for example styles.css or app.js), write each referenced file in the same response.
- Never place JavaScript in styles.css.
- No external links.
- No CDN.
- No remote images.
- Make it work offline.
- Make it touch-friendly for Android.
- Use responsive layout for phone screens.
- Center the main app content by default unless the user asks for a top/header layout.
- Use min-height:100vh and grid/flex centering for standalone generated apps.
- Use width:min(92vw, ...px) or similar responsive sizing instead of fixed wide layouts.
- Keep the code simple, complete, and compact for mobile: no unnecessary comments, huge CSS blocks, or repeated code.
- Never reference missing functions or variables.
- Do not write markdown or explanations outside XML actions.
- Never stop inside an XML action. The XML must be complete and must end with </content> and </action>.
"""

fun welcomeHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Mochu Code</title>
<style>
  body { margin:0; min-height:100vh; font-family:system-ui,sans-serif; background:linear-gradient(135deg,#111827,#4338ca); color:white; display:grid; place-items:center; }
  .card { width:min(92vw,680px); background:rgba(255,255,255,.12); border:1px solid rgba(255,255,255,.18); border-radius:28px; padding:28px; box-shadow:0 30px 80px rgba(0,0,0,.35); }
  h1 { font-size:clamp(32px,8vw,64px); line-height:.95; margin:0 0 16px; }
  p { font-size:18px; line-height:1.5; color:#e5e7eb; }
  .pill { display:inline-block; padding:10px 14px; border-radius:999px; background:rgba(255,255,255,.16); margin-top:12px; }
</style>
</head>
<body>
  <main class="card">
    <h1>Mochu Code</h1>
    <p>Ask the chat panel to build a calculator, game menu, landing page, or another tiny offline web app. The result will be written to index.html and previewed here.</p>
    <span class="pill">Ready for Preview</span>
  </main>
</body>
</html>
""".trimIndent()

fun defaultPwaManifest(): String = """
{
  "name": "Mochu Code App",
  "short_name": "Mochu",
  "start_url": "./index.html",
  "display": "standalone",
  "background_color": "#0f172a",
  "theme_color": "#4338ca",
  "icons": []
}
""".trimIndent()

fun defaultServiceWorker(): String = """
const CACHE_NAME = "mochu-app-v1";
const APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest"
];

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(key => key !== CACHE_NAME ? caches.delete(key) : null))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(networkResponse => {
        const copy = networkResponse.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, copy));
        return networkResponse;
      }).catch(() => caches.match("./index.html"));
    })
  );
});
""".trimIndent()

fun calculatorHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Touch Calculator</title>
<style>
  * { box-sizing:border-box; }
  body { margin:0; min-height:100vh; display:grid; place-items:center; background:#0b1020; color:white; font-family:system-ui,sans-serif; }
  .calc { width:min(94vw,420px); background:#151b2f; border-radius:30px; padding:20px; box-shadow:0 28px 70px rgba(0,0,0,.55); }
  .screen { min-height:96px; background:#050816; border-radius:22px; padding:18px; text-align:right; font-size:42px; overflow:hidden; margin-bottom:16px; }
  .keys { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; }
  button { height:72px; border:0; border-radius:20px; font-size:25px; font-weight:800; color:white; background:#283149; box-shadow:inset 0 -4px rgba(0,0,0,.25); }
  button:active { transform:translateY(2px); }
  .op { background:#4f46e5; }
  .danger { background:#dc2626; }
  .equals { background:#16a34a; grid-column:span 2; }
</style>
</head>
<body>
  <main class="calc">
    <div id="screen" class="screen">0</div>
    <div class="keys">
      <button class="danger" onclick="clearAll()">C</button><button onclick="press('(')">(</button><button onclick="press(')')">)</button><button class="op" onclick="press('/')">÷</button>
      <button onclick="press('7')">7</button><button onclick="press('8')">8</button><button onclick="press('9')">9</button><button class="op" onclick="press('*')">×</button>
      <button onclick="press('4')">4</button><button onclick="press('5')">5</button><button onclick="press('6')">6</button><button class="op" onclick="press('-')">−</button>
      <button onclick="press('1')">1</button><button onclick="press('2')">2</button><button onclick="press('3')">3</button><button class="op" onclick="press('+')">+</button>
      <button onclick="press('0')">0</button><button onclick="press('.')">.</button><button class="equals" onclick="calc()">=</button>
    </div>
  </main>
<script>
let expr = '';
const screen = document.getElementById('screen');
function render(){ screen.textContent = expr || '0'; }
function press(v){ expr += v; render(); }
function clearAll(){ expr = ''; render(); }
function calc(){
  try {
    if(!/^[0-9+\-*/().\s]+$/.test(expr)) throw new Error('bad input');
    expr = String(Function('return (' + expr + ')')());
  } catch(e) { expr = 'Error'; setTimeout(clearAll, 800); }
  render();
}
</script>
</body>
</html>
""".trimIndent()

fun racingMenuHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Retro Rally Menu</title>
<style>
  html,body { margin:0; height:100%; overflow:hidden; font-family:Impact, system-ui, sans-serif; background:#111; color:#fff; }
  body { background:radial-gradient(circle at 50% 25%, #f97316 0 8%, #7f1d1d 30%, #020617 78%); }
  .scan { position:fixed; inset:0; background:repeating-linear-gradient(to bottom, rgba(255,255,255,.08), rgba(255,255,255,.08) 1px, transparent 2px, transparent 5px); pointer-events:none; mix-blend-mode:overlay; }
  .road { position:fixed; left:50%; bottom:-12%; width:68vw; height:70vh; background:linear-gradient(#374151,#050505); transform:translateX(-50%) perspective(300px) rotateX(62deg); clip-path:polygon(38% 0,62% 0,100% 100%,0 100%); border:6px solid #facc15; }
  .car { position:fixed; left:50%; bottom:9%; transform:translateX(-50%); width:min(64vw,420px); height:120px; background:#ef4444; clip-path:polygon(10% 75%,25% 35%,45% 15%,75% 25%,92% 75%,82% 100%,18% 100%); box-shadow:0 30px #111; }
  .menu { position:relative; z-index:3; padding:28px; max-width:520px; }
  h1 { font-size:clamp(48px,14vw,96px); line-height:.8; margin:20px 0; text-shadow:6px 6px #000; color:#fde047; }
  button { display:block; width:min(86vw,360px); margin:14px 0; padding:18px; border:4px solid white; background:#111827cc; color:white; font-size:28px; text-align:left; text-transform:uppercase; box-shadow:6px 6px #000; }
  button:active { transform:translate(4px,4px); box-shadow:2px 2px #000; }
</style>
</head>
<body>
  <div class="road"></div><div class="car"></div><div class="scan"></div>
  <main class="menu">
    <h1>RALLY<br>1998</h1>
    <button onclick="flash('Arcade Race')">Start Race</button>
    <button onclick="flash('Garage')">Garage</button>
    <button onclick="flash('Options')">Options</button>
    <p id="msg">Touch a menu item.</p>
  </main>
<script>
function flash(txt){ document.getElementById('msg').textContent = txt + ' selected'; if(navigator.vibrate) navigator.vibrate(35); }
</script>
</body>
</html>
""".trimIndent()

fun genericAppHtml(prompt: String): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Generated App</title>
<style>
  body { margin:0; min-height:100vh; font-family:system-ui,sans-serif; background:#f3f4f6; color:#111827; display:grid; place-items:center; }
  .app { width:min(92vw,720px); background:white; border-radius:28px; padding:28px; box-shadow:0 24px 80px rgba(0,0,0,.12); }
  h1 { font-size:clamp(34px,8vw,64px); margin:0; }
  p { font-size:18px; line-height:1.5; color:#4b5563; }
  button { border:0; border-radius:18px; padding:18px 22px; font-size:18px; font-weight:800; background:#4f46e5; color:white; }
  .count { font-size:64px; font-weight:900; margin:18px 0; }
</style>
</head>
<body>
  <main class="app">
    <h1>Generated Offline App</h1>
    <p>This demo page was created from your request. Load Gemma to generate custom code with the local model.</p>
    <p><strong>Request:</strong> ${prompt.takeLast(500).replace("<", "&lt;").replace(">", "&gt;")}</p>
    <div id="count" class="count">0</div>
    <button onclick="add()">Tap me</button>
  </main>
<script>
let n=0; function add(){ n++; document.getElementById('count').textContent=n; }
</script>
</body>
</html>
""".trimIndent()


/** System prompt for the general assistant (Chat space). Replies are plain Markdown, not XML actions. */
const val ASSISTANT_SYSTEM_PROMPT = """
You are Mochu, a friendly, cheerful AI assistant running entirely on the user's phone. Nothing the user writes leaves the device.

- Answer directly and concisely. Use Markdown: short paragraphs, bullet lists, and fenced code blocks when they help.
- If you are not sure about something, say so instead of guessing. You can't browse by yourself; when the app gives you web sources, answer from them and cite them like [1].
- If the user wants a web app, game, or page built, tell them to open the Code tab, which builds and previews it.
- The app itself sets timers, reminders, a stopwatch and scheduled tasks, and can search the web on a schedule. Never say you can't set alarms or schedule things. If the user asks for one, tell them to phrase it like "every day at 8am summarize tech news", "remind me at 5pm to call mom" or "set a timer for 10 minutes", or to use the clock button at the top of Chat.
- The app itself sets timers, reminders, a stopwatch and scheduled tasks, and can search the web on a schedule. Never say you can't set alarms or schedule things. If the user asks for one and it wasn't set, tell them to phrase it like "every day at 8am summarize tech news", "remind me at 5pm to call mom" or "set a timer for 10 minutes", or to use the clock button at the top of Chat.
- Write in the user's language.
"""
