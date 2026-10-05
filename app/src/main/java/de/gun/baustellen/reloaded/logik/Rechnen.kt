package de.gun.baustellen.reloaded.logik

import de.gun.baustellen.reloaded.daten.AppDaten
import de.gun.baustellen.reloaded.daten.Auftrag
import de.gun.baustellen.reloaded.daten.Kunde
import de.gun.baustellen.reloaded.daten.Material
import de.gun.baustellen.reloaded.daten.STATUS
import java.time.LocalDate

// ---------------- Beträge ----------------

data class Betraege(val netto: Double, val brutto: Double, val steuer: Double)

/** Netto/Brutto aus der Eingabe ableiten; der Betrag ist netto oder brutto eingegeben. */
fun betraege(betrag: Double, mwst: Double, basis: String): Betraege {
    val satz = mwst / 100.0
    return if (basis == "brutto") {
        val netto = betrag / (1 + satz)
        Betraege(netto, betrag, betrag - netto)
    } else {
        val brutto = betrag * (1 + satz)
        Betraege(betrag, brutto, brutto - betrag)
    }
}

fun Auftrag.betraege(): Betraege = betraege(betrag, mwst, basis)
fun Auftrag.netto(): Double = betraege().netto

// ---------------- Material ----------------

fun einheitText(e: String) = if (e == "m3") "m³" else "t"

data class MaterialSummen(
    val bedarfT: Double = 0.0, val bedarfM3: Double = 0.0,
    val verbrauchT: Double = 0.0, val verbrauchM3: Double = 0.0,
    val kostenBedarf: Double = 0.0, val kostenVerbrauch: Double = 0.0,
) {
    val differenzKosten get() = kostenVerbrauch - kostenBedarf
}

fun materialSummen(liste: List<Material>): MaterialSummen = liste.fold(MaterialSummen()) { s, m ->
    val kosten = m.menge * m.preis
    val m3 = m.einheit == "m3"
    if (m.art == "verbrauch") s.copy(
        verbrauchT = s.verbrauchT + if (m3) 0.0 else m.menge,
        verbrauchM3 = s.verbrauchM3 + if (m3) m.menge else 0.0,
        kostenVerbrauch = s.kostenVerbrauch + kosten,
    ) else s.copy(
        bedarfT = s.bedarfT + if (m3) 0.0 else m.menge,
        bedarfM3 = s.bedarfM3 + if (m3) m.menge else 0.0,
        kostenBedarf = s.kostenBedarf + kosten,
    )
}

/** Materialkosten eines Auftrags: der tatsächliche Verbrauch, ersatzweise der Bedarf. */
fun Auftrag.materialKosten(): Double = materialSummen(material).let { if (it.kostenVerbrauch != 0.0) it.kostenVerbrauch else it.kostenBedarf }

data class MaterialZeile(
    val bez: String, val einheit: String,
    val bedarf: Double, val verbrauch: Double, val kostenBedarf: Double, val kostenVerbrauch: Double,
)

/** Material über alle Aufträge, je Bezeichnung und Einheit zusammengefasst. */
fun materialGesamt(d: AppDaten): List<MaterialZeile> {
    val nach = LinkedHashMap<String, MaterialZeile>()
    d.auftraege.eintraege.forEach { a ->
        a.material.forEach { m ->
            val eh = if (m.einheit == "m3") "m3" else "t"
            val bez = m.bez.ifBlank { "—" }
            val k = bez.lowercase() + "|" + eh
            val z = nach[k] ?: MaterialZeile(bez, eh, 0.0, 0.0, 0.0, 0.0)
            val kosten = m.menge * m.preis
            nach[k] = if (m.art == "verbrauch") z.copy(verbrauch = z.verbrauch + m.menge, kostenVerbrauch = z.kostenVerbrauch + kosten)
            else z.copy(bedarf = z.bedarf + m.menge, kostenBedarf = z.kostenBedarf + kosten)
        }
    }
    return nach.values.sortedBy { it.bez.lowercase() }
}

