// Start, Radio, Szukaj, Ustawienia, wybór kategorii Show, logowanie.
import { esc, formatDuration, debounce, formatRating } from "../util.js";
import { el, img, lazyList, toast, icon, confirmDialog } from "../dom.js";
import { nav } from "../nav.js";
import { repo } from "../repo.js";
import { Player, Radio } from "../player.js";
import { RadioBrowser, credentialsFromUrl } from "../api.js";
import { liveItem, radioItem, catalogGate } from "../items.js";
import { categoryColumn } from "./live.js";
import { openMovie, openSeries } from "./vod.js";

// ---------------- Start ----------------

function rowSection(title, items, render) {
  if (!items.length) return null;
  var s = el('<div class="section"><h2>' + esc(title) + '</h2><div class="hrow hscroll"><div class="hrow-inner"></div></div></div>');
  var inner = s.querySelector(".hrow-inner");
  items.forEach(function (it, i) { inner.appendChild(render(it, i)); });
  return s;
}

function wideCard(title, sub, image, fit, progress) {
  return el(
    '<div class="wcard f' + (fit ? " fit" : "") + '">' + img(image, title, "wimg") +
    (progress != null ? '<div class="pbar"><div style="width:' + Math.round(progress * 100) + '%"></div></div>' : "") +
    '<div class="wtitle">' + esc(title) + "</div>" + (sub ? '<div class="wsub">' + esc(sub) + "</div>" : "") + "</div>"
  );
}

function openFavoriteVod(f, rerender) {
  var id = f.key.slice(f.key.indexOf(":") + 1);
  if (f.kind === "movie") {
    var m = repo.findMovie(id);
    if (m) openMovie(m, rerender); else toast("Tej pozycji nie ma już w playliście");
  } else {
    var s = repo.findSeries(id);
    if (s) openSeries(s, rerender); else toast("Tej pozycji nie ma już w playliście");
  }
}

export function renderHome(main, goTo) {
  main.innerHTML = "";
  var wrap = el('<div class="home scroll"></div>');
  main.appendChild(wrap);
  var rerender = function () { renderHome(main, goTo); };

  var cont = Object.keys(repo.progress).map(function (k) { return repo.progressOf(k); })
    .filter(function (p) { return !p.finished && p.pos > 30000; })
    .sort(function (a, b) { return b.at - a.at; }).slice(0, 20);
  var favLive = repo.favorites.filter(function (f) { return f.kind === "live"; });
  var favVod = repo.favorites.filter(function (f) { return f.kind === "movie" || f.kind === "series"; });
  var favRadio = repo.favorites.filter(function (f) { return f.kind === "radio"; });
  var recent = repo.recent;

  var sections = [
    rowSection("Kontynuuj oglądanie", cont, function (p) {
      var n = wideCard(p.item.title, p.item.subtitle || "Pozostało " + formatDuration(p.dur - p.pos), p.item.image, false, p.fraction);
      n.onEnter = function () { Player.play([p.item], 0, -1, rerender); };
      return n;
    }),
    rowSection("Ulubione kanały", favLive, function (f, i) {
      var n = wideCard(f.title, null, f.image, true);
      n.onEnter = function () { Player.play(favLive, i, -1); };
      return n;
    }),
    rowSection("Ostatnio oglądane", recent, function (f, i) {
      var n = wideCard(f.title, f.kind === "radio" ? "Radio" : null, f.image, true);
      n.onEnter = function () {
        if (f.kind === "radio") Radio.play(f);
        else Player.play(recent.filter(function (r) { return r.kind === "live"; }), recent.filter(function (r) { return r.kind === "live"; }).indexOf(f), -1);
      };
      return n;
    }),
    rowSection("Ulubione filmy i seriale", favVod, function (f) {
      var n = el('<div class="card f">' + img(f.image, f.title, "poster") + '<div class="card-title">' + esc(f.title) + "</div></div>");
      n.onEnter = function () { openFavoriteVod(f, rerender); };
      return n;
    }),
    rowSection("Ulubione stacje radiowe", favRadio, function (f) {
      var n = wideCard(f.title, f.subtitle, f.image, true);
      n.onEnter = function () { Radio.play(f); };
      return n;
    }),
  ].filter(Boolean);

  if (!sections.length) {
    var hello = el(
      '<div class="center-msg"><h1>Witaj w MojaTV</h1><p>Tu pojawią się ulubione kanały, ostatnio oglądane i „kontynuuj oglądanie”.<br>' +
      "Czerwony przycisk na pilocie dodaje do ulubionych.</p>" +
      '<div class="row-btns"><div class="btn f" data-t="live">Telewizja</div><div class="btn f" data-t="movies">Filmy</div><div class="btn f" data-t="series">Seriale</div></div></div>'
    );
    hello.querySelectorAll(".btn").forEach(function (b) { b.onEnter = function () { goTo(b.getAttribute("data-t"), true); }; });
    wrap.appendChild(hello);
    if (repo.source && !repo.catalog && !repo.loading) repo.load();
    return;
  }
  sections.forEach(function (s) { wrap.appendChild(s); });
  if (repo.source && !repo.catalog && !repo.loading) repo.load();
}

