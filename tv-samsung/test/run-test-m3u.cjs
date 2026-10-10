// Logowanie linkiem M3U + radio w tle.
const { chromium } = require(process.env.PW_PATH || "playwright");
const OUT = process.argv[2] || "shots";
(async () => {
  const errors = [];
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on("pageerror", (e) => errors.push(e.message));
  const key = async (k, n = 1, w = 150) => { for (let i = 0; i < n; i++) { await page.keyboard.press(k); await page.waitForTimeout(w); } };
  const focused = () => page.evaluate(() => { const f = document.querySelector(".focused"); return f ? f.textContent.trim().slice(0, 50) : "(brak)"; });
  await page.goto("http://localhost:8000/index.html");
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForTimeout(300);
  // zakładka M3U: w górę do pól, potem do zakładek
  await key("ArrowUp", 4);
  console.log("zakładka:", await focused());
  await key("ArrowRight");
  await key("Enter");
  await page.evaluate(() => { document.querySelector('input[name=m3uUrl]').value = "http://localhost:8001/playlist.m3u"; });
  await key("ArrowDown", 3);
  console.log("przycisk:", await focused());
  await key("Enter", 1, 2000);
  await page.screenshot({ path: OUT + "/m3u-01-start.png" });
  await key("ArrowLeft"); await key("ArrowDown"); await key("Enter", 1, 600);
  await page.screenshot({ path: OUT + "/m3u-02-tv.png" });
  await key("Escape"); await key("ArrowDown", 2); await key("Enter", 1, 600);
  await page.screenshot({ path: OUT + "/m3u-03-seriale.png" });
  await key("Escape"); await key("ArrowDown"); await key("Enter", 1, 600);
  await page.screenshot({ path: OUT + "/m3u-04-show.png" });
  await key("Escape"); await key("ArrowDown"); await key("Enter", 1, 600);
  // Radio → "Z Twojej playlisty" → RMF
  await key("ArrowUp", 1, 400);
  console.log("radio kat.:", await focused());
  await key("Enter", 1, 500);
  console.log("stacja:", await focused());
  await key("Enter", 1, 2500);
  await page.screenshot({ path: OUT + "/m3u-05-radio-gra.png" });
  // pauza klawiszem play/pause (10252)
  await page.evaluate(() => document.dispatchEvent(new KeyboardEvent("keydown", { keyCode: 10252, bubbles: true })));
  await page.waitForTimeout(500);
  const bar = await page.evaluate(() => document.querySelector(".radio-bar").textContent);
  console.log("pasek radia po pauzie:", bar);
  console.log("BŁĘDY:", errors.length ? errors.join("\n") : "brak");
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
