// Telewizja na żywo: kategorie | kanały | program wybranego kanału.
import { esc, formatTime, debounce } from "../util.js";
import { el, img, lazyList, toast, icon } from "../dom.js";
import { nav } from "../nav.js";
import { repo } from "../repo.js";
import { Player } from "../player.js";
import { liveItem, catalogGate } from "../items.js";

var FAV = "__fav", ALL = "__all", RECENT = "__recent";
var selected = ALL;

/** Kolumna kategorii używana na ekranach TV, Filmy, Seriale, Show i Radio. */
export function categoryColumn(container, options, selectedId, onSelect) {
  container.innerHTML = "";
  var change = debounce(function (id) { onSelect(id, false); }, 250);
  var selectedNode = null;
  options.forEach(function (o) {
    var n = el('<div class="cat f" data-group="cats">' + esc(o.name) + (o.count != null ? '<span class="cnt">' + o.count + "</span>" : "") + "</div>");
    if (o.id === selectedId) { n.classList.add("sel"); selectedNode = n; }
    n.onFocus = function () {
      if (container.querySelector(".sel") !== n) {
        var prev = container.querySelector(".sel");
        if (prev) prev.classList.remove("sel");
        n.classList.add("sel");
        change(o.id);
      }
    };
    n.onEnter = function () { onSelect(o.id, true); };
    container.appendChild(n);
  });
  return selectedNode;
}

export function renderLive(main) {
  if (!catalogGate(main)) return;
  var c = repo.catalog;
  main.innerHTML = "";
  var root = el(
    '<div class="cols"><div class="cats scroll"></div>' +
    '<div class="chan-list scroll"></div>' +
    '<div class="epg-panel"></div></div>'
  );
  main.appendChild(root);
  var catsBox = root.querySelector(".cats");
  var listBox = root.querySelector(".chan-list");
  var panel = root.querySelector(".epg-panel");

  var counts = {};
  c.live.forEach(function (ch) { counts[ch.categoryId] = (counts[ch.categoryId] || 0) + 1; });
  var options = [
    { id: FAV, name: "★ Ulubione" },
    { id: RECENT, name: "Ostatnio oglądane" },
    { id: ALL, name: "Wszystkie kanały", count: c.live.length },
  ].concat(c.liveCategories.map(function (x) { return { id: x.id, name: x.name, count: counts[x.id] || 0 }; }));

  function itemsFor(id) {
    if (id === FAV) return repo.favorites.filter(function (f) { return f.kind === "live"; });
    if (id === RECENT) return repo.recent.filter(function (f) { return f.kind === "live"; });
    var src = id === ALL ? c.live : c.live.filter(function (ch) { return ch.categoryId === id; });
    return src.map(liveItem);
  }

  var showPanel = debounce(function (item) { renderPanel(panel, item); }, 350);

  function fill(id, focusList) {
    selected = id;
    var items = itemsFor(id);
    if (!items.length) {
      listBox.innerHTML = '<div class="empty">' + (id === FAV ? "Brak ulubionych kanałów.<br>Na liście kanałów naciśnij czerwony przycisk, żeby dodać." : "Brak kanałów.") + "</div>";
      panel.innerHTML = "";
      return;
    }
    listBox.scrollTop = 0;
    lazyList(listBox, items, function (item, i) {
      var n = el(
        '<div class="row f" data-group="list"><span class="num">' + (i + 1) + "</span>" + img(item.image, item.title, "logo-s") +
        '<span class="name">' + esc(item.title) + "</span>" +
        (repo.isFavorite(item.key) ? icon("star", "fav") : "") + "</div>"
      );
      n.onFocus = function () { showPanel(item); };
      n.onEnter = function () { Player.play(items, i, -1); };
      n.onKey = function (k) {
        if (k !== "red") return false;
        var added = repo.toggleFavorite(item);
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

function renderPanel(panel, item) {
  if (!item) { panel.innerHTML = ""; return; }
  panel.innerHTML =
    '<div class="epg-head">' + img(item.image, item.title, "logo-l") + '<div class="epg-name">' + esc(item.title) + "</div></div>" +
    '<div class="epg-body"><div class="muted">' + (repo.api() ? "Wczytywanie programu…" : "Program TV dostępny przy logowaniu Xtream.") + "</div></div>" +
    '<div class="hint">OK – oglądaj · czerwony – ulubione</div>';
  if (!repo.api()) return;
  repo.programs(item).then(function (list) {
    if (!panel.isConnected && !document.body.contains(panel)) return;
    var body = panel.querySelector(".epg-body");
    if (!body) return;
    if (!list.length) { body.innerHTML = '<div class="muted">Brak programu dla tego kanału.</div>'; return; }
    var now = Date.now();
    body.innerHTML = list.slice(0, 5).map(function (p, i) {
      var isNow = p.start <= now && p.end > now;
      var frac = isNow ? Math.round(((now - p.start) / (p.end - p.start)) * 100) : 0;
      return '<div class="prog' + (isNow ? " now" : "") + '"><div class="prog-time">' + formatTime(p.start) + " – " + formatTime(p.end) + "</div>" +
        '<div class="prog-title">' + esc(p.title) + "</div>" +
        (isNow ? '<div class="bar"><div style="width:' + frac + '%"></div></div>' : "") +
        (i === 0 && p.description ? '<div class="prog-desc">' + esc(p.description) + "</div>" : "") + "</div>";
    }).join("");
  });
}