// ---------------- Radio ----------------

var COUNTRIES = [["PL", "Polska"], ["OM", "Oman"], ["EG", "Egipt"], ["AE", "ZEA"], ["GB", "Wlk. Brytania"], ["US", "USA"], ["DE", "Niemcy"]];
var radioSelected = null;

export function renderRadio(main) {
  main.innerHTML = "";
  var root = el(
    '<div class="cols"><div class="cats scroll"><input class="f search-input" data-group="cats" placeholder="Szukaj stacji…"><div class="cat-list"></div></div>' +
    '<div class="chan-list wide scroll"></div></div>'
  );
  main.appendChild(root);
  var input = root.querySelector("input");
  var catList = root.querySelector(".cat-list");
  var listBox = root.querySelector(".chan-list");

  var playlistRadio = (repo.catalog && repo.catalog.radioChannels || []).map(function (ch) {
    var it = liveItem(ch);
    it.kind = "radio";
    it.key = "radio:pl-" + ch.id;
    return it;
  });

  var options = [{ id: "fav", name: "★ Ulubione" }];
  if (playlistRadio.length) options.push({ id: "playlist", name: "Z Twojej playlisty", count: playlistRadio.length });
  COUNTRIES.forEach(function (c) { options.push({ id: "c:" + c[0], name: c[1] }); });
  if (!radioSelected) radioSelected = "c:" + (repo.settings.radioCountry || "PL");

  var token = 0;
  function showList(items, emptyText) {
    if (!items.length) { listBox.innerHTML = '<div class="empty">' + esc(emptyText) + "</div>"; return; }
    lazyList(listBox, items, function (it) {
      var n = el('<div class="row f" data-group="list">' + img(it.image, it.title, "logo-s") +
        '<span class="name">' + esc(it.title) + (it.subtitle ? '<small>' + esc(it.subtitle) + "</small>" : "") + "</span>" +
        (repo.isFavorite(it.key) ? icon("star", "fav") : "") + "</div>");
      n.onEnter = function () { Radio.play(it); };
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
      showList(list.map(radioItem), "Brak stacji.");
    }, function () {
      if (my === token) listBox.innerHTML = '<div class="empty">Nie udało się pobrać listy stacji.</div>';
    });
  }

  function fill(id, focus) {
    radioSelected = id;
    token++;
    if (id === "fav") showList(repo.favorites.filter(function (f) { return f.kind === "radio"; }), "Brak ulubionych stacji. Czerwony przycisk dodaje do ulubionych.");
    else if (id === "playlist") showList(playlistRadio, "Brak stacji.");
    else {
      var cc = id.slice(2);
      repo.settings.radioCountry = cc;
      repo.saveSettings();
      load(RadioBrowser.top(cc));
    }
    if (focus) setTimeout(function () { var f = listBox.querySelector(".f"); if (f) nav.focus(f); }, 0);
  }

  input.oninput = debounce(function () {
    var q = input.value.trim();
    if (q.length >= 2) load(RadioBrowser.search(q));
  }, 600);

  categoryColumn(catList, options, radioSelected, fill);
  fill(radioSelected, false);
}

// ---------------- Szukaj ----------------

var lastQuery = "";

