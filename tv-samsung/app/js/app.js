"use strict";
function _typeof(o) { "@babel/helpers - typeof"; return _typeof = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (o) { return typeof o; } : function (o) { return o && "function" == typeof Symbol && o.constructor === Symbol && o !== Symbol.prototype ? "symbol" : typeof o; }, _typeof(o); }
(() => {
  // src/polyfills.js
  if (!Object.values) {
    Object.values = function (o) {
      return Object.keys(o).map(function (k) {
        return o[k];
      });
    };
  }
  if (!Object.entries) {
    Object.entries = function (o) {
      return Object.keys(o).map(function (k) {
        return [k, o[k]];
      });
    };
  }
  if (!String.prototype.padStart) {
    String.prototype.padStart = function (len, fill) {
      var s = String(this);
      fill = fill === void 0 ? " " : String(fill);
      while (s.length < len) s = fill + s;
      return s.slice(s.length - Math.max(len, String(this).length));
    };
  }
  if (window.NodeList && !NodeList.prototype.forEach) {
    NodeList.prototype.forEach = Array.prototype.forEach;
  }
  if (!Array.prototype.flat) {
    Array.prototype.flat = function () {
      return [].concat.apply([], this);
    };
  }
  if (!Array.prototype.includes) {
    Array.prototype.includes = function (v) {
      return this.indexOf(v) !== -1;
    };
  }
  if (!String.prototype.includes) {
    String.prototype.includes = function (v) {
      return this.indexOf(v) !== -1;
    };
  }

  // src/util.js
  function HttpError(status, message) {
    var e = new Error(message || (status ? "Serwer zwrócił błąd HTTP " + status : "Brak połączenia z serwerem"));
    e.isHttp = true;
    e.status = status;
    return e;
  }
  function httpText(url, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open("GET", url, true);
      xhr.timeout = timeoutMs || 6e4;
      xhr.onload = function () {
        if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.responseText || "");else reject(HttpError(xhr.status));
      };
      xhr.onerror = function () {
        reject(HttpError(0));
      };
      xhr.ontimeout = function () {
        reject(HttpError(0, "Serwer nie odpowiada (przekroczony czas)"));
      };
      xhr.send();
    });
  }
  function httpJson(url, timeoutMs) {
    return httpText(url, timeoutMs).then(function (t) {
      var s = t.trim();
      if (!s) return null;
      try {
        return JSON.parse(s);
      } catch (e) {
        var err = new Error("Nieprawidłowa odpowiedź serwera");
        err.notJson = true;
        throw err;
      }
    });
  }
  var PREFIX = "mojatv.";
  var store = {
    get: function get(key, fallback) {
      try {
        var v = localStorage.getItem(PREFIX + key);
        return v == null ? fallback : JSON.parse(v);
      } catch (e) {
        return fallback;
      }
    },
    set: function set(key, value) {
      try {
        if (value === void 0 || value === null) localStorage.removeItem(PREFIX + key);else localStorage.setItem(PREFIX + key, JSON.stringify(value));
      } catch (e) {}
    }
  };
  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  var PL = {
    "ą": "a",
    "ć": "c",
    "ę": "e",
    "ł": "l",
    "ń": "n",
    "ó": "o",
    "ś": "s",
    "ź": "z",
    "ż": "z"
  };
  function normalize(s) {
    var t = String(s || "").toLowerCase();
    if (t.normalize) t = t.normalize("NFD").replace(/[̀-ͯ]/g, "");
    return t.replace(/[ąćęłńóśźż]/g, function (c) {
      return PL[c];
    });
  }
  function pad2(n) {
    return n < 10 ? "0" + n : "" + n;
  }
  function formatTime(ms) {
    var d = new Date(ms);
    return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
  }
  function formatClock(ms) {
    var s = Math.max(0, Math.floor(ms / 1e3));
    var h = Math.floor(s / 3600);
    var m = Math.floor(s % 3600 / 60);
    var sec = s % 60;
    return (h > 0 ? h + ":" + pad2(m) : m) + ":" + pad2(sec);
  }
  function formatDuration(ms) {
    var min = Math.max(0, Math.round(ms / 6e4));
    var h = Math.floor(min / 60);
    return h > 0 ? h + " godz. " + min % 60 + " min" : min + " min";
  }
  function formatRating(r) {
    if (r == null || r === "") return null;
    var d = parseFloat(String(r).replace(",", "."));
    if (isNaN(d)) return String(r).slice(0, 4);
    if (d <= 0) return null;
    if (d > 10) d = d / 10;
    return "★ " + d.toFixed(1);
  }
  function debounce(fn, ms) {
    var t = null;
    return function () {
      var args = arguments;
      var self = this;
      clearTimeout(t);
      t = setTimeout(function () {
        fn.apply(self, args);
      }, ms);
    };
  }
  function str(o, key) {
    if (!o) return null;
    var v = o[key];
    if (v === void 0 || v === null) return null;
    v = String(v).trim();
    return v === "" || v === "null" ? null : v;
  }
  function b64decode(s) {
    if (!s) return s;
    try {
      var bin = atob(s);
      var bytes = [];
      for (var i = 0; i < bin.length; i++) bytes.push("%" + ("0" + bin.charCodeAt(i).toString(16)).slice(-2));
      return decodeURIComponent(bytes.join("")).trim();
    } catch (e) {
      return s;
    }
  }

  // src/nav.js
  var KEYS = {
    37: "left",
    38: "up",
    39: "right",
    40: "down",
    13: "enter",
    10009: "back",
    27: "back",
    8: "backspace",
    10252: "playpause",
    415: "play",
    19: "pause",
    413: "stop",
    417: "ff",
    412: "rw",
    427: "chup",
    428: "chdown",
    33: "chup",
    34: "chdown",
    403: "red",
    404: "green",
    405: "yellow",
    406: "blue",
    457: "info",
    10073: "chlist",
    65376: "imedone",
    65385: "imecancel"
  };
  for (d = 0; d <= 9; d++) KEYS[48 + d] = "d" + d;
  var d;
  var layers = [];
  var _current = null;
  function top() {
    return layers[layers.length - 1];
  }
  function visible(el2) {
    if (!el2 || !el2.isConnected && !document.body.contains(el2)) return false;
    if (el2.offsetParent === null && getComputedStyle(el2).position !== "fixed") return false;
    var r = el2.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }
  var nav = {
    /** Nowa warstwa (ekran albo okno nad ekranem). */
    push: function push(root2, opts) {
      if (_current) _current.classList.remove("focused");
      layers.push({
        root: root2,
        focused: null,
        groups: {},
        onBack: opts && opts.onBack
      });
      _current = null;
    },
    pop: function pop() {
      var l = layers.pop();
      if (_current) _current.classList.remove("focused");
      _current = null;
      var t = top();
      if (t && t.focused && document.body.contains(t.focused)) nav.focus(t.focused, true);
      return l;
    },
    /** Zastępuje całą zawartość (ekran główny). */
    reset: function reset(root2, opts) {
      layers = [];
      _current = null;
      nav.push(root2, opts);
    },
    layerCount: function layerCount() {
      return layers.length;
    },
    setBack: function setBack(fn) {
      var t = top();
      if (t) t.onBack = fn;
    },
    current: function current() {
      return _current;
    },
    focus: function focus(el2, noScroll) {
      if (!el2) return;
      var t = top();
      if (_current && _current !== el2) _current.classList.remove("focused");
      if (document.activeElement && document.activeElement !== el2 && document.activeElement.blur) {
        document.activeElement.blur();
      }
      _current = el2;
      el2.classList.add("focused");
      if (t) {
        t.focused = el2;
        var g = el2.getAttribute("data-group");
        if (g) t.groups[g] = el2;
      }
      if (!noScroll) ensureVisible(el2);
      if (el2.onFocus) el2.onFocus();
    },
    /** Podświetla pierwszy element (albo zapamiętany w grupie). */
    focusFirst: function focusFirst(root2, group) {
      var t = top();
      if (group && t && t.groups[group] && visible(t.groups[group])) return nav.focus(t.groups[group]);
      var scope = root2 || t && t.root || document.body;
      var list = scope.querySelectorAll(".f");
      for (var i = 0; i < list.length; i++) if (visible(list[i])) return nav.focus(list[i]);
    },
    rememberedIn: function rememberedIn(group) {
      var t = top();
      var el2 = t && t.groups[group];
      return el2 && visible(el2) ? el2 : null;
    },
    move: function move(dir) {
      var t = top();
      if (!t) return false;
      if (!_current || !visible(_current)) {
        nav.focusFirst();
        return true;
      }
      var best = findNext(t.root, _current, dir);
      if (!best) return false;
      var g = best.getAttribute("data-group");
      if (g && g !== _current.getAttribute("data-group")) {
        if (t.groups[g] && visible(t.groups[g])) best = t.groups[g];else {
          var marked = t.root.querySelector('[data-group="' + g + '"].active, [data-group="' + g + '"].sel');
          if (marked && visible(marked)) best = marked;
        }
      }
      nav.focus(best);
      return true;
    },
    handleKey: function handleKey(name) {
      var t = top();
      if (!t) return false;
      if (_current && _current.onKey && _current.onKey(name)) return true;
      if (name === "left" || name === "right" || name === "up" || name === "down") return nav.move(name);
      if (name === "enter") {
        if (!_current) {
          nav.focusFirst();
          return true;
        }
        if (_current.tagName === "INPUT") {
          _current.focus();
          return true;
        }
        if (_current.onEnter) _current.onEnter();else _current.click();
        return true;
      }
      if (name === "back" && t.onBack) {
        t.onBack();
        return true;
      }
      return false;
    }
  };
  function findNext(root2, from, dir) {
    var a = from.getBoundingClientRect();
    var ax = a.left + a.width / 2,
      ay = a.top + a.height / 2;
    var list = root2.querySelectorAll(".f");
    var cands = [];
    var minPrimary = Infinity;
    for (var i = 0; i < list.length; i++) {
      var el2 = list[i];
      if (el2 === from || !visible(el2)) continue;
      var b = el2.getBoundingClientRect();
      var bx = b.left + b.width / 2,
        by = b.top + b.height / 2;
      var primary, overlap, secondary;
      if (dir === "left") {
        if (b.right > a.left + 1 && bx >= ax) continue;
        primary = Math.max(0, a.left - b.right);
        overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        secondary = Math.abs(by - ay);
      } else if (dir === "right") {
        if (b.left < a.right - 1 && bx <= ax) continue;
        primary = Math.max(0, b.left - a.right);
        overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        secondary = Math.abs(by - ay);
      } else if (dir === "up") {
        if (b.bottom > a.top + 1 && by >= ay) continue;
        primary = Math.max(0, a.top - b.bottom);
        overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        secondary = Math.abs(bx - ax);
      } else {
        if (b.top < a.bottom - 1 && by <= ay) continue;
        primary = Math.max(0, b.top - a.bottom);
        overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        secondary = Math.abs(bx - ax);
      }
      cands.push({
        el: el2,
        primary,
        secondary: overlap > 0 ? secondary * 0.5 : secondary + 200
      });
      if (primary < minPrimary) minPrimary = primary;
    }
    var best = null,
      bestScore = Infinity;
    for (var j = 0; j < cands.length; j++) {
      var c = cands[j];
      if (c.primary > minPrimary + 40) continue;
      var score = c.secondary + c.primary * 0.1;
      if (score < bestScore) {
        bestScore = score;
        best = c.el;
      }
    }
    return best;
  }
  function ensureVisible(el2) {
    var p = el2.parentElement;
    while (p && p !== document.body) {
      var cls = p.classList;
      if (cls.contains("scroll")) {
        var pr = p.getBoundingClientRect(),
          er = el2.getBoundingClientRect();
        var margin = Math.min(140, pr.height / 4);
        if (er.top < pr.top + margin) p.scrollTop -= pr.top + margin - er.top;else if (er.bottom > pr.bottom - margin) p.scrollTop += er.bottom - (pr.bottom - margin);
      }
      if (cls.contains("hscroll")) {
        var hr = p.getBoundingClientRect(),
          ehr = el2.getBoundingClientRect();
        var hm = Math.min(120, hr.width / 5);
        if (ehr.left < hr.left + hm) p.scrollLeft -= hr.left + hm - ehr.left;else if (ehr.right > hr.right - hm) p.scrollLeft += ehr.right - (hr.right - hm);
      }
      p = p.parentElement;
    }
  }

  // src/dom.js
  function el(html) {
    var t = document.createElement("div");
    t.innerHTML = html.trim();
    return t.firstChild;
  }
  var ICON_PATHS = {
    home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    live: "M21 6h-7.59l3.29-3.29L16 2l-4 4-4-4-.71.71L10.59 6H3c-1.1 0-2 .89-2 2v12c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.11-.9-2-2-2zm0 14H3V8h18v12zM9 10v8l7-4z",
    movie: "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z",
    series: "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z",
    show: "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
    radio: "M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.89 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z",
    search: "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    settings: "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    star: "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
    play: "M8 5v14l11-7z"
  };
  function icon(name, cls) {
    return '<svg class="ic ' + (cls || "") + '" viewBox="0 0 24 24"><path d="' + ICON_PATHS[name] + '"/></svg>';
  }
  function img(url, fallback, cls) {
    var initials = esc(String(fallback || "").trim().slice(0, 2).toUpperCase());
    if (!url) return '<div class="ph ' + (cls || "") + '"><span>' + initials + "</span></div>";
    return '<div class="ph ' + (cls || "") + '"><span>' + initials + '</span><img src="' + esc(url) + `" onload="this.parentNode.className+=' loaded'" onerror="this.parentNode.removeChild(this)"></div>`;
  }
  function lazyList(container, items, render2, batch) {
    batch = batch || 60;
    var shown = 0;
    function more() {
      var end = Math.min(items.length, shown + batch);
      var frag = document.createDocumentFragment();
      for (var i = shown; i < end; i++) {
        var node = render2(items[i], i);
        (function (idx, n) {
          var prev = n.onFocus;
          n.onFocus = function () {
            if (idx >= shown - Math.ceil(batch / 3) && shown < items.length) more();
            if (prev) prev.call(n);
          };
        })(i, node);
        frag.appendChild(node);
      }
      shown = end;
      container.appendChild(frag);
    }
    container.innerHTML = "";
    if (items.length) more();
    return {
      /** Doładowuje elementy aż do podanego indeksu i go zwraca. */
      nodeAt: function nodeAt(i) {
        while (i >= shown && shown < items.length) more();
        return container.children[i] || null;
      }
    };
  }
  var toastTimer = null;
  function toast(text) {
    var t = document.getElementById("toast");
    if (!t) {
      t = el('<div id="toast"></div>');
      document.body.appendChild(t);
    }
    t.textContent = text;
    t.className = "show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      t.className = "";
    }, 2500);
  }
  function confirmDialog(title, text, okLabel) {
    return new Promise(function (resolve) {
      var d = el('<div class="modal"><div class="modal-box"><h2>' + esc(title) + "</h2><p>" + esc(text) + '</p><div class="row-btns"><div class="btn f" data-v="1">' + esc(okLabel || "OK") + '</div><div class="btn f" data-v="0">Anuluj</div></div></div></div>');
      document.getElementById("app").appendChild(d);
      function close(v) {
        nav.pop();
        d.parentNode.removeChild(d);
        resolve(v);
      }
      nav.push(d, {
        onBack: function onBack() {
          close(false);
        }
      });
      d.querySelectorAll(".btn").forEach(function (b) {
        b.onEnter = function () {
          close(b.getAttribute("data-v") === "1");
        };
      });
      nav.focusFirst(d);
    });
  }

  // src/api.js
  function normalizeServer(raw) {
    var s = String(raw || "").trim();
    if (!s) return s;
    if (!/^https?:\/\//i.test(s)) s = "http://" + s;
    ["/player_api.php", "/get.php", "/xmltv.php"].forEach(function (m) {
      var i = s.indexOf(m);
      if (i > 0) s = s.slice(0, i);
    });
    return s.replace(/\/+$/, "");
  }
  function credentialsFromUrl(url) {
    var t = String(url || "").trim();
    var m = t.match(/^(https?:\/\/[^/?#]+)[^?]*\?(.*)$/i);
    if (!m) return null;
    var params = {};
    m[2].split("&").forEach(function (p) {
      var kv = p.split("=");
      if (kv.length >= 2) {
        try {
          params[kv[0]] = decodeURIComponent(kv.slice(1).join("=").replace(/\+/g, " "));
        } catch (e) {
          params[kv[0]] = kv.slice(1).join("=");
        }
      }
    });
    if (!params.username || !params.password) return null;
    return {
      type: "xtream",
      server: m[1],
      username: params.username,
      password: params.password
    };
  }
  function Xtream(cfg) {
    var base = normalizeServer(cfg.server);
    var user = String(cfg.username || "").trim();
    var pass = String(cfg.password || "").trim();
    var auth = "username=" + encodeURIComponent(user) + "&password=" + encodeURIComponent(pass);
    function api(action, extra) {
      return base + "/player_api.php?" + auth + (action ? "&action=" + action : "") + (extra || "");
    }
    function list(json) {
      return Array.isArray(json) ? json : [];
    }
    function firstImage(v) {
      if (Array.isArray(v)) {
        for (var i = 0; i < v.length; i++) if (/^https?:/.test(v[i])) return v[i];
        return null;
      }
      return typeof v === "string" && /^https?:/.test(v) ? v : null;
    }
    var self = {
      base,
      liveUrl: function liveUrl(id, ext) {
        return base + "/live/" + user + "/" + pass + "/" + id + "." + ext;
      },
      movieUrl: function movieUrl(id, ext) {
        return base + "/movie/" + user + "/" + pass + "/" + id + "." + ext;
      },
      episodeUrl: function episodeUrl(id, ext) {
        return base + "/series/" + user + "/" + pass + "/" + id + "." + ext;
      },
      m3uUrl: function m3uUrl(fmt) {
        return base + "/get.php?" + auth + "&type=m3u_plus&output=" + fmt;
      },
      xmltvUrl: function xmltvUrl() {
        return base + "/xmltv.php?" + auth;
      },
      login: function login() {
        return httpJson(api(), 3e4).then(function (root2) {
          var info = root2 && root2.user_info;
          if (!info) {
            var e = new Error("Serwer nie odpowiada jak panel Xtream. Sprawdź adres.");
            e.notXtream = true;
            throw e;
          }
          if (String(info.auth) !== "1") {
            var a = new Error("Nieprawidłowy login lub hasło.");
            a.auth = true;
            throw a;
          }
          var exp = parseInt(str(info, "exp_date"), 10);
          return {
            status: str(info, "status"),
            expiresAt: isNaN(exp) ? null : exp * 1e3,
            maxConnections: str(info, "max_connections"),
            activeConnections: str(info, "active_cons"),
            isTrial: str(info, "is_trial") === "1"
          };
        });
      },
      categories: function categories(action) {
        return httpJson(api(action)).then(function (j) {
          return list(j).filter(function (o) {
            return o && str(o, "category_id");
          }).map(function (o) {
            return {
              id: str(o, "category_id"),
              name: str(o, "category_name") || "Bez nazwy"
            };
          });
        });
      },
      live: function live(ext) {
        return httpJson(api("get_live_streams"), 12e4).then(function (j) {
          return list(j).filter(function (o) {
            return o && str(o, "stream_id");
          }).map(function (o) {
            var id = str(o, "stream_id");
            return {
              id,
              name: str(o, "name") || "Kanał " + id,
              logo: str(o, "stream_icon"),
              categoryId: str(o, "category_id") || "",
              epgId: str(o, "epg_channel_id"),
              url: self.liveUrl(id, ext),
              num: parseInt(str(o, "num"), 10) || 0
            };
          });
        });
      },
      movies: function movies() {
        return httpJson(api("get_vod_streams"), 12e4).then(function (j) {
          return list(j).filter(function (o) {
            return o && str(o, "stream_id");
          }).map(function (o) {
            var id = str(o, "stream_id");
            var r = str(o, "rating");
            return {
              id,
              name: str(o, "name") || "Film " + id,
              poster: str(o, "stream_icon"),
              categoryId: str(o, "category_id") || "",
              rating: r === "0" ? null : r,
              url: self.movieUrl(id, str(o, "container_extension") || "mp4"),
              added: parseInt(str(o, "added"), 10) || 0
            };
          });
        });
      },
      series: function series() {
        return httpJson(api("get_series"), 12e4).then(function (j) {
          return list(j).filter(function (o) {
            return o && str(o, "series_id");
          }).map(function (o) {
            var id = str(o, "series_id");
            var r = str(o, "rating");
            var date = str(o, "releaseDate") || str(o, "release_date");
            return {
              id,
              name: str(o, "name") || "Serial " + id,
              poster: str(o, "cover"),
              categoryId: str(o, "category_id") || "",
              rating: r === "0" ? null : r,
              plot: str(o, "plot"),
              genre: str(o, "genre"),
              year: date ? date.slice(0, 4) : null
            };
          });
        });
      },
      movieDetail: function movieDetail(movie) {
        return httpJson(api("get_vod_info", "&vod_id=" + movie.id)).then(function (root2) {
          var info = root2 && root2.info || {};
          var date = str(info, "releasedate") || str(info, "release_date");
          var r = str(info, "rating");
          return {
            plot: str(info, "plot") || str(info, "description"),
            cast: str(info, "cast") || str(info, "actors"),
            director: str(info, "director"),
            genre: str(info, "genre"),
            year: date ? date.slice(0, 4) : null,
            duration: str(info, "duration"),
            backdrop: firstImage(info.backdrop_path) || str(info, "movie_image"),
            rating: r && r !== "0" ? r : movie.rating
          };
        });
      },
      seriesDetail: function seriesDetail(series) {
        return httpJson(api("get_series_info", "&series_id=" + series.id)).then(function (root2) {
          root2 = root2 || {};
          var info = root2.info || {};
          var seasons = {};
          function add(o, hint) {
            var id = str(o, "id");
            if (!id) return;
            var season = parseInt(str(o, "season"), 10) || hint;
            var arr = seasons[season] || (seasons[season] = []);
            var num = parseInt(str(o, "episode_num"), 10) || arr.length + 1;
            var ei = o.info || {};
            arr.push({
              id,
              season,
              number: num,
              title: str(o, "title") || "Odcinek " + num,
              plot: str(ei, "plot"),
              image: str(ei, "movie_image"),
              duration: str(ei, "duration"),
              url: self.episodeUrl(id, str(o, "container_extension") || "mp4")
            });
          }
          var eps = root2.episodes;
          if (Array.isArray(eps)) {
            eps.forEach(function (x, i) {
              if (Array.isArray(x)) x.forEach(function (o) {
                add(o, i + 1);
              });else if (x) add(x, 1);
            });
          } else if (eps && _typeof(eps) === "object") {
            Object.keys(eps).forEach(function (k) {
              (eps[k] || []).forEach(function (o) {
                add(o, parseInt(k, 10) || 1);
              });
            });
          }
          Object.keys(seasons).forEach(function (k) {
            seasons[k].sort(function (a, b) {
              return a.number - b.number;
            });
          });
          var date = str(info, "releaseDate") || str(info, "release_date");
          return {
            plot: str(info, "plot") || series.plot,
            cast: str(info, "cast"),
            genre: str(info, "genre") || series.genre,
            year: date ? date.slice(0, 4) : series.year,
            backdrop: firstImage(info.backdrop_path) || str(info, "cover"),
            seasons
          };
        });
      },
      shortEpg: function shortEpg(streamId, limit) {
        return httpJson(api("get_short_epg", "&stream_id=" + streamId + "&limit=" + (limit || 6)), 2e4).then(function (root2) {
          var arr = root2 && root2.epg_listings || [];
          return arr.map(function (o) {
            var start = parseInt(str(o, "start_timestamp"), 10);
            var end = parseInt(str(o, "stop_timestamp") || str(o, "end_timestamp"), 10);
            if (isNaN(start) || isNaN(end)) return null;
            return {
              title: b64decode(str(o, "title")) || "",
              description: b64decode(str(o, "description")),
              start: start * 1e3,
              end: end * 1e3
            };
          }).filter(Boolean);
        });
      }
    };
    return self;
  }
  var ATTR = /([A-Za-z0-9_-]+)="([^"]*)"/g;
  var VIDEO_EXT = [".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".flv"];
  var EPISODE = /^(.*?)[\s._\-|:]*[Ss](\d{1,2})[\s._-]*[Ee](\d{1,3})/;
  function attrs(line) {
    var out = {};
    var m;
    ATTR.lastIndex = 0;
    while (m = ATTR.exec(line)) out[m[1].toLowerCase()] = m[2];
    return out;
  }
  function nameOf(line) {
    var from = line.indexOf(":");
    var m;
    ATTR.lastIndex = 0;
    while (m = ATTR.exec(line)) from = m.index + m[0].length - 1;
    var comma = line.indexOf(",", from + 1);
    return comma >= 0 ? line.slice(comma + 1).trim() : "";
  }
  function parseM3u(text) {
    var lines = text.split(/\r?\n/);
    var entries = [];
    var epgUrl = null;
    var info = null;
    var group = null;
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (!line) continue;
      if (line.indexOf("#EXTM3U") === 0) {
        var a = attrs(line);
        epgUrl = a["x-tvg-url"] || a["url-tvg"] || a["tvg-url"] || null;
        if (epgUrl) epgUrl = epgUrl.split(",")[0].trim() || null;
      } else if (line.indexOf("#EXTINF") === 0) {
        info = line;
        group = null;
      } else if (line.indexOf("#EXTGRP:") === 0) {
        group = line.slice(8).trim();
      } else if (line.charAt(0) === "#") {} else if (info) {
        var at = attrs(info);
        entries.push({
          name: nameOf(info) || at["tvg-name"] || "Bez nazwy",
          url: line,
          group: at["group-title"] && at["group-title"].trim() || group || "Inne",
          logo: at["tvg-logo"] || null,
          tvgId: at["tvg-id"] || null
        });
        info = null;
        group = null;
      }
    }
    return {
      epgUrl,
      entries
    };
  }
  function isVideoFile(url) {
    var p = url.split("?")[0].toLowerCase();
    return VIDEO_EXT.some(function (e) {
      return p.slice(-e.length) === e;
    });
  }
  function isRadioName(name) {
    var n = String(name || "");
    return /radio/i.test(n) || n.indexOf("راديو") >= 0 || n.indexOf("إذاع") >= 0;
  }
  function isSeriesEntry(e) {
    return e.url.indexOf("/series/") >= 0 || isVideoFile(e.url) && EPISODE.test(e.name);
  }
  function isMovieEntry(e) {
    return !isSeriesEntry(e) && (e.url.indexOf("/movie/") >= 0 || isVideoFile(e.url));
  }
  function hashId(s) {
    var h = 0;
    for (var i = 0; i < s.length; i++) h = (h << 5) - h + s.charCodeAt(i) | 0;
    return (h >>> 0).toString(16);
  }
  function catalogFromM3u(playlist) {
    var liveCats = [],
      movieCats = [],
      seriesCats = [];
    var seenL = {},
      seenM = {},
      seenS = {};
    var live = [],
      movies = [],
      seriesMap = {},
      seriesList = [],
      episodes = {};
    playlist.entries.forEach(function (e, idx) {
      var id = hashId(e.url) + "-" + idx;
      if (isSeriesEntry(e)) {
        var m = e.name.match(EPISODE);
        var title = m ? m[1].replace(/[-|:.\s]+$/, "").trim() || e.group : e.name;
        var season = m ? parseInt(m[2], 10) || 1 : 1;
        var number = m ? parseInt(m[3], 10) || idx + 1 : idx + 1;
        var sid = "m" + hashId(normalize(title));
        if (!seriesMap[sid]) {
          seriesMap[sid] = {
            id: sid,
            name: title,
            poster: e.logo,
            categoryId: e.group,
            rating: null,
            plot: null,
            genre: null,
            year: null
          };
          seriesList.push(seriesMap[sid]);
          if (!seenS[e.group]) {
            seenS[e.group] = 1;
            seriesCats.push({
              id: e.group,
              name: e.group
            });
          }
        }
        (episodes[sid] || (episodes[sid] = [])).push({
          id,
          season,
          number,
          title: e.name,
          plot: null,
          image: e.logo,
          duration: null,
          url: e.url
        });
      } else if (isMovieEntry(e)) {
        if (!seenM[e.group]) {
          seenM[e.group] = 1;
          movieCats.push({
            id: e.group,
            name: e.group
          });
        }
        movies.push({
          id,
          name: e.name,
          poster: e.logo,
          categoryId: e.group,
          rating: null,
          url: e.url,
          added: 0
        });
      } else {
        if (!seenL[e.group]) {
          seenL[e.group] = 1;
          liveCats.push({
            id: e.group,
            name: e.group
          });
        }
        live.push({
          id,
          name: e.name,
          logo: e.logo,
          categoryId: e.group,
          epgId: e.tvgId || e.name,
          url: e.url,
          num: 0
        });
      }
    });
    return {
      liveCategories: liveCats,
      live,
      movieCategories: movieCats,
      movies,
      seriesCategories: seriesCats,
      series: seriesList,
      m3uEpisodes: episodes,
      epgUrl: playlist.epgUrl
    };
  }
  function fetchM3u(url) {
    return httpText(url, 18e4).then(function (text) {
      var t = text.replace(/^﻿/, "").trim();
      if (t.indexOf("#EXTM3U") !== 0 && t.indexOf("#EXTINF") < 0) {
        var e = new Error("Pod tym linkiem nie ma playlisty M3U.");
        e.notM3u = true;
        throw e;
      }
      return parseM3u(t);
    });
  }
  var RADIO_HOSTS = ["https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info", "https://fi1.api.radio-browser.info"];
  function radioGet(path, i) {
    i = i || 0;
    return httpJson(RADIO_HOSTS[i] + path, 2e4).then(function (arr) {
      return (Array.isArray(arr) ? arr : []).map(function (o) {
        var url = str(o, "url_resolved") || str(o, "url");
        if (!url) return null;
        var tags = str(o, "tags");
        return {
          id: str(o, "stationuuid") || url,
          name: str(o, "name") || "Radio",
          url,
          logo: str(o, "favicon"),
          tags: tags ? tags.split(",").slice(0, 3).join(" · ") : null
        };
      }).filter(Boolean);
    }, function (err) {
      if (i + 1 < RADIO_HOSTS.length) return radioGet(path, i + 1);
      throw err;
    });
  }
  var RadioBrowser = {
    top: function top(country) {
      return radioGet("/json/stations/bycountrycodeexact/" + country + "?order=clickcount&reverse=true&hidebroken=true&limit=150");
    },
    search: function search(q2) {
      return radioGet("/json/stations/search?name=" + encodeURIComponent(q2) + "&order=clickcount&reverse=true&hidebroken=true&limit=80");
    }
  };

  // src/repo.js
  var listeners = [];
  var repo = {
    source: store.get("source", null),
    settings: Object.assign({
      liveFormat: "ts",
      hideAdult: false,
      radioCountry: "PL",
      showCategories: null
    }, store.get("settings", {})),
    favorites: store.get("favorites", []),
    recent: store.get("recent", []),
    progress: store.get("progress", {}),
    account: null,
    catalog: null,
    loading: false,
    error: null,
    epgCache: {},
    detailCache: {},
    onChange: function onChange(fn) {
      listeners.push(fn);
    },
    emit: function emit(what) {
      listeners.forEach(function (fn) {
        fn(what);
      });
    },
    saveSettings: function saveSettings() {
      store.set("settings", this.settings);
    },
    api: function api() {
      return this.source && this.source.type === "xtream" ? Xtream(this.source) : null;
    },
    // ---------------- Logowanie ----------------
    /** Sprawdza dane (z automatycznymi próbami http i M3U), zapisuje i wczytuje katalog. */
    signIn: function signIn(input) {
      var self = this;
      var p;
      if (input.type === "xtream") {
        if (!input.server || !input.username) return Promise.reject(new Error("Uzupełnij adres serwera i login."));
        p = connectXtream(Object.assign({}, input, {
          server: normalizeServer(input.server)
        }), self);
      } else {
        if (!/^https?:\/\//i.test(input.m3uUrl || "")) return Promise.reject(new Error("Podaj pełny link do playlisty (http…)."));
        p = Promise.resolve(input);
      }
      return p.then(function (cfg) {
        self.source = cfg;
        store.set("source", cfg);
        return self.load(true);
      }).then(function () {
        if (self.error) throw new Error(self.error);
      });
    },
    signOut: function signOut() {
      this.source = null;
      this.catalog = null;
      this.account = null;
      this.epgCache = {};
      this.detailCache = {};
      store.set("source", null);
    },
    // ---------------- Katalog ----------------
    load: function load(force) {
      var self = this;
      if (!self.source) return Promise.resolve();
      if (self.catalog && !force) return Promise.resolve();
      if (self._loading) return self._loading;
      self.loading = true;
      self.error = null;
      self.emit("loading");
      var cfg = self.source;
      var job;
      if (cfg.type === "xtream") {
        var api = Xtream(cfg);
        job = Promise.all([api.login().catch(function () {
          return null;
        }), api.categories("get_live_categories"), api.live(self.settings.liveFormat), api.categories("get_vod_categories"), api.movies(), api.categories("get_series_categories"), api.series()]).then(function (r) {
          if (r[0]) self.account = r[0];
          return {
            liveCategories: r[1],
            live: r[2],
            movieCategories: r[3],
            movies: r[4].sort(function (a, b) {
              return b.added - a.added;
            }),
            seriesCategories: r[5],
            series: r[6],
            m3uEpisodes: {},
            epgUrl: null
          };
        });
      } else {
        job = fetchM3u(cfg.m3uUrl).then(function (pl) {
          if (!pl.entries.length) throw new Error("Playlista jest pusta.");
          return catalogFromM3u(pl);
        });
      }
      self._loading = job.then(function (cat) {
        if (self.settings.hideAdult) cat = withoutAdult(cat);
        var radioCats = {};
        cat.liveCategories.forEach(function (c) {
          if (isRadioName(c.name)) radioCats[c.id] = 1;
        });
        cat.radioChannels = cat.live.filter(function (ch) {
          return radioCats[ch.categoryId];
        });
        self.catalog = cat;
        self.detailCache = {};
      }).catch(function (e) {
        self.error = friendlyError(e);
      }).then(function () {
        self.loading = false;
        self._loading = null;
        self.emit("catalog");
      });
      return self._loading;
    },
    findMovie: function findMovie(id) {
      return this.catalog ? this.catalog.movies.find(function (m) {
        return m.id === id;
      }) : null;
    },
    findSeries: function findSeries(id) {
      return this.catalog ? this.catalog.series.find(function (s) {
        return s.id === id;
      }) : null;
    },
    movieDetail: function movieDetail(movie) {
      var key = "m" + movie.id;
      var self = this;
      if (self.detailCache[key]) return Promise.resolve(self.detailCache[key]);
      var api = self.api();
      var empty = {
        rating: movie.rating
      };
      var p = api ? api.movieDetail(movie).catch(function () {
        return empty;
      }) : Promise.resolve(empty);
      return p.then(function (d) {
        self.detailCache[key] = d;
        return d;
      });
    },
    seriesDetail: function seriesDetail(series) {
      var key = "s" + series.id;
      var self = this;
      if (self.detailCache[key]) return Promise.resolve(self.detailCache[key]);
      var api = self.api();
      var p;
      if (api) {
        p = api.seriesDetail(series);
      } else {
        var eps = self.catalog && self.catalog.m3uEpisodes[series.id] || [];
        var seasons = {};
        eps.forEach(function (e) {
          (seasons[e.season] || (seasons[e.season] = [])).push(e);
        });
        Object.keys(seasons).forEach(function (k) {
          seasons[k].sort(function (a, b) {
            return a.number - b.number;
          });
        });
        p = Promise.resolve({
          seasons
        });
      }
      return p.then(function (d) {
        self.detailCache[key] = d;
        return d;
      });
    },
    // ---------------- Program TV ----------------
    /** Program dla kanału (Xtream, krótkie EPG z serwera; zapamiętywane na 10 minut). */
    programs: function programs(item2) {
      var self = this;
      var api = self.api();
      if (!api || !item2 || item2.kind !== "live") return Promise.resolve([]);
      var id = item2.key.slice(5);
      var c = self.epgCache[id];
      var now = Date.now();
      if (c && now - c.at < 6e5) return Promise.resolve(c.list.filter(function (p) {
        return p.end > now;
      }));
      return api.shortEpg(id, 6).then(function (list) {
        self.epgCache[id] = {
          at: Date.now(),
          list
        };
        return list.filter(function (p) {
          return p.end > Date.now();
        });
      }).catch(function () {
        return [];
      });
    },
    // ---------------- Ulubione / historia / postęp ----------------
    isFavorite: function isFavorite(key) {
      return this.favorites.some(function (f) {
        return f.key === key;
      });
    },
    toggleFavorite: function toggleFavorite(item2) {
      var i = -1;
      this.favorites.forEach(function (f, idx) {
        if (f.key === item2.key) i = idx;
      });
      if (i >= 0) this.favorites.splice(i, 1);else this.favorites.unshift(item2);
      store.set("favorites", this.favorites);
      return i < 0;
    },
    addRecent: function addRecent(item2) {
      if (item2.kind !== "live" && item2.kind !== "radio") return;
      this.recent = [item2].concat(this.recent.filter(function (r) {
        return r.key !== item2.key;
      })).slice(0, 30);
      store.set("recent", this.recent);
    },
    saveProgress: function saveProgress(item2, pos, dur) {
      if (item2.kind === "live" || item2.kind === "radio" || !(dur > 0)) return;
      this.progress[item2.key] = {
        item: item2,
        pos,
        dur,
        at: Date.now()
      };
      var keys = Object.keys(this.progress);
      if (keys.length > 300) {
        var self = this;
        keys.sort(function (a, b) {
          return self.progress[a].at - self.progress[b].at;
        });
        keys.slice(0, keys.length - 300).forEach(function (k) {
          delete self.progress[k];
        });
      }
      store.set("progress", this.progress);
    },
    progressOf: function progressOf(key) {
      var p = this.progress[key];
      if (!p) return null;
      return {
        pos: p.pos,
        dur: p.dur,
        at: p.at,
        item: p.item,
        fraction: p.dur > 0 ? Math.min(1, p.pos / p.dur) : 0,
        finished: p.dur > 0 && p.pos >= p.dur * 0.95
      };
    },
    clearHistory: function clearHistory() {
      this.progress = {};
      this.recent = [];
      store.set("progress", {});
      store.set("recent", []);
    },
    // ---------------- Show ----------------
    allShowCandidates: function allShowCandidates() {
      var c = this.catalog;
      if (!c) return [];
      return c.movieCategories.map(function (x) {
        return {
          key: "movie:" + x.id,
          id: x.id,
          name: x.name,
          isSeries: false
        };
      }).concat(c.seriesCategories.map(function (x) {
        return {
          key: "series:" + x.id,
          id: x.id,
          name: x.name,
          isSeries: true
        };
      }));
    },
    autoShowKeys: function autoShowKeys() {
      return this.allShowCandidates().filter(function (cat) {
        var n = normalize(cat.name);
        return SHOW_WORDS.some(function (w) {
          return n.indexOf(w) >= 0;
        });
      }).map(function (c) {
        return c.key;
      });
    },
    showCategories: function showCategories() {
      var keys = this.settings.showCategories || this.autoShowKeys();
      return this.allShowCandidates().filter(function (c) {
        return keys.indexOf(c.key) >= 0;
      });
    },
    // ---------------- Wyszukiwanie ----------------
    search: function search(q2) {
      var c = this.catalog;
      var n = normalize(q2.trim());
      if (!c || n.length < 2) return {
        live: [],
        movies: [],
        series: []
      };
      function pick(arr) {
        var out = [];
        for (var i = 0; i < arr.length && out.length < 40; i++) if (normalize(arr[i].name).indexOf(n) >= 0) out.push(arr[i]);
        return out;
      }
      return {
        live: pick(c.live),
        movies: pick(c.movies),
        series: pick(c.series)
      };
    }
  };
  var SHOW_WORDS = ["show", "program", "reality", "talk", "rozrywk", "entertain", "dokument", "documentar", "kabaret", "stand-up", "stand up", "standup", "teleturniej", "quiz", "magazyn", "lifestyle", "kulinar", "cooking", "travel", "podroz", "variety", "برامج", "منوعات", "وثائقي", "عروض"];
  var ADULT = ["xxx", "adult", "18+", "+18", "porn", "erotic", "erotyk", "dla doroslych"];
  function isAdult(name) {
    var n = normalize(name);
    return ADULT.some(function (w) {
      return n.indexOf(w) >= 0;
    });
  }
  function withoutAdult(c) {
    function split(cats) {
      var blocked = {};
      var kept = cats.filter(function (x) {
        if (isAdult(x.name)) {
          blocked[x.id] = 1;
          return false;
        }
        return true;
      });
      return {
        kept,
        blocked
      };
    }
    var l = split(c.liveCategories),
      m = split(c.movieCategories),
      s = split(c.seriesCategories);
    return Object.assign({}, c, {
      liveCategories: l.kept,
      live: c.live.filter(function (x) {
        return !l.blocked[x.categoryId] && !isAdult(x.name);
      }),
      movieCategories: m.kept,
      movies: c.movies.filter(function (x) {
        return !m.blocked[x.categoryId];
      }),
      seriesCategories: s.kept,
      series: c.series.filter(function (x) {
        return !s.blocked[x.categoryId];
      })
    });
  }
  function friendlyError(e) {
    if (!e) return "Wystąpił nieznany błąd.";
    if (e.isHttp && e.status === 0) return e.message === "Brak połączenia z serwerem" ? "Nie można połączyć się z serwerem. Sprawdź adres (http/https, port) i internet." : e.message;
    return e.message || "Wystąpił nieznany błąd.";
  }
  function connectXtream(cfg, self) {
    var servers = [cfg.server];
    if (/^https:\/\//i.test(cfg.server)) servers.push("http://" + cfg.server.slice(8));
    var reachable = null;
    var lastError = null;
    function tryServer(i) {
      if (i >= servers.length) return fallbackM3u();
      var candidate = Object.assign({}, cfg, {
        server: servers[i]
      });
      return Xtream(candidate).login().then(function (acc) {
        self.account = acc;
        return candidate;
      }, function (e) {
        lastError = e;
        if (e.auth) throw e;
        if (e.isHttp && e.status > 0 || e.notXtream || e.message === "Nieprawidłowa odpowiedź serwera") reachable = reachable || servers[i];
        return tryServer(i + 1);
      });
    }
    function fallbackM3u() {
      if (!reachable) throw new Error(friendlyError(lastError));
      var api = Xtream(Object.assign({}, cfg, {
        server: reachable
      }));
      var url = api.m3uUrl(self.settings.liveFormat);
      return httpText(url, 6e4).then(function (t) {
        if (t.replace(/^﻿/, "").trim().indexOf("#EXTM3U") !== 0) throw new Error("x");
        return Object.assign({}, cfg, {
          type: "m3u",
          server: reachable,
          m3uUrl: url,
          epgUrl: cfg.epgUrl || api.xmltvUrl()
        });
      }).catch(function () {
        var code = lastError && lastError.isHttp && lastError.status ? " (HTTP " + lastError.status + ")" : "";
        throw new Error("Serwer odpowiada, ale nie przyjmuje logowania pod tym adresem" + code + ". Sprawdź dane i adres serwera albo użyj linku M3U.");
      });
    }
    return tryServer(0);
  }

  // src/player.js
  function avplayEngine() {
    var av = window.webapis.avplay;
    var paused = false;
    return {
      name: "avplay",
      open: function open(url, startMs, video, cb) {
        try {
          av.stop();
        } catch (e) {}
        try {
          av.close();
        } catch (e) {}
        paused = false;
        try {
          av.open(url);
          av.setListener({
            onbufferingstart: function onbufferingstart() {
              cb.onBuffering(true);
            },
            onbufferingprogress: function onbufferingprogress() {},
            onbufferingcomplete: function onbufferingcomplete() {
              cb.onBuffering(false);
            },
            onstreamcompleted: function onstreamcompleted() {
              cb.onEnd();
            },
            oncurrentplaytime: function oncurrentplaytime() {},
            onevent: function onevent() {},
            onerror: function onerror(e) {
              cb.onError(String(e));
            },
            onsubtitlechange: function onsubtitlechange() {},
            ondrmevent: function ondrmevent() {}
          });
          if (video) {
            av.setDisplayRect(0, 0, 1920, 1080);
            try {
              av.setDisplayMethod("PLAYER_DISPLAY_MODE_LETTER_BOX");
            } catch (e) {}
          } else {
            try {
              av.setDisplayRect(0, 0, 1, 1);
            } catch (e) {}
          }
          cb.onBuffering(true);
          av.prepareAsync(function () {
            function go() {
              try {
                av.play();
              } catch (e) {
                cb.onError(String(e));
                return;
              }
              cb.onBuffering(false);
              cb.onReady();
            }
            if (startMs > 0) {
              try {
                av.seekTo(startMs, go, go);
              } catch (e) {
                go();
              }
            } else {
              go();
            }
          }, function (err) {
            cb.onError(String(err && err.name || err));
          });
        } catch (e) {
          cb.onError(String(e && e.name || e));
        }
      },
      stop: function stop() {
        try {
          av.stop();
        } catch (e) {}
        try {
          av.close();
        } catch (e) {}
      },
      pause: function pause() {
        try {
          av.pause();
          paused = true;
        } catch (e) {}
      },
      resume: function resume() {
        try {
          av.play();
          paused = false;
        } catch (e) {}
      },
      isPaused: function isPaused() {
        return paused;
      },
      time: function time() {
        try {
          return av.getCurrentTime() || 0;
        } catch (e) {
          return 0;
        }
      },
      duration: function duration() {
        try {
          return av.getDuration() || 0;
        } catch (e) {
          return 0;
        }
      },
      seek: function seek(ms) {
        try {
          av.seekTo(Math.max(0, ms), function () {}, function () {});
        } catch (e) {}
      },
      suspend: function suspend() {
        try {
          av.suspend();
        } catch (e) {}
      },
      restore: function restore() {
        try {
          av.restore();
        } catch (e) {}
      }
    };
  }
  function htmlEngine() {
    var v = document.getElementById("html-player");
    var handlers = null;
    function on(name, fn) {
      v.addEventListener(name, function (e) {
        if (handlers) fn(e);
      });
    }
    on("waiting", function () {
      handlers.onBuffering(true);
    });
    on("playing", function () {
      handlers.onBuffering(false);
    });
    on("ended", function () {
      handlers.onEnd();
    });
    on("error", function () {
      handlers.onError("HTML5_MEDIA_ERROR");
    });
    return {
      name: "html5",
      open: function open(url, startMs, video, cb) {
        handlers = cb;
        v.className = video ? "on" : "";
        cb.onBuffering(true);
        v.src = url;
        v.onloadedmetadata = function () {
          if (startMs > 0) v.currentTime = startMs / 1e3;
          var p = v.play();
          if (p && p.catch) p.catch(function () {});
          cb.onBuffering(false);
          cb.onReady();
          v.onloadedmetadata = null;
        };
      },
      stop: function stop() {
        handlers = null;
        v.pause();
        v.removeAttribute("src");
        v.load();
        v.className = "";
      },
      pause: function pause() {
        v.pause();
      },
      resume: function resume() {
        var p = v.play();
        if (p && p.catch) p.catch(function () {});
      },
      isPaused: function isPaused() {
        return v.paused;
      },
      time: function time() {
        return (v.currentTime || 0) * 1e3;
      },
      duration: function duration() {
        return isFinite(v.duration) ? v.duration * 1e3 : 0;
      },
      seek: function seek(ms) {
        v.currentTime = Math.max(0, ms / 1e3);
      },
      suspend: function suspend() {
        v.pause();
      },
      restore: function restore() {}
    };
  }
  var engine = null;
  function getEngine() {
    if (!engine) engine = window.webapis && window.webapis.avplay ? avplayEngine() : htmlEngine();
    return engine;
  }
  var root = null;
  var queue = [];
  var index = 0;
  var item = null;
  var active = false;
  var mode = "play";
  var osdTimer = null;
  var progressTimer = null;
  var tickTimer = null;
  var liveRetries = 0;
  var retryTimer = null;
  var buffering = false;
  var numberBuffer = "";
  var numberTimer = null;
  var programs = [];
  var onExitCb = null;
  function build() {
    if (root) return;
    root = el('<div id="player" class="hidden"><div class="p-top"><div class="p-logo"></div><div class="p-info"><div class="p-title"></div><div class="p-sub"></div><div class="p-now"></div><div class="p-next"></div></div><div class="p-clock"></div></div><div class="p-spinner"></div><div class="p-paused">II</div><div class="p-num"></div><div class="p-error hidden"></div><div class="p-bottom"><div class="p-bar"><div class="p-fill"></div></div><div class="p-times"><span class="p-cur"></span><span class="p-hint"></span><span class="p-dur"></span></div></div><div class="p-list hidden"><div class="p-list-title">Kanały</div><div class="p-list-items scroll"></div></div></div>');
    document.getElementById("app").appendChild(root);
  }
  function q(sel) {
    return root.querySelector(sel);
  }
  function isLive() {
    return item && item.kind === "live";
  }
  var Player = {
    isActive: function isActive() {
      return active;
    },
    /** Odtwarza element z kolejki (np. listy kanałów lub odcinków). startMs: -1 = wznów, 0 = od początku. */
    play: function play(items, i, startMs, onExit) {
      if (!items || !items.length) return;
      Radio.stop();
      build();
      queue = items;
      onExitCb = onExit || null;
      if (!active) {
        active = true;
        document.body.classList.add("playing");
        root.className = "";
        nav.push(root, {
          onBack: function onBack() {
            Player.exit();
          }
        });
        progressTimer = setInterval(saveProgress, 1e4);
        tickTimer = setInterval(updateOsd, 1e3);
      }
      openAt(i, startMs === void 0 ? -1 : startMs);
    },
    exit: function exit() {
      if (!active) return;
      saveProgress();
      getEngine().stop();
      clearInterval(progressTimer);
      clearInterval(tickTimer);
      clearTimeout(retryTimer);
      clearTimeout(osdTimer);
      active = false;
      if (mode !== "play") nav.pop();
      mode = "play";
      root.className = "hidden";
      document.body.classList.remove("playing");
      nav.pop();
      repo.emit("progress");
      if (onExitCb) onExitCb();
    },
    /** Zwraca true, jeśli klawisz został obsłużony przez odtwarzacz. */
    handleKey: function handleKey(name) {
      if (!active) return false;
      if (mode === "list" || mode === "error") {
        if (mode === "list" && (name === "chup" || name === "chdown")) {
          closeList();
          return Player.handleKey(name);
        }
        nav.handleKey(name);
        return true;
      }
      var live = isLive();
      if (name.charAt(0) === "d" && name.length === 2 && live) {
        typeNumber(name.charAt(1));
        return true;
      }
      switch (name) {
        case "back":
        case "stop":
          Player.exit();
          return true;
        case "up":
        case "chup":
          if (live || name === "chup") {
            zap(-1);
            return true;
          }
          showOsd();
          return true;
        case "down":
        case "chdown":
          if (live || name === "chdown") {
            zap(1);
            return true;
          }
          showOsd();
          return true;
        case "left":
          if (live) {
            openList();
            return true;
          }
          seekBy(-1e4);
          return true;
        case "right":
          if (live) {
            openList();
            return true;
          }
          seekBy(1e4);
          return true;
        case "rw":
          seekBy(-6e4);
          return true;
        case "ff":
          seekBy(6e4);
          return true;
        case "chlist":
          openList();
          return true;
        case "enter":
        case "playpause":
          if (live && name === "enter") {
            showOsd();
            return true;
          }
          togglePause();
          return true;
        case "play":
          if (getEngine().isPaused()) togglePause();
          return true;
        case "pause":
          if (!getEngine().isPaused()) togglePause();
          return true;
        case "red":
          toggleFav();
          return true;
        default:
          showOsd();
          return true;
      }
    },
    onVisibility: function onVisibility(hidden) {
      if (!active) return;
      if (hidden) {
        saveProgress();
        getEngine().suspend();
      } else {
        getEngine().restore();
      }
    }
  };
  function openAt(i, startMs) {
    saveProgress();
    index = Math.max(0, Math.min(queue.length - 1, i));
    item = queue[index];
    liveRetries = 0;
    clearTimeout(retryTimer);
    hideError();
    repo.addRecent(item);
    var start = 0;
    if (item.kind !== "live") {
      if (startMs >= 0) start = startMs;else {
        var p = repo.progressOf(item.key);
        start = p && !p.finished ? p.pos : 0;
      }
    }
    renderInfo();
    showOsd();
    startEngine(start);
    loadPrograms();
  }
  function startEngine(startMs) {
    var current2 = item;
    getEngine().open(current2.url, startMs, true, {
      onReady: function onReady() {
        if (item === current2) {
          liveRetries = 0;
          updateOsd();
        }
      },
      onBuffering: function onBuffering(b) {
        if (item === current2) {
          buffering = b;
          root.classList.toggle("buffering", b);
        }
      },
      onEnd: function onEnd() {
        if (item !== current2) return;
        if (current2.kind === "live") return onFail("Transmisja została przerwana.");
        var d = getEngine().duration();
        if (d > 0) repo.saveProgress(current2, d, d);
        if (index < queue.length - 1) openAt(index + 1, 0);else Player.exit();
      },
      onError: function onError(code) {
        if (item !== current2) return;
        onFail(code);
      }
    });
  }
  function onFail(code) {
    if (isLive() && liveRetries < 3) {
      liveRetries++;
      root.classList.add("buffering");
      retryTimer = setTimeout(function () {
        startEngine(0);
      }, 1500 * liveRetries);
      return;
    }
    showError(describe(code));
  }
  function describe(code) {
    var c = String(code || "");
    if (/NETWORK|CONNECTION|TIMEOUT/i.test(c)) return "Brak połączenia z serwerem albo serwer odrzucił połączenie (np. limit urządzeń).";
    if (/UNSUPPORTED|NOT_SUPPORT|CODEC|FORMAT/i.test(c)) return "Telewizor nie obsługuje formatu tego materiału.";
    if (/HTML5_MEDIA_ERROR/.test(c)) return "Nie udało się odtworzyć tego materiału.";
    return "Nie udało się odtworzyć (" + esc(c || "nieznany błąd") + ").";
  }
  function showError(text) {
    var box = q(".p-error");
    box.innerHTML = '<div class="p-error-text">' + esc(text) + '</div><div class="row-btns"><div class="btn f" data-a="retry">Spróbuj ponownie</div>' + (queue.length > 1 ? '<div class="btn f" data-a="next">' + (isLive() ? "Następny kanał" : "Następny") + "</div>" : "") + '<div class="btn f" data-a="exit">Wyjdź</div></div>';
    box.className = "p-error";
    root.classList.remove("buffering");
    box.querySelectorAll(".btn").forEach(function (b) {
      b.onEnter = function () {
        var a = b.getAttribute("data-a");
        hideError();
        if (a === "retry") openAt(index, -1);else if (a === "next") openAt((index + 1) % queue.length, 0);else Player.exit();
      };
    });
    if (mode === "list") closeList();
    mode = "error";
    nav.push(box, {
      onBack: function onBack() {
        hideError();
        Player.exit();
      }
    });
    nav.focusFirst(box);
    showOsd(true);
  }
  function hideError() {
    if (mode === "error") {
      mode = "play";
      nav.pop();
    }
    if (root) q(".p-error").className = "p-error hidden";
  }
  function zap(dir) {
    if (queue.length < 2) return;
    var n = (index + dir + queue.length) % queue.length;
    openAt(n, isLive() ? -1 : 0);
  }
  function seekBy(ms) {
    if (isLive()) return;
    var e = getEngine();
    var d = e.duration();
    var t = Math.max(0, Math.min(d > 0 ? d - 3e3 : Infinity, e.time() + ms));
    e.seek(t);
    showOsd();
    updateOsd(t);
  }
  function togglePause() {
    var e = getEngine();
    if (e.isPaused()) e.resume();else e.pause();
    root.classList.toggle("paused", e.isPaused());
    showOsd(e.isPaused());
  }
  function toggleFav() {
    var added = repo.toggleFavorite(stripItem(item));
    toast(added ? "Dodano do ulubionych" : "Usunięto z ulubionych");
  }
  function stripItem(it) {
    return {
      key: it.key,
      kind: it.kind,
      title: it.title,
      url: it.url,
      image: it.image,
      subtitle: it.subtitle,
      parentId: it.parentId
    };
  }
  function typeNumber(digit) {
    numberBuffer = (numberBuffer + digit).slice(-4);
    var box = q(".p-num");
    box.textContent = numberBuffer;
    box.className = "p-num show";
    clearTimeout(numberTimer);
    numberTimer = setTimeout(function () {
      var n = parseInt(numberBuffer, 10);
      numberBuffer = "";
      box.className = "p-num";
      if (n >= 1 && n <= queue.length) openAt(n - 1, -1);else toast("Brak kanału o numerze " + n);
    }, 1800);
  }
  function openList() {
    if (queue.length < 2) {
      showOsd();
      return;
    }
    mode = "list";
    var box = q(".p-list");
    box.className = "p-list";
    var list = q(".p-list-items");
    var ll = lazyList(list, queue, function (it, i) {
      var n = el('<div class="p-list-row f"><span class="num">' + (i + 1) + "</span>" + img(it.image, it.title, "logo-s") + '<span class="name">' + esc(it.title) + "</span></div>");
      if (i === index) n.classList.add("current");
      n.onEnter = function () {
        closeList();
        openAt(i, -1);
      };
      return n;
    }, 80);
    nav.push(box, {
      onBack: closeList
    });
    var cur = ll.nodeAt(index);
    if (cur) nav.focus(cur);
    hideOsd();
  }
  function closeList() {
    if (mode !== "list") return;
    mode = "play";
    q(".p-list").className = "p-list hidden";
    nav.pop();
  }
  function showOsd(sticky) {
    root.classList.add("osd");
    clearTimeout(osdTimer);
    if (!sticky) osdTimer = setTimeout(hideOsd, 5e3);
    updateOsd();
  }
  function hideOsd() {
    if (getEngine().isPaused() || mode === "error") return;
    root.classList.remove("osd");
  }
  function renderInfo() {
    q(".p-title").textContent = (isLive() ? index + 1 + ". " : "") + item.title;
    q(".p-sub").textContent = !isLive() && item.subtitle ? item.subtitle : "";
    q(".p-logo").innerHTML = isLive() ? img(item.image, item.title, "logo-m") : "";
    q(".p-now").textContent = "";
    q(".p-next").textContent = "";
    q(".p-hint").textContent = isLive() ? "▲▼ zmiana kanału · ◀▶ lista kanałów · cyfry: numer · czerwony: ulubione" : "◀▶ przewijanie 10 s · ⏪⏩ 1 min · OK pauza · czerwony: ulubione";
    root.classList.toggle("live", isLive());
    root.classList.remove("paused");
  }
  function loadPrograms() {
    programs = [];
    var cur = item;
    if (!isLive()) return;
    repo.programs(cur).then(function (list) {
      if (item !== cur) return;
      programs = list;
      updateOsd();
    });
  }
  function updateOsd(forcedTime) {
    if (!active || !item) return;
    q(".p-clock").textContent = formatTime(Date.now());
    var fill = q(".p-fill");
    if (isLive()) {
      var now = Date.now();
      var cur = programs.filter(function (p) {
        return p.start <= now && p.end > now;
      })[0];
      var next = programs.filter(function (p) {
        return p.start > now;
      })[0];
      q(".p-now").textContent = cur ? formatTime(cur.start) + " – " + formatTime(cur.end) + "  " + cur.title : "";
      q(".p-next").textContent = next ? "Następnie " + formatTime(next.start) + "  " + next.title : "";
      var frac = cur ? (now - cur.start) / (cur.end - cur.start) : 0;
      fill.style.width = Math.max(0, Math.min(100, frac * 100)) + "%";
      q(".p-cur").textContent = cur ? formatTime(cur.start) : "";
      q(".p-dur").textContent = cur ? formatTime(cur.end) : "";
    } else {
      var e = getEngine();
      var t = forcedTime !== void 0 ? forcedTime : e.time();
      var d = e.duration();
      fill.style.width = d > 0 ? Math.min(100, t / d * 100) + "%" : "0%";
      q(".p-cur").textContent = formatClock(t);
      q(".p-dur").textContent = d > 0 ? formatClock(d) : "";
    }
  }
  function saveProgress() {
    if (!active || !item || item.kind === "live") return;
    var e = getEngine();
    var d = e.duration();
    if (d > 0) repo.saveProgress(stripItem(item), e.time(), d);
  }
  var radioItem = null;
  var radioState = null;
  var radioListeners = [];
  var Radio = {
    current: function current() {
      return radioItem ? {
        item: radioItem,
        state: radioState
      } : null;
    },
    onChange: function onChange(fn) {
      radioListeners.push(fn);
    },
    play: function play(it) {
      if (active) return;
      radioItem = it;
      setRadio("buffering");
      repo.addRecent(it);
      getEngine().open(it.url, 0, false, {
        onReady: function onReady() {
          if (radioItem === it) setRadio("playing");
        },
        onBuffering: function onBuffering(b) {
          if (radioItem === it && radioState !== "paused") setRadio(b ? "buffering" : "playing");
        },
        onEnd: function onEnd() {
          if (radioItem === it) setRadio("error");
        },
        onError: function onError() {
          if (radioItem === it) setRadio("error");
        }
      });
    },
    toggle: function toggle() {
      if (!radioItem) return;
      if (radioState === "paused") {
        getEngine().resume();
        setRadio("playing");
      } else if (radioState === "error") Radio.play(radioItem);else {
        getEngine().pause();
        setRadio("paused");
      }
    },
    stop: function stop() {
      if (!radioItem) return;
      radioItem = null;
      getEngine().stop();
      setRadio(null);
    }
  };
  function setRadio(s) {
    radioState = s;
    radioListeners.forEach(function (fn) {
      fn(Radio.current());
    });
  }

  // src/items.js
  function liveItem(ch) {
    return {
      key: "live:" + ch.id,
      kind: "live",
      title: ch.name,
      url: ch.url,
      image: ch.logo,
      epgId: ch.epgId
    };
  }
  function movieItem(m) {
    return {
      key: "movie:" + m.id,
      kind: "movie",
      title: m.name,
      url: m.url,
      image: m.poster
    };
  }
  function seriesItem(s) {
    return {
      key: "series:" + s.id,
      kind: "series",
      title: s.name,
      url: "",
      image: s.poster
    };
  }
  function episodeItem(ep, s) {
    var se = "S" + String(ep.season).padStart(2, "0") + "E" + String(ep.number).padStart(2, "0");
    return {
      key: "episode:" + ep.id,
      kind: "episode",
      title: s.name,
      url: ep.url,
      image: ep.image || s.poster,
      subtitle: se + " · " + ep.title,
      parentId: s.id
    };
  }
  function radioItem2(st) {
    return {
      key: "radio:" + st.id,
      kind: "radio",
      title: st.name,
      url: st.url,
      image: st.logo,
      subtitle: st.tags
    };
  }
  function catalogGate(main2, onRetryFocus) {
    if (repo.catalog) return true;
    if (repo.error && !repo.loading) {
      main2.innerHTML = "";
      var box = el('<div class="center-msg"><p>' + esc(repo.error) + '</p><div class="row-btns"><div class="btn f">Spróbuj ponownie</div></div></div>');
      box.querySelector(".btn").onEnter = function () {
        repo.load(true);
      };
      main2.appendChild(box);
      if (onRetryFocus) nav.focusFirst(box);
      return false;
    }
    main2.innerHTML = '<div class="center-msg"><div class="spinner"></div><p>Wczytywanie kanałów, filmów i seriali…</p></div>';
    if (!repo.loading) repo.load();
    return false;
  }

  // src/screens/live.js
  var FAV = "__fav";
  var ALL = "__all";
  var RECENT = "__recent";
  var selected = ALL;
  function categoryColumn(container, options, selectedId, onSelect) {
    container.innerHTML = "";
    var change = debounce(function (id) {
      onSelect(id, false);
    }, 250);
    var selectedNode = null;
    options.forEach(function (o) {
      var n = el('<div class="cat f" data-group="cats">' + esc(o.name) + (o.count != null ? '<span class="cnt">' + o.count + "</span>" : "") + "</div>");
      if (o.id === selectedId) {
        n.classList.add("sel");
        selectedNode = n;
      }
      n.onFocus = function () {
        if (container.querySelector(".sel") !== n) {
          var prev = container.querySelector(".sel");
          if (prev) prev.classList.remove("sel");
          n.classList.add("sel");
          change(o.id);
        }
      };
      n.onEnter = function () {
        onSelect(o.id, true);
      };
      container.appendChild(n);
    });
    return selectedNode;
  }
  function renderLive(main2) {
    if (!catalogGate(main2)) return;
    var c = repo.catalog;
    main2.innerHTML = "";
    var root2 = el('<div class="cols"><div class="cats scroll"></div><div class="chan-list scroll"></div><div class="epg-panel"></div></div>');
    main2.appendChild(root2);
    var catsBox = root2.querySelector(".cats");
    var listBox = root2.querySelector(".chan-list");
    var panel = root2.querySelector(".epg-panel");
    var counts2 = {};
    c.live.forEach(function (ch) {
      counts2[ch.categoryId] = (counts2[ch.categoryId] || 0) + 1;
    });
    var options = [{
      id: FAV,
      name: "★ Ulubione"
    }, {
      id: RECENT,
      name: "Ostatnio oglądane"
    }, {
      id: ALL,
      name: "Wszystkie kanały",
      count: c.live.length
    }].concat(c.liveCategories.map(function (x) {
      return {
        id: x.id,
        name: x.name,
        count: counts2[x.id] || 0
      };
    }));
    function itemsFor(id) {
      if (id === FAV) return repo.favorites.filter(function (f) {
        return f.kind === "live";
      });
      if (id === RECENT) return repo.recent.filter(function (f) {
        return f.kind === "live";
      });
      var src = id === ALL ? c.live : c.live.filter(function (ch) {
        return ch.categoryId === id;
      });
      return src.map(liveItem);
    }
    var showPanel = debounce(function (item2) {
      renderPanel(panel, item2);
    }, 350);
    function fill(id, focusList) {
      selected = id;
      var items = itemsFor(id);
      if (!items.length) {
        listBox.innerHTML = '<div class="empty">' + (id === FAV ? "Brak ulubionych kanałów.<br>Na liście kanałów naciśnij czerwony przycisk, żeby dodać." : "Brak kanałów.") + "</div>";
        panel.innerHTML = "";
        return;
      }
      listBox.scrollTop = 0;
      lazyList(listBox, items, function (item2, i) {
        var n = el('<div class="row f" data-group="list"><span class="num">' + (i + 1) + "</span>" + img(item2.image, item2.title, "logo-s") + '<span class="name">' + esc(item2.title) + "</span>" + (repo.isFavorite(item2.key) ? icon("star", "fav") : "") + "</div>");
        n.onFocus = function () {
          showPanel(item2);
        };
        n.onEnter = function () {
          Player.play(items, i, -1);
        };
        n.onKey = function (k) {
          if (k !== "red") return false;
          var added = repo.toggleFavorite(item2);
          var star = n.querySelector(".fav");
          if (added && !star) n.insertAdjacentHTML("beforeend", icon("star", "fav"));
          if (!added && star) star.parentNode.removeChild(star);
          toast(added ? "Dodano do ulubionych" : "Usunięto z ulubionych");
          return true;
        };
        return n;
      });
      renderPanel(panel, items[0]);
      if (focusList) nav.focus(listBox.querySelector(".f"));
    }
    categoryColumn(catsBox, options, selected, fill);
    fill(selected, false);
  }
  function renderPanel(panel, item2) {
    if (!item2) {
      panel.innerHTML = "";
      return;
    }
    panel.innerHTML = '<div class="epg-head">' + img(item2.image, item2.title, "logo-l") + '<div class="epg-name">' + esc(item2.title) + '</div></div><div class="epg-body"><div class="muted">' + (repo.api() ? "Wczytywanie programu…" : "Program TV dostępny przy logowaniu Xtream.") + '</div></div><div class="hint">OK – oglądaj · czerwony – ulubione</div>';
    if (!repo.api()) return;
    repo.programs(item2).then(function (list) {
      if (!panel.isConnected && !document.body.contains(panel)) return;
      var body = panel.querySelector(".epg-body");
      if (!body) return;
      if (!list.length) {
        body.innerHTML = '<div class="muted">Brak programu dla tego kanału.</div>';
        return;
      }
      var now = Date.now();
      body.innerHTML = list.slice(0, 5).map(function (p, i) {
        var isNow = p.start <= now && p.end > now;
        var frac = isNow ? Math.round((now - p.start) / (p.end - p.start) * 100) : 0;
        return '<div class="prog' + (isNow ? " now" : "") + '"><div class="prog-time">' + formatTime(p.start) + " – " + formatTime(p.end) + '</div><div class="prog-title">' + esc(p.title) + "</div>" + (isNow ? '<div class="bar"><div style="width:' + frac + '%"></div></div>' : "") + (i === 0 && p.description ? '<div class="prog-desc">' + esc(p.description) + "</div>" : "") + "</div>";
      }).join("");
    });
  }

  // src/screens/vod.js
  var ALL2 = "__all";
  var FAV2 = "__fav";
  var selectedBy = {
    movies: ALL2,
    series: ALL2,
    show: ALL2
  };
  function posterGrid(box, entries, emptyText, onChanged) {
    box.scrollTop = 0;
    if (!entries.length) {
      box.innerHTML = '<div class="empty">' + emptyText + "</div>";
      return;
    }
    lazyList(box, entries, function (e) {
      var o = e.obj;
      var key = (e.type === "movie" ? "movie:" : "series:") + o.id;
      var p = repo.progressOf(key);
      var badge = formatRating(o.rating) || (e.type === "series" && o.year ? o.year : null);
      var n = el('<div class="card f" data-group="grid">' + img(o.poster, o.name, "poster") + (badge ? '<span class="badge">' + esc(badge) + "</span>" : "") + (repo.isFavorite(key) ? icon("star", "fav") : "") + (p && !p.finished ? '<div class="pbar"><div style="width:' + Math.round(p.fraction * 100) + '%"></div></div>' : "") + '<div class="card-title">' + esc(o.name) + "</div></div>");
      n.onEnter = function () {
        if (e.type === "movie") openMovie(o, onChanged);else openSeries(o, onChanged);
      };
      n.onKey = function (k) {
        if (k !== "red") return false;
        var added = repo.toggleFavorite(e.type === "movie" ? movieItem(o) : seriesItem(o));
        var star = n.querySelector(".fav");
        if (added && !star) n.insertAdjacentHTML("beforeend", icon("star", "fav"));
        if (!added && star) star.parentNode.removeChild(star);
        toast(added ? "Dodano do ulubionych" : "Usunięto z ulubionych");
        return true;
      };
      return n;
    }, 40);
  }
  function vodLayout(main2) {
    main2.innerHTML = "";
    var root2 = el('<div class="cols"><div class="cats scroll"></div><div class="grid scroll"></div></div>');
    main2.appendChild(root2);
    return {
      cats: root2.querySelector(".cats"),
      grid: root2.querySelector(".grid")
    };
  }
  function counts(list) {
    var m = {};
    list.forEach(function (x) {
      m[x.categoryId] = (m[x.categoryId] || 0) + 1;
    });
    return m;
  }
  function renderMovies(main2) {
    if (!catalogGate(main2)) return;
    var c = repo.catalog;
    var L = vodLayout(main2);
    var cnt = counts(c.movies);
    var options = [{
      id: ALL2,
      name: "Wszystkie filmy",
      count: c.movies.length
    }, {
      id: FAV2,
      name: "★ Ulubione"
    }].concat(c.movieCategories.map(function (x) {
      return {
        id: x.id,
        name: x.name,
        count: cnt[x.id] || 0
      };
    }));
    function fill(id, focus) {
      selectedBy.movies = id;
      var list;
      if (id === ALL2) list = c.movies;else if (id === FAV2) {
        var keys = {};
        repo.favorites.forEach(function (f) {
          if (f.kind === "movie") keys[f.key.slice(6)] = 1;
        });
        list = c.movies.filter(function (m) {
          return keys[m.id];
        });
      } else list = c.movies.filter(function (m) {
        return m.categoryId === id;
      });
      posterGrid(L.grid, list.map(function (m) {
        return {
          type: "movie",
          obj: m
        };
      }), id === FAV2 ? "Brak ulubionych filmów." : "Brak filmów.");
      if (focus) nav.focus(L.grid.querySelector(".f"));
    }
    categoryColumn(L.cats, options, selectedBy.movies, fill);
    fill(selectedBy.movies, false);
  }
  function renderSeries(main2) {
    if (!catalogGate(main2)) return;
    var c = repo.catalog;
    var L = vodLayout(main2);
    var cnt = counts(c.series);
    var options = [{
      id: ALL2,
      name: "Wszystkie seriale",
      count: c.series.length
    }, {
      id: FAV2,
      name: "★ Ulubione"
    }].concat(c.seriesCategories.map(function (x) {
      return {
        id: x.id,
        name: x.name,
        count: cnt[x.id] || 0
      };
    }));
    function fill(id, focus) {
      selectedBy.series = id;
      var list;
      if (id === ALL2) list = c.series;else if (id === FAV2) {
        var keys = {};
        repo.favorites.forEach(function (f) {
          if (f.kind === "series") keys[f.key.slice(7)] = 1;
        });
        list = c.series.filter(function (s) {
          return keys[s.id];
        });
      } else list = c.series.filter(function (s) {
        return s.categoryId === id;
      });
      posterGrid(L.grid, list.map(function (s) {
        return {
          type: "series",
          obj: s
        };
      }), id === FAV2 ? "Brak ulubionych seriali." : "Brak seriali.");
      if (focus) nav.focus(L.grid.querySelector(".f"));
    }
    categoryColumn(L.cats, options, selectedBy.series, fill);
    fill(selectedBy.series, false);
  }
  function renderShow(main2, openShowSettings2) {
    if (!catalogGate(main2)) return;
    var c = repo.catalog;
    var cats = repo.showCategories();
    if (!cats.length) {
      main2.innerHTML = "";
      var box = el('<div class="center-msg"><p>Nie znaleziono automatycznie kategorii z programami typu show.<br>Wybierz je z listy kategorii Twojej playlisty.</p><div class="row-btns"><div class="btn f">Wybierz kategorie Show</div></div></div>');
      box.querySelector(".btn").onEnter = openShowSettings2;
      main2.appendChild(box);
      return;
    }
    var L = vodLayout(main2);
    var options = [{
      id: ALL2,
      name: "Wszystkie"
    }].concat(cats.map(function (x) {
      return {
        id: x.key,
        name: x.name + (x.isSeries ? "" : " (filmy)")
      };
    })).concat([{
      id: "__edit",
      name: "✎ Zmień kategorie…"
    }]);
    function fill(id, focus) {
      if (id === "__edit") {
        if (focus) openShowSettings2();
        return;
      }
      selectedBy.show = id;
      var keys = id === ALL2 ? cats.map(function (x) {
        return x.key;
      }) : [id];
      var mc = {},
        sc = {};
      keys.forEach(function (k) {
        if (k.indexOf("movie:") === 0) mc[k.slice(6)] = 1;else sc[k.slice(7)] = 1;
      });
      var entries = c.series.filter(function (s) {
        return sc[s.categoryId];
      }).map(function (s) {
        return {
          type: "series",
          obj: s
        };
      }).concat(c.movies.filter(function (m) {
        return mc[m.categoryId];
      }).map(function (m) {
        return {
          type: "movie",
          obj: m
        };
      }));
      posterGrid(L.grid, entries, "Brak pozycji w tej kategorii.");
      if (focus) nav.focus(L.grid.querySelector(".f"));
    }
    if (selectedBy.show !== ALL2 && !cats.some(function (x) {
      return x.key === selectedBy.show;
    })) selectedBy.show = ALL2;
    categoryColumn(L.cats, options, selectedBy.show, fill);
    fill(selectedBy.show, false);
  }
  function overlay() {
    var o = el('<div class="detail"><div class="detail-bg"></div><div class="detail-shade"></div><div class="detail-content scroll"></div></div>');
    document.getElementById("app").appendChild(o);
    function close() {
      nav.pop();
      if (o.parentNode) o.parentNode.removeChild(o);
    }
    nav.push(o, {
      onBack: close
    });
    return {
      root: o,
      content: o.querySelector(".detail-content"),
      bg: o.querySelector(".detail-bg"),
      close
    };
  }
  function setBg(bg, url) {
    if (url) bg.style.backgroundImage = 'url("' + url.replace(/"/g, "%22") + '")';
  }
  function header(o, meta) {
    return '<div class="d-head">' + img(o.poster, o.name, "poster d-poster") + '<div class="d-info"><h1>' + esc(o.name) + '</h1><div class="d-meta">' + meta.filter(Boolean).map(esc).join("<span class='dot'>·</span>") + '</div><div class="d-buttons row-btns"></div><div class="d-plot"></div><div class="d-extra"></div></div></div>';
  }
  function favButton(item2) {
    var b = el('<div class="btn f">' + icon("star") + "<span></span></div>");
    function label() {
      b.querySelector("span").textContent = repo.isFavorite(item2.key) ? "W ulubionych" : "Dodaj do ulubionych";
    }
    label();
    b.onEnter = function () {
      repo.toggleFavorite(item2);
      label();
    };
    return b;
  }
  function openMovie(movie, onChanged) {
    var ov = overlay();
    var item2 = movieItem(movie);
    ov.content.innerHTML = header(movie, [formatRating(movie.rating)]);
    var btns = ov.content.querySelector(".d-buttons");
    function renderButtons() {
      btns.innerHTML = "";
      var p = repo.progressOf(item2.key);
      var resume = p && !p.finished && p.pos > 3e4;
      var play = el('<div class="btn primary f">' + icon("play") + "<span>" + (resume ? "Wznów od " + formatClock(p.pos) : "Odtwórz") + "</span></div>");
      play.onEnter = function () {
        Player.play([item2], 0, -1, afterPlay);
      };
      btns.appendChild(play);
      if (resume) {
        var again = el('<div class="btn f">Od początku</div>');
        again.onEnter = function () {
          Player.play([item2], 0, 0, afterPlay);
        };
        btns.appendChild(again);
      }
      btns.appendChild(favButton(item2));
    }
    function afterPlay() {
      renderButtons();
      nav.focusFirst(btns);
      if (onChanged) onChanged();
    }
    renderButtons();
    nav.focusFirst(btns);
    repo.movieDetail(movie).then(function (d) {
      if (!ov.root.parentNode) return;
      setBg(ov.bg, d.backdrop || movie.poster);
      ov.content.querySelector(".d-meta").innerHTML = [d.year, d.duration, formatRating(d.rating), d.genre].filter(Boolean).map(esc).join("<span class='dot'>·</span>");
      ov.content.querySelector(".d-plot").textContent = d.plot || "";
      ov.content.querySelector(".d-extra").innerHTML = (d.director ? "<p><b>Reżyseria:</b> " + esc(d.director) + "</p>" : "") + (d.cast ? "<p><b>Obsada:</b> " + esc(d.cast) + "</p>" : "");
    });
  }
  function openSeries(series, onChanged) {
    var ov = overlay();
    ov.content.innerHTML = header(series, [series.year, formatRating(series.rating), series.genre]) + '<div class="seasons hscroll"></div><div class="episodes"></div>';
    var btns = ov.content.querySelector(".d-buttons");
    var seasonsBox = ov.content.querySelector(".seasons");
    var epsBox = ov.content.querySelector(".episodes");
    ov.content.querySelector(".d-plot").textContent = series.plot || "";
    setBg(ov.bg, series.poster);
    btns.appendChild(favButton(seriesItem(series)));
    nav.focusFirst(btns);
    epsBox.innerHTML = '<div class="spinner"></div>';
    var detail = null;
    var season = null;
    repo.seriesDetail(series).then(function (d) {
      if (!ov.root.parentNode) return;
      detail = d;
      if (d.backdrop) setBg(ov.bg, d.backdrop);
      if (d.plot) ov.content.querySelector(".d-plot").textContent = d.plot;
      if (d.cast) ov.content.querySelector(".d-extra").innerHTML = "<p><b>Obsada:</b> " + esc(d.cast) + "</p>";
      var nums = Object.keys(d.seasons).map(Number).sort(function (a, b) {
        return a - b;
      });
      if (!nums.length) {
        epsBox.innerHTML = '<div class="empty">Brak odcinków.</div>';
        return;
      }
      var last = lastWatched();
      season = last ? last.ep.season : nums[0];
      renderContinue(last, nums);
      seasonsBox.innerHTML = "";
      nums.forEach(function (n) {
        var b = el('<div class="chip f" data-group="seasons">Sezon ' + n + "</div>");
        if (n === season) b.classList.add("sel");
        b.onFocus = function () {
          if (season === n) return;
          season = n;
          var prev = seasonsBox.querySelector(".sel");
          if (prev) prev.classList.remove("sel");
          b.classList.add("sel");
          renderEpisodes();
        };
        seasonsBox.appendChild(b);
      });
      renderEpisodes();
      nav.focusFirst(btns);
    }, function () {
      epsBox.innerHTML = '<div class="empty">Nie udało się pobrać odcinków.</div>';
    });
    function allEpisodes() {
      var out = [];
      Object.keys(detail.seasons).map(Number).sort(function (a, b) {
        return a - b;
      }).forEach(function (k) {
        out = out.concat(detail.seasons[k]);
      });
      return out;
    }
    function lastWatched() {
      var best = null;
      Object.keys(repo.progress).forEach(function (k) {
        var p = repo.progress[k];
        if (p.item && p.item.parentId === series.id && (!best || p.at > best.at)) best = p;
      });
      if (!best) return null;
      var ep = allEpisodes().filter(function (e) {
        return "episode:" + e.id === best.item.key;
      })[0];
      return ep ? {
        ep,
        p: repo.progressOf(best.item.key)
      } : null;
    }
    function play(list, i, from) {
      Player.play(list.map(function (e) {
        return episodeItem(e, series);
      }), i, from === void 0 ? -1 : from, function () {
        var last = lastWatched();
        renderContinue(last);
        renderEpisodes();
        if (onChanged) onChanged();
      });
    }
    function renderContinue(last) {
      var old = btns.querySelector(".cont");
      if (old) btns.removeChild(old);
      var target = null,
        label = "Odtwórz";
      var all = allEpisodes();
      if (last) {
        if (!last.p.finished) {
          target = last.ep;
          label = "Kontynuuj S" + last.ep.season + "E" + last.ep.number;
        } else {
          var idx = all.indexOf(last.ep);
          target = all[idx + 1] || last.ep;
          label = "Następny: S" + target.season + "E" + target.number;
        }
      } else target = all[0];
      if (!target) return;
      var b = el('<div class="btn primary f cont">' + icon("play") + "<span>" + esc(label) + "</span></div>");
      b.onEnter = function () {
        var list = detail.seasons[target.season];
        play(list, list.indexOf(target));
      };
      btns.insertBefore(b, btns.firstChild);
    }
    function renderEpisodes() {
      var list = detail.seasons[season] || [];
      epsBox.innerHTML = "";
      list.forEach(function (ep, i) {
        var p = repo.progressOf("episode:" + ep.id);
        var n = el('<div class="ep f" data-group="eps">' + img(ep.image || series.poster, String(ep.number), "ep-img") + '<div class="ep-text"><div class="ep-title">' + ep.number + ". " + esc(ep.title) + "</div>" + (ep.duration ? '<div class="muted">' + esc(ep.duration) + "</div>" : "") + (ep.plot ? '<div class="ep-plot">' + esc(ep.plot) + "</div>" : "") + "</div>" + (p ? '<div class="pbar"><div style="width:' + Math.round(p.fraction * 100) + '%"></div></div>' : "") + "</div>");
        n.onEnter = function () {
          play(list, i);
        };
        epsBox.appendChild(n);
      });
    }
  }

  // src/screens/other.js
  function rowSection(title, items, render2) {
    if (!items.length) return null;
    var s = el('<div class="section"><h2>' + esc(title) + '</h2><div class="hrow hscroll"><div class="hrow-inner"></div></div></div>');
    var inner = s.querySelector(".hrow-inner");
    items.forEach(function (it, i) {
      inner.appendChild(render2(it, i));
    });
    return s;
  }
  function wideCard(title, sub, image, fit, progress) {
    return el('<div class="wcard f' + (fit ? " fit" : "") + '">' + img(image, title, "wimg") + (progress != null ? '<div class="pbar"><div style="width:' + Math.round(progress * 100) + '%"></div></div>' : "") + '<div class="wtitle">' + esc(title) + "</div>" + (sub ? '<div class="wsub">' + esc(sub) + "</div>" : "") + "</div>");
  }
  function openFavoriteVod(f, rerender) {
    var id = f.key.slice(f.key.indexOf(":") + 1);
    if (f.kind === "movie") {
      var m = repo.findMovie(id);
      if (m) openMovie(m, rerender);else toast("Tej pozycji nie ma już w playliście");
    } else {
      var s = repo.findSeries(id);
      if (s) openSeries(s, rerender);else toast("Tej pozycji nie ma już w playliście");
    }
  }
  function renderHome(main2, goTo2) {
    main2.innerHTML = "";
    var wrap = el('<div class="home scroll"></div>');
    main2.appendChild(wrap);
    var rerender = function rerender() {
      renderHome(main2, goTo2);
    };
    var cont = Object.keys(repo.progress).map(function (k) {
      return repo.progressOf(k);
    }).filter(function (p) {
      return !p.finished && p.pos > 3e4;
    }).sort(function (a, b) {
      return b.at - a.at;
    }).slice(0, 20);
    var favLive = repo.favorites.filter(function (f) {
      return f.kind === "live";
    });
    var favVod = repo.favorites.filter(function (f) {
      return f.kind === "movie" || f.kind === "series";
    });
    var favRadio = repo.favorites.filter(function (f) {
      return f.kind === "radio";
    });
    var recent = repo.recent;
    var sections = [rowSection("Kontynuuj oglądanie", cont, function (p) {
      var n = wideCard(p.item.title, p.item.subtitle || "Pozostało " + formatDuration(p.dur - p.pos), p.item.image, false, p.fraction);
      n.onEnter = function () {
        Player.play([p.item], 0, -1, rerender);
      };
      return n;
    }), rowSection("Ulubione kanały", favLive, function (f, i) {
      var n = wideCard(f.title, null, f.image, true);
      n.onEnter = function () {
        Player.play(favLive, i, -1);
      };
      return n;
    }), rowSection("Ostatnio oglądane", recent, function (f, i) {
      var n = wideCard(f.title, f.kind === "radio" ? "Radio" : null, f.image, true);
      n.onEnter = function () {
        if (f.kind === "radio") Radio.play(f);else Player.play(recent.filter(function (r) {
          return r.kind === "live";
        }), recent.filter(function (r) {
          return r.kind === "live";
        }).indexOf(f), -1);
      };
      return n;
    }), rowSection("Ulubione filmy i seriale", favVod, function (f) {
      var n = el('<div class="card f">' + img(f.image, f.title, "poster") + '<div class="card-title">' + esc(f.title) + "</div></div>");
      n.onEnter = function () {
        openFavoriteVod(f, rerender);
      };
      return n;
    }), rowSection("Ulubione stacje radiowe", favRadio, function (f) {
      var n = wideCard(f.title, f.subtitle, f.image, true);
      n.onEnter = function () {
        Radio.play(f);
      };
      return n;
    })].filter(Boolean);
    if (!sections.length) {
      var hello = el('<div class="center-msg"><h1>Witaj w MojaTV</h1><p>Tu pojawią się ulubione kanały, ostatnio oglądane i „kontynuuj oglądanie”.<br>Czerwony przycisk na pilocie dodaje do ulubionych.</p><div class="row-btns"><div class="btn f" data-t="live">Telewizja</div><div class="btn f" data-t="movies">Filmy</div><div class="btn f" data-t="series">Seriale</div></div></div>');
      hello.querySelectorAll(".btn").forEach(function (b) {
        b.onEnter = function () {
          goTo2(b.getAttribute("data-t"), true);
        };
      });
      wrap.appendChild(hello);
      if (repo.source && !repo.catalog && !repo.loading) repo.load();
      return;
    }
    sections.forEach(function (s) {
      wrap.appendChild(s);
    });
    if (repo.source && !repo.catalog && !repo.loading) repo.load();
  }
  var COUNTRIES = [["PL", "Polska"], ["OM", "Oman"], ["EG", "Egipt"], ["AE", "ZEA"], ["GB", "Wlk. Brytania"], ["US", "USA"], ["DE", "Niemcy"]];
  var radioSelected = null;
  function renderRadio(main2) {
    main2.innerHTML = "";
    var root2 = el('<div class="cols"><div class="cats scroll"><input class="f search-input" data-group="cats" placeholder="Szukaj stacji…"><div class="cat-list"></div></div><div class="chan-list wide scroll"></div></div>');
    main2.appendChild(root2);
    var input = root2.querySelector("input");
    var catList = root2.querySelector(".cat-list");
    var listBox = root2.querySelector(".chan-list");
    var playlistRadio = (repo.catalog && repo.catalog.radioChannels || []).map(function (ch) {
      var it = liveItem(ch);
      it.kind = "radio";
      it.key = "radio:pl-" + ch.id;
      return it;
    });
    var options = [{
      id: "fav",
      name: "★ Ulubione"
    }];
    if (playlistRadio.length) options.push({
      id: "playlist",
      name: "Z Twojej playlisty",
      count: playlistRadio.length
    });
    COUNTRIES.forEach(function (c) {
      options.push({
        id: "c:" + c[0],
        name: c[1]
      });
    });
    if (!radioSelected) radioSelected = "c:" + (repo.settings.radioCountry || "PL");
    var token = 0;
    function showList(items, emptyText) {
      if (!items.length) {
        listBox.innerHTML = '<div class="empty">' + esc(emptyText) + "</div>";
        return;
      }
      lazyList(listBox, items, function (it) {
        var n = el('<div class="row f" data-group="list">' + img(it.image, it.title, "logo-s") + '<span class="name">' + esc(it.title) + (it.subtitle ? "<small>" + esc(it.subtitle) + "</small>" : "") + "</span>" + (repo.isFavorite(it.key) ? icon("star", "fav") : "") + "</div>");
        n.onEnter = function () {
          Radio.play(it);
        };
        n.onKey = function (k) {
          if (k !== "red") return false;
          var added = repo.toggleFavorite(it);
          var star = n.querySelector(".fav");
          if (added && !star) n.insertAdjacentHTML("beforeend", icon("star", "fav"));
          if (!added && star) star.parentNode.removeChild(star);
          toast(added ? "Dodano do ulubionych" : "Usunięto z ulubionych");
          return true;
        };
        return n;
      });
    }
    function load(promise) {
      var my = ++token;
      listBox.innerHTML = '<div class="center-msg"><div class="spinner"></div></div>';
      promise.then(function (list) {
        if (my !== token) return;
        showList(list.map(radioItem2), "Brak stacji.");
      }, function () {
        if (my === token) listBox.innerHTML = '<div class="empty">Nie udało się pobrać listy stacji.</div>';
      });
    }
    function fill(id, focus) {
      radioSelected = id;
      token++;
      if (id === "fav") showList(repo.favorites.filter(function (f) {
        return f.kind === "radio";
      }), "Brak ulubionych stacji. Czerwony przycisk dodaje do ulubionych.");else if (id === "playlist") showList(playlistRadio, "Brak stacji.");else {
        var cc = id.slice(2);
        repo.settings.radioCountry = cc;
        repo.saveSettings();
        load(RadioBrowser.top(cc));
      }
      if (focus) setTimeout(function () {
        var f = listBox.querySelector(".f");
        if (f) nav.focus(f);
      }, 0);
    }
    input.oninput = debounce(function () {
      var q2 = input.value.trim();
      if (q2.length >= 2) load(RadioBrowser.search(q2));
    }, 600);
    categoryColumn(catList, options, radioSelected, fill);
    fill(radioSelected, false);
  }
  var lastQuery = "";
  function renderSearch(main2) {
    if (!catalogGate(main2)) return;
    main2.innerHTML = "";
    var root2 = el('<div class="search scroll"><input class="f search-input big" placeholder="Szukaj kanałów, filmów i seriali…"><div class="results"></div></div>');
    main2.appendChild(root2);
    var input = root2.querySelector("input");
    var results = root2.querySelector(".results");
    input.value = lastQuery;
    function run() {
      lastQuery = input.value;
      var r = repo.search(input.value);
      results.innerHTML = "";
      if (input.value.trim().length < 2) {
        results.innerHTML = '<div class="empty">Wpisz co najmniej 2 znaki (OK otwiera klawiaturę).</div>';
        return;
      }
      if (!r.live.length && !r.movies.length && !r.series.length) {
        results.innerHTML = '<div class="empty">Nic nie znaleziono.</div>';
        return;
      }
      var liveItems = r.live.map(liveItem);
      [rowSection("Kanały (" + r.live.length + ")", liveItems, function (it, i) {
        var n = wideCard(it.title, null, it.image, true);
        n.onEnter = function () {
          Player.play(liveItems, i, -1);
        };
        return n;
      }), rowSection("Filmy (" + r.movies.length + ")", r.movies, function (m) {
        var n = el('<div class="card f">' + img(m.poster, m.name, "poster") + '<div class="card-title">' + esc(m.name) + "</div></div>");
        n.onEnter = function () {
          openMovie(m);
        };
        return n;
      }), rowSection("Seriale i show (" + r.series.length + ")", r.series, function (s) {
        var n = el('<div class="card f">' + img(s.poster, s.name, "poster") + '<div class="card-title">' + esc(s.name) + "</div></div>");
        n.onEnter = function () {
          openSeries(s);
        };
        return n;
      })].filter(Boolean).forEach(function (s) {
        results.appendChild(s);
      });
    }
    input.oninput = debounce(run, 400);
    input.onSubmit = run;
    run();
  }
  function renderSettings(main2, actions) {
    main2.innerHTML = "";
    var s = repo.settings;
    var src = repo.source || {};
    var a = repo.account;
    var c = repo.catalog;
    var info = [["Źródło", src.type === "xtream" ? "Xtream Codes" : "Playlista M3U"], ["Adres", src.type === "xtream" ? src.server + " · " + src.username : String(src.m3uUrl || "").slice(0, 70)]];
    if (a) {
      info.push(["Status konta", a.status || "—"]);
      if (a.expiresAt) info.push(["Ważne do", new Date(a.expiresAt).toLocaleDateString("pl-PL")]);
      info.push(["Połączenia", (a.activeConnections || "?") + " / " + (a.maxConnections || "?")]);
    }
    if (c) info.push(["Zawartość", c.live.length + " kanałów · " + c.movies.length + " filmów · " + c.series.length + " seriali"]);
    var root2 = el('<div class="settings scroll"><h1>Ustawienia</h1><div class="info"></div><div class="opts"></div><p class="muted small">MojaTV dla Samsung TV · odtwarza Twoje własne playlisty</p></div>');
    root2.querySelector(".info").innerHTML = info.map(function (r) {
      return '<div class="info-row"><span>' + esc(r[0]) + "</span><b>" + esc(r[1]) + "</b></div>";
    }).join("");
    main2.appendChild(root2);
    var opts = root2.querySelector(".opts");
    function opt(label, value, fn) {
      var n = el('<div class="opt f"><span>' + esc(label) + '</span><b class="val">' + esc(value || "") + "</b></div>");
      n.onEnter = function () {
        fn(n);
      };
      opts.appendChild(n);
      return n;
    }
    opt("Odśwież playlistę", "", function () {
      repo.load(true);
      toast("Odświeżam playlistę…");
    });
    if (src.type === "xtream") {
      opt("Format kanałów na żywo", s.liveFormat === "ts" ? "MPEG-TS" : "HLS", function (n) {
        s.liveFormat = s.liveFormat === "ts" ? "m3u8" : "ts";
        repo.saveSettings();
        n.querySelector(".val").textContent = s.liveFormat === "ts" ? "MPEG-TS" : "HLS";
        repo.load(true);
      });
    }
    opt("Ukryj kategorie dla dorosłych", s.hideAdult ? "Tak" : "Nie", function (n) {
      s.hideAdult = !s.hideAdult;
      repo.saveSettings();
      n.querySelector(".val").textContent = s.hideAdult ? "Tak" : "Nie";
      repo.load(true);
    });
    opt("Kategorie w zakładce Show", "›", function () {
      openShowSettings();
    });
    opt("Wyczyść historię oglądania", "", function () {
      repo.clearHistory();
      toast("Historia wyczyszczona");
    });
    opt("Zmień źródło (wyloguj)", "", function () {
      confirmDialog("Zmienić źródło?", "Wylogujesz się z obecnej playlisty. Ulubione i historia zostaną zachowane.", "Zmień").then(function (ok) {
        if (ok) actions.signOut();
      });
    });
  }
  function openShowSettings(onDone) {
    if (!repo.catalog) {
      toast("Playlista jeszcze się wczytuje…");
      return;
    }
    var o = el('<div class="detail plain"><div class="detail-content scroll"><h1>Kategorie Show</h1><p class="muted mode"></p><div class="row-btns"><div class="btn f auto">Wybierz automatycznie</div></div><div class="checks"></div></div></div>');
    document.getElementById("app").appendChild(o);
    function close() {
      nav.pop();
      o.parentNode.removeChild(o);
      if (onDone) onDone();
    }
    nav.push(o, {
      onBack: close
    });
    var selected2 = (repo.settings.showCategories || repo.autoShowKeys()).slice();
    var mode2 = o.querySelector(".mode");
    function setMode() {
      mode2.textContent = repo.settings.showCategories ? "Wybór ręczny. OK zaznacza lub odznacza kategorię." : "Tryb automatyczny: zaznaczone są kategorie rozpoznane po nazwie. Zmiana dowolnej pozycji przełącza na wybór ręczny.";
    }
    setMode();
    var checks = o.querySelector(".checks");
    function render2() {
      checks.innerHTML = "";
      repo.allShowCandidates().forEach(function (cat) {
        var on = selected2.indexOf(cat.key) >= 0;
        var n = el('<div class="check f' + (on ? " on" : "") + '"><span class="box"></span><span>' + esc(cat.name) + "</span><small>" + (cat.isSeries ? "Seriale" : "Filmy") + "</small></div>");
        n.onEnter = function () {
          var i = selected2.indexOf(cat.key);
          if (i >= 0) selected2.splice(i, 1);else selected2.push(cat.key);
          n.classList.toggle("on", i < 0);
          repo.settings.showCategories = selected2.slice();
          repo.saveSettings();
          setMode();
        };
        checks.appendChild(n);
      });
    }
    render2();
    o.querySelector(".auto").onEnter = function () {
      repo.settings.showCategories = null;
      repo.saveSettings();
      selected2 = repo.autoShowKeys();
      setMode();
      render2();
    };
    nav.focusFirst(o);
  }
  function renderLogin(container, onDone) {
    var d = window.MOJATV_DEFAULTS || {};
    var saved = repo.source || {};
    var tab = saved.type === "m3u" || !saved.type && d.m3uUrl && !d.server ? "m3u" : "xtream";
    container.innerHTML = "";
    var root2 = el('<div class="login"><div class="login-box"><div class="login-logo">' + icon("live") + '<h1>MojaTV</h1><p>Dodaj swoją playlistę IPTV</p></div><div class="row-btns tabs"><div class="btn f tab" data-t="xtream">Xtream Codes</div><div class="btn f tab" data-t="m3u">Playlista M3U</div></div><div class="fields xtream"><input class="f" name="server" placeholder="Adres serwera, np. http://serwer.com:8080"><input class="f" name="username" placeholder="Nazwa użytkownika"><input class="f" name="password" placeholder="Hasło"></div><div class="fields m3u"><input class="f" name="m3uUrl" placeholder="Link do playlisty M3U (http://…)"></div><p class="muted small">OK otwiera klawiaturę telewizora. Możesz też wpisać dane wcześniej w pliku js/defaults.js przed instalacją.</p><div class="login-error"></div><div class="row-btns"><div class="btn primary f submit">Zaloguj</div></div></div></div>');
    container.appendChild(root2);
    function field(n) {
      return root2.querySelector('input[name="' + n + '"]');
    }
    field("server").value = saved.server || d.server || "";
    field("username").value = saved.username || d.username || "";
    field("password").value = saved.password || d.password || "";
    field("m3uUrl").value = saved.m3uUrl || d.m3uUrl || "";
    var err = root2.querySelector(".login-error");
    var submit = root2.querySelector(".submit");
    var busy = false;
    function setTab(t) {
      tab = t;
      root2.querySelector(".fields.xtream").style.display = t === "xtream" ? "" : "none";
      root2.querySelector(".fields.m3u").style.display = t === "m3u" ? "" : "none";
      root2.querySelectorAll(".tab").forEach(function (b) {
        b.classList.toggle("sel", b.getAttribute("data-t") === t);
      });
    }
    root2.querySelectorAll(".tab").forEach(function (b) {
      b.onEnter = function () {
        setTab(b.getAttribute("data-t"));
      };
    });
    setTab(tab);
    function go() {
      if (busy) return;
      var cfg;
      if (tab === "xtream") {
        cfg = credentialsFromUrl(field("server").value) || {
          type: "xtream",
          server: field("server").value.trim(),
          username: field("username").value.trim(),
          password: field("password").value.trim()
        };
      } else {
        var detected = credentialsFromUrl(field("m3uUrl").value);
        cfg = detected || {
          type: "m3u",
          m3uUrl: field("m3uUrl").value.trim()
        };
      }
      busy = true;
      err.textContent = "";
      submit.innerHTML = '<div class="spinner small"></div> Wczytywanie…';
      repo.signIn(cfg).then(function () {
        busy = false;
        onDone();
      }, function (e) {
        busy = false;
        submit.textContent = "Zaloguj";
        err.textContent = e.message || "Nie udało się zalogować.";
      });
    }
    submit.onEnter = go;
    root2.querySelectorAll("input").forEach(function (i) {
      i.onSubmit = function () {
        nav.focus(i);
      };
    });
    nav.focus(submit);
  }

  // src/main.js
  var MENU = [["home", "Start", "home"], ["live", "Telewizja", "live"], ["movies", "Filmy", "movie"], ["series", "Seriale", "series"], ["show", "Show", "show"], ["radio", "Radio", "radio"], ["search", "Szukaj", "search"], ["settings", "Ustawienia", "settings"]];
  var app = document.getElementById("app");
  var shell = null;
  var main = null;
  var currentScreen = "home";
  function registerKeys() {
    try {
      var names = ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaStop", "MediaFastForward", "MediaRewind", "ChannelUp", "ChannelDown", "ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue", "Info", "ChannelList", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
      var dev = window.tizen && window.tizen.tvinputdevice;
      if (!dev) return;
      if (dev.registerKeyBatch) {
        dev.registerKeyBatch(names, function () {}, function () {
          names.forEach(function (n) {
            try {
              dev.registerKey(n);
            } catch (e) {}
          });
        });
      } else {
        names.forEach(function (n) {
          try {
            dev.registerKey(n);
          } catch (e) {}
        });
      }
    } catch (e) {}
  }
  function exitApp() {
    try {
      window.tizen.application.getCurrentApplication().exit();
    } catch (e) {}
  }
  function buildShell() {
    app.innerHTML = "";
    shell = el('<div class="shell"><div class="side"><div class="brand">' + icon("live") + '<span>MojaTV</span></div><div class="menu"></div><div class="radio-bar"></div><div class="clock"></div></div><div class="main"></div></div>');
    app.appendChild(shell);
    main = shell.querySelector(".main");
    var menu = shell.querySelector(".menu");
    MENU.forEach(function (m) {
      var n = el('<div class="mi f" data-group="menu" data-id="' + m[0] + '">' + icon(m[2]) + "<span>" + m[1] + "</span></div>");
      n.onEnter = function () {
        goTo(m[0], true);
      };
      menu.appendChild(n);
    });
    nav.reset(shell, {
      onBack: onRootBack
    });
    updateClock();
    renderRadioBar(Radio.current());
  }
  function updateClock() {
    var c = shell && shell.querySelector(".clock");
    if (!c) return;
    var d = /* @__PURE__ */new Date();
    c.textContent = ("0" + d.getHours()).slice(-2) + ":" + ("0" + d.getMinutes()).slice(-2);
  }
  setInterval(updateClock, 15e3);
  function markMenu() {
    shell.querySelectorAll(".mi").forEach(function (m) {
      m.classList.toggle("active", m.getAttribute("data-id") === currentScreen);
    });
  }
  function render(id) {
    switch (id) {
      case "home":
        renderHome(main, goTo);
        break;
      case "live":
        renderLive(main);
        break;
      case "movies":
        renderMovies(main);
        break;
      case "series":
        renderSeries(main);
        break;
      case "show":
        renderShow(main, function () {
          openShowSettings(function () {
            goTo("show", true);
          });
        });
        break;
      case "radio":
        renderRadio(main);
        break;
      case "search":
        renderSearch(main);
        break;
      case "settings":
        renderSettings(main, {
          signOut
        });
        break;
    }
  }
  function goTo(id, focusContent) {
    currentScreen = id;
    markMenu();
    render(id);
    if (focusContent) focusMain();
  }
  function focusMain() {
    var target = main.querySelector(".cat.sel.f") || main.querySelector(".f");
    if (target) nav.focus(target);
  }
  function onRootBack() {
    var cur = nav.current();
    if (cur && !cur.classList.contains("mi")) {
      nav.focus(shell.querySelector('.mi[data-id="' + currentScreen + '"]'));
      return;
    }
    confirmDialog("Zamknąć MojaTV?", "", "Zamknij").then(function (ok) {
      if (ok) exitApp();
    });
  }
  function signOut() {
    Radio.stop();
    repo.signOut();
    showLogin();
  }
  function showLogin() {
    app.innerHTML = "";
    var box = el('<div class="login-wrap"></div>');
    app.appendChild(box);
    nav.reset(box, {
      onBack: function onBack() {
        confirmDialog("Zamknąć MojaTV?", "", "Zamknij").then(function (ok) {
          if (ok) exitApp();
        });
      }
    });
    renderLogin(box, startApp);
  }
  function startApp() {
    buildShell();
    currentScreen = "home";
    goTo("home", false);
    var first = main.querySelector(".f");
    nav.focus(first || shell.querySelector('.mi[data-id="home"]'));
  }
  repo.onChange(function (what) {
    if (!shell || !main || !main.isConnected && !document.body.contains(main)) return;
    if (what === "catalog") {
      if (nav.layerCount() > 1 || Player.isActive()) return;
      var cur = nav.current();
      var inMain = cur && main.contains(cur);
      render(currentScreen);
      if (inMain || !cur || !document.body.contains(cur)) focusMain();
    }
    if (what === "progress" && currentScreen === "home" && nav.layerCount() === 1) {
      var c = nav.current();
      var wasMain = c && main.contains(c);
      render("home");
      if (wasMain) focusMain();
    }
  });
  function renderRadioBar(state) {
    var bar = shell && shell.querySelector(".radio-bar");
    if (!bar) return;
    if (!state) {
      bar.innerHTML = "";
      bar.className = "radio-bar";
      return;
    }
    var label = {
      buffering: "Łączenie…",
      playing: "Teraz gra",
      paused: "Pauza",
      error: "Błąd odtwarzania"
    }[state.state] || "";
    bar.className = "radio-bar on";
    bar.innerHTML = img(state.item.image, state.item.title, "logo-s") + '<div><div class="rb-label">' + esc(label) + '</div><div class="rb-name">' + esc(state.item.title) + '</div><div class="rb-hint">⏯ pauza · ⏹ stop</div></div>';
  }
  Radio.onChange(renderRadioBar);
  document.addEventListener("keydown", function (e) {
    var name = KEYS[e.keyCode];
    var ae = document.activeElement;
    if (ae && ae.tagName === "INPUT") {
      if (name === "imedone" || name === "imecancel" || e.keyCode === 13) {
        e.preventDefault();
        ae.blur();
        if (ae.onSubmit) ae.onSubmit();
        return;
      }
      if (name === "back") {
        e.preventDefault();
        ae.blur();
        return;
      }
      if (name === "up" || name === "down") ae.blur();else return;
    }
    if (!name) return;
    e.preventDefault();
    if (name === "backspace") name = "back";
    if (Player.handleKey(name)) return;
    if (Radio.current() && (name === "playpause" || name === "play" || name === "pause")) {
      Radio.toggle();
      return;
    }
    if (Radio.current() && name === "stop") {
      Radio.stop();
      return;
    }
    nav.handleKey(name);
  });
  document.addEventListener("visibilitychange", function () {
    Player.onVisibility(document.hidden);
  });
  registerKeys();
  if (!(window.webapis && window.webapis.avplay)) {
    avObj = document.getElementById("av-player");
    if (avObj) avObj.parentNode.removeChild(avObj);
  }
  var avObj;
  if (repo.source) {
    startApp();
    repo.load();
  } else {
    showLogin();
  }
})();