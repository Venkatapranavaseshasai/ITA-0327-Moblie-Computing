package dev.netstream.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.netstream.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * Media3 adapter for the independently tested rule state machine. We constrain the maximum height,
 * never force a representation that may not exist in this particular manifest.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class AdaptiveQualityEngine(
    private val selector: DefaultTrackSelector,
    private val policy: AdaptiveQualityPolicy = AdaptiveQualityPolicy(),
) {
    private val _status =
        MutableStateFlow(AdaptiveQualityPolicy.Decision(policy.currentCap, "Measuring context"))
    val status = _status.asStateFlow()

    init {
        applyCap(policy.currentCap)
    }

    fun bind(
        context: StateFlow<ContextState>,
        scope: CoroutineScope,
        onSwitch: (Quality, Quality, String, ContextState) -> Unit,
    ) =
        context
            .onEach { sample ->
                val old = policy.currentCap
                val decision = policy.evaluate(sample)
                _status.value = decision
                if (old != decision.cap) {
                    applyCap(decision.cap)
                    onSwitch(old, decision.cap, decision.reason, sample)
                }
            }
            .launchIn(scope)

    private fun applyCap(quality: Quality) {
        selector.parameters =
            selector
                .buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, quality.height)
                .setExceedVideoConstraintsIfNecessary(false)
                .build()
    }
}
