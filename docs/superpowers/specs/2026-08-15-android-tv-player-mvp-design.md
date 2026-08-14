# Android TV Player MVP – návrh

## Cieľ

Vytvoriť natívnu aplikáciu iba pre Android TV/Google TV, ktorá po spustení vyrieši aktuálny live stream STVR Jednotky a prehrá ho na celej obrazovke. Hlavným dôvodom vlastnej aplikácie je úplná kontrola nad prehrávačom a predvídateľné, symetrické posúvanie o 10 sekúnd oboma smermi.

Prvým akceptačným zariadením je Philips 58PUS8545/12 (TPM191E). Ovládanie musí byť navrhnuté pre fyzický D-pad, nie pre dotyk.

## Rozsah MVP

- jediný kanál: STVR Jednotka,
- dynamické získanie aktuálnej HLS URL z oficiálneho STVR webového toku,
- fullscreen prehrávanie cez Media3/ExoPlayer,
- vlastný minimalistický Compose overlay,
- play/pause, presne −10 s, presne +10 s a návrat na LIVE,
- stav načítavania a zrozumiteľná chyba s možnosťou Retry,
- unit testy resolvera a logiky ovládania,
- reálny test prehrávania, D-padu a focusu na televízore Philips.

Mimo MVP sú ďalšie kanály, EPG, archív, používateľské účty, mobilné rozhranie, nastaviteľná dĺžka seeku a vlastný DVR.

## Architektúra

Projekt bude natívny Kotlin projekt s Jetpack Compose a Media3. Zodpovednosti zostanú oddelené v troch malých vrstvách:

1. `StvrResolver` získa čerstvý stream a vráti prehrateľný zdroj. Neovláda prehrávač ani UI.
2. `PlayerController` vlastní ExoPlayer, mapuje jeho stav do jednoduchého aplikačného stavu a vykonáva play, pause, seek a návrat na live edge.
3. `PlayerScreen` vykreslí video a overlay, spracuje udalosti z diaľkového ovládača a posiela príkazy controlleru.

UI nebude poznať STVR endpointy ani HTTP detaily. Resolver nebude poznať Compose ani Media3 UI. Toto umožní neskôr pridať ďalšie providery bez prepisovania prehrávača.

## Získanie streamu

Resolver zopakuje overený postup oficiálneho webového prehrávača:

1. otvorí `https://www.rtvs.sk/televizia/tv` so sledovaním presmerovaní a uložením cookies,
2. v rovnakej session zavolá `https://www.rtvs.sk/json/live5f.json?c=1&ad=1&b=chrome&p=win&v=77&f=0&d=1`,
3. v `clip.sources` vyberie zdroj typu `application/x-mpegurl`,
4. vráti HLS URL spolu s požadovaným User-Agentom,
5. Media3 použije rovnaký User-Agent aj pre manifest a segmenty.

Časovo obmedzená Livebox URL sa nikdy neuloží natvrdo. Každé nové načítanie po chybe alebo Retry vykoná nový resolve.

## Správanie prehrávača

- Po úspešnom resolve sa stream automaticky spustí fullscreen.
- `OK` zobrazí overlay; na ovládacom prvku aktivuje príslušnú akciu.
- `←` vykoná okamžite seek o 10 sekúnd dozadu a zobrazí overlay.
- `→` vykoná okamžite seek o 10 sekúnd dopredu a zobrazí overlay.
- Seek je ohraničený dostupným live oknom. Ak stream seek nepodporuje, seek akcie budú neaktívne.
- `LIVE` sa vráti na live edge pomocou predvolenej live pozície Media3.
- Overlay sa po štyroch sekundách bez vstupu automaticky skryje.
- `Back` pri zobrazenom overlayi overlay skryje. `Back` pri skrytom overlayi ukončí player/aplikáciu.

## Vzhľad a focus

Video je vždy hlavný obsah. Overlay použije jemný tmavý gradient pri spodnom okraji, názov kanála, tenkú reprezentáciu dostupného live okna a ovládanie `−10  ⏯  +10`, pričom `LIVE` bude napravo. Focus sa označí decentným zosvetlením a zväčšením; aplikácia nepoužije predvolené ovládanie Media3 ani hrubé systémové TV rámiky.

## Stav a chyby

Aplikácia rozlíši stavy `Resolving`, `Preparing`, `Playing`, `Paused` a `Error`. Pri chybe nezostane čierna obrazovka ani nekonečný spinner. Zobrazí text „Stream sa nepodarilo načítať“ a tlačidlo `Retry`.

Do diagnostického logu sa zapíše konkrétna príčina bez citlivých alebo časovo obmedzených tokenov: HTTP status, sieťová chyba, neplatný JSON, chýbajúci HLS zdroj alebo chyba Media3. Retry zahodí starý zdroj, vykoná nový resolve a znovu pripraví player.

## Testovanie a kritériá úspechu

Automatické testy pokryjú:

- úspešné vybratie HLS zdroja z fake STVR odpovede,
- chýbajúci alebo neplatný zdroj a sieťové chyby,
- presné výpočty −10/+10 s a ohraničenie dostupným oknom,
- návrat na live edge,
- automatické skrytie overlayu po štyroch sekundách,
- Retry vykonávajúci nový resolve.

MVP je úspešné, keď debug APK prejde buildom, nainštaluje sa cez ADB na Philips, Jednotka sa spustí bez ručne vloženej stream URL a všetky D-pad akcie sa na fyzickom ovládači správajú podľa tohto návrhu.
