# Priamy výber kanála číslicami

## Cieľ

Umožniť prepnutie na kanál podľa jeho jednoprvkového poradia v aktuálnom
katalógu `TvChannel` stlačením číslic na TV ovládači. Číslo `1` znamená
prvý kanál, `12` dvanásty kanál.

## Správanie

- Číslice `0` až `9` sa zachytávajú v hlavnej obrazovke prehrávača, nezávisle
  od viditeľnosti overlayu a aktuálneho zamerania ovládacieho prvku.
- Prvá číslica otvorí alebo obnoví indikátor rozpracovaného čísla.
- Každá ďalšia číslica zloží viacciferné číslo. Zadaný prefix je viditeľný
  v overlayi.
- Po 1 sekunde bez ďalšej číslice sa vyberie kanál s poradovým číslom
  odpovedajúcim zadanému číslu. Zadanie sa potom vymaže.
- Pre číslo mimo rozsahu katalógu sa kanál neprepne; indikátor zmizne po
  uplynutí rovnakého času.
- Po platnom výbere sa použije existujúca cesta prepnutia prehrávača, aby
  zostali zachované stavy načítavania a chýb.

## Štruktúra

`RemoteCommandMapper` rozpozná číselné klávesy a odovzdá ich ako samostatný
príkaz s číslicou. Nový malý, testovateľný stavový objekt bude zhromažďovať
číslice a po vypršaní timeoutu vráti platné poradové číslo alebo nič.
`PlayerScreen` objekt spojí s existujúcim `LaunchedEffect`, zobrazí aktuálne
zadanie v overlayi a následne požiada `PlayerController` o prepnutie na
zodpovedajúci `TvChannel`.

## Chybové stavy a okraje

- `0` samo osebe je neplatné poradové číslo.
- Číslo väčšie než počet kanálov nič neprepne.
- Nová číslica pred timeoutom nahrádza čakajúci timeout.
- Ak sa obrazovka opustí alebo sa stav prehrávača zmení, čakajúci timeout sa
  zruší spolu s Compose efektom.

## Overenie

Testy najprv pokryjú mapovanie všetkých číselných klávesov, skladanie
viacciferného čísla, obnovu timeoutu a odmietnutie čísel mimo katalógu.
Následne sa spustia príslušné unit testy a celý `testDebugUnitTest`.
