# Pekná TV – branding a Android TV launcher assety

## Cieľ

Premenovať aplikáciu z `Android TV Player` na `Pekná TV` a nahradiť súčasný prázdny launcher banner vlastným, rozpoznateľným vizuálom pre Android TV. Zmena je výhradne prezentačná: prehrávanie, katalóg kanálov, ovládanie aj package name zostávajú bez zmeny.

## Názov

Používateľský názov aplikácie je `Pekná TV`. Používa sa v Android TV launcheri, v systémovom zozname aplikácií a všade, kde manifest číta `@string/app_name`. `applicationId` `sk.ziacik.androidtvplayer` sa nemení.

## Launcher banner

Banner má formát 16:9, určený pre Android TV launcher. Finálny zdroj sa dodá vo vysokom rozlíšení (minimálne 1280 × 720 px) a exportuje sa do Android resources bez automatického škálovania. V launcheri nahradí dnešný jednoduchý zeleno-modrý gradient.

Vizuál používa tmavomodro-fialový až koralový prechod. Vľavo je veľký biely text `Pekná TV`, nad ním menší popis `TV NAŽIVO`; vpravo je biely kruh s jednoduchým symbolom prehrávania a jemný priesvitný kruhový detail v pozadí. Má zostať čitateľný aj v malom launcher tile a nesmie používať logá, názvy ani vizuály konkrétnych vysielateľov.

## Launcher ikona

Štvorcová adaptívna ikona zdieľa tú istú paletu: tmavomodro-fialové pozadie a biely symbol prehrávania. Bez nápisu, aby bola čitateľná v malej veľkosti. Ikona a banner budú vlastné vektorové alebo rastrové assety aplikácie, nie neoverený stock prvok tretej strany.

## Implementačné hranice

- Upravia sa len názov a manifestom používané launcher resource assety.
- Žiadne zmeny package name, sieťových resolverov, playeru, navigácie, kanálov ani UI overlayu.
- Zachová sa `android:banner` aj existujúca Android TV launcher konfigurácia v manifeste.

## Overenie

- Debug build dokončí spracovanie resources bez chýb.
- V nainštalovanej Android TV aplikácii launcher zobrazí `Pekná TV`, nový 16:9 banner a novú štvorcovú ikonu bez orezania alebo rozmazania.
- Spustenie aplikácie a prehrávanie zostanú funkčne nezmenené.
