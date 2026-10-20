// Runs a Vite-style React + TypeScript project in Mochu's Code preview, offline.
// Each source file is compiled with Babel (TypeScript + JSX -> CommonJS) and wired together with a
// small require(). Only react and react-dom can be imported; everything else is a project file.
(function () {
  'use strict';
  var root = new URL('./', location.href);
  var entry = (window.__MOCHU_ENTRY || 'src/main.tsx').replace(/^\.?\//, '');
  var EXTS = ['', '.tsx', '.ts', '.jsx', '.js', '.json', '/index.tsx', '/index.ts', '/index.jsx', '/index.js'];
  var ASSET = /\.(png|jpe?g|gif|webp|svg|ico|bmp|mp3|wav|ogg|mp4|webm|woff2?|ttf)$/i;
  var builtins = {
    'react': function () { return window.React; },
    'react-dom': function () { return window.ReactDOM; },
    'react-dom/client': function () { return window.ReactDOM; },
    'react/jsx-runtime': function () { return jsxRuntime(); },
    'react/jsx-dev-runtime': function () { return jsxRuntime(); }
  };
  var modules = {};

  function jsxRuntime() {
    var R = window.React;
    function jsx(type, props, key) {
      var p = Object.assign({}, props);
      if (key !== undefined) p.key = key;
      var children = p.children;
      delete p.children;
      return children === undefined ? R.createElement(type, p) : R.createElement.apply(null, [type, p].concat(Array.isArray(children) ? children : [children]));
    }
    return { jsx: jsx, jsxs: jsx, jsxDEV: jsx, Fragment: R.Fragment };
  }

  function showError(title, detail) {
    console.error(title + (detail ? '\n' + detail : ''));
    var box = document.getElementById('__mochu_error') || document.createElement('pre');
    box.id = '__mochu_error';
    box.style.cssText = 'position:fixed;inset:auto 8px 8px 8px;max-height:45vh;overflow:auto;margin:0;padding:12px;' +
      'background:#2b0b0e;color:#ffb4b4;font:12px/1.4 monospace;white-space:pre-wrap;border-radius:10px;z-index:2147483647';
    box.textContent = title + (detail ? '\n\n' + detail : '');
    document.body.appendChild(box);
  }

  function dirOf(path) { var i = path.lastIndexOf('/'); return i < 0 ? '' : path.slice(0, i + 1); }

  function normalize(path) {
    var out = [];
    path.split('/').forEach(function (part) {
      if (part === '..') out.pop(); else if (part && part !== '.') out.push(part);
    });
    return out.join('/');
  }

  function resolveSpec(spec, fromFile) {
    if (spec.charAt(0) === '/') return normalize(spec);
    if (spec.indexOf('@/') === 0) return normalize('src/' + spec.slice(2));
    return normalize(dirOf(fromFile) + spec);
  }

  async function fetchFirst(path) {
    for (var i = 0; i < EXTS.length; i++) {
      var candidate = path + EXTS[i];
      if (ASSET.test(candidate)) return { path: candidate, asset: true };
      try {
        var res = await fetch(new URL(candidate, root).href, { cache: 'no-store' });
        if (res.ok) {
          var text = await res.text();
          // The app's file server answers missing files with an empty 404; skip folders and HTML fallbacks.
          if (EXTS[i] === '' && !/\.[a-z0-9]+$/i.test(candidate)) continue;
          return { path: candidate, text: text };
        }
      } catch (e) { /* try the next extension */ }
    }
    return null;
  }

  function compile(path, code) {
    var out = Babel.transform(code, {
      filename: path,
      sourceType: 'module',
      presets: [
        ['env', { modules: 'commonjs', targets: { chrome: '100' } }],
        ['react', { runtime: 'classic' }],
        ['typescript', { isTSX: /\.tsx$/i.test(path), allExtensions: true }]
      ],
      retainLines: true
    }).code;
    return out;
  }

  async function load(spec, fromFile) {
    if (builtins[spec]) return spec;
    var isRelative = spec.charAt(0) === '.' || spec.charAt(0) === '/' || spec.indexOf('@/') === 0;
    if (!isRelative) {
      throw new Error('"' + spec + '" is a package that isn\'t available offline in Mochu Code (imported in ' + fromFile + '). ' +
        'Only react and react-dom can be imported; write the rest as project files.');
    }
    var wanted = resolveSpec(spec, fromFile);
    if (modules[wanted]) return wanted;
    var found = await fetchFirst(wanted);
    if (!found) throw new Error('Cannot find "' + spec + '" (imported in ' + fromFile + '). Looked for ' + wanted + '.');
    var key = found.path;
    if (modules[key]) return key;
    var mod = modules[key] = { exports: {}, loaded: false, deps: {} };
    if (found.asset) {
      mod.factory = function (require, module) { module.exports = { default: new URL(key, root).href, __esModule: true }; };
    } else if (/\.css$/i.test(key)) {
      mod.factory = function () {
        var style = document.createElement('style');
        style.setAttribute('data-file', key);
        style.textContent = found.text;
        document.head.appendChild(style);
      };
    } else if (/\.json$/i.test(key)) {
      mod.factory = function (require, module) { module.exports = JSON.parse(found.text); };
    } else {
      var compiled;
      try {
        compiled = compile(key, found.text);
      } catch (e) {
        throw new Error('Syntax error in ' + key + ': ' + (e && e.message ? e.message : e));
      }
      var specs = [];
      compiled.replace(/require\((["'])([^"']+)\1\)/g, function (_, __, s) { specs.push(s); return _; });
      for (var i = 0; i < specs.length; i++) mod.deps[specs[i]] = await load(specs[i], key);
      mod.factory = new Function('require', 'module', 'exports', compiled + '\n//# sourceURL=' + key);
    }
    return key;
  }

  function run(key) {
    if (builtins[key]) return builtins[key]();
    var mod = modules[key];
    if (mod.loaded) return mod.exports;
    mod.loaded = true;
    var module = { exports: mod.exports };
    mod.factory(function (spec) { return run(mod.deps[spec] || spec); }, module, module.exports);
    mod.exports = module.exports;
    return mod.exports;
  }

  window.addEventListener('error', function (event) {
    showError('Error: ' + event.message, event.filename ? 'at ' + event.filename + ':' + event.lineno : '');
  });
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    showError('Unhandled error: ' + (reason && reason.message ? reason.message : reason), reason && reason.stack ? reason.stack : '');
  });

  (async function () {
    try {
      var key = await load('/' + entry, 'index.html');
      run(key);
    } catch (e) {
      showError(e && e.message ? e.message : String(e), '');
    }
  })();
})();
