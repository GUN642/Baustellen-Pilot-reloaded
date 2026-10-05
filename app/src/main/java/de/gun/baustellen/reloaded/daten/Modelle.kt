package de.gun.baustellen.reloaded.daten

import kotlinx.serialization.Serializable

/*
 * Datenmodell. Aufbau und Feldnamen entsprechen bewusst genau der
 * Sicherungsdatei des alten Baustellen Pilot – so lässt sich eine dort
 * exportierte .json unverändert einlesen und umgekehrt. Datumsangaben
 * stehen wie dort als JJJJ-MM-TT, Uhrzeiten als HH:MM.
 */

typealias FText = @Serializable(with = FlexText::class) String
typealias FZahl = @Serializable(with = FlexZahl::class) Double
typealias FZahlN = @Serializable(with = FlexZahlOderNull::class) Double?
typealias FBool = @Serializable(with = FlexBool::class) Boolean

// ---------------- Kunden ----------------

@Serializable
data class Kunde(
    val id: FText = "",
    val name: FText = "",
    val ansprech: FText = "",
    val strasse: FText = "",
    val plz: FText = "",
    val ort: FText = "",
    val telefon: FText = "",
    val email: FText = "",
    val notiz: FText = "",
)

@Serializable
data class Kunden(val eintraege: List<Kunde> = emptyList())

// ---------------- Aufträge ----------------

/** Materialposten: Bedarf (geplant) oder Verbrauch (tatsächlich), in t oder m³. */
@Serializable
data class Material(
    val id: FText = "",
    val bez: FText = "",
    val art: FText = "bedarf",
    val menge: FZahl = 0.0,
    val einheit: FText = "t",
    val preis: FZahl = 0.0,
    val datum: FText = "",
    val lieferant: FText = "",
)

/** Angebot, Rechnung, Lieferschein … Der Inhalt liegt unter files/dateien/<id>. */
@Serializable
data class DateiInfo(
    val id: FText = "",
    val name: FText = "",
    val typ: FText = "",
    val groesse: FZahl = 0.0,
    val kategorie: FText = "Unterlage",
    val datum: FText = "",
)

/** Baustellenfoto. Der Inhalt liegt unter files/dateien/<id> (JPEG). */
@Serializable
data class Foto(
    val id: FText = "",
    val name: FText = "",
    val groesse: FZahl = 0.0,
    val datum: FText = "",
    val notiz: FText = "",
)

@Serializable
data class Position(
    val beschreibung: FText = "",
    val menge: FZahlN = null,
    val einheit: FText = "",
)

@Serializable
data class Regiebericht(
    val id: FText = "",
    val datum: FText = "",
    val mitarbeiter: FText = "",
    val beginn: FText = "07:00",
    val ende: FText = "16:00",
    val pause: FZahl = 30.0,
    val nettoMinuten: FZahl = 0.0,
    val maschine: FText = "",
    val maschinenstunden: FZahl = 0.0,
    val positionen: List<Position> = emptyList(),
    val wetterBoden: FText = "",
    val unterschriftName: FText = "",
    val notiz: FText = "",
    /** Unterschrift als data:image/png;base64,… (wie im alten Baustellen Pilot). */
    val unterschriftBild: FText = "",
)

@Serializable
data class Auftrag(
    val id: FText = "",
    val titel: FText = "",
    val nummer: FText = "",
    val kundeId: FText = "",
    val status: FText = "ausstehend",
    val strasse: FText = "",
    val plz: FText = "",
    val ort: FText = "",
    val von: FText = "",
    val bis: FText = "",
    val ganztags: FBool = true,
    val zeitVon: FText = "07:00",
    val zeitBis: FText = "16:00",
    val betrag: FZahl = 0.0,
    val maschine: FText = "",
    val mwst: FZahl = 19.0,
    /** "netto" oder "brutto": wie der Betrag eingegeben wurde. */
    val basis: FText = "netto",
    val notiz: FText = "",
    val material: List<Material> = emptyList(),
    val dateien: List<DateiInfo> = emptyList(),
    val fotos: List<Foto> = emptyList(),
    val regieberichte: List<Regiebericht> = emptyList(),
    /** Termin im Gerätekalender (leer = nicht eingetragen). */
    val eventId: FText = "",
)

@Serializable
data class Auftraege(val eintraege: List<Auftrag> = emptyList())

// ---------------- Einstellungen ----------------

@Serializable
data class Firma(
    val name: FText = "",
    val strasse: FText = "",
    val plz: FText = "",
    val ort: FText = "",
    val telefon: FText = "",
    val email: FText = "",
)

@Serializable
data class Einstellungen(
    val mwst: FZahl = 19.0,
    val zielKalenderId: FText = "",
    val autoKalender: FBool = true,
    val firma: Firma = Firma(),
    val versteckteKalender: List<String> = emptyList(),
    val kalenderFarben: Map<String, String> = emptyMap(),
)

@Serializable
data class WetterOrt(
    val name: FText = "Berlin",
    val breite: FZahl = 52.52,
    val laenge: FZahl = 13.405,
)

@Serializable
data class WetterEinstellung(val ort: WetterOrt = WetterOrt())

// ---------------- Nur in Reloaded ----------------

/** Darstellung: Thema, Akzentfarbe, Punktschrift. */
@Serializable
data class Design(
    val thema: String = "nothing",
    val akzent: String = "amber",
    val punktSchrift: Boolean = true,
    val punktRaster: Boolean = true,
    /** Reiterleiste unten statt oben. */
    val reiterUnten: Boolean = false,
)

@Serializable
data class SicherungsStand(
    /** Datum der letzten eigenen Sicherung (JJJJ-MM-TT). */
    val letzte: String = "",
    val popupZuletzt: Long = 0,
    /** Beim Exportieren Dateien und Fotos mitnehmen. */
    val mitDateien: Boolean = true,
)

@Serializable
data class UpdateEinstellungen(
    val repo: String = "GUN642/Baustellen-Pilot-reloaded",
    val uebersprungen: String = "",
)

/** Gesamter Datenbestand der App, als eine Datei gespeichert. */
@Serializable
data class AppDaten(
    val kunden: Kunden = Kunden(),
    val auftraege: Auftraege = Auftraege(),
    val einstellungen: Einstellungen = Einstellungen(),
    val wetter: WetterEinstellung = WetterEinstellung(),
    /** Notiz je Tag der Wochenansicht, Schlüssel JJJJ-MM-TT. */
    val tagesnotizen: Map<String, String> = emptyMap(),
    val design: Design = Design(),
    val sicherung: SicherungsStand = SicherungsStand(),
    val update: UpdateEinstellungen = UpdateEinstellungen(),
    /** Statistik nach Auftragsende ("bis") oder Auftragsbeginn ("von"). */
    val statBezug: String = "bis",
)

val STATUS = linkedMapOf("ausstehend" to "Ausstehend", "laufend" to "Laufend", "abgeschlossen" to "Abgeschlossen")
