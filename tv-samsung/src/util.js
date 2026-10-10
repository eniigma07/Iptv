// Narzędzia: zapytania sieciowe, pamięć lokalna, formatowanie.

// Bez "class extends Error" — na starszych telewizorach instanceof by nie działał.
export function HttpError(status, message) {
  var e = new Error(message || (status ? "Serwer zwrócił błąd HTTP " + status : "Brak połączenia z serwerem"));
  e.isHttp = true;
  e.status = status; // 0 = brak połączenia / błąd TLS / blokada
  return e;
}

/** Pobiera tekst. XMLHttpRequest działa na wszystkich wersjach Tizen (fetch nie zawsze). */
export function httpText(url, timeoutMs) {
  return new Promise(function (resolve, reject) {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", url, true);
    xhr.timeout = timeoutMs || 60000;
    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.responseText || "");
      else reject(HttpError(xhr.status));
    };
    xhr.onerror = function () { reject(HttpError(0)); };
    xhr.ontimeout = function () { reject(HttpError(0, "Serwer nie odpowiada (przekroczony czas)")); };
    xhr.send();
  });
}

export function httpJson(url, timeoutMs) {
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

// ---------- Pamięć lokalna ----------

var PREFIX = "mojatv.";

export const store = {
  get: function (key, fallback) {
    try {
      var v = localStorage.getItem(PREFIX + key);
      return v == null ? fallback : JSON.parse(v);
    } catch (e) {
      return fallback;
    }
  },
  set: function (key, value) {
    try {
      if (value === undefined || value === null) localStorage.removeItem(PREFIX + key);
      else localStorage.setItem(PREFIX + key, JSON.stringify(value));
    } catch (e) {
      /* pełna pamięć — ignorujemy */
    }
  },
};

// ---------- Tekst ----------

export function esc(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

var PL = { "ą": "a", "ć": "c", "ę": "e", "ł": "l", "ń": "n", "ó": "o", "ś": "s", "ź": "z", "ż": "z" };

export function normalize(s) {
  var t = String(s || "").toLowerCase();
  if (t.normalize) t = t.normalize("NFD").replace(/[̀-ͯ]/g, "");
  return t.replace(/[ąćęłńóśźż]/g, function (c) { return PL[c]; });
}

export function pad2(n) {
  return n < 10 ? "0" + n : "" + n;
}

export function formatTime(ms) {
  var d = new Date(ms);
  return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
}

export function formatClock(ms) {
  var s = Math.max(0, Math.floor(ms / 1000));
  var h = Math.floor(s / 3600);
  var m = Math.floor((s % 3600) / 60);
  var sec = s % 60;
  return (h > 0 ? h + ":" + pad2(m) : m) + ":" + pad2(sec);
}

export function formatDuration(ms) {
  var min = Math.max(0, Math.round(ms / 60000));
  var h = Math.floor(min / 60);
  return h > 0 ? h + " godz. " + (min % 60) + " min" : min + " min";
}

export function formatRating(r) {
  if (r == null || r === "") return null;
  var d = parseFloat(String(r).replace(",", "."));
  if (isNaN(d)) return String(r).slice(0, 4);
  if (d <= 0) return null;
  if (d > 10) d = d / 10;
  return "★ " + d.toFixed(1);
}

export function debounce(fn, ms) {
  var t = null;
  return function () {
    var args = arguments;
    var self = this;
    clearTimeout(t);
    t = setTimeout(function () { fn.apply(self, args); }, ms);
  };
}

/** Bezpieczne pobranie tekstu z obiektu JSON. */
export function str(o, key) {
  if (!o) return null;
  var v = o[key];
  if (v === undefined || v === null) return null;
  v = String(v).trim();
  return v === "" || v === "null" ? null : v;
}

export function b64decode(s) {
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
