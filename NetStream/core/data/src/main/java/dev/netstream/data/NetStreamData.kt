package dev.netstream.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.netstream.domain.CatalogueRepository
import dev.netstream.domain.TelemetryRepository

/** Composition root for data sources; the rest of the app is unaware of Firebase types. */
class NetStreamData(context: Context, val cloudEnabled: Boolean) {
    val catalogue: CatalogueRepository
    val telemetry: TelemetryRepository

    init {
        val app = context.applicationContext
        val cloud =
            if (cloudEnabled) {
                checkNotNull(FirebaseApp.initializeApp(app)) {
                    "Missing Firebase options. Rebuild with app/google-services.json."
                }
                val gate = AuthGate(FirebaseAuth.getInstance())
                val db = FirebaseFirestore.getInstance()
                catalogue = FirestoreCatalogue(db, gate)
                CloudWriter(db, gate, FirebaseAnalytics.getInstance(app))
            } else {
                catalogue = BundledCatalogue(app)
                null
            }
        telemetry = TelemetryStore(app, cloud)
    }
}