fun materialTextliste(a: Auftrag, kundeName: String): String {
    val s = materialSummen(a.material)
    val z = mutableListOf<String>()
    z += "Materialliste — " + a.titel
    if (kundeName.isNotBlank()) z += "Kunde: $kundeName"
    z += "Zeitraum: " + zeitraumText(a)
    z += ""
    listOf("bedarf" to "BEDARF (geplant)", "verbrauch" to "VERBRAUCH (tatsächlich)").forEach { (art, titel) ->
        val teil = a.material.filter { (if (it.art == "verbrauch") "verbrauch" else "bedarf") == art }
        if (teil.isEmpty()) return@forEach
        z += titel
        teil.forEach { m ->
            val e = einheitText(m.einheit)
            z += "  ${m.bez}: ${zahl(m.menge)} $e x ${zahl(m.preis)} €/$e = ${zahl(m.menge * m.preis)} €" +
                (if (m.lieferant.isNotBlank()) "  (${m.lieferant})" else "")
        }
        z += ""
    }
    z += "SUMMEN"
    z += "  Bedarf:    ${zahl(s.bedarfT)} t / ${zahl(s.bedarfM3)} m³ = ${zahl(s.kostenBedarf)} €"
    z += "  Verbrauch: ${zahl(s.verbrauchT)} t / ${zahl(s.verbrauchM3)} m³ = ${zahl(s.kostenVerbrauch)} €"
    z += "  Differenz: ${zahl(s.verbrauchT - s.bedarfT)} t / ${zahl(s.verbrauchM3 - s.bedarfM3)} m³ = ${zahl(s.differenzKosten)} €"
    return z.joinToString("\n")
}

// ---------------- Aufträge und Kunden ----------------

fun AppDaten.kunde(id: String): Kunde? = if (id.isBlank()) null else kunden.eintraege.firstOrNull { it.id == id }
fun AppDaten.kundeName(id: String): String = kunde(id)?.name ?: ""
fun AppDaten.auftrag(id: String): Auftrag? = auftraege.eintraege.firstOrNull { it.id == id }

fun Auftrag.anschrift(): String =
    listOf(strasse.trim(), listOf(plz.trim(), ort.trim()).filter { it.isNotEmpty() }.joinToString(" "))
        .filter { it.isNotEmpty() }.joinToString(", ")

fun Kunde.anschrift(): String =
    listOf(strasse.trim(), listOf(plz.trim(), ort.trim()).filter { it.isNotEmpty() }.joinToString(" "))
        .filter { it.isNotEmpty() }.joinToString(", ")

fun Auftrag.beginn(): LocalDate? = parseIso(von)
fun Auftrag.ende(): LocalDate? = parseIso(bis) ?: parseIso(von)

fun Auftrag.statusText(): String = STATUS[status] ?: "Ausstehend"

fun Auftrag.laeuftAm(tag: LocalDate): Boolean {
    val s = beginn() ?: return false
    val e = ende() ?: s
    return !tag.isBefore(s) && !tag.isAfter(if (e.isBefore(s)) s else e)
}

fun auftraegeAnTag(d: AppDaten, tag: LocalDate): List<Auftrag> =
    d.auftraege.eintraege.filter { it.laeuftAm(tag) }.sortedWith(compareBy({ it.ganztags }, { it.zeitVon }, { it.titel }))

fun zeitraumText(a: Auftrag): String {
    val t = de(a.von) + (if (a.bis.isNotBlank() && a.bis != a.von) " – " + de(a.bis) else "")
    return t + if (!a.ganztags) " · ${a.zeitVon}–${a.zeitBis}" else ""
}

/** Netto-Arbeitszeit eines Regieberichts in Minuten (über Mitternacht möglich). */
fun regieMinuten(beginn: String, ende: String, pause: Double): Int {
    val b = zeitAus(beginn) ?: return 0
    val e = zeitAus(ende) ?: return 0
    var start = b.hour * 60 + b.minute
    var schluss = e.hour * 60 + e.minute
    if (schluss < start) schluss += 24 * 60
    return maxOf(0, schluss - start - pause.toInt())
}

fun stundenText(minuten: Double): String = zahl(minuten / 60.0, 1) + " Std."

// ---------------- Kalender-Termin aus einem Auftrag ----------------

fun terminTitel(a: Auftrag, kundeName: String) = a.titel + if (kundeName.isNotBlank()) " — $kundeName" else ""

