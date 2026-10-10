// Nawigacja pilotem: strzałki przenoszą podświetlenie do najbliższego elementu w danym kierunku.
//
// Element z klasą "f" można podświetlić. Opcjonalne hooki na elemencie:
//   el.onEnter()            — OK na pilocie
//   el.onFocus()            — po podświetleniu
//   el.onKey(name) → true   — własna obsługa klawisza (np. czerwony przycisk)
// Atrybut data-group="nazwa" zapamiętuje ostatnią pozycję w grupie (np. lista kategorii),
// żeby po powrocie do grupy wrócić w to samo miejsce.

export const KEYS = {
  37: "left", 38: "up", 39: "right", 40: "down", 13: "enter",
  10009: "back", 27: "back", 8: "backspace",
  10252: "playpause", 415: "play", 19: "pause", 413: "stop", 417: "ff", 412: "rw",
  427: "chup", 428: "chdown", 33: "chup", 34: "chdown",
  403: "red", 404: "green", 405: "yellow", 406: "blue",
  457: "info", 10073: "chlist",
  65376: "imedone", 65385: "imecancel",
};

for (var d = 0; d <= 9; d++) KEYS[48 + d] = "d" + d;

var layers = []; // [{ root, focused, groups: {}, onBack }]
var current = null;

function top() {
  return layers[layers.length - 1];
}

function visible(el) {
  if (!el || !el.isConnected && !document.body.contains(el)) return false;
  if (el.offsetParent === null && getComputedStyle(el).position !== "fixed") return false;
  var r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0;
}

export const nav = {
  /** Nowa warstwa (ekran albo okno nad ekranem). */
  push: function (root, opts) {
    if (current) current.classList.remove("focused");
    layers.push({ root: root, focused: null, groups: {}, onBack: opts && opts.onBack });
    current = null;
  },

  pop: function () {
    var l = layers.pop();
    if (current) current.classList.remove("focused");
    current = null;
    var t = top();
    if (t && t.focused && document.body.contains(t.focused)) nav.focus(t.focused, true);
    return l;
  },

  /** Zastępuje całą zawartość (ekran główny). */
  reset: function (root, opts) {
    layers = [];
    current = null;
    nav.push(root, opts);
  },

  layerCount: function () { return layers.length; },

  setBack: function (fn) {
    var t = top();
    if (t) t.onBack = fn;
  },

  current: function () { return current; },

  focus: function (el, noScroll) {
    if (!el) return;
    var t = top();
    if (current && current !== el) current.classList.remove("focused");
    if (document.activeElement && document.activeElement !== el && document.activeElement.blur) {
      document.activeElement.blur();
    }
    current = el;
    el.classList.add("focused");
    if (t) {
      t.focused = el;
      var g = el.getAttribute("data-group");
      if (g) t.groups[g] = el;
    }
    if (!noScroll) ensureVisible(el);
    if (el.onFocus) el.onFocus();
  },

  /** Podświetla pierwszy element (albo zapamiętany w grupie). */
  focusFirst: function (root, group) {
    var t = top();
    if (group && t && t.groups[group] && visible(t.groups[group])) return nav.focus(t.groups[group]);
    var scope = root || (t && t.root) || document.body;
    var list = scope.querySelectorAll(".f");
    for (var i = 0; i < list.length; i++) if (visible(list[i])) return nav.focus(list[i]);
  },

  rememberedIn: function (group) {
    var t = top();
    var el = t && t.groups[group];
    return el && visible(el) ? el : null;
  },

  move: function (dir) {
    var t = top();
    if (!t) return false;
    if (!current || !visible(current)) {
      nav.focusFirst();
      return true;
    }
    var best = findNext(t.root, current, dir);
    if (!best) return false;
    // wejście do innej grupy → wróć do zapamiętanej pozycji w tej grupie
    var g = best.getAttribute("data-group");
    if (g && g !== current.getAttribute("data-group")) {
      if (t.groups[g] && visible(t.groups[g])) best = t.groups[g];
      else {
        // pierwsze wejście do grupy → aktywna/zaznaczona pozycja (np. bieżąca zakładka menu)
        var marked = t.root.querySelector('[data-group="' + g + '"].active, [data-group="' + g + '"].sel');
        if (marked && visible(marked)) best = marked;
      }
    }
    nav.focus(best);
    return true;
  },

  handleKey: function (name) {
    var t = top();
    if (!t) return false;
    if (current && current.onKey && current.onKey(name)) return true;
    if (name === "left" || name === "right" || name === "up" || name === "down") return nav.move(name);
    if (name === "enter") {
      if (!current) { nav.focusFirst(); return true; }
      if (current.tagName === "INPUT") {
        current.focus();
        return true;
      }
      if (current.onEnter) current.onEnter();
      else current.click();
      return true;
    }
    if (name === "back" && t.onBack) {
      t.onBack();
      return true;
    }
    return false;
  },
};

/** Najbliższy element w danym kierunku (prosta geometria prostokątów). */
function findNext(root, from, dir) {
  var a = from.getBoundingClientRect();
  var ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  var list = root.querySelectorAll(".f");
  var cands = [];
  var minPrimary = Infinity;
  for (var i = 0; i < list.length; i++) {
    var el = list[i];
    if (el === from || !visible(el)) continue;
    var b = el.getBoundingClientRect();
    var bx = b.left + b.width / 2, by = b.top + b.height / 2;
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
    cands.push({ el: el, primary: primary, secondary: overlap > 0 ? secondary * 0.5 : secondary + 200 });
    if (primary < minPrimary) minPrimary = primary;
  }
  // 1) najbliższy „rząd/kolumna” w danym kierunku, 2) w nim element najbliżej w osi poprzecznej
  var best = null, bestScore = Infinity;
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

/** Przewija kontenery z klasą "scroll" (pion) lub "hscroll" (poziom), żeby element był widoczny. */
export function ensureVisible(el) {
  var p = el.parentElement;
  while (p && p !== document.body) {
    var cls = p.classList;
    if (cls.contains("scroll")) {
      var pr = p.getBoundingClientRect(), er = el.getBoundingClientRect();
      var margin = Math.min(140, pr.height / 4);
      if (er.top < pr.top + margin) p.scrollTop -= pr.top + margin - er.top;
      else if (er.bottom > pr.bottom - margin) p.scrollTop += er.bottom - (pr.bottom - margin);
    }
    if (cls.contains("hscroll")) {
      var hr = p.getBoundingClientRect(), ehr = el.getBoundingClientRect();
      var hm = Math.min(120, hr.width / 5);
      if (ehr.left < hr.left + hm) p.scrollLeft -= hr.left + hm - ehr.left;
      else if (ehr.right > hr.right - hm) p.scrollLeft += ehr.right - (hr.right - hm);
    }
    p = p.parentElement;
  }
}
