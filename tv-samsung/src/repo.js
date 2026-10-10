// Stan aplikacji: źródło, katalog, ulubione, historia, ustawienia.
import { store, normalize, httpText } from "./util.js";
import { Xtream, normalizeServer, fetchM3u, catalogFromM3u, isRadioName } from "./api.js";

var listeners = [];

export const repo = {
  source: store.get("source", null),
  settings: Object.assign({ liveFormat: "ts", hideAdult: false, radioCountry: "PL", showCategories: null }, store.get("settings", {})),
  favorites: store.get("favorites", []),
  recent: store.get("recent", []),
  progress: store.get("progress", {}),
  account: null,
  catalog: null,
  loading: false,
  error: null,
  epgCache: {},
  detailCache: {},

  onChange: function (fn) { listeners.push(fn); },
  emit: function (what) { listeners.forEach(function (fn) { fn(what); }); },

  saveSettings: function () { store.set("settings", this.settings); },

  api: function () {
    return this.source && this.source.type === "xtream" ? Xtream(this.source) : null;
  },

  // ---------------- Logowanie ----------------

  /** Sprawdza dane (z automatycznymi próbami http i M3U), zapisuje i wczytuje katalog. */
  signIn: function (input) {
    var self = this;
    var p;
    if (input.type === "xtream") {
      if (!input.server || !input.username) return Promise.reject(new Error("Uzupełnij adres serwera i login."));
      p = connectXtream(Object.assign({}, input, { server: normalizeServer(input.server) }), self);
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

  signOut: function () {
    this.source = null;
    this.catalog = null;
    this.account = null;
    this.epgCache = {};
    this.detailCache = {};
    store.set("source", null);
  },

  // ---------------- Katalog ----------------

  load: function (force) {
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
      job = Promise.all([
        api.login().catch(function () { return null; }),
        api.categories("get_live_categories"),
        api.live(self.settings.liveFormat),
        api.categories("get_vod_categories"),
        api.movies(),
        api.categories("get_series_categories"),
        api.series(),
      ]).then(function (r) {
        if (r[0]) self.account = r[0];
        return {
          liveCategories: r[1],
          live: r[2],
          movieCategories: r[3],
          movies: r[4].sort(function (a, b) { return b.added - a.added; }),
          seriesCategories: r[5],
          series: r[6],
          m3uEpisodes: {},
          epgUrl: null,
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
      cat.liveCategories.forEach(function (c) { if (isRadioName(c.name)) radioCats[c.id] = 1; });
      cat.radioChannels = cat.live.filter(function (ch) { return radioCats[ch.categoryId]; });
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

  findMovie: function (id) {
    return this.catalog ? this.catalog.movies.find(function (m) { return m.id === id; }) : null;
  },
  findSeries: function (id) {
    return this.catalog ? this.catalog.series.find(function (s) { return s.id === id; }) : null;
  },

  movieDetail: function (movie) {
    var key = "m" + movie.id;
    var self = this;
    if (self.detailCache[key]) return Promise.resolve(self.detailCache[key]);
    var api = self.api();
    var empty = { rating: movie.rating };
    var p = api ? api.movieDetail(movie).catch(function () { return empty; }) : Promise.resolve(empty);
    return p.then(function (d) { self.detailCache[key] = d; return d; });
  },

  seriesDetail: function (series) {
    var key = "s" + series.id;
    var self = this;
    if (self.detailCache[key]) return Promise.resolve(self.detailCache[key]);
    var api = self.api();
    var p;
    if (api) {
      p = api.seriesDetail(series);
    } else {
      var eps = (self.catalog && self.catalog.m3uEpisodes[series.id]) || [];
      var seasons = {};
      eps.forEach(function (e) { (seasons[e.season] || (seasons[e.season] = [])).push(e); });
      Object.keys(seasons).forEach(function (k) { seasons[k].sort(function (a, b) { return a.number - b.number; }); });
      p = Promise.resolve({ seasons: seasons });
    }
    return p.then(function (d) { self.detailCache[key] = d; return d; });
  },

  // ---------------- Program TV ----------------

  /** Program dla kanału (Xtream, krótkie EPG z serwera; zapamiętywane na 10 minut). */
  programs: function (item) {
    var self = this;
    var api = self.api();
    if (!api || !item || item.kind !== "live") return Promise.resolve([]);
    var id = item.key.slice(5);
    var c = self.epgCache[id];
    var now = Date.now();
    if (c && now - c.at < 600000) return Promise.resolve(c.list.filter(function (p) { return p.end > now; }));
    return api.shortEpg(id, 6).then(function (list) {
      self.epgCache[id] = { at: Date.now(), list: list };
      return list.filter(function (p) { return p.end > Date.now(); });
    }).catch(function () { return []; });
  },

  // ---------------- Ulubione / historia / postęp ----------------

  isFavorite: function (key) {
    return this.favorites.some(function (f) { return f.key === key; });
  },
  toggleFavorite: function (item) {
    var i = -1;
    this.favorites.forEach(function (f, idx) { if (f.key === item.key) i = idx; });
    if (i >= 0) this.favorites.splice(i, 1);
    else this.favorites.unshift(item);
    store.set("favorites", this.favorites);
    return i < 0;
  },
  addRecent: function (item) {
    if (item.kind !== "live" && item.kind !== "radio") return;
    this.recent = [item].concat(this.recent.filter(function (r) { return r.key !== item.key; })).slice(0, 30);
    store.set("recent", this.recent);
  },
  saveProgress: function (item, pos, dur) {
    if (item.kind === "live" || item.kind === "radio" || !(dur > 0)) return;
    this.progress[item.key] = { item: item, pos: pos, dur: dur, at: Date.now() };
    var keys = Object.keys(this.progress);
    if (keys.length > 300) {
      var self = this;
      keys.sort(function (a, b) { return self.progress[a].at - self.progress[b].at; });
      keys.slice(0, keys.length - 300).forEach(function (k) { delete self.progress[k]; });
    }
    store.set("progress", this.progress);
  },
  progressOf: function (key) {
    var p = this.progress[key];
    if (!p) return null;
    return { pos: p.pos, dur: p.dur, at: p.at, item: p.item, fraction: p.dur > 0 ? Math.min(1, p.pos / p.dur) : 0, finished: p.dur > 0 && p.pos >= p.dur * 0.95 };
  },
  clearHistory: function () {
    this.progress = {};
    this.recent = [];
    store.set("progress", {});
    store.set("recent", []);
  },

  // ---------------- Show ----------------

  allShowCandidates: function () {
    var c = this.catalog;
    if (!c) return [];
    return c.movieCategories.map(function (x) { return { key: "movie:" + x.id, id: x.id, name: x.name, isSeries: false }; })
      .concat(c.seriesCategories.map(function (x) { return { key: "series:" + x.id, id: x.id, name: x.name, isSeries: true }; }));
  },
  autoShowKeys: function () {
    return this.allShowCandidates().filter(function (cat) {
      var n = normalize(cat.name);
      return SHOW_WORDS.some(function (w) { return n.indexOf(w) >= 0; });
    }).map(function (c) { return c.key; });
  },
  showCategories: function () {
    var keys = this.settings.showCategories || this.autoShowKeys();
    return this.allShowCandidates().filter(function (c) { return keys.indexOf(c.key) >= 0; });
  },

  // ---------------- Wyszukiwanie ----------------

  search: function (q) {
    var c = this.catalog;
    var n = normalize(q.trim());
    if (!c || n.length < 2) return { live: [], movies: [], series: [] };
    function pick(arr) {
      var out = [];
      for (var i = 0; i < arr.length && out.length < 40; i++) if (normalize(arr[i].name).indexOf(n) >= 0) out.push(arr[i]);
      return out;
    }
    return { live: pick(c.live), movies: pick(c.movies), series: pick(c.series) };
  },
};

var SHOW_WORDS = [
  "show", "program", "reality", "talk", "rozrywk", "entertain", "dokument", "documentar",
  "kabaret", "stand-up", "stand up", "standup", "teleturniej", "quiz", "magazyn", "lifestyle",
  "kulinar", "cooking", "travel", "podroz", "variety", "برامج", "منوعات", "وثائقي", "عروض",
];

var ADULT = ["xxx", "adult", "18+", "+18", "porn", "erotic", "erotyk", "dla doroslych"];

function isAdult(name) {
  var n = normalize(name);
  return ADULT.some(function (w) { return n.indexOf(w) >= 0; });
}

function withoutAdult(c) {
  function split(cats) {
    var blocked = {};
    var kept = cats.filter(function (x) {
      if (isAdult(x.name)) { blocked[x.id] = 1; return false; }
      return true;
    });
    return { kept: kept, blocked: blocked };
  }
  var l = split(c.liveCategories), m = split(c.movieCategories), s = split(c.seriesCategories);
  return Object.assign({}, c, {
    liveCategories: l.kept,
    live: c.live.filter(function (x) { return !l.blocked[x.categoryId] && !isAdult(x.name); }),
    movieCategories: m.kept,
    movies: c.movies.filter(function (x) { return !m.blocked[x.categoryId]; }),
    seriesCategories: s.kept,
    series: c.series.filter(function (x) { return !s.blocked[x.categoryId]; }),
  });
}

export function friendlyError(e) {
  if (!e) return "Wystąpił nieznany błąd.";
  if (e.isHttp && e.status === 0) return e.message === "Brak połączenia z serwerem"
    ? "Nie można połączyć się z serwerem. Sprawdź adres (http/https, port) i internet."
    : e.message;
  return e.message || "Wystąpił nieznany błąd.";
}

/**
 * Logowanie Xtream z próbami:
 * 1) https:// → http:// (gdy serwer nie obsługuje szyfrowania),
 * 2) gdy logowanie Xtream jest niedostępne — playlista get.php z tymi samymi danymi.
 */
function connectXtream(cfg, self) {
  var servers = [cfg.server];
  if (/^https:\/\//i.test(cfg.server)) servers.push("http://" + cfg.server.slice(8));
  var reachable = null;
  var lastError = null;

  function tryServer(i) {
    if (i >= servers.length) return fallbackM3u();
    var candidate = Object.assign({}, cfg, { server: servers[i] });
    return Xtream(candidate).login().then(function (acc) {
      self.account = acc;
      return candidate;
    }, function (e) {
      lastError = e;
      if (e.auth) throw e;
      if ((e.isHttp && e.status > 0) || e.notXtream || e.message === "Nieprawidłowa odpowiedź serwera") reachable = reachable || servers[i];
      return tryServer(i + 1);
    });
  }

  function fallbackM3u() {
    if (!reachable) throw new Error(friendlyError(lastError));
    var api = Xtream(Object.assign({}, cfg, { server: reachable }));
    var url = api.m3uUrl(self.settings.liveFormat);
    return httpText(url, 60000).then(function (t) {
      if (t.replace(/^﻿/, "").trim().indexOf("#EXTM3U") !== 0) throw new Error("x");
      return Object.assign({}, cfg, { type: "m3u", server: reachable, m3uUrl: url, epgUrl: cfg.epgUrl || api.xmltvUrl() });
    }).catch(function () {
      var code = lastError && lastError.isHttp && lastError.status ? " (HTTP " + lastError.status + ")" : "";
      throw new Error(
        "Serwer odpowiada, ale nie przyjmuje logowania pod tym adresem" + code +
        ". Sprawdź dane i adres serwera albo użyj linku M3U."
      );
    });
  }

  return tryServer(0);
}
