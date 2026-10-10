// Źródła danych: Xtream Codes, playlisty M3U, katalog radia.
import { httpJson, httpText, str, b64decode, normalize } from "./util.js";

// ================= Xtream Codes =================

export function normalizeServer(raw) {
  var s = String(raw || "").trim();
  if (!s) return s;
  if (!/^https?:\/\//i.test(s)) s = "http://" + s;
  ["/player_api.php", "/get.php", "/xmltv.php"].forEach(function (m) {
    var i = s.indexOf(m);
    if (i > 0) s = s.slice(0, i);
  });
  return s.replace(/\/+$/, "");
}

/** Rozpoznaje link typu http://serwer:port/get.php?username=X&password=Y */
export function credentialsFromUrl(url) {
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
  return { type: "xtream", server: m[1], username: params.username, password: params.password };
}

export function Xtream(cfg) {
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
    base: base,
    liveUrl: function (id, ext) { return base + "/live/" + user + "/" + pass + "/" + id + "." + ext; },
    movieUrl: function (id, ext) { return base + "/movie/" + user + "/" + pass + "/" + id + "." + ext; },
    episodeUrl: function (id, ext) { return base + "/series/" + user + "/" + pass + "/" + id + "." + ext; },
    m3uUrl: function (fmt) { return base + "/get.php?" + auth + "&type=m3u_plus&output=" + fmt; },
    xmltvUrl: function () { return base + "/xmltv.php?" + auth; },

    login: function () {
      return httpJson(api(), 30000).then(function (root) {
        var info = root && root.user_info;
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
          expiresAt: isNaN(exp) ? null : exp * 1000,
          maxConnections: str(info, "max_connections"),
          activeConnections: str(info, "active_cons"),
          isTrial: str(info, "is_trial") === "1",
        };
      });
    },

    categories: function (action) {
      return httpJson(api(action)).then(function (j) {
        return list(j).filter(function (o) { return o && str(o, "category_id"); }).map(function (o) {
          return { id: str(o, "category_id"), name: str(o, "category_name") || "Bez nazwy" };
        });
      });
    },

    live: function (ext) {
      return httpJson(api("get_live_streams"), 120000).then(function (j) {
        return list(j).filter(function (o) { return o && str(o, "stream_id"); }).map(function (o) {
          var id = str(o, "stream_id");
          return {
            id: id,
            name: str(o, "name") || "Kanał " + id,
            logo: str(o, "stream_icon"),
            categoryId: str(o, "category_id") || "",
            epgId: str(o, "epg_channel_id"),
            url: self.liveUrl(id, ext),
            num: parseInt(str(o, "num"), 10) || 0,
          };
        });
      });
    },

    movies: function () {
      return httpJson(api("get_vod_streams"), 120000).then(function (j) {
        return list(j).filter(function (o) { return o && str(o, "stream_id"); }).map(function (o) {
          var id = str(o, "stream_id");
          var r = str(o, "rating");
          return {
            id: id,
            name: str(o, "name") || "Film " + id,
            poster: str(o, "stream_icon"),
            categoryId: str(o, "category_id") || "",
            rating: r === "0" ? null : r,
            url: self.movieUrl(id, str(o, "container_extension") || "mp4"),
            added: parseInt(str(o, "added"), 10) || 0,
          };
        });
      });
    },

    series: function () {
      return httpJson(api("get_series"), 120000).then(function (j) {
        return list(j).filter(function (o) { return o && str(o, "series_id"); }).map(function (o) {
          var id = str(o, "series_id");
          var r = str(o, "rating");
          var date = str(o, "releaseDate") || str(o, "release_date");
          return {
            id: id,
            name: str(o, "name") || "Serial " + id,
            poster: str(o, "cover"),
            categoryId: str(o, "category_id") || "",
            rating: r === "0" ? null : r,
            plot: str(o, "plot"),
            genre: str(o, "genre"),
            year: date ? date.slice(0, 4) : null,
          };
        });
      });
    },

    movieDetail: function (movie) {
      return httpJson(api("get_vod_info", "&vod_id=" + movie.id)).then(function (root) {
        var info = (root && root.info) || {};
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
          rating: r && r !== "0" ? r : movie.rating,
        };
      });
    },

    seriesDetail: function (series) {
      return httpJson(api("get_series_info", "&series_id=" + series.id)).then(function (root) {
        root = root || {};
        var info = root.info || {};
        var seasons = {};

        function add(o, hint) {
          var id = str(o, "id");
          if (!id) return;
          var season = parseInt(str(o, "season"), 10) || hint;
          var arr = seasons[season] || (seasons[season] = []);
          var num = parseInt(str(o, "episode_num"), 10) || arr.length + 1;
          var ei = o.info || {};
          arr.push({
            id: id,
            season: season,
            number: num,
            title: str(o, "title") || "Odcinek " + num,
            plot: str(ei, "plot"),
            image: str(ei, "movie_image"),
            duration: str(ei, "duration"),
            url: self.episodeUrl(id, str(o, "container_extension") || "mp4"),
          });
        }

        var eps = root.episodes;
        if (Array.isArray(eps)) {
          eps.forEach(function (x, i) {
            if (Array.isArray(x)) x.forEach(function (o) { add(o, i + 1); });
            else if (x) add(x, 1);
          });
        } else if (eps && typeof eps === "object") {
          Object.keys(eps).forEach(function (k) {
            (eps[k] || []).forEach(function (o) { add(o, parseInt(k, 10) || 1); });
          });
        }
        Object.keys(seasons).forEach(function (k) {
          seasons[k].sort(function (a, b) { return a.number - b.number; });
        });
        var date = str(info, "releaseDate") || str(info, "release_date");
        return {
          plot: str(info, "plot") || series.plot,
          cast: str(info, "cast"),
          genre: str(info, "genre") || series.genre,
          year: date ? date.slice(0, 4) : series.year,
          backdrop: firstImage(info.backdrop_path) || str(info, "cover"),
          seasons: seasons,
        };
      });
    },

    shortEpg: function (streamId, limit) {
      return httpJson(api("get_short_epg", "&stream_id=" + streamId + "&limit=" + (limit || 6)), 20000).then(function (root) {
        var arr = (root && root.epg_listings) || [];
        return arr.map(function (o) {
          var start = parseInt(str(o, "start_timestamp"), 10);
          var end = parseInt(str(o, "stop_timestamp") || str(o, "end_timestamp"), 10);
          if (isNaN(start) || isNaN(end)) return null;
          return {
            title: b64decode(str(o, "title")) || "",
            description: b64decode(str(o, "description")),
            start: start * 1000,
            end: end * 1000,
          };
        }).filter(Boolean);
      });
    },
  };
  return self;
}

