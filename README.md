# OP-Assistent (by Jxlian0502)

Fabric-Client-Mod für **Minecraft 1.21.11**. Sie speichert AH-Verkäufe von OP SUCHT lokal auf deinem PC und zeigt im Item-Tooltip:

```
Letzter AH-Verkauf: 130.000$ (vor 8 Minuten)
Durchschnittspreis: 120.000$ (2 Verkäufe)
```

Hat ein Item noch keinen erfassten Verkauf, steht dort „Noch keine AH-Verkäufe erfasst".

## Installation

1. Fabric Loader 0.19.5+ für 1.21.11 installieren: https://fabricmc.net/use/
2. Fabric API für 1.21.11 in `%appdata%\.minecraft\mods` legen.
3. Die Mod-Datei `opsuchtprices-1.1.1.jar` ebenfalls dorthin legen.

## Bauen

JDK 21 installieren, dann im Projektordner:

```
gradlew.bat clean test build
```

Ergebnis: `build\libs\opsuchtprices-1.1.1.jar`

## So funktioniert es

- Solange du auf OP SUCHT bist, hört die Mod den Auktions-Stream der API mit (`https://api.opsucht.net/auctions/stream`).
- Gespeichert wird nur ein **echter Verkauf**: Sofortkauf (`auction.instant_bought`) oder Versteigerung mit Höchstbietendem (`auction.sold`). Abgelaufene oder abgebrochene Auktionen zählen nicht.
- Die Verkäufe liegen in `.minecraft\config\opsuchtprices\sales.json` (bis zu 50 pro Item, die ältesten fallen raus).
- **Preise sind pro Stück**: Verkaufspreis geteilt durch die Stapelgröße.
- **Durchschnittspreis** = Mittelwert aller gespeicherten Verkäufe dieses Items.
- Items werden über Material, eigenen Namen und Verzauberungen zugeordnet. Ein Schärfe-5-Schwert und ein Schärfe-4-Schwert sind also verschiedene Items.

Mit `/opa` siehst du, ob der Stream verbunden ist und wie viele Verkäufe gespeichert sind.

## Config: `.minecraft\config\opsuchtprices\config.json`

| Option | Standard | Bedeutung |
|---|---|---|
| `enabled` | `true` | Mod an/aus |
| `currencyLabel` | `$` | Währungsname im Tooltip |
| `onlyOnOpSucht` | `true` | nur auf OP SUCHT aktiv |
| `serverAddressContains` | `["opsucht"]` | Erkennung über die Serveradresse |
| `streamUrl` | `https://api.opsucht.net/auctions/stream` | Adresse des Auktions-Streams |
| `maxSalesPerItem` | `50` | gespeicherte Verkäufe pro Item |
| `debugLogging` | `false` | ausführliches Log |

## Einschränkungen

- Erfasst werden nur Verkäufe, die passieren, **während dein Minecraft läuft** und du auf OP SUCHT bist. Die API bietet keine Verkaufshistorie zum Nachladen.
- Die Stream-Adresse ist aus der API-Beschreibung abgeleitet und konnte nicht live geprüft werden. Wenn `/opa` dauerhaft einen Fehler zeigt, muss `streamUrl` angepasst werden.
- Dass der AH-Preis für den ganzen Stapel gilt, ist eine Annahme aus den Daten, die API sagt es nicht ausdrücklich.

## Markt-Overlay für /markt (ab 1.7.0)

Öffnest du auf OP SUCHT `/markt`, legt sich eine eigene Oberfläche über den Marktplatz des Servers:

- **Übersicht:** Kategorien links, Items als Karten mit exaktem Kauf- und Verkaufspreis (scrollbar), Weiter/Zurück, Suche, Deine Aufträge, Items verkaufen, Modus.
- **Item-Seite:** Preise, Menge im Inventar, Steuern, Orderbuch (Top 10 Verkaufsangebote und Kaufaufträge mit Mengen), Verlauf der letzten 24 Std. aus der Markt-API.
  Knöpfe: „1 kaufen", „Kaufen: Anzahl…", „1 verkaufen", „Verkauf: Anzahl…", Kaufauftrag, Verkaufsangebot, Aufträge, Zurück.

