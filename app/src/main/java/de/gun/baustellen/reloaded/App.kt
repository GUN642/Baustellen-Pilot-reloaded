package de.gun.baustellen.reloaded

import android.app.Application
import android.content.Context
import de.gun.baustellen.reloaded.daten.Speicher
import de.gun.baustellen.reloaded.geraet.GeraeteKalender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Speicher.init(this)
    }
}

/** Hält die Gerätekalender auf dem aktuellen Stand. */
object Aktualisierung {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ladeJob: Job? = null

    /** Gerätekalender neu einlesen (bei Start und Rückkehr in die App). */
    fun geraetLaden(ctx: Context) {
        val app = ctx.applicationContext
        if (ladeJob?.isActive == true) return
        ladeJob = scope.launch { GeraeteKalender.einlesen(app) }
    }

    suspend fun geraetLadenJetzt(ctx: Context): Boolean = GeraeteKalender.einlesen(ctx.applicationContext)
}