export function renderSearch(main) {
  if (!catalogGate(main)) return;
  main.innerHTML = "";
  var root = el('<div class="search scroll"><input class="f search-input big" placeholder="Szukaj kanałów, filmów i seriali…"><div class="results"></div></div>');
  main.appendChild(root);
  var input = root.querySelector("input");
  var results = root.querySelector(".results");
  input.value = lastQuery;

  function run() {
    lastQuery = input.value;
    var r = repo.search(input.value);
    results.innerHTML = "";
    if (input.value.trim().length < 2) { results.innerHTML = '<div class="empty">Wpisz co najmniej 2 znaki (OK otwiera klawiaturę).</div>'; return; }
    if (!r.live.length && !r.movies.length && !r.series.length) { results.innerHTML = '<div class="empty">Nic nie znaleziono.</div>'; return; }
    var liveItems = r.live.map(liveItem);
    [
      rowSection("Kanały (" + r.live.length + ")", liveItems, function (it, i) {
        var n = wideCard(it.title, null, it.image, true);
        n.onEnter = function () { Player.play(liveItems, i, -1); };
        return n;
      }),
      rowSection("Filmy (" + r.movies.length + ")", r.movies, function (m) {
        var n = el('<div class="card f">' + img(m.poster, m.name, "poster") + '<div class="card-title">' + esc(m.name) + "</div></div>");
        n.onEnter = function () { openMovie(m); };
        return n;
      }),
      rowSection("Seriale i show (" + r.series.length + ")", r.series, function (s) {
        var n = el('<div class="card f">' + img(s.poster, s.name, "poster") + '<div class="card-title">' + esc(s.name) + "</div></div>");
        n.onEnter = function () { openSeries(s); };
        return n;
      }),
    ].filter(Boolean).forEach(function (s) { results.appendChild(s); });
  }

  input.oninput = debounce(run, 400);
  input.onSubmit = run;
  run();
}

// ---------------- Ustawienia ----------------

export function renderSettings(main, actions) {
  main.innerHTML = "";
  var s = repo.settings;
  var src = repo.source || {};
  var a = repo.account;
  var c = repo.catalog;
  var info = [
    ["Źródło", src.type === "xtream" ? "Xtream Codes" : "Playlista M3U"],
    ["Adres", src.type === "xtream" ? src.server + " · " + src.username : String(src.m3uUrl || "").slice(0, 70)],
  ];
  if (a) {
    info.push(["Status konta", a.status || "—"]);
    if (a.expiresAt) info.push(["Ważne do", new Date(a.expiresAt).toLocaleDateString("pl-PL")]);
    info.push(["Połączenia", (a.activeConnections || "?") + " / " + (a.maxConnections || "?")]);
  }
  if (c) info.push(["Zawartość", c.live.length + " kanałów · " + c.movies.length + " filmów · " + c.series.length + " seriali"]);

  var root = el('<div class="settings scroll"><h1>Ustawienia</h1><div class="info"></div><div class="opts"></div>' +
    '<p class="muted small">MojaTV dla Samsung TV · odtwarza Twoje własne playlisty</p></div>');
  root.querySelector(".info").innerHTML = info.map(function (r) {
    return '<div class="info-row"><span>' + esc(r[0]) + "</span><b>" + esc(r[1]) + "</b></div>";
  }).join("");
  main.appendChild(root);
  var opts = root.querySelector(".opts");

  function opt(label, value, fn) {
    var n = el('<div class="opt f"><span>' + esc(label) + '</span><b class="val">' + esc(value || "") + "</b></div>");
    n.onEnter = function () { fn(n); };
    opts.appendChild(n);
    return n;
  }

  opt("Odśwież playlistę", "", function () { repo.load(true); toast("Odświeżam playlistę…"); });
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
  opt("Kategorie w zakładce Show", "›", function () { openShowSettings(); });
  opt("Wyczyść historię oglądania", "", function () { repo.clearHistory(); toast("Historia wyczyszczona"); });
  opt("Zmień źródło (wyloguj)", "", function () {
    confirmDialog("Zmienić źródło?", "Wylogujesz się z obecnej playlisty. Ulubione i historia zostaną zachowane.", "Zmień").then(function (ok) {
      if (ok) actions.signOut();
    });
  });
}

