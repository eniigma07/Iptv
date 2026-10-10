// Filmy, Seriale, Show oraz ekrany szczegółów.
import { esc, formatRating, formatClock } from "../util.js";
import { el, img, lazyList, toast, icon } from "../dom.js";
import { nav } from "../nav.js";
import { repo } from "../repo.js";
import { Player } from "../player.js";
import { movieItem, seriesItem, episodeItem, catalogGate } from "../items.js";
import { categoryColumn } from "./live.js";

var ALL = "__all", FAV = "__fav";
var selectedBy = { movies: ALL, series: ALL, show: ALL };

/** Siatka plakatów. entries: [{type:'movie'|'series', obj}] */
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
    var n = el(
      '<div class="card f" data-group="grid">' + img(o.poster, o.name, "poster") +
      (badge ? '<span class="badge">' + esc(badge) + "</span>" : "") +
      (repo.isFavorite(key) ? icon("star", "fav") : "") +
      (p && !p.finished ? '<div class="pbar"><div style="width:' + Math.round(p.fraction * 100) + '%"></div></div>' : "") +
      '<div class="card-title">' + esc(o.name) + "</div></div>"
    );
    n.onEnter = function () {
      if (e.type === "movie") openMovie(o, onChanged);
      else openSeries(o, onChanged);
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

function vodLayout(main) {
  main.innerHTML = "";
  var root = el('<div class="cols"><div class="cats scroll"></div><div class="grid scroll"></div></div>');
  main.appendChild(root);
  return { cats: root.querySelector(".cats"), grid: root.querySelector(".grid") };
}

function counts(list) {
  var m = {};
  list.forEach(function (x) { m[x.categoryId] = (m[x.categoryId] || 0) + 1; });
  return m;
}

export function renderMovies(main) {
  if (!catalogGate(main)) return;
  var c = repo.catalog;
  var L = vodLayout(main);
  var cnt = counts(c.movies);
  var options = [{ id: ALL, name: "Wszystkie filmy", count: c.movies.length }, { id: FAV, name: "★ Ulubione" }]
    .concat(c.movieCategories.map(function (x) { return { id: x.id, name: x.name, count: cnt[x.id] || 0 }; }));
  function fill(id, focus) {
    selectedBy.movies = id;
    var list;
    if (id === ALL) list = c.movies;
    else if (id === FAV) {
      var keys = {};
      repo.favorites.forEach(function (f) { if (f.kind === "movie") keys[f.key.slice(6)] = 1; });
      list = c.movies.filter(function (m) { return keys[m.id]; });
    } else list = c.movies.filter(function (m) { return m.categoryId === id; });
    posterGrid(L.grid, list.map(function (m) { return { type: "movie", obj: m }; }), id === FAV ? "Brak ulubionych filmów." : "Brak filmów.");
    if (focus) nav.focus(L.grid.querySelector(".f"));
  }
  categoryColumn(L.cats, options, selectedBy.movies, fill);
  fill(selectedBy.movies, false);
}

export function renderSeries(main) {
  if (!catalogGate(main)) return;
  var c = repo.catalog;
  var L = vodLayout(main);
  var cnt = counts(c.series);
  var options = [{ id: ALL, name: "Wszystkie seriale", count: c.series.length }, { id: FAV, name: "★ Ulubione" }]
    .concat(c.seriesCategories.map(function (x) { return { id: x.id, name: x.name, count: cnt[x.id] || 0 }; }));
  function fill(id, focus) {
    selectedBy.series = id;
    var list;
    if (id === ALL) list = c.series;
    else if (id === FAV) {
      var keys = {};
      repo.favorites.forEach(function (f) { if (f.kind === "series") keys[f.key.slice(7)] = 1; });
      list = c.series.filter(function (s) { return keys[s.id]; });
    } else list = c.series.filter(function (s) { return s.categoryId === id; });
    posterGrid(L.grid, list.map(function (s) { return { type: "series", obj: s }; }), id === FAV ? "Brak ulubionych seriali." : "Brak seriali.");
    if (focus) nav.focus(L.grid.querySelector(".f"));
  }
  categoryColumn(L.cats, options, selectedBy.series, fill);
  fill(selectedBy.series, false);
}

export function renderShow(main, openShowSettings) {
  if (!catalogGate(main)) return;
  var c = repo.catalog;
  var cats = repo.showCategories();
  if (!cats.length) {
    main.innerHTML = "";
    var box = el(
      '<div class="center-msg"><p>Nie znaleziono automatycznie kategorii z programami typu show.<br>Wybierz je z listy kategorii Twojej playlisty.</p>' +
      '<div class="row-btns"><div class="btn f">Wybierz kategorie Show</div></div></div>'
    );
    box.querySelector(".btn").onEnter = openShowSettings;
    main.appendChild(box);
    return;
  }
  var L = vodLayout(main);
  var options = [{ id: ALL, name: "Wszystkie" }].concat(cats.map(function (x) {
    return { id: x.key, name: x.name + (x.isSeries ? "" : " (filmy)") };
  })).concat([{ id: "__edit", name: "✎ Zmień kategorie…" }]);
  function fill(id, focus) {
    if (id === "__edit") {
      if (focus) openShowSettings();
      return;
    }
    selectedBy.show = id;
    var keys = id === ALL ? cats.map(function (x) { return x.key; }) : [id];
    var mc = {}, sc = {};
    keys.forEach(function (k) {
      if (k.indexOf("movie:") === 0) mc[k.slice(6)] = 1;
      else sc[k.slice(7)] = 1;
    });
    var entries = c.series.filter(function (s) { return sc[s.categoryId]; }).map(function (s) { return { type: "series", obj: s }; })
      .concat(c.movies.filter(function (m) { return mc[m.categoryId]; }).map(function (m) { return { type: "movie", obj: m }; }));
    posterGrid(L.grid, entries, "Brak pozycji w tej kategorii.");
    if (focus) nav.focus(L.grid.querySelector(".f"));
  }
  if (selectedBy.show !== ALL && !cats.some(function (x) { return x.key === selectedBy.show; })) selectedBy.show = ALL;
  categoryColumn(L.cats, options, selectedBy.show, fill);
  fill(selectedBy.show, false);
}

// ---------------- Szczegóły ----------------

function overlay() {
  var o = el('<div class="detail"><div class="detail-bg"></div><div class="detail-shade"></div><div class="detail-content scroll"></div></div>');
  document.getElementById("app").appendChild(o);
  function close() {
    nav.pop();
    if (o.parentNode) o.parentNode.removeChild(o);
  }
  nav.push(o, { onBack: close });
  return { root: o, content: o.querySelector(".detail-content"), bg: o.querySelector(".detail-bg"), close: close };
}

function setBg(bg, url) {
  if (url) bg.style.backgroundImage = 'url("' + url.replace(/"/g, "%22") + '")';
}

function header(o, meta) {
  return '<div class="d-head">' + img(o.poster, o.name, "poster d-poster") + '<div class="d-info"><h1>' + esc(o.name) + "</h1>" +
    '<div class="d-meta">' + meta.filter(Boolean).map(esc).join("<span class='dot'>·</span>") + "</div>" +
    '<div class="d-buttons row-btns"></div><div class="d-plot"></div><div class="d-extra"></div></div></div>';
}

function favButton(item) {
  var b = el('<div class="btn f">' + icon("star") + "<span></span></div>");
  function label() { b.querySelector("span").textContent = repo.isFavorite(item.key) ? "W ulubionych" : "Dodaj do ulubionych"; }
  label();
  b.onEnter = function () { repo.toggleFavorite(item); label(); };
  return b;
}

export function openMovie(movie, onChanged) {
  var ov = overlay();
  var item = movieItem(movie);
  ov.content.innerHTML = header(movie, [formatRating(movie.rating)]);
  var btns = ov.content.querySelector(".d-buttons");

  function renderButtons() {
    btns.innerHTML = "";
    var p = repo.progressOf(item.key);
    var resume = p && !p.finished && p.pos > 30000;
    var play = el('<div class="btn primary f">' + icon("play") + "<span>" + (resume ? "Wznów od " + formatClock(p.pos) : "Odtwórz") + "</span></div>");
    play.onEnter = function () { Player.play([item], 0, -1, afterPlay); };
    btns.appendChild(play);
    if (resume) {
      var again = el('<div class="btn f">Od początku</div>');
      again.onEnter = function () { Player.play([item], 0, 0, afterPlay); };
      btns.appendChild(again);
    }
    btns.appendChild(favButton(item));
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
    ov.content.querySelector(".d-meta").innerHTML = [d.year, d.duration, formatRating(d.rating), d.genre]
      .filter(Boolean).map(esc).join("<span class='dot'>·</span>");
    ov.content.querySelector(".d-plot").textContent = d.plot || "";
    ov.content.querySelector(".d-extra").innerHTML =
      (d.director ? "<p><b>Reżyseria:</b> " + esc(d.director) + "</p>" : "") +
      (d.cast ? "<p><b>Obsada:</b> " + esc(d.cast) + "</p>" : "");
  });
}

