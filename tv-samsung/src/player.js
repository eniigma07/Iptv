// Odtwarzacz: AVPlay (silnik Samsunga, obsługuje MPEG-TS, HLS, MKV, MP4) albo <video> poza telewizorem.
import { esc, formatTime, formatClock } from "./util.js";
import { nav } from "./nav.js";
import { el, lazyList, img, toast } from "./dom.js";
import { repo } from "./repo.js";

// ---------------- Silniki ----------------

function avplayEngine() {
  var av = window.webapis.avplay;
  var paused = false;
  return {
    name: "avplay",
    open: function (url, startMs, video, cb) {
      try { av.stop(); } catch (e) { /* */ }
      try { av.close(); } catch (e) { /* */ }
      paused = false;
      try {
        av.open(url);
        av.setListener({
          onbufferingstart: function () { cb.onBuffering(true); },
          onbufferingprogress: function () {},
          onbufferingcomplete: function () { cb.onBuffering(false); },
          onstreamcompleted: function () { cb.onEnd(); },
          oncurrentplaytime: function () {},
          onevent: function () {},
          onerror: function (e) { cb.onError(String(e)); },
          onsubtitlechange: function () {},
          ondrmevent: function () {},
        });
        if (video) {
          av.setDisplayRect(0, 0, 1920, 1080);
          try { av.setDisplayMethod("PLAYER_DISPLAY_MODE_LETTER_BOX"); } catch (e) { /* */ }
        } else {
          try { av.setDisplayRect(0, 0, 1, 1); } catch (e) { /* */ }
        }
        cb.onBuffering(true);
        av.prepareAsync(function () {
          function go() {
            try { av.play(); } catch (e) { cb.onError(String(e)); return; }
            cb.onBuffering(false);
            cb.onReady();
          }
          if (startMs > 0) {
            try { av.seekTo(startMs, go, go); } catch (e) { go(); }
          } else {
            go();
          }
        }, function (err) {
          cb.onError(String((err && err.name) || err));
        });
      } catch (e) {
        cb.onError(String((e && e.name) || e));
      }
    },
    stop: function () {
      try { av.stop(); } catch (e) { /* */ }
      try { av.close(); } catch (e) { /* */ }
    },
    pause: function () { try { av.pause(); paused = true; } catch (e) { /* */ } },
    resume: function () { try { av.play(); paused = false; } catch (e) { /* */ } },
    isPaused: function () { return paused; },
    time: function () { try { return av.getCurrentTime() || 0; } catch (e) { return 0; } },
    duration: function () { try { return av.getDuration() || 0; } catch (e) { return 0; } },
    seek: function (ms) { try { av.seekTo(Math.max(0, ms), function () {}, function () {}); } catch (e) { /* */ } },
    suspend: function () { try { av.suspend(); } catch (e) { /* */ } },
    restore: function () { try { av.restore(); } catch (e) { /* */ } },
  };
}

function htmlEngine() {
  var v = document.getElementById("html-player");
  var handlers = null;
  function on(name, fn) { v.addEventListener(name, function (e) { if (handlers) fn(e); }); }
  on("waiting", function () { handlers.onBuffering(true); });
  on("playing", function () { handlers.onBuffering(false); });
  on("ended", function () { handlers.onEnd(); });
  on("error", function () { handlers.onError("HTML5_MEDIA_ERROR"); });
  return {
    name: "html5",
    open: function (url, startMs, video, cb) {
      handlers = cb;
      v.className = video ? "on" : "";
      cb.onBuffering(true);
      v.src = url;
      v.onloadedmetadata = function () {
        if (startMs > 0) v.currentTime = startMs / 1000;
        var p = v.play();
        if (p && p.catch) p.catch(function () {});
        cb.onBuffering(false);
        cb.onReady();
        v.onloadedmetadata = null;
      };
    },
    stop: function () {
      handlers = null;
      v.pause();
      v.removeAttribute("src");
      v.load();
      v.className = "";
    },
    pause: function () { v.pause(); },
    resume: function () { var p = v.play(); if (p && p.catch) p.catch(function () {}); },
    isPaused: function () { return v.paused; },
    time: function () { return (v.currentTime || 0) * 1000; },
    duration: function () { return isFinite(v.duration) ? v.duration * 1000 : 0; },
    seek: function (ms) { v.currentTime = Math.max(0, ms / 1000); },
    suspend: function () { v.pause(); },
    restore: function () {},
  };
}

var engine = null;
function getEngine() {
  if (!engine) engine = window.webapis && window.webapis.avplay ? avplayEngine() : htmlEngine();
  return engine;
}

// ---------------- Odtwarzacz wideo ----------------