// ================= M3U =================

var ATTR = /([A-Za-z0-9_-]+)="([^"]*)"/g;
var VIDEO_EXT = [".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".flv"];
var EPISODE = /^(.*?)[\s._\-|:]*[Ss](\d{1,2})[\s._-]*[Ee](\d{1,3})/;

function attrs(line) {
  var out = {};
  var m;
  ATTR.lastIndex = 0;
  while ((m = ATTR.exec(line))) out[m[1].toLowerCase()] = m[2];
  return out;
}

function nameOf(line) {
  // nazwa jest po przecinku za ostatnim atrybutem
  var from = line.indexOf(":");
  var m;
  ATTR.lastIndex = 0;
  while ((m = ATTR.exec(line))) from = m.index + m[0].length - 1;
  var comma = line.indexOf(",", from + 1);
  return comma >= 0 ? line.slice(comma + 1).trim() : "";
}

export function parseM3u(text) {
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
    } else if (line.charAt(0) === "#") {
      // pomijamy
    } else if (info) {
      var at = attrs(info);
      entries.push({
        name: nameOf(info) || at["tvg-name"] || "Bez nazwy",
        url: line,
        group: (at["group-title"] && at["group-title"].trim()) || group || "Inne",
        logo: at["tvg-logo"] || null,
        tvgId: at["tvg-id"] || null,
      });
      info = null;
      group = null;
    }
  }
  return { epgUrl: epgUrl, entries: entries };
}

function isVideoFile(url) {
  var p = url.split("?")[0].toLowerCase();
  return VIDEO_EXT.some(function (e) { return p.slice(-e.length) === e; });
}

export function isRadioName(name) {
  var n = String(name || "");
  return /radio/i.test(n) || n.indexOf("راديو") >= 0 || n.indexOf("إذاع") >= 0;
}

function isSeriesEntry(e) {
  return e.url.indexOf("/series/") >= 0 || (isVideoFile(e.url) && EPISODE.test(e.name));
}

