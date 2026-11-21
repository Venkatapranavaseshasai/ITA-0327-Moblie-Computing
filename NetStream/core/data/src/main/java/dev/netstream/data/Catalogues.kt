package dev.netstream.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.netstream.domain.*
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

internal class AuthGate(private val auth: FirebaseAuth) {
    private val lock = Mutex()

    suspend fun uid(): String =
        auth.currentUser?.uid
            ?: lock.withLock {
                auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid
            }
}

private fun https(value: String?): String? =
    value?.takeIf { url ->
        runCatching {
                URI(url).let {
                    it.scheme.equals("https", true) && it.host != null && it.userInfo == null
                }
            }
            .getOrDefault(false)
    }

private fun Map<String, Any?>.video(id: String): VideoItem {
    fun text(key: String) = (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)
    return VideoItem(
        id,
        requireNotNull(text("title")) { "$id: missing title" },
        requireNotNull(https(text("streamUrl"))) { "$id: streamUrl must be HTTPS" },
        StreamProtocol.valueOf(text("protocol")?.uppercase() ?: error("$id: missing protocol")),
        text("description").orEmpty(),
        text("genre") ?: "Film",
        text("creator") ?: "Independent",
        (this["year"] as? Number)?.toInt(),
        (this["durationSeconds"] as? Number)?.toInt()?.takeIf { it > 0 },
        text("posterKey"),
        https(text("posterUrl")),
    )
}

internal class BundledCatalogue(private val context: Context) : CatalogueRepository {
    override fun observeVideos() =
        flow {
                val array =
                    context.assets.open("catalogue.json").bufferedReader().use {
                        JSONArray(it.readText())
                    }
                emit(
                    (0 until array.length()).map { i ->
                        val obj = array.getJSONObject(i)
                        obj.keys()
                            .asSequence()
                            .associateWith { obj.get(it) }
                            .video(obj.getString("id"))
                    }
                )
            }
            .flowOn(Dispatchers.IO)
}

internal class FirestoreCatalogue(private val db: FirebaseFirestore, private val auth: AuthGate) :
    CatalogueRepository {
    override fun observeVideos(): Flow<List<VideoItem>> = callbackFlow {
        auth.uid() // Never query before authentication. A cached user can read the offline cache.
        val subscription =
            db.collection("videos").orderBy("sortOrder").addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error)
                    snapshot != null ->
                        runCatching {
                                snapshot.documents.map { doc -> doc.data.orEmpty().video(doc.id) }
                            }
                            .onSuccess { trySend(it) }
                            .onFailure { close(it) }
                }
            }
        awaitClose { subscription.remove() }
    }
}
