# Baustellen Pilot Reloaded

Der Baustellen Pilot – komplett neu als **native Android-App** (Kotlin, Jetpack Compose)
im Stil des **Soldaten Dashboard Reloaded**: Punktschrift, Punktraster, klare Linien,
Schwarz/Weiß mit einem Akzent.

Läuft **parallel** zum alten Baustellen Pilot (eigene App-ID `de.gun.baustellen.reloaded`).
Die Daten lassen sich über die Sicherungsdatei übernehmen – in beide Richtungen.

## Funktionen

| Reiter | Inhalt |
|---|---|
| **Woche** | Auftragslage (ausstehend, laufend, abgeschlossen, offen netto), Wochenansicht mit Aufträgen, Wetter der Arbeitszeit (7–16 Uhr) und Notiz je Tag, Wetter auf der Baustelle |
| **Aufträge** | Alle Aufträge nach Status gefiltert, Summen je Status, Netto/Brutto-Eingabe mit MwSt |
| **Kunden** | Stammdaten, Telefon und E-Mail antippbar, Aufträge und Umsatz je Kunde |
| **Material** | Bedarf und Verbrauch über alle Aufträge (t und m³), Differenz, Liste kopieren |
| **Kalender** | Monatsraster mit durchgehenden Balken, Wischen zwischen Monaten, Vollbild per Tipp auf den Monat, lange drücken = neuer Auftrag, Termine aus dem Gerätekalender |
| **Statistik** | Jahr wählbar, nach Auftragsende oder -beginn, Einnahmen je Monat, Jahresvergleich, Kunden im Jahr, Auswertung kopieren |

### Aufträge

In der Detailansicht eines Auftrags liegen:

* **Material** – jeder Posten als Bedarf (geplant) oder Verbrauch (tatsächlich), Menge in t oder m³, Preis je Einheit
* **Unterlagen** – Angebote, Rechnungen, Lieferscheine, Aufmaße, Pläne … öffnen und teilen
* **Baustellenfotos** – direkt mit der Kamera oder aus der Galerie, verkleinert auf 1600 px, mit Notiz, Zoom
* **Regieberichte** – Arbeitszeit, Mitarbeiter, Maschine mit Betriebsstunden, Leistungen, Wetter/Boden,
  **Unterschrift des Auftraggebers** auf dem Gerät und **PDF** zum Teilen (z. B. per Mail) – komplett ohne Internet
* **Kalender** – Zeitraum in den Gerätekalender, als `.ics` oder als Google-Kalender-Vorlage
* **Navigation** zur Baustelle und **Wetter für diesen Ort**

### Kalender

Die App schreibt **direkt in den Kalender des Geräts**. Liegt dort ein Google- oder
Outlook-Konto, überträgt Android die Auftragszeiträume selbst in die Cloud. Änderungen am
Zeitraum werden nachgeführt, Löschen entfernt den Termin. Einstellungen im Kalender unter ⚙:
Zielkalender, automatische Übertragung, sichtbare Gerätekalender und deren Farben.

### Wetter

Open-Meteo ohne Konto: ICON-D2 des DWD für die ersten beiden Tage, ECMWF für die Woche.
Nächste zwölf Stunden, sieben Tage mit 7, 12 und 16 Uhr, Hinweise bei Frost, ergiebigem
Regen und Sturmböen. Ort suchen oder aktuellen Standort übernehmen.

### Designs

Im Menü unter **Design**: Nothing (AMOLED-Schwarz), Nothing hell, Graphit, Aulumu, System –
dazu sieben Akzentfarben (Standard: Amber) sowie Punktschrift, Punktraster und Reiterleiste
unten zum Ein- und Ausschalten.

## Daten aus dem alten Baustellen Pilot übernehmen

1. Im **alten** Baustellen Pilot: ☰ → Sicherung → **Export mit Dateien** (oder Export .json)
2. Datei auf dem Handy ablegen (oder in Google Drive)
3. In **Reloaded**: Menü → Sicherung → **Importieren** → Datei wählen

Übernommen werden Aufträge mit Material, Unterlagen, Fotos und Regieberichten, Kunden,
Einstellungen (MwSt, Firma, Kalender), Wetterort und Tagesnotizen. Eine in Reloaded erstellte
Sicherung lässt sich umgekehrt auch im alten Baustellen Pilot einlesen.

## Speicherung

Wie im Soldaten Dashboard Reloaded: Der gesamte Bestand liegt als `files/daten.json` auf dem
Gerät und wird bei jeder Änderung atomar geschrieben. Unterlagen und Fotos liegen unter
`files/dateien`. Android sichert beides zusätzlich selbsttätig ins Google-Konto. Die App
erinnert alle sieben Tage an eine eigene Sicherung.

## APK bauen und installieren

Der Build läuft über **GitHub Actions** (`.github/workflows/build-apk.yml`), bei jedem Push:

* Unter **Actions** den Lauf öffnen → unten unter **Artifacts** liegt
  `BaustellenPilotReloaded-APK` (ZIP mit der APK).
* Auf dem Standard-Branch entsteht zusätzlich ein **Release** mit der APK – daraus liest
  die App ihre Update-Prüfung und installiert neue Versionen direkt.

Installation: APK auf das Handy, antippen, Installation aus dieser Quelle erlauben.

### Signierschlüssel

`keystore/baustellen.keystore` ist ein fester Schlüssel für den Eigengebrauch. Dadurch lässt
sich jede neue Version über die vorhandene installieren, ohne Datenverlust.
**Nicht ersetzen**, sonst verlangt das nächste Update eine Deinstallation.

### Version erhöhen

In `app/build.gradle.kts` `appVersionName` und `appVersionCode` anheben und in
`ui/seiten/Ebenen.kt` den Changelog ergänzen.

## Aufbau

```
app/src/main/java/de/gun/baustellen/reloaded/
  daten/   Datenmodell (kompatibel zur alten Sicherung), Speicher, Dateien/Fotos, Sicherung
  logik/   Datum, Beträge, Material, Statistik, Kalendereinträge, ICS
  geraet/  Gerätekalender (CalendarContract), Abgleich der Aufträge, Standort
  netz/    Wetter (Open-Meteo), Update-Prüfung und -Download
  pdf/     Regiebericht als PDF
  ui/      Design (Themen, Schriften, Bausteine) und alle Seiten
```

Alle Daten liegen ausschließlich auf dem Gerät. Es gibt keinen Server.
Schriften: Doto, JetBrains Mono, Space Grotesk (SIL Open Font License).
