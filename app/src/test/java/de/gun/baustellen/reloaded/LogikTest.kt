package de.gun.baustellen.reloaded

import de.gun.baustellen.reloaded.daten.*
import de.gun.baustellen.reloaded.logik.*
import de.gun.baustellen.reloaded.netz.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogikTest {
    init { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin")) }

    @Test fun nettoBrutto() {
        val n = betraege(1000.0, 19.0, "netto")
        assertEquals(1190.0, n.brutto, 0.001); assertEquals(190.0, n.steuer, 0.001)
        val b = betraege(1190.0, 19.0, "brutto")
        assertEquals(1000.0, b.netto, 0.001); assertEquals(190.0, b.steuer, 0.001)
    }

    @Test fun material() {
        val l = listOf(
            Material("1", "Schotter 0/32", "bedarf", 10.0, "t", 20.0),
            Material("2", "Schotter 0/32", "verbrauch", 12.0, "t", 20.0),
            Material("3", "Mutterboden", "verbrauch", 5.0, "m3", 15.0),
        )
        val s = materialSummen(l)
        assertEquals(10.0, s.bedarfT, 0.0); assertEquals(12.0, s.verbrauchT, 0.0); assertEquals(5.0, s.verbrauchM3, 0.0)
        assertEquals(200.0, s.kostenBedarf, 0.001); assertEquals(315.0, s.kostenVerbrauch, 0.001)
        assertEquals(315.0, Auftrag(material = l).materialKosten(), 0.001)
        val g = materialGesamt(AppDaten(auftraege = Auftraege(listOf(Auftrag(id = "a", material = l), Auftrag(id = "b", material = l)))))
        assertEquals(2, g.size)
        assertEquals(24.0, g.first { it.bez == "Schotter 0/32" }.verbrauch, 0.0)
    }

    @Test fun regiezeit() {
        assertEquals(510, regieMinuten("07:00", "16:00", 30.0))
        assertEquals(420, regieMinuten("22:00", "06:00", 60.0))
        assertEquals("09:30", zeitNormieren("930"))
        assertEquals(1234.5, zahlLesen("1.234,5")!!, 0.0001)
        assertEquals(12.5, zahlLesen("12.5")!!, 0.0001)
    }

    /** Eine Sicherung im Format des alten Baustellen Pilot muss vollständig lesbar sein. */
    @Test fun alteSicherung() {
        val text = """
        {"app":"Baustellen Pilot","version":"1.7-beta","exportedAt":"2026-08-26T10:00:00.000Z",
         "kunden":{"eintraege":[{"id":"k1","name":"Muster GmbH","ansprech":"Herr Muster","telefon":"0170 1","email":"a@b.de"}]},
         "auftraege":{"eintraege":[{"id":"a1","titel":"Kellerausschachtung","nummer":"2026-014","kundeId":"k1","status":"laufend",
            "strasse":"Musterweg 12","plz":"89186","ort":"Illerrieden","von":"2026-08-24","bis":"2026-08-28","ganztags":false,
            "zeitVon":"07:00","zeitBis":"16:00","betrag":"1500","maschine":"CAT 320","mwst":19,"basis":"brutto","notiz":"",
            "material":[{"id":"m1","bez":"Schotter","art":"verbrauch","menge":"12,5","einheit":"t","preis":18,"datum":"2026-08-25","lieferant":"Kieswerk"}],
            "dateien":[{"id":"d1","name":"Angebot.pdf","typ":"application/pdf","groesse":1234,"kategorie":"Angebot","datum":"2026-08-20"}],
            "fotos":[{"id":"f1","name":"bild.jpg","groesse":2000,"datum":"2026-08-25","notiz":"Baugrube"}],
            "regieberichte":[{"id":"r1","datum":"2026-08-25","mitarbeiter":"Max","beginn":"07:00","ende":"16:00","pause":30,"nettoMinuten":510,
               "maschine":"CAT 320","maschinenstunden":8.5,"positionen":[{"beschreibung":"Aushub","menge":null,"einheit":"m³"},{"beschreibung":"Abfuhr","menge":24,"einheit":"t"}],
               "wetterBoden":"trocken","unterschriftName":"Herr Muster","notiz":"","unterschriftBild":""}],
            "eventId":"4711"}]},
         "einstellungen":{"mwst":19,"zielKalenderId":"3","autoKalender":true,"firma":{"name":"Bagger Müller"},"versteckteKalender":["5"],"kalenderFarben":{"3":"#ffb020"},"letzterUpdateCheck":1756000000000},
         "wetter":{"ort":{"name":"Ulm","breite":48.4,"laenge":9.99}},
         "tagesnotizen":{"2026-08-24":"Bagger abholen"},
         "dateien":[{"id":"f1","typ":"image/jpeg","daten":"AAEC"}]}
        """.trimIndent()
        val g = SicherungsFormat.lesen(text, AppDaten())
        val d = g.daten
        assertEquals(1, d.kunden.eintraege.size)
        val a = d.auftraege.eintraege.single()
        assertEquals("laufend", a.status); assertFalse(a.ganztags); assertEquals(1500.0, a.betrag, 0.0); assertEquals("brutto", a.basis)
        assertEquals(12.5, a.material.single().menge, 0.0)
        assertNull(a.regieberichte.single().positionen[0].menge)
        assertEquals(24.0, a.regieberichte.single().positionen[1].menge!!, 0.0)
        assertEquals("4711", a.eventId)
        assertEquals("Bagger Müller", d.einstellungen.firma.name)
        assertEquals(listOf("5"), d.einstellungen.versteckteKalender)
        assertEquals("Ulm", d.wetter.ort.name)
        assertEquals("Bagger abholen", d.tagesnotizen["2026-08-24"])
        assertEquals(1, g.dateien.size)
        assertArrayEquals(byteArrayOf(0, 1, 2), SicherungsFormat.bytes(g.dateien[0].daten))
        assertTrue(g.probleme.isEmpty())
    }

    /** Was Reloaded schreibt, muss die alte App unter denselben Schlüsseln wiederfinden. */
    @Test fun rundreise() {
        val d = AppDaten(
            kunden = Kunden(listOf(Kunde("k1", "Muster"))),
            auftraege = Auftraege(listOf(Auftrag("a1", "Baugrube", kundeId = "k1", von = "2026-09-01", bis = "2026-09-03", betrag = 2500.0))),
            tagesnotizen = mapOf("2026-09-01" to "Notiz"),
        )
        val kopf = SicherungsFormat.kopf(d, "2.0.0", "2026-09-01T00:00:00Z")
        assertEquals("Baustellen Pilot", kopf["app"]!!.jsonPrimitive.content)
        val auftrag = kopf["auftraege"]!!.jsonObject["eintraege"]!!.jsonArray[0].jsonObject
        listOf("id", "titel", "von", "bis", "ganztags", "betrag", "mwst", "basis", "material", "dateien", "fotos", "regieberichte", "eventId")
            .forEach { assertTrue("Schlüssel $it fehlt", auftrag.containsKey(it)) }
        val zurueck = SicherungsFormat.lesen(kopf.toString(), AppDaten()).daten
        assertEquals(d.auftraege, zurueck.auftraege)
        assertEquals(d.kunden, zurueck.kunden)
        assertEquals(d.tagesnotizen, zurueck.tagesnotizen)
    }

    /** Leere oder fehlende Werte (null) aus älteren Fassungen dürfen nichts verwerfen. */
    @Test fun nullWerte() {
        val text = """{"app":"Baustellen Pilot","kunden":{"eintraege":[{"id":"k1","name":"A","telefon":null}]},
            "auftraege":{"eintraege":[{"id":"a1","titel":"T","von":"2026-01-02","material":null,"fotos":null,"betrag":null,"mwst":"19,0"}]},
            "tagesnotizen":{"2026-01-02":null,"2026-01-03":"x"}}"""
        val g = SicherungsFormat.lesen(text, AppDaten())
        assertTrue(g.probleme.toString(), g.probleme.isEmpty())
        val a = g.daten.auftraege.eintraege.single()
        assertTrue(a.material.isEmpty()); assertEquals(0.0, a.betrag, 0.0); assertEquals(19.0, a.mwst, 0.0)
        assertEquals("x", g.daten.tagesnotizen["2026-01-03"])
        assertEquals(2, SicherungsFormat.kopf(g.daten, "2", "z")["tagesnotizen"]!!.jsonObject.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fremdeSicherung() {
        SicherungsFormat.lesen("""{"app":"Soldaten Dashboard","todos":{"eintraege":[]}}""", AppDaten())
    }

    @Test fun ics() {
        val a = Auftrag("a1", "Baugrube", von = "2026-09-01", bis = "2026-09-03", strasse = "Weg 1", ort = "Ulm")
        val t = Ics.dokument(listOf(a to "Muster"))
        assertTrue(t.contains("DTSTART;VALUE=DATE:20260901"))
        assertTrue(t.contains("DTEND;VALUE=DATE:20260904"))
        assertTrue(t.contains("SUMMARY:Baugrube — Muster"))
        assertTrue(t.contains("LOCATION:Weg 1\\, Ulm"))
        val m = a.copy(ganztags = false, zeitVon = "07:00", zeitBis = "16:00")
        assertTrue(Ics.dokument(listOf(m to "")).contains("DTEND;TZID=Europe/Berlin:20260903T160000"))
    }

    @Test fun statistikJahr() {
        val d = AppDaten(auftraege = Auftraege(listOf(
            Auftrag("1", "A", von = "2025-12-28", bis = "2026-01-05", betrag = 1000.0, status = "abgeschlossen"),
            Auftrag("2", "B", von = "2026-03-01", bis = "2026-03-02", betrag = 500.0),
        )))
        assertEquals(1500.0, statistik(d, 2026, "bis").netto, 0.001)
        assertEquals(500.0, statistik(d, 2026, "von").netto, 0.001)
        assertEquals(1000.0, statistik(d, 2026, "bis").monate[0], 0.001)
    }

    @Test fun kalenderEintraege() {
        val d = AppDaten(auftraege = Auftraege(listOf(Auftrag("1", "A", von = "2026-09-01", bis = "2026-09-03", eventId = "9"))))
        val geraet = listOf(
            de.gun.baustellen.reloaded.geraet.GeraetTermin(9, "1", "A — Kunde", LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 4, 0, 0), true, "", "", null, 0),
            de.gun.baustellen.reloaded.geraet.GeraetTermin(10, "1", "Zahnarzt", LocalDateTime.of(2026, 9, 2, 9, 0), LocalDateTime.of(2026, 9, 2, 10, 0), false, "", "", null, 0),
        )
        val l = kalenderEintraege(d, emptyList(), geraet, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(2, l.size) // der eigene Termin des Auftrags erscheint nicht doppelt
        assertEquals(LocalDate.of(2026, 9, 3), l.first { it.auftragId == "1" }.letzterTag)
        assertEquals(2, eintraegeAm(l, LocalDate.of(2026, 9, 2)).size)
    }

    @Test fun wetterArbeitszeit() {
        val tag = LocalDate.of(2026, 9, 1)
        val nah = (0 until 48).map { h -> WetterStunde(tag.atStartOfDay().plusHours(h.toLong()), h % 24 * 1.0, 3, if (h % 24 == 10) 40 else 0, 0.0, 5.0) }
        val weit = (0 until 7 * 8).map { i -> WetterStunde(tag.atStartOfDay().plusHours(i * 3L), 5.0, 61, null, 0.5, 10.0) }
        val alle = WetterDienst.stundenVereinen(nah, weit)
        assertEquals(48 + 56 - 16, alle.size)
        val w = fensterWerte(alle, tag)!!
        assertEquals(7, w.min); assertEquals(16, w.max); assertEquals("40%", w.regen)
        val tag3 = tag.plusDays(3)
        val s = arbeitsStunden(alle, tag3)
        assertEquals(listOf(7, 12, 16), s.map { it.stunde })
        assertTrue(baustellenWarnungen(listOf(WetterTag(tag, 0, 5.0, -2.0, 0, 0.0, null, null, 20.0))).first().startsWith("Frost"))
    }
}
