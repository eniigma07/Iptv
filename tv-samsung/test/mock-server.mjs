// Testowy serwer: aplikacja na :8000, symulowany panel Xtream na :8001.
// Uruchom: node test/mock-server.mjs   (z folderu tv-samsung)
import http from "node:http";
import fs from "node:fs";
import path from "node:path";

const APP_DIR = path.resolve("app");
const MEDIA = path.resolve("test/media");
const USER = "demo", PASS = "demo";
const types = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".png": "image/png", ".webm": "video/webm", ".ogg": "audio/ogg", ".svg": "image/svg+xml" };

function svgPoster(text, w, h, color) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="100%" height="100%" fill="${color}"/>` +
    `<text x="50%" y="50%" font-size="${Math.round(w / 8)}" fill="#fff" text-anchor="middle" font-family="Arial">${text}</text></svg>`;
}

// ---- dane ----
const liveCats = [
  { category_id: "1", category_name: "Polska" },
  { category_id: "2", category_name: "Sport" },
  { category_id: "3", category_name: "Radio PL" },
  { category_id: "4", category_name: "XXX Adult" },
];
const names = ["TVP 1 HD", "TVP 2 HD", "Polsat HD", "TVN HD", "TV4", "TVN 24", "Polsat News", "TVP Info", "Canal+ Premium", "HBO HD"];
const live = [];
for (let i = 0; i < 140; i++) {
  live.push({ num: i + 1, name: i < names.length ? names[i] : "Kanał testowy " + (i + 1), stream_id: 1000 + i,
    stream_icon: i % 3 === 0 ? `http://localhost:8001/img/logo/${i}` : "", epg_channel_id: "ch" + i, category_id: i < 100 ? "1" : "2" });
}
live.push({ num: 141, name: "RMF FM", stream_id: 2000, stream_icon: "", epg_channel_id: null, category_id: "3" });
live.push({ num: 142, name: "Kanał dla dorosłych", stream_id: 2001, stream_icon: "", category_id: "4" });

const vodCats = [
  { category_id: "10", category_name: "Filmy PL" },
  { category_id: "11", category_name: "Akcja" },
  { category_id: "12", category_name: "Reality Show" },
];
const movies = [];
for (let i = 0; i < 90; i++) {
  movies.push({ name: (i === 0 ? "Incepcja" : "Film " + (i + 1)) + " (" + (2000 + (i % 24)) + ")", stream_id: 5000 + i,
    stream_icon: `http://localhost:8001/img/poster/${i}`, rating: String((i % 9) + 1), category_id: i < 50 ? "10" : i < 80 ? "11" : "12",
    container_extension: "webm", added: String(1700000000 + i) });
}
const serCats = [{ category_id: "20", category_name: "Seriale Netflix" }, { category_id: "21", category_name: "Talk show" }];
const series = [];
for (let i = 0; i < 30; i++) {
  series.push({ name: i === 0 ? "Dark" : i === 1 ? "Kuchenne rewolucje" : "Serial " + (i + 1), series_id: 7000 + i,
    cover: `http://localhost:8001/img/poster/s${i}`, plot: "Opis serialu " + (i + 1), genre: "Dramat", releaseDate: "2019-01-01",
    rating: "8.1", category_id: i === 1 || i > 25 ? "21" : "20" });
}

const b64 = (s) => Buffer.from(s, "utf8").toString("base64");

