# MojaTV — odtwarzacz IPTV na Androida

Live TV z programem (EPG), filmy, seriale, show i radio. Obsługuje logowanie Xtream Codes oraz playlisty M3U.

## Pobierz najnowszą wersję na telefon

https://github.com/eniigma07/Iptv/releases/latest/download/MojaTV.apk

Każda zmiana w kodzie automatycznie buduje nową wersję pod tym samym linkiem.

## Fire TV (Amazon Fire TV Stick / Cube)

Ta sama aplikacja działa na Fire TV — sterowanie pilotem, menu z boku ekranu.

**Instalacja (raz):**
1. Na Fire TV: **Ustawienia → My Fire TV → Developer Options** i włącz instalację aplikacji z nieznanych źródeł (dla aplikacji **Downloader**). Jeśli nie widzisz Developer Options: **My Fire TV → About** i kliknij 7 razy nazwę urządzenia.
2. Zainstaluj z Amazon Appstore darmową aplikację **Downloader**.
3. W Downloaderze wpisz link: `https://github.com/eniigma07/Iptv/releases/latest/download/MojaTV.apk` → **Install**.

**Logowanie telefonem (bez wpisywania pilotem):**
- Przy pierwszym uruchomieniu Fire TV pokazuje kod QR i 4-cyfrowy PIN.
- Zeskanuj kod aparatem telefonu — otworzy się strona, na której wpisujesz dane (Xtream albo M3U). Telewizor zaloguje się sam.
- Albo w MojaTV na telefonie: **Ustawienia → Wyślij profil na telewizor** — wybierasz zapisany profil, telewizor jest wyszukiwany automatycznie, wpisujesz tylko PIN.
- Telefon i Fire TV muszą być w tej samej sieci Wi‑Fi.

**Profile:** każde konto/playlista zapisuje się jako profil. W **Ustawieniach → Profile** przełączasz je jednym kliknięciem; nowy profil na Fire TV dodajesz przez **Dodaj profil telefonem (kod QR)**.

**Pilot w odtwarzaczu:** ▲▼ zmiana kanału, ◀▶ przewijanie filmów, OK pokazuje przyciski, ≡ (Menu) otwiera opcje (proporcje obrazu, program TV i nagrywanie, VLC), Wstecz chowa przyciski / zamyka.

## Jak zbudować plik APK (bez instalowania niczego)

1. Załóż darmowe konto na github.com i utwórz nowe repozytorium (np. `mojatv`, może być prywatne).
2. Rozpakuj `MojaTV.zip` na komputerze. W repozytorium kliknij **Add file → Upload files** i przeciągnij **całą zawartość** folderu `MojaTV` (razem z folderami `app`, `gradle` i `.github`). Zatwierdź przyciskiem **Commit changes**.
3. Wejdź w zakładkę **Actions**. Budowanie „Zbuduj APK” uruchomi się samo (ok. 5–8 minut).
4. Po zielonym znaczku otwórz to uruchomienie i pobierz na dole **MojaTV-apk** (plik zip z `MojaTV.apk`).
5. Przenieś `MojaTV.apk` na telefon, otwórz go i zezwól na instalację z tego źródła.

Jeśli folder `.github` nie wgrał się (bywa ukryty w systemie): w repozytorium wybierz **Actions → set up a workflow yourself**, wklej zawartość pliku `.github/workflows/build.yml` i zatwierdź.

## Alternatywnie: Android Studio

Otwórz folder `MojaTV` w Android Studio i wybierz **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Co jest w aplikacji

- **Start** — kontynuuj oglądanie, ulubione kanały, ostatnio oglądane, ulubione filmy/seriale i stacje radiowe.
- **TV** — kategorie, logo, aktualny i następny program z paskiem postępu, gwiazdka ulubionych. W odtwarzaczu przełączanie kanałów strzałkami.
- **Filmy / Seriale** — siatka plakatów, kategorie, opisy, obsada, oceny, sezony i odcinki, wznawianie od miejsca zatrzymania, automatyczny następny odcinek.
- **Show** — kategorie typu show, programy, reality, talk-show, dokumenty, kabaret (rozpoznawane też po arabsku). Listę można ustawić ręcznie w Ustawieniach.
- **Radio** — stacje z Twojej playlisty oraz światowy katalog radio-browser.info (wyszukiwarka, popularne wg kraju), granie w tle z powiadomieniem.
- **Szukaj** — kanały, filmy i seriale naraz.
- **Odtwarzacz** — ExoPlayer (HLS, MPEG-TS, MP4, MKV), obraz w obrazie, zmiana proporcji, automatyczne ponowne łączenie na żywo, przycisk „Otwórz w VLC” dla nietypowych formatów dźwięku.
- **Ustawienia** — status konta i data ważności, format strumieni (TS/HLS), własny link EPG, User-Agent, ukrywanie treści dla dorosłych, czyszczenie historii, zmiana źródła.

Nazwę aplikacji zmienisz w `app/src/main/res/values/strings.xml`.
