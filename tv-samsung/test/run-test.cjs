// Przejście przez aplikację „pilotem” (klawisze) w przeglądarce 1920×1080 + zrzuty ekranu.
// Wymaga działającego test/mock-server.mjs. Użycie: node test/run-test.cjs <folder_na_zrzuty>
const { chromium } = require(process.env.PW_PATH || "playwright");
const path = require("path");

const OUT = process.argv[2] || "shots";
const errors = [];
let step = 0;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on("pageerror", (e) => errors.push("PAGEERROR: " + e.message));
  page.on("console", (m) => { if (m.type() === "error" && !/webapis|ERR_|Failed to load resource/.test(m.text())) errors.push("CONSOLE: " + m.text()); });

  const key = async (k, n = 1, wait = 120) => { for (let i = 0; i < n; i++) { await page.keyboard.press(k); await page.waitForTimeout(wait); } };
  const shot = async (name, wait = 500) => {
    await page.waitForTimeout(wait);
    step++;
    await page.screenshot({ path: path.join(OUT, String(step).padStart(2, "0") + "-" + name + ".png") });
  };
  const focused = () => page.evaluate(() => { const f = document.querySelector(".focused"); return f ? f.className + " | " + f.textContent.trim().slice(0, 60) : "(brak)"; });
  const log = async (label) => console.log(label.padEnd(28), "→", await focused());

  await page.goto("http://localhost:8000/index.html");
  await shot("logowanie");

  // --- logowanie: złe hasło, potem dobre ---
  await page.evaluate(() => {
    document.querySelector('input[name=server]').value = "localhost:8001";
    document.querySelector('input[name=username]').value = "demo";
    document.querySelector('input[name=password]').value = "zle";
  });
  await key("Enter", 1, 2500);
  await shot("logowanie-zle-haslo", 200);
  // wpisanie hasła „pilotem”: w górę do pola hasła, OK, pisanie, OK
  await key("ArrowUp");
  await log("pole po strzałce w górę");
  await key("Enter");
  await page.keyboard.press("Control+A");
  await page.keyboard.type("demo");
  await key("Enter");
  await key("ArrowDown", 1);
  await log("po zejściu w dół");
  await key("Enter", 1, 3000);
  await shot("start-po-zalogowaniu", 800);
  await log("start");

  // --- Telewizja ---
  await key("ArrowLeft"); // do menu
  await log("menu");
  await key("ArrowDown");
  await key("Enter", 1, 800);
  await log("TV – kategoria");
  await shot("tv", 1200);
  await key("ArrowRight", 1, 300);
  await key("ArrowDown", 3, 150);
  await log("TV – kanał");
  await shot("tv-kanal-epg", 900);
  // ulubione czerwonym (klawisz F1 nie istnieje – symulujemy keyCode 403)
  await page.evaluate(() => document.dispatchEvent(new KeyboardEvent("keydown", { keyCode: 403, bubbles: true })));
  await page.waitForTimeout(300);
  await key("Enter", 1, 2500);
  await shot("odtwarzacz-live", 300);
  await key("ArrowUp", 1, 1500);
  await shot("odtwarzacz-zmiana-kanalu", 300);
  await key("ArrowLeft", 1, 600);
  await shot("odtwarzacz-lista-kanalow", 300);
  await key("ArrowDown", 2);
  await key("Enter", 1, 1500);
  await log("po wyborze z listy");
  await key("Digit1"); await key("Digit2", 1, 2500);
  await shot("odtwarzacz-numer-12", 300);
  await key("Escape", 1, 600);
  await log("po wyjściu z odtwarzacza");
  // przewiń długą listę kanałów (doładowywanie)
  await key("ArrowDown", 70, 25);
  await log("kanał ~74");
  await shot("tv-dlugie-przewijanie", 400);

  // --- Filmy ---
  await key("Escape"); // do menu
  await key("ArrowDown");
  await key("Enter", 1, 800);
  await shot("filmy", 900);
  await key("ArrowRight");
  await key("ArrowRight");
  await key("ArrowDown");
  await log("film");
  await key("Enter", 1, 800);
  await shot("film-szczegoly", 900);
  await key("Enter", 1, 3500);
  await shot("film-odtwarzanie", 300);
  await key("ArrowRight", 3, 300);
  await key("Escape", 1, 800);
  await log("po filmie");
  await shot("film-po-ogladaniu-wznow", 500);
  await key("Escape", 1, 400);

  // --- Seriale ---
  await key("Escape");
  await key("ArrowDown");
  await key("Enter", 1, 800);
  await key("ArrowRight");
  await key("Enter", 1, 1200);
  await shot("serial-szczegoly", 900);
  await key("ArrowDown");
  await key("ArrowRight");
  await log("sezon");
  await key("ArrowDown");
  await log("odcinek");
  await shot("serial-sezon-2", 500);
  await key("Escape", 1, 400);

  // --- Show ---
  await key("Escape");
  await key("ArrowDown");
  await key("Enter", 1, 900);
  await shot("show", 900);

  // --- Radio ---
  await key("Escape");
  await key("ArrowDown");
  await key("Enter", 1, 2500);
  await shot("radio", 800);

  // --- Szukaj ---
  await key("Escape");
  await key("ArrowDown");
  await key("Enter", 1, 500);
  await key("Enter");
  await page.keyboard.type("tvp");
  await key("Enter", 1, 800);
  await shot("szukaj", 600);

  // --- Ustawienia ---
  await key("ArrowLeft");
  await key("Escape");
  await key("ArrowDown");
  await key("Enter", 1, 600);
  await shot("ustawienia", 500);

  // --- Start z historią ---
  await key("Escape");
  await key("ArrowUp", 8, 80);
  await key("Enter", 1, 800);
  await shot("start-z-historia", 900);

  // --- wyjście (Back w menu) ---
  await key("ArrowLeft");
  await key("Escape", 1, 400);
  await shot("pytanie-o-zamkniecie", 300);

  console.log("\nBŁĘDY:", errors.length ? "\n" + errors.join("\n") : "brak");
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
