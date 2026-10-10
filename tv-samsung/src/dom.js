// Pomocnicze funkcje do budowania widoków.
import { esc } from "./util.js";
import { nav } from "./nav.js";

export function el(html) {
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
  play: "M8 5v14l11-7z",
};

export function icon(name, cls) {
  return '<svg class="ic ' + (cls || "") + '" viewBox="0 0 24 24"><path d="' + ICON_PATHS[name] + '"/></svg>';
}

/** Obrazek z sieci; przy braku/błędzie pokazuje inicjały. */
export function img(url, fallback, cls) {
  var initials = esc(String(fallback || "").trim().slice(0, 2).toUpperCase());
  if (!url) return '<div class="ph ' + (cls || "") + '"><span>' + initials + "</span></div>";
  return '<div class="ph ' + (cls || "") + '"><span>' + initials + '</span><img src="' + esc(url) +
    '" onload="this.parentNode.className+=\' loaded\'" onerror="this.parentNode.removeChild(this)"></div>';
}

/**
 * Lista doładowywana partiami — telewizor nie poradzi sobie z tysiącami elementów naraz.
 * render(item, index) → element z klasą "f".
 */
export function lazyList(container, items, render, batch) {
  batch = batch || 60;
  var shown = 0;
  function more() {
    var end = Math.min(items.length, shown + batch);
    var frag = document.createDocumentFragment();
    for (var i = shown; i < end; i++) {
      var node = render(items[i], i);
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
    nodeAt: function (i) {
      while (i >= shown && shown < items.length) more();
      return container.children[i] || null;
    },
  };
}

var toastTimer = null;
export function toast(text) {
  var t = document.getElementById("toast");
  if (!t) {
    t = el('<div id="toast"></div>');
    document.body.appendChild(t);
  }
  t.textContent = text;
  t.className = "show";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function () { t.className = ""; }, 2500);
}

/** Okno z pytaniem. Zwraca Promise<boolean>. */
export function confirmDialog(title, text, okLabel) {
  return new Promise(function (resolve) {
    var d = el(
      '<div class="modal"><div class="modal-box"><h2>' + esc(title) + "</h2><p>" + esc(text) +
      '</p><div class="row-btns"><div class="btn f" data-v="1">' + esc(okLabel || "OK") +
      '</div><div class="btn f" data-v="0">Anuluj</div></div></div></div>'
    );
    document.getElementById("app").appendChild(d);
    function close(v) {
      nav.pop();
      d.parentNode.removeChild(d);
      resolve(v);
    }
    nav.push(d, { onBack: function () { close(false); } });
    d.querySelectorAll(".btn").forEach(function (b) {
      b.onEnter = function () { close(b.getAttribute("data-v") === "1"); };
    });
    nav.focusFirst(d);
  });
}