var root = null;
var queue = [];
var index = 0;
var item = null;
var active = false;
var mode = "play"; // play | list | error
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
  root = el(
    '<div id="player" class="hidden">' +
      '<div class="p-top"><div class="p-logo"></div><div class="p-info">' +
        '<div class="p-title"></div><div class="p-sub"></div><div class="p-now"></div><div class="p-next"></div>' +
      '</div><div class="p-clock"></div></div>' +
      '<div class="p-spinner"></div>' +
      '<div class="p-paused">II</div>' +
      '<div class="p-num"></div>' +
      '<div class="p-error hidden"></div>' +
      '<div class="p-bottom"><div class="p-bar"><div class="p-fill"></div></div>' +
        '<div class="p-times"><span class="p-cur"></span><span class="p-hint"></span><span class="p-dur"></span></div></div>' +
      '<div class="p-list hidden"><div class="p-list-title">Kanały</div><div class="p-list-items scroll"></div></div>' +
    "</div>"
  );
  document.getElementById("app").appendChild(root);
}

function q(sel) { return root.querySelector(sel); }

function isLive() { return item && item.kind === "live"; }

export const Player = {
  isActive: function () { return active; },

  /** Odtwarza element z kolejki (np. listy kanałów lub odcinków). startMs: -1 = wznów, 0 = od początku. */
  play: function (items, i, startMs, onExit) {
    if (!items || !items.length) return;
    Radio.stop();
    build();
    queue = items;
    onExitCb = onExit || null;
    if (!active) {
      active = true;
      document.body.classList.add("playing");
      root.className = "";
      nav.push(root, { onBack: function () { Player.exit(); } });
      progressTimer = setInterval(saveProgress, 10000);
      tickTimer = setInterval(updateOsd, 1000);
    }
    openAt(i, startMs === undefined ? -1 : startMs);
  },

  exit: function () {
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
  handleKey: function (name) {
    if (!active) return false;
    if (mode === "list" || mode === "error") {
      if (mode === "list" && (name === "chup" || name === "chdown")) { closeList(); return Player.handleKey(name); }
      nav.handleKey(name);
      return true;
    }
    var live = isLive();
    if (name.charAt(0) === "d" && name.length === 2 && live) { typeNumber(name.charAt(1)); return true; }
    switch (name) {
      case "back":
      case "stop":
        Player.exit();
        return true;
      case "up":
      case "chup":
        if (live || name === "chup") { zap(-1); return true; }
        showOsd();
        return true;
      case "down":
      case "chdown":
        if (live || name === "chdown") { zap(1); return true; }
        showOsd();
        return true;
      case "left":
        if (live) { openList(); return true; }
        seekBy(-10000);
        return true;
      case "right":
        if (live) { openList(); return true; }
        seekBy(10000);
        return true;
      case "rw": seekBy(-60000); return true;
      case "ff": seekBy(60000); return true;
      case "chlist":
        openList();
        return true;
      case "enter":
      case "playpause":
        if (live && name === "enter") { showOsd(); return true; }
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

  onVisibility: function (hidden) {
    if (!active) return;
    if (hidden) {
      saveProgress();
      getEngine().suspend();
    } else {
      getEngine().restore();
    }
  },
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
    if (startMs >= 0) start = startMs;
    else {
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
  var current = item;
  getEngine().open(current.url, startMs, true, {
    onReady: function () { if (item === current) { liveRetries = 0; updateOsd(); } },
    onBuffering: function (b) { if (item === current) { buffering = b; root.classList.toggle("buffering", b); } },
    onEnd: function () {
      if (item !== current) return;
      if (current.kind === "live") return onFail("Transmisja została przerwana.");
      var d = getEngine().duration();
      if (d > 0) repo.saveProgress(current, d, d);
      if (index < queue.length - 1) openAt(index + 1, 0);
      else Player.exit();
    },
    onError: function (code) {
      if (item !== current) return;
      onFail(code);
    },
  });
}

function onFail(code) {
  if (isLive() && liveRetries < 3) {
    liveRetries++;
    root.classList.add("buffering");
    retryTimer = setTimeout(function () { startEngine(0); }, 1500 * liveRetries);
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
  box.innerHTML = '<div class="p-error-text">' + esc(text) + '</div><div class="row-btns">' +
    '<div class="btn f" data-a="retry">Spróbuj ponownie</div>' +
    (queue.length > 1 ? '<div class="btn f" data-a="next">' + (isLive() ? "Następny kanał" : "Następny") + "</div>" : "") +
    '<div class="btn f" data-a="exit">Wyjdź</div></div>';
  box.className = "p-error";
  root.classList.remove("buffering");
  box.querySelectorAll(".btn").forEach(function (b) {
    b.onEnter = function () {
      var a = b.getAttribute("data-a");
      hideError();
      if (a === "retry") openAt(index, -1);
      else if (a === "next") openAt((index + 1) % queue.length, 0);
      else Player.exit();
    };
  });
  if (mode === "list") closeList();
  mode = "error";
  nav.push(box, { onBack: function () { hideError(); Player.exit(); } });
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
  var t = Math.max(0, Math.min(d > 0 ? d - 3000 : Infinity, e.time() + ms));
  e.seek(t);
  showOsd();
  updateOsd(t);
}

function togglePause() {
  var e = getEngine();
  if (e.isPaused()) e.resume();
  else e.pause();
  root.classList.toggle("paused", e.isPaused());
  showOsd(e.isPaused());
}

function toggleFav() {
  var added = repo.toggleFavorite(stripItem(item));
  toast(added ? "Dodano do ulubionych" : "Usunięto z ulubionych");
}

function stripItem(it) {
  return { key: it.key, kind: it.kind, title: it.title, url: it.url, image: it.image, subtitle: it.subtitle, parentId: it.parentId };
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
    if (n >= 1 && n <= queue.length) openAt(n - 1, -1);
    else toast("Brak kanału o numerze " + n);
  }, 1800);
}

function openList() {
  if (queue.length < 2) { showOsd(); return; }
  mode = "list";
  var box = q(".p-list");
  box.className = "p-list";
  var list = q(".p-list-items");
  var ll = lazyList(list, queue, function (it, i) {
    var n = el('<div class="p-list-row f"><span class="num">' + (i + 1) + "</span>" + img(it.image, it.title, "logo-s") +
      '<span class="name">' + esc(it.title) + "</span></div>");
    if (i === index) n.classList.add("current");
    n.onEnter = function () { closeList(); openAt(i, -1); };
    return n;
  }, 80);
  nav.push(box, { onBack: closeList });
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
  if (!sticky) osdTimer = setTimeout(hideOsd, 5000);
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
  q(".p-hint").textContent = isLive()
    ? "▲▼ zmiana kanału · ◀▶ lista kanałów · cyfry: numer · czerwony: ulubione"
    : "◀▶ przewijanie 10 s · ⏪⏩ 1 min · OK pauza · czerwony: ulubione";
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
    var cur = programs.filter(function (p) { return p.start <= now && p.end > now; })[0];
    var next = programs.filter(function (p) { return p.start > now; })[0];
    q(".p-now").textContent = cur ? formatTime(cur.start) + " – " + formatTime(cur.end) + "  " + cur.title : "";
    q(".p-next").textContent = next ? "Następnie " + formatTime(next.start) + "  " + next.title : "";
    var frac = cur ? (now - cur.start) / (cur.end - cur.start) : 0;
    fill.style.width = Math.max(0, Math.min(100, frac * 100)) + "%";
    q(".p-cur").textContent = cur ? formatTime(cur.start) : "";
    q(".p-dur").textContent = cur ? formatTime(cur.end) : "";
  } else {
    var e = getEngine();
    var t = forcedTime !== undefined ? forcedTime : e.time();
    var d = e.duration();
    fill.style.width = d > 0 ? Math.min(100, (t / d) * 100) + "%" : "0%";
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

// ---------------- Radio w tle ----------------

var radioItem = null;
var radioState = null; // buffering | playing | paused | error
var radioListeners = [];

export const Radio = {
  current: function () { return radioItem ? { item: radioItem, state: radioState } : null; },
  onChange: function (fn) { radioListeners.push(fn); },
  play: function (it) {
    if (active) return;
    radioItem = it;
    setRadio("buffering");
    repo.addRecent(it);
    getEngine().open(it.url, 0, false, {
      onReady: function () { if (radioItem === it) setRadio("playing"); },
      onBuffering: function (b) { if (radioItem === it && radioState !== "paused") setRadio(b ? "buffering" : "playing"); },
      onEnd: function () { if (radioItem === it) setRadio("error"); },
      onError: function () { if (radioItem === it) setRadio("error"); },
    });
  },
  toggle: function () {
    if (!radioItem) return;
    if (radioState === "paused") { getEngine().resume(); setRadio("playing"); }
    else if (radioState === "error") Radio.play(radioItem);
    else { getEngine().pause(); setRadio("paused"); }
  },
  stop: function () {
    if (!radioItem) return;
    radioItem = null;
    getEngine().stop();
    setRadio(null);
  },
};

function setRadio(s) {
  radioState = s;
  radioListeners.forEach(function (fn) { fn(Radio.current()); });
}
