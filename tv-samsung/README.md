# MojaTV na telewizor Samsung

Ta sama aplikacja co na telefonie, przerobiona pod pilota: telewizja na żywo z programem TV, filmy, seriale, show, radio, wyszukiwarka, ulubione i „kontynuuj oglądanie”. Działa na telewizorach Samsung z systemem Tizen, mniej więcej od roczników 2017–2018.

## Instalacja (sposób zalecany: Apps2Samsung)

Potrzebujesz komputera z Windowsem **albo** telefonu z Androidem w tej samej sieci Wi‑Fi co telewizor.

### 1. Pobierz pliki
- **Aplikacja na telewizor:** https://github.com/eniigma07/Iptv/releases/latest/download/MojaTV-Samsung.wgt
- **Instalator Apps2Samsung** (darmowy, ma wersję po polsku): https://github.com/Apps2Samsung/Apps2Samsung/releases
  - na Windows: plik kończący się na `.exe` lub `win…zip`,
  - na telefon: plik kończący się na `-android.apk`.

### 2. Sprawdź adres IP urządzenia z instalatorem
- **Windows:** Start → wpisz `cmd` → w oknie wpisz `ipconfig` → zapisz „Adres IPv4” (np. `192.168.1.23`).
- **Telefon:** Ustawienia → Wi‑Fi → Twoja sieć → Adres IP.

### 3. Włącz w telewizorze tryb programisty
1. Na pilocie naciśnij **Home** i wejdź w **Aplikacje** (Apps).
2. Na ekranie Aplikacji wpisz na pilocie (lub na klawiaturze ekranowej) **1 2 3 4 5**.
3. Przełącz **Developer mode** na **On** i wpisz adres IP z kroku 2. Zatwierdź **OK**.
4. **Uruchom telewizor ponownie** (najlepiej wyłącz go z prądu na 15 sekund). Na górze ekranu Aplikacji pojawi się napis „Develop mode”.

### 4. Zainstaluj
1. Uruchom Apps2Samsung. Wyszuka telewizor w sieci (albo wpisz jego adres IP ręcznie — znajdziesz go w telewizorze: Ustawienia → Ogólne → Sieć → Stan sieci).
2. Wybierz instalację **własnego pliku .wgt** i wskaż pobrany `MojaTV-Samsung.wgt`.
3. Przy nowszych telewizorach (Tizen 7 i nowszy, mniej więcej od 2023 r.) instalator poprosi o zalogowanie się kontem Samsung — to jednorazowe i potrzebne do certyfikatu.
4. Po zakończeniu MojaTV pojawi się w **Aplikacjach** na telewizorze.

### Aktualizacja
Pobierz nowy `MojaTV-Samsung.wgt` z tego samego linku i zainstaluj go tak samo przez Apps2Samsung. Ulubione i historia zostają.

## Obsługa pilotem

| Przycisk | Co robi |
|---|---|
| Strzałki, OK | poruszanie się i wybór |
| Wstecz | powrót; z menu — zamknięcie aplikacji |
| **Czerwony** | dodaj / usuń z ulubionych (kanał, film, serial, stacja) |
| W odtwarzaczu: ▲ ▼ lub CH+ / CH− | poprzedni / następny kanał |
| W odtwarzaczu: ◀ ▶ | na żywo: lista kanałów · filmy: przewijanie o 10 s |
| ⏪ ⏩ | przewijanie o minutę |
| Cyfry | przejście do kanału o danym numerze |
| ⏯ / ⏹ | pauza / stop (także radio grające w tle) |

Przy logowaniu OK na polu tekstowym otwiera klawiaturę telewizora. Żeby nie wpisywać długich danych pilotem, możesz przed instalacją przez Tizen Studio wpisać je w pliku `app/js/defaults.js` (tylko na swoim komputerze — nie wrzucaj tego pliku z danymi na GitHuba).

## Sposób zapasowy: Tizen Studio

Gdyby Apps2Samsung nie zadziałał:
1. Zainstaluj **Tizen Studio** (https://developer.tizen.org/development/tizen-studio/download) w `C:\tizen-studio`. W Package Manager doinstaluj **Samsung Certificate Extension** i **TV Extensions**.
2. Włącz tryb programisty w telewizorze (jak wyżej, z IP komputera).
3. W Tizen Studio: **Tools → Device Manager** → dodaj telewizor po IP i przełącz połączenie na On.
4. **Tools → Certificate Manager** → utwórz certyfikat **Samsung** (typ TV, logowanie kontem Samsung).
5. W Device Manager kliknij prawym na telewizor → **Permit to install applications**.
6. Pobierz `MojaTV-Samsung-projekt.zip` z wydania na GitHubie, rozpakuj i uruchom `zainstaluj.bat`.

## Dla programisty

- Kod źródłowy: `src/` (JavaScript), wynik budowania: `app/js/app.js`.
- Budowanie: `npm ci && npm run build` (Babel przerabia kod na starsze przeglądarki telewizorów).
- Test w przeglądarce: wygeneruj próbki do `test/media` (`sample.webm`, `radio.ogg`, np. ffmpeg), uruchom `node test/mock-server.mjs` i otwórz http://localhost:8000 (login `localhost:8001`, `demo` / `demo`).
- Odtwarzanie na telewizorze: silnik Samsunga AVPlay (MPEG‑TS, HLS, MP4, MKV).
