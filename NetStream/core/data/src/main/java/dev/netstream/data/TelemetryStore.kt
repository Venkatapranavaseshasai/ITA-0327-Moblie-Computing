package dev.netstream.data

import android.content.Context
import android.os.Bundle
import android.util.AtomicFile
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dev.netstream.domain.*
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private fun QualitySwitchLog.fields(): Map<String, Any?> =
    mapOf(
        "eventId" to eventId,
        "sessionId" to sessionId,
        "videoId" to videoId,
        "timestampMs" to timestampMs,
        "kind" to kind.name,
        "oldResolution" to (oldWidth?.let { "${it}x$oldHeight" } ?: "${oldHeight}p"),
        "newResolution" to (newWidth?.let { "${it}x$newHeight" } ?: "${newHeight}p"),
        "oldHeight" to oldHeight,
        "newHeight" to newHeight,
        "oldWidth" to oldWidth,
        "newWidth" to newWidth,
        "triggerReason" to triggerReason,
        "activeCapHeight" to activeCapHeight,
        "bandwidthBps" to bandwidthBps,
        "bufferedDurationMs" to bufferedDurationMs,
        "batteryPercent" to batteryPercent,
        "isCharging" to isCharging,
        "isStalling" to isStalling,
    )

private fun SessionSummary.fields(): Map<String, Any?> =
    mapOf(
        "sessionId" to sessionId,
        "videoId" to videoId,
        "protocol" to protocol.name,
        "startedAtMs" to startedAtMs,
        "updatedAtMs" to updatedAtMs,
        "endedAtMs" to endedAtMs,
        "sessionDurationMs" to sessionDurationMs,
        "totalStallCount" to totalStallCount,
        "totalStallDurationMs" to totalStallDurationMs,
        "capSwitchCount" to capSwitchCount,
        "outputSwitchCount" to outputSwitchCount,
        "endReason" to endReason,
        "status" to if (endedAtMs == null) "active" else "finished",
    )

/**
 * All writes are submitted in order. Firestore queues offline writes on disk; we never block
 * playback (or other submissions) waiting for a server acknowledgement.
 */
internal class CloudWriter(
    private val db: FirebaseFirestore,
    private val auth: AuthGate,
    private val analytics: FirebaseAnalytics,
) {
    suspend fun write(
        sessionId: String,
        event: QualitySwitchLog?,
        fields: Map<String, Any?>,
        onError: (Throwable) -> Unit,
    ) {
        val uid = auth.uid()
        val session =
            db.collection("users").document(uid).collection("sessions").document(sessionId)
        val doc = event?.let { session.collection("switches").document(it.eventId) } ?: session
        doc.set(fields + ("serverReceivedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener { onError(it) }
        if (event != null) {
            analytics.logEvent(
                "ns_quality_switch",
                Bundle().apply {
                    putString("switch_kind", event.kind.name)
                    putString("video_id", event.videoId.take(100))
                    putLong("from_height", event.oldHeight.toLong())
                    putLong("to_height", event.newHeight.toLong())
                    putString("trigger_reason", event.triggerReason)
                    putLong("buffer_ms", event.bufferedDurationMs)
                    event.bandwidthBps?.let { putDouble("bandwidth_mbps", it / 1_000_000.0) }
                    event.batteryPercent?.let { putLong("battery_pct", it.toLong()) }
                    putLong("charging", if (event.isCharging) 1 else 0)
                },
            )
        } else if (fields["endedAtMs"] != null) {
            analytics.logEvent(
                "ns_session_end",
                Bundle().apply {
                    putString("video_id", (fields["videoId"] as String).take(100))
                    putLong("stall_count", (fields["totalStallCount"] as Number).toLong())
                    putLong("stall_ms", fields["totalStallDurationMs"] as Long)
                    putLong("duration_ms", fields["sessionDurationMs"] as Long)
                    putString("end_reason", fields["endReason"] as? String)
                },
            )
        }
    }
}

/**
 * An ordered, app-lifetime writer. The local mirror is useful for both demo QA and cloud failures.
 * Atomic summary replacement prevents truncated JSON after a process interruption.
 */
internal class TelemetryStore(context: Context, private val cloud: CloudWriter?) :
    TelemetryRepository {
    private val base = File(context.filesDir, "qoe")
    private val local = Executors.newSingleThreadExecutor()
    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Write(
        val sessionId: String,
        val event: QualitySwitchLog?,
        val fields: Map<String, Any?>,
    )

    private val queue =
        kotlinx.coroutines.channels.Channel<Write>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val _warning = MutableStateFlow<String?>(null)
    override val warning = _warning.asStateFlow()

    init {
        if (cloud != null)
            cloudScope.launch {
                for (write in queue) {
                    try {
                        cloud.write(write.sessionId, write.event, write.fields) {
                            fail("Cloud logging", it)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        fail("Cloud logging", e)
                    }
                }
            }
        // Retain private local diagnostics for seven days. No external-storage permission.
        local.execute {
            base
                .listFiles()
                ?.filter { System.currentTimeMillis() - it.lastModified() > 7L * 86_400_000 }
                ?.forEach { it.deleteRecursively() }
        }
    }

    override fun recordSwitch(event: QualitySwitchLog) =
        submit(Write(event.sessionId, event, event.fields()))

    override fun saveSession(session: SessionSummary) =
        submit(Write(session.sessionId, null, session.fields()))

    private fun submit(write: Write) {
        local.execute {
            runCatching {
                    val dir = File(base, write.sessionId).apply { mkdirs() }
                    val json = JSONObject(write.fields).toString()
                    if (write.event != null) File(dir, "switches.jsonl").appendText("$json\n")
                    else {
                        val target = File(dir, "session.json")
                        val tmp = File(dir, "session.json.tmp")
                        tmp.writeText(json)
                        if (target.exists()) target.delete()
                        if (!tmp.renameTo(target)) {
                            target.writeText(json)
                            tmp.delete()
                        }
                    }
                }
                .onFailure { fail("Local logging", it) }
        }
        if (cloud != null) queue.trySend(write)
    }

    private fun fail(label: String, error: Throwable) {
        Log.w("NetStream", label, error)
        _warning.value = "$label failed: ${error.localizedMessage ?: "unknown error"}"
    }
}