So funktioniert es:
- Das Server-Menü bleibt im Hintergrund offen. Jeder Klick im Overlay löst **genau einen** Klick auf den passenden Slot des Server-Menüs aus (z. B. „1 kaufen" = Linksklick auf „Sofort kaufen"). Keine Automatik, keine Klickfolgen.
- „1 kaufen" und „1 verkaufen" brauchen **zwei Klicks** („Sicher?"), weil der Server diese Aktionen sofort ausführt.
- Ab 1.8.0 läuft auch **Kaufauftrag / Verkaufsangebot** im Overlay: Schritt 1 Menge (1x / 64x / ganzes Inventar), Schritt 2 Preis (Bestes Angebot, +0,1$, 5 % der Spanne, mit Marktlage), Schritt 3 Bestätigen (Zusammenfassung; jeder Klick auf „Bestätigen" ist ein Server-Klick, der Server verlangt 3).
- „Eigene Menge/Eigenen Preis eingeben…" öffnet das Eingabefeld des Servers. Suche und „Items verkaufen" erscheinen weiterhin **im Original**.
- Ab 1.9.0 **„Alle einsammeln (N)“** in Deine Aufträge: klickt nacheinander jeden einsammelbaren Auftrag an (erkannt am Filter „Einsammelbar“ oder einem „Einsammeln“-Hinweis). Nach jedem Klick wird auf die Antwort des Servers gewartet (mind. 0,35 s Abstand). Stopp bei anderem Menü, Meldung wie „Inventar voll“, fehlender Antwort (2,5 s) oder jedem Mausklick.
- Ab 1.8.2 auch **Deine Aufträge** im Overlay: Liste mit Menge, Preis/Stk., Gesamtwert, Fortschritt und Status; Filter (ein Klick = nächster Filter wie im Original); Klick auf einen Auftrag öffnet „Auftrag abbrechen" (ebenfalls im Overlay, der Server verlangt 3 Klicks).
- „Original-Menü" oben rechts zeigt jederzeit das normale Server-Menü (bis du den Markt schließt); dort bringt „Overlay zeigen" das Overlay zurück.
- Ausschalten: `"marketOverlay": false` in der Config.

Test: `gradlew runClientGameTest` öffnet nachgebaute Menüs aus einer echten Aufnahme und prüft jeden Overlay-Klick (der Testcode ist nicht in der fertigen .jar).

## Jobtracker (ab 1.10.0)

Liest die Job-Actionbar von OP SUCHT (z. B. `+2,75 XP • +15,887$ • Holzfäller Level 78 • [|||] 56,79%`) und zeigt oben links ein HUD:
Job und Level, Fortschrittsbalken, aktive Zeit, Geld und Geld/h, XP und XP/h, geschätzte Zeit bis zum Level-Up.

- Gezählt wird pro Job und pro Sitzung (bis zum Neustart oder `/opa job reset`).
- Die Zeit pausiert nach 60 s ohne Job-Meldung (`jobAutoPauseSeconds`), das HUD verschwindet nach 5 Min. (`jobHideAfterMinutes`, 0 = nie).
- Jede Job-Actionbar zählt einzeln, genau wie in der OPMOD (bei Baumfäller-Äxten schickt der Server eine Meldung pro Block).
- Befehle: `/opa job` (Stand im Chat), `/opa job reset`, `/opa job toggle` (an/aus), `/opa job actionbar an|aus`. Position per Drag & Drop in den Einstellungen.
- Zähllogik übernommen vom Jobtracker der OPMOD (MIT-Lizenz, Copyright (c) 2025 Geldbedarf & CalledCracki, siehe `licenses/OPMOD-LICENSE.txt` in der .jar).
- **Jobschatz (XP-Zähler, ab 1.11.0):** zählt alle Job-XP bis 100.000, dann Ton + Chat-Meldung und Neustart bei 0; die vollen Runden werden mitgezählt. Bleibt über Neustarts erhalten (`job-zaehler.json`). Befehle: `/opa job zaehler` (Stand), `job zaehler reset`, `job zaehler <grenze>` (z. B. 50000; 0 = aus).
- **Statistik (ab 1.19.0, in den Einstellungen):** dauerhafte Zähler für gefällte Bäume, abgebaute Diamanterze (auch Tiefenschiefer) und Spawner. Gezählt wird nur auf Farmwelt 1–12 und Nether 1–2 (Welt aus dem Scoreboard „Server: FARMWELT 1“, ersatzweise aus „Verbinde zu …“). Ein Baum = Stamm angeschlagen + mind. 2 Holzfäller-Meldungen innerhalb von 2,5 s. Wird nie zurückgesetzt (`statistik.json`).
- **Jobschatz-Funde (ab 1.15.0):** Bei „Du hast einen Jobschatz erhalten!“ springt der Jobschatz-Zähler auf 0 und „× gefunden“ zählt +1. Die Funde bleiben immer erhalten (auch bei `job reset`, `job zaehler reset` und Neustart).
- **Shard-Wert (ab 1.12.0):** Der Jobtracker zeigt „Shards (Inv.)“: Summe aller Items im Inventar, die der Händler annimmt, mal dem aktuellen Kurs aus `https://api.opsucht.net/merchant/rates` (alle 5 Min. neu geladen, `shardRefreshMinutes`). Custom Items (z. B. Holzbündel, Gräbergemisch, Steinplatten) werden über Custom-Model-Data erkannt. Redcoins-Items erscheinen als eigene Zeile, falls vorhanden.

## Chatscanner (ab 1.13.0)

Schreibt **derselbe Spieler** denselben Text innerhalb von 15 Minuten erneut, bekommt die Zeile vorne ein goldenes **⟳2×** (⟳3× usw.). Maus drauf zeigt, seit wann die Nachricht wiederholt wird.

- Vergleich ohne Farbcodes, Groß-/Kleinschreibung und doppelte Leerzeichen; Spielername = letztes Wort vor „»“ / „:“ / „<Name>“ (Rang egal).
- Nachrichten unter 5 Zeichen und Server-Meldungen („OPSUCHT » …“) werden ignoriert.
- Config: `chatScanner`, `chatScannerMinutes` (15), `chatScannerMinLength` (5), `chatScannerSkipServer`. Befehl: `/opa chat an|aus`.


## Einstellungen & Befehle (ab 1.17.0)

Name: **OP-Assistent (by Jxlian0502)**. Einstellungen über **Mod Menu** (Knopf „Einstellungen“ beim OP-Assistenten) oder `/opa einstellungen`:
- Jobtracker-Pause: Schieberegler 1 s – 5 Min. plus Freitextfeld (`45s`, `1:30`, `2m`, bis 60 Min.).
- Jobtracker an/aus, Job-Actionbar an/aus, Chatscanner an/aus.
- „Jobtracker verschieben…“: Fenster per Drag & Drop platzieren, „Zurücksetzen“ = oben links.

Befehle: `/opa` (Status), `/opa liste [text]`, `/opa einstellungen`, `/opa job`, `/opa job toggle`, `/opa job reset`, `/opa job actionbar an|aus`, `/opa job zaehler [reset|<grenze>]`, `/opa chat an|aus`, `/opa aufnahme start|stop`, `/opa ordner`, `/marktinfo`.
Die Daten bleiben in `.minecraft\config\opsuchtprices\`.

## Automatische Updates

Die Mod prüft beim Start `dist/latest.json` in diesem Repository (https://github.com/Jxlian0502/OP-Assist) und lädt eine neuere `dist/op-assistent-<version>.jar` herunter (Prüfsumme SHA-256 + Mod-ID/Version werden kontrolliert). Installiert wird beim Beenden von Minecraft; beim nächsten Start läuft die neue Version. Abschalten: `"autoUpdate": false` in der config.json.

**Download der aktuellen Version:** [`dist/`](dist/) – die .jar in den mods-Ordner legen.
