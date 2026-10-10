import "./polyfills.js";
import { esc } from "./util.js";
import { el, icon, img, confirmDialog } from "./dom.js";
import { nav, KEYS } from "./nav.js";
import { repo } from "./repo.js";
import { Player, Radio } from "./player.js";
import { renderLive } from "./screens/live.js";
import { renderMovies, renderSeries, renderShow } from "./screens/vod.js";
import { renderHome, renderRadio, renderSearch, renderSettings, renderLogin, openShowSettings } from "./screens/other.js";

var MENU = [
  ["home", "Start", "home"],
  ["live", "Telewizja", "live"],
  ["movies", "Filmy", "movie"],
  ["series", "Seriale", "series"],
  ["show", "Show", "show"],
  ["radio", "Radio", "radio"],
  ["search", "Szukaj", "search"],
  ["settings", "Ustawienia", "settings"],
];

var app = document.getElementById("app");
var shell = null;
var main = null;
var currentScreen = "home";

function registerKeys() {
  try {
    var names = ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaStop", "MediaFastForward", "MediaRewind",
      "ChannelUp", "ChannelDown", "ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue", "Info", "ChannelList",
      "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
    var dev = window.tizen && window.tizen.tvinputdevice;
    if (!dev) return;
    if (dev.registerKeyBatch) {
      dev.registerKeyBatch(names, function () {}, function () {
        names.forEach(function (n) { try { dev.registerKey(n); } catch (e) { /* */ } });
      });
    } else {
      names.forEach(function (n) { try { dev.registerKey(n); } catch (e) { /* */ } });
    }
  } catch (e) { /* poza telewizorem */ }
}

function exitApp() {
  try { window.tizen.application.getCurrentApplication().exit(); } catch (e) { /* przeglądarka */ }
}

// ---------------- Główny układ ----------------

function buildShell() {
  app.innerHTML = "";
  shell = el('<div class="shell"><div class="side"><div class="brand">' + icon("live") + "<span>MojaTV</span></div>" +
    '<div class="menu"></div><div class="radio-bar"></div><div class="clock"></div></div><div class="main"></div></div>');
  app.appendChild(shell);
  main = shell.querySelector(".main");
  var menu = shell.querySelector(".menu");
  MENU.forEach(function (m) {
    var n = el('<div class="mi f" data-group="menu" data-id="' + m[0] + '">' + icon(m[2]) + "<span>" + m[1] + "</span></div>");
    n.onEnter = function () { goTo(m[0], true); };
    menu.appendChild(n);
  });
  nav.reset(shell, { onBack: onRootBack });
  updateClock();
  renderRadioBar(Radio.current());
}

function updateClock() {
  var c = shell && shell.querySelector(".clock");
  if (!c) return;
  var d = new Date();
  c.textContent = ("0" + d.getHours()).slice(-2) + ":" + ("0" + d.getMinutes()).slice(-2);
}
setInterval(updateClock, 15000);

function markMenu() {
  shell.querySelectorAll(".mi").forEach(function (m) { m.classList.toggle("active", m.getAttribute("data-id") === currentScreen); });
}

function render(id) {
  switch (id) {
    case "home": renderHome(main, goTo); break;
    case "live": renderLive(main); break;
    case "movies": renderMovies(main); break;
    case "series": renderSeries(main); break;
    case "show": renderShow(main, function () { openShowSettings(function () { goTo("show", true); }); }); break;
    case "radio": renderRadio(main); break;
    case "search": renderSearch(main); break;
    case "settings": renderSettings(main, { signOut: signOut }); break;
  }
}

/** Otwiera ekran; focusContent = przenieś podświetlenie do treści. */
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
  confirmDialog("Zamknąć MojaTV?", "", "Zamknij").then(function (ok) { if (ok) exitApp(); });
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
  nav.reset(box, { onBack: function () { confirmDialog("Zamknąć MojaTV?", "", "Zamknij").then(function (ok) { if (ok) exitApp(); }); } });
  renderLogin(box, startApp);
}

function startApp() {
  buildShell();
  currentScreen = "home";
  goTo("home", false);
  var first = main.querySelector(".f");
  nav.focus(first || shell.querySelector('.mi[data-id="home"]'));
}

// Odświeżanie ekranu po wczytaniu katalogu
repo.onChange(function (what) {
  if (!shell || !main || !main.isConnected && !document.body.contains(main)) return;
  if (what === "catalog") {
    if (nav.layerCount() > 1 || Player.isActive()) return; // otwarte okno — nie przeszkadzamy
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

// ---------------- Radio w tle ----------------

function renderRadioBar(state) {
  var bar = shell && shell.querySelector(".radio-bar");
  if (!bar) return;
  if (!state) { bar.innerHTML = ""; bar.className = "radio-bar"; return; }
  var label = { buffering: "Łączenie…", playing: "Teraz gra", paused: "Pauza", error: "Błąd odtwarzania" }[state.state] || "";
  bar.className = "radio-bar on";
  bar.innerHTML = img(state.item.image, state.item.title, "logo-s") +
    '<div><div class="rb-label">' + esc(label) + '</div><div class="rb-name">' + esc(state.item.title) + "</div>" +
    '<div class="rb-hint">⏯ pauza · ⏹ stop</div></div>';
}
Radio.onChange(renderRadioBar);

// ---------------- Pilot ----------------

document.addEventListener("keydown", function (e) {
  var name = KEYS[e.keyCode];
  var ae = document.activeElement;
  if (ae && ae.tagName === "INPUT") {
    // trwa wpisywanie — klawiatura telewizora obsługuje litery i strzałki w polu
    if (name === "imedone" || name === "imecancel" || e.keyCode === 13) {
      e.preventDefault();
      ae.blur();
      if (ae.onSubmit) ae.onSubmit();
      return;
    }
    if (name === "back") { e.preventDefault(); ae.blur(); return; }
    if (name === "up" || name === "down") ae.blur();
    else return;
  }
  if (!name) return;
  e.preventDefault();
  if (name === "backspace") name = "back";
  if (Player.handleKey(name)) return;
  if (Radio.current() && (name === "playpause" || name === "play" || name === "pause")) { Radio.toggle(); return; }
  if (Radio.current() && name === "stop") { Radio.stop(); return; }
  nav.handleKey(name);
});

document.addEventListener("visibilitychange", function () {
  Player.onVisibility(document.hidden);
});

// ---------------- Start ----------------

registerKeys();
if (!(window.webapis && window.webapis.avplay)) {
  // poza telewizorem (np. test w przeglądarce) warstwa AVPlay nie istnieje
  var avObj = document.getElementById("av-player");
  if (avObj) avObj.parentNode.removeChild(avObj);
}
if (repo.source) {
  startApp();
  repo.load();
} else {
  showLogin();
}