function api(q, res) {
  const json = (o) => { res.writeHead(200, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }); res.end(JSON.stringify(o)); };
  if (q.get("username") !== USER || q.get("password") !== PASS) return json({ user_info: { auth: 0 } });
  const a = q.get("action");
  if (!a) return json({ user_info: { auth: 1, status: "Active", exp_date: "1830000000", max_connections: "2", active_cons: "0", is_trial: "0" }, server_info: {} });
  if (a === "get_live_categories") return json(liveCats);
  if (a === "get_live_streams") return json(live);
  if (a === "get_vod_categories") return json(vodCats);
  if (a === "get_vod_streams") return json(movies);
  if (a === "get_series_categories") return json(serCats);
  if (a === "get_series") return json(series);
  if (a === "get_vod_info") return json({ info: { plot: "Złodziej, który kradnie sekrety z podświadomości, dostaje ostatnie zadanie.", cast: "Leonardo DiCaprio, Elliot Page", director: "Christopher Nolan", genre: "Sci-Fi", releasedate: "2010-07-16", duration: "02:28:00", backdrop_path: ["http://localhost:8001/img/backdrop/1"], rating: "8.8" } });
  if (a === "get_series_info") {
    const ep = (s, n) => ({ id: String(s * 100 + n), episode_num: n, season: s, title: `Odcinek ${n}`, container_extension: "webm", info: { plot: "Opis odcinka " + n, duration: "00:52:00", movie_image: `http://localhost:8001/img/ep/${s}-${n}` } });
    return json({ info: { plot: "Zaginięcie dziecka ujawnia tajemnice czterech rodzin.", cast: "Louis Hofmann", genre: "Sci-Fi", releaseDate: "2017-12-01", backdrop_path: ["http://localhost:8001/img/backdrop/2"] },
      episodes: { "1": [1, 2, 3, 4, 5].map((n) => ep(1, n)), "2": [1, 2, 3].map((n) => ep(2, n)) } });
  }
  if (a === "get_short_epg") {
    const now = Math.floor(Date.now() / 1000);
    const start = now - 1200;
    const titles = ["Wiadomości", "Pogoda", "Film: Pan Tadeusz", "Sport", "Teleexpress", "Magazyn"];
    return json({ epg_listings: titles.map((t, i) => ({ title: b64(t), description: b64("Opis programu " + t), start_timestamp: String(start + i * 1800), stop_timestamp: String(start + (i + 1) * 1800) })) });
  }
  json([]);
}

const colors = ["#5b3a8c", "#2d6a4f", "#9d0208", "#1d3557", "#bc6c25", "#3a0ca3"];

http.createServer((req, res) => {
  const u = new URL(req.url, "http://localhost:8001");
  if (u.pathname === "/player_api.php") return api(u.searchParams, res);
  if (u.pathname === "/playlist.m3u") {
    res.writeHead(200, { "Content-Type": "audio/x-mpegurl", "Access-Control-Allow-Origin": "*" });
    return res.end([
      '#EXTM3U x-tvg-url="http://localhost:8001/epg.xml"',
      '#EXTINF:-1 tvg-id="tvp1" tvg-logo="http://localhost:8001/img/logo/1" group-title="Polska",TVP 1 HD',
      "http://localhost:8001/live/u/p/1.ts",
      '#EXTINF:-1 group-title="Polska",Polsat',
      "http://localhost:8001/live/u/p/2.ts",
      '#EXTINF:-1 group-title="Radio PL",RMF FM',
      "http://localhost:8001/live/u/p/3.ts",
      '#EXTINF:-1 tvg-logo="http://localhost:8001/img/poster/9" group-title="Filmy",Matrix (1999)',
      "http://localhost:8001/movie/u/p/9.mkv",
      '#EXTINF:-1 group-title="Seriale",Dark S01E01',
      "http://localhost:8001/series/u/p/11.mkv",
      '#EXTINF:-1 group-title="Seriale",Dark S01E02',
      "http://localhost:8001/series/u/p/12.mkv",
      '#EXTINF:-1 group-title="Programy rozrywkowe",Taniec z gwiazdami S12E03',
      "http://localhost:8001/series/u/p/13.mkv",
    ].join("\n"));
  }
  if (u.pathname.startsWith("/img/")) {
    const parts = u.pathname.split("/");
    const kind = parts[2], id = parts[3];
    const [w, h] = kind === "poster" ? [300, 450] : kind === "logo" ? [240, 140] : [1280, 720];
    res.writeHead(200, { "Content-Type": "image/svg+xml", "Access-Control-Allow-Origin": "*" });
    return res.end(svgPoster(kind === "logo" ? "TV " + id : id, w, h, colors[(id.length + (parseInt(id, 10) || 0)) % colors.length]));
  }
  // strumienie: /live|movie|series/u/p/ID.ext → próbka wideo
  if (/^\/(live|movie|series)\//.test(u.pathname)) {
    const f = path.join(MEDIA, "sample.webm");
    res.writeHead(200, { "Content-Type": "video/webm", "Access-Control-Allow-Origin": "*", "Content-Length": fs.statSync(f).size });
    return fs.createReadStream(f).pipe(res);
  }
  res.writeHead(404); res.end();
}).listen(8001);

http.createServer((req, res) => {
  let p = decodeURIComponent(new URL(req.url, "http://localhost:8000").pathname);
  if (p === "/") p = "/index.html";
  const f = path.join(APP_DIR, p);
  if (!f.startsWith(APP_DIR) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "Content-Type": types[path.extname(f)] || "application/octet-stream" });
  fs.createReadStream(f).pipe(res);
}).listen(8000);

console.log("Aplikacja: http://localhost:8000  ·  panel Xtream: http://localhost:8001 (login demo/demo)");