export function openSeries(series, onChanged) {
  var ov = overlay();
  ov.content.innerHTML = header(series, [series.year, formatRating(series.rating), series.genre]) +
    '<div class="seasons hscroll"></div><div class="episodes"></div>';
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
    var nums = Object.keys(d.seasons).map(Number).sort(function (a, b) { return a - b; });
    if (!nums.length) { epsBox.innerHTML = '<div class="empty">Brak odcinków.</div>'; return; }
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
    Object.keys(detail.seasons).map(Number).sort(function (a, b) { return a - b; }).forEach(function (k) {
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
    var ep = allEpisodes().filter(function (e) { return "episode:" + e.id === best.item.key; })[0];
    return ep ? { ep: ep, p: repo.progressOf(best.item.key) } : null;
  }

  function play(list, i, from) {
    Player.play(list.map(function (e) { return episodeItem(e, series); }), i, from === undefined ? -1 : from, function () {
      var last = lastWatched();
      renderContinue(last);
      renderEpisodes();
      if (onChanged) onChanged();
    });
  }

  function renderContinue(last) {
    var old = btns.querySelector(".cont");
    if (old) btns.removeChild(old);
    var target = null, label = "Odtwórz";
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
      var n = el(
        '<div class="ep f" data-group="eps">' + img(ep.image || series.poster, String(ep.number), "ep-img") +
        '<div class="ep-text"><div class="ep-title">' + ep.number + ". " + esc(ep.title) + "</div>" +
        (ep.duration ? '<div class="muted">' + esc(ep.duration) + "</div>" : "") +
        (ep.plot ? '<div class="ep-plot">' + esc(ep.plot) + "</div>" : "") + "</div>" +
        (p ? '<div class="pbar"><div style="width:' + Math.round(p.fraction * 100) + '%"></div></div>' : "") + "</div>"
      );
      n.onEnter = function () { play(list, i); };
      epsBox.appendChild(n);
    });
  }
}
