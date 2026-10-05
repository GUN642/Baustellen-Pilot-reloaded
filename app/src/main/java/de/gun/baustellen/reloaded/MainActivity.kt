package de.gun.baustellen.reloaded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.gun.baustellen.reloaded.daten.Dateien
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.ui.Oberflaeche

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Speicher.init(this)
        setContent { Oberflaeche(this) }
        // Einmal je Start: Dateien ohne Verweis entfernen (nach Ablauf aller Rückgängig-Fristen)
        window.decorView.postDelayed({
            try { Dateien.verwaisteAufraeumen(this, Speicher.aktuell) } catch (e: Exception) { }
        }, 4000)
    }

    override fun onResume() {
        super.onResume()
        // Änderungen aus Outlook oder der Kalender-App bekommt die App nicht mitgeteilt
        Aktualisierung.geraetLaden(this)
    }

    override fun onPause() {
        super.onPause()
        Speicher.sofortSchreiben()
    }
}