function isMovieEntry(e) {
  return !isSeriesEntry(e) && (e.url.indexOf("/movie/") >= 0 || isVideoFile(e.url));
}

function hashId(s) {
  var h = 0;
  for (var i = 0; i < s.length; i++) h = ((h << 5) - h + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(16);
}

/** Zamienia playlistę M3U na katalog z kanałami, filmami i serialami. */
export function catalogFromM3u(playlist) {
  var liveCats = [], movieCats = [], seriesCats = [];
  var seenL = {}, seenM = {}, seenS = {};
  var live = [], movies = [], seriesMap = {}, seriesList = [], episodes = {};

  playlist.entries.forEach(function (e, idx) {
    var id = hashId(e.url) + "-" + idx;
    if (isSeriesEntry(e)) {
      var m = e.name.match(EPISODE);
      var title = m ? m[1].replace(/[-|:.\s]+$/, "").trim() || e.group : e.name;
      var season = m ? parseInt(m[2], 10) || 1 : 1;
      var number = m ? parseInt(m[3], 10) || idx + 1 : idx + 1;
      var sid = "m" + hashId(normalize(title));
      if (!seriesMap[sid]) {
        seriesMap[sid] = { id: sid, name: title, poster: e.logo, categoryId: e.group, rating: null, plot: null, genre: null, year: null };
        seriesList.push(seriesMap[sid]);
        if (!seenS[e.group]) { seenS[e.group] = 1; seriesCats.push({ id: e.group, name: e.group }); }
      }
      (episodes[sid] || (episodes[sid] = [])).push({
        id: id, season: season, number: number, title: e.name, plot: null, image: e.logo, duration: null, url: e.url,
      });
    } else if (isMovieEntry(e)) {
      if (!seenM[e.group]) { seenM[e.group] = 1; movieCats.push({ id: e.group, name: e.group }); }
      movies.push({ id: id, name: e.name, poster: e.logo, categoryId: e.group, rating: null, url: e.url, added: 0 });
    } else {
      if (!seenL[e.group]) { seenL[e.group] = 1; liveCats.push({ id: e.group, name: e.group }); }
      live.push({ id: id, name: e.name, logo: e.logo, categoryId: e.group, epgId: e.tvgId || e.name, url: e.url, num: 0 });
    }
  });

  return {
    liveCategories: liveCats,
    live: live,
    movieCategories: movieCats,
    movies: movies,
    seriesCategories: seriesCats,
    series: seriesList,
    m3uEpisodes: episodes,
    epgUrl: playlist.epgUrl,
  };
}

export function fetchM3u(url) {
  return httpText(url, 180000).then(function (text) {
    var t = text.replace(/^﻿/, "").trim();
    if (t.indexOf("#EXTM3U") !== 0 && t.indexOf("#EXTINF") < 0) {
      var e = new Error("Pod tym linkiem nie ma playlisty M3U.");
      e.notM3u = true;
      throw e;
    }
    return parseM3u(t);
  });
}

// ================= Radio (radio-browser.info) =================

var RADIO_HOSTS = [
  "https://de1.api.radio-browser.info",
  "https://nl1.api.radio-browser.info",
  "https://at1.api.radio-browser.info",
  "https://fi1.api.radio-browser.info",
];

function radioGet(path, i) {
  i = i || 0;
  return httpJson(RADIO_HOSTS[i] + path, 20000).then(function (arr) {
    return (Array.isArray(arr) ? arr : []).map(function (o) {
      var url = str(o, "url_resolved") || str(o, "url");
      if (!url) return null;
      var tags = str(o, "tags");
      return {
        id: str(o, "stationuuid") || url,
        name: str(o, "name") || "Radio",
        url: url,
        logo: str(o, "favicon"),
        tags: tags ? tags.split(",").slice(0, 3).join(" · ") : null,
      };
    }).filter(Boolean);
  }, function (err) {
    if (i + 1 < RADIO_HOSTS.length) return radioGet(path, i + 1);
    throw err;
  });
}

export const RadioBrowser = {
  top: function (country) {
    return radioGet("/json/stations/bycountrycodeexact/" + country + "?order=clickcount&reverse=true&hidebroken=true&limit=150");
  },
  search: function (q) {
    return radioGet("/json/stations/search?name=" + encodeURIComponent(q) + "&order=clickcount&reverse=true&hidebroken=true&limit=80");
  },
};