fun terminBeschreibung(a: Auftrag, kundeName: String): String {
    val teile = mutableListOf<String>()
    if (kundeName.isNotBlank()) teile += "Kunde: $kundeName"
    if (a.nummer.isNotBlank()) teile += "Auftragsnummer: " + a.nummer
    if (a.betrag != 0.0) {
        val b = a.betraege()
        teile += "Auftragssumme: ${zahl(b.netto)} € netto / ${zahl(b.brutto)} € brutto"
    }
    if (a.notiz.isNotBlank()) teile += a.notiz
    return teile.joinToString("\n")
}

// ---------------- Statistik ----------------

fun statDatum(a: Auftrag, bezug: String): LocalDate? =
    if (bezug == "von") parseIso(a.von) ?: parseIso(a.bis) else parseIso(a.bis) ?: parseIso(a.von)

data class JahrWert(val anzahl: Int, val netto: Double, val material: Double)
data class KundeWert(val name: String, val anzahl: Int, val netto: Double)

data class Statistik(
    val jahr: Int,
    val auftraege: List<Auftrag>,
    val netto: Double,
    val brutto: Double,
    val material: Double,
    val nachStatus: Map<String, Double>,
    val monate: List<Double>,
    val monateAnzahl: List<Int>,
    val proJahr: List<Pair<Int, JahrWert>>,
    val proKunde: List<KundeWert>,
) {
    val anzahl get() = auftraege.size
    val schnitt get() = if (auftraege.isEmpty()) 0.0 else netto / auftraege.size
}

fun jahreListe(d: AppDaten, bezug: String): List<Int> =
    (d.auftraege.eintraege.mapNotNull { statDatum(it, bezug)?.year } + LocalDate.now().year).distinct().sortedDescending()

fun statistik(d: AppDaten, jahr: Int, bezug: String): Statistik {
    val imJahr = d.auftraege.eintraege.filter { statDatum(it, bezug)?.year == jahr }
    val monate = DoubleArray(12)
    val anzahl = IntArray(12)
    val status = linkedMapOf("ausstehend" to 0.0, "laufend" to 0.0, "abgeschlossen" to 0.0)
    var netto = 0.0
    var brutto = 0.0
    var material = 0.0
    imJahr.forEach { a ->
        val b = a.betraege()
        netto += b.netto; brutto += b.brutto
        material += a.materialKosten()
        status[a.status] = (status[a.status] ?: 0.0) + b.netto
        statDatum(a, bezug)?.let { monate[it.monthValue - 1] += b.netto; anzahl[it.monthValue - 1]++ }
    }
    val proJahr = d.auftraege.eintraege.groupBy { statDatum(it, bezug)?.year }
        .filterKeys { it != null }
        .map { (j, l) -> j!! to JahrWert(l.size, l.sumOf { it.netto() }, l.sumOf { it.materialKosten() }) }
        .sortedByDescending { it.first }
    val proKunde = imJahr.groupBy { d.kundeName(it.kundeId).ifBlank { "Ohne Kunde" } }
        .map { (n, l) -> KundeWert(n, l.size, l.sumOf { it.netto() }) }
        .sortedByDescending { it.netto }
    return Statistik(jahr, imJahr, netto, brutto, material, status, monate.toList(), anzahl.toList(), proJahr, proKunde)
}

fun statistikText(d: AppDaten, s: Statistik, bezug: String): String {
    val z = mutableListOf<String>()
    z += "Auswertung ${s.jahr} (" + (if (bezug == "von") "nach Beginn" else "nach Ende") + ")"
    z += ""
    z += "Aufträge: ${s.anzahl}"
    z += "Einnahmen netto: ${zahl(s.netto)} €"
    z += "Materialkosten:  ${zahl(s.material)} €"
    z += "Netto nach Material: ${zahl(s.netto - s.material)} €"
    z += ""
    z += "Monate:"
    s.monate.forEachIndexed { i, w -> z += "  ${MONATE[i]}: ${zahl(w)} €" }
    z += ""
    z += "Aufträge:"
    s.auftraege.sortedBy { statDatum(it, bezug) }.forEach { a ->
        val k = d.kundeName(a.kundeId)
        z += "  " + (statDatum(a, bezug)?.alsDE() ?: "—") + "  " + a.titel + (if (k.isNotBlank()) " ($k)" else "") +
            "  ${zahl(a.netto())} € netto  [${a.statusText()}]"
    }
    return z.joinToString("\n")
}