export function openShowSettings(onDone) {
  if (!repo.catalog) { toast("Playlista jeszcze się wczytuje…"); return; }
  var o = el('<div class="detail plain"><div class="detail-content scroll"><h1>Kategorie Show</h1>' +
    '<p class="muted mode"></p><div class="row-btns"><div class="btn f auto">Wybierz automatycznie</div></div><div class="checks"></div></div></div>');
  document.getElementById("app").appendChild(o);
  function close() {
    nav.pop();
    o.parentNode.removeChild(o);
    if (onDone) onDone();
  }
  nav.push(o, { onBack: close });
  var selected = (repo.settings.showCategories || repo.autoShowKeys()).slice();
  var mode = o.querySelector(".mode");
  function setMode() {
    mode.textContent = repo.settings.showCategories
      ? "Wybór ręczny. OK zaznacza lub odznacza kategorię."
      : "Tryb automatyczny: zaznaczone są kategorie rozpoznane po nazwie. Zmiana dowolnej pozycji przełącza na wybór ręczny.";
  }
  setMode();
  var checks = o.querySelector(".checks");
  function render() {
    checks.innerHTML = "";
    repo.allShowCandidates().forEach(function (cat) {
      var on = selected.indexOf(cat.key) >= 0;
      var n = el('<div class="check f' + (on ? " on" : "") + '"><span class="box"></span><span>' + esc(cat.name) +
        '</span><small>' + (cat.isSeries ? "Seriale" : "Filmy") + "</small></div>");
      n.onEnter = function () {
        var i = selected.indexOf(cat.key);
        if (i >= 0) selected.splice(i, 1); else selected.push(cat.key);
        n.classList.toggle("on", i < 0);
        repo.settings.showCategories = selected.slice();
        repo.saveSettings();
        setMode();
      };
      checks.appendChild(n);
    });
  }
  render();
  o.querySelector(".auto").onEnter = function () {
    repo.settings.showCategories = null;
    repo.saveSettings();
    selected = repo.autoShowKeys();
    setMode();
    render();
  };
  nav.focusFirst(o);
}

// ---------------- Logowanie ----------------

export function renderLogin(container, onDone) {
  var d = window.MOJATV_DEFAULTS || {};
  var saved = repo.source || {};
  var tab = saved.type === "m3u" || (!saved.type && d.m3uUrl && !d.server) ? "m3u" : "xtream";
  container.innerHTML = "";
  var root = el(
    '<div class="login"><div class="login-box">' +
      '<div class="login-logo">' + icon("live") + "<h1>MojaTV</h1><p>Dodaj swoją playlistę IPTV</p></div>" +
      '<div class="row-btns tabs"><div class="btn f tab" data-t="xtream">Xtream Codes</div><div class="btn f tab" data-t="m3u">Playlista M3U</div></div>' +
      '<div class="fields xtream"><input class="f" name="server" placeholder="Adres serwera, np. http://serwer.com:8080">' +
        '<input class="f" name="username" placeholder="Nazwa użytkownika"><input class="f" name="password" placeholder="Hasło"></div>' +
      '<div class="fields m3u"><input class="f" name="m3uUrl" placeholder="Link do playlisty M3U (http://…)"></div>' +
      '<p class="muted small">OK otwiera klawiaturę telewizora. Możesz też wpisać dane wcześniej w pliku js/defaults.js przed instalacją.</p>' +
      '<div class="login-error"></div>' +
      '<div class="row-btns"><div class="btn primary f submit">Zaloguj</div></div>' +
    "</div></div>"
  );
  container.appendChild(root);
  function field(n) { return root.querySelector('input[name="' + n + '"]'); }
  field("server").value = saved.server || d.server || "";
  field("username").value = saved.username || d.username || "";
  field("password").value = saved.password || d.password || "";
  field("m3uUrl").value = saved.m3uUrl || d.m3uUrl || "";
  var err = root.querySelector(".login-error");
  var submit = root.querySelector(".submit");
  var busy = false;

  function setTab(t) {
    tab = t;
    root.querySelector(".fields.xtream").style.display = t === "xtream" ? "" : "none";
    root.querySelector(".fields.m3u").style.display = t === "m3u" ? "" : "none";
    root.querySelectorAll(".tab").forEach(function (b) { b.classList.toggle("sel", b.getAttribute("data-t") === t); });
  }
  root.querySelectorAll(".tab").forEach(function (b) {
    b.onEnter = function () { setTab(b.getAttribute("data-t")); };
  });
  setTab(tab);

  function go() {
    if (busy) return;
    var cfg;
    if (tab === "xtream") {
      cfg = credentialsFromUrl(field("server").value) ||
        { type: "xtream", server: field("server").value.trim(), username: field("username").value.trim(), password: field("password").value.trim() };
    } else {
      var detected = credentialsFromUrl(field("m3uUrl").value);
      cfg = detected || { type: "m3u", m3uUrl: field("m3uUrl").value.trim() };
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
  root.querySelectorAll("input").forEach(function (i) { i.onSubmit = function () { nav.focus(i); }; });
  nav.focus(submit);
}
