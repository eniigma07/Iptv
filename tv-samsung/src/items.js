// Zamiana pozycji z katalogu na elementy do odtwarzania / ulubionych.
import { esc } from "./util.js";
import { el } from "./dom.js";
import { repo } from "./repo.js";
import { nav } from "./nav.js";

export function liveItem(ch) {
  return { key: "live:" + ch.id, kind: "live", title: ch.name, url: ch.url, image: ch.logo, epgId: ch.epgId };
}
export function movieItem(m) {
  return { key: "movie:" + m.id, kind: "movie", title: m.name, url: m.url, image: m.poster };
}
export function seriesItem(s) {
  return { key: "series:" + s.id, kind: "series", title: s.name, url: "", image: s.poster };
}
export function episodeItem(ep, s) {
  var se = "S" + String(ep.season).padStart(2, "0") + "E" + String(ep.number).padStart(2, "0");
  return {
    key: "episode:" + ep.id, kind: "episode", title: s.name, url: ep.url,
    image: ep.image || s.poster, subtitle: se + " · " + ep.title, parentId: s.id,
  };
}
export function radioItem(st) {
  return { key: "radio:" + st.id, kind: "radio", title: st.name, url: st.url, image: st.logo, subtitle: st.tags };
}

/**
 * Pokazuje ekran dopiero po wczytaniu katalogu; w międzyczasie kółko ładowania albo błąd.
 * Zwraca true, jeśli katalog jest gotowy.
 */
export function catalogGate(main, onRetryFocus) {
  if (repo.catalog) return true;
  if (repo.error && !repo.loading) {
    main.innerHTML = "";
    var box = el(
      '<div class="center-msg"><p>' + esc(repo.error) + '</p><div class="row-btns">' +
      '<div class="btn f">Spróbuj ponownie</div></div></div>'
    );
    box.querySelector(".btn").onEnter = function () { repo.load(true); };
    main.appendChild(box);
    if (onRetryFocus) nav.focusFirst(box);
    return false;
  }
  main.innerHTML = '<div class="center-msg"><div class="spinner"></div><p>Wczytywanie kanałów, filmów i seriali…</p></div>';
  if (!repo.loading) repo.load();
  return false;
}
