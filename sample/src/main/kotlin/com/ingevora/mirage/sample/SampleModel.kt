// Mirrors Distribution/Example/MirageExample/ExampleModel.swift (iOS): the client and both players, made once the
// key is typed; every SDK outcome in words.
package com.ingevora.mirage.sample

import android.content.Context
import com.ingevora.mirage.MirageCancellable
import com.ingevora.mirage.MirageClient
import com.ingevora.mirage.MirageEffectPlayer
import com.ingevora.mirage.MirageError
import com.ingevora.mirage.MirageLensFailure
import com.ingevora.mirage.MirageLensPlayer
import com.ingevora.mirage.MiragePlayback
import com.ingevora.mirage.MirageResult

/**
 * The client (holding the key in memory only) and the two players. Main thread only, like the players.
 * [onMessage] gets a sentence to show, or null to clear it.
 */
internal class SampleModel(context: Context, apiKey: String) : AutoCloseable {
    val mirage = MirageClient(context, MirageClient.Configuration(apiKey = apiKey))
    val effects = MirageEffectPlayer(mirage)
    val lens = MirageLensPlayer(mirage)
    var onMessage: ((String?) -> Unit)? = null
    private var cueCall: MirageCancellable? = null

    init {
        effects.onEnd = { playback ->
            val ending = (playback.state as? MiragePlayback.State.Ended)?.ending
            if (ending is MiragePlayback.Ending.Skipped) onMessage?.invoke(describe(ending.reason))
        }
    }

    /** Attaches a face lens: the stored version at once, else after its download. */
    fun show(lensId: String) {
        val id = clean(lensId)
        if (id.isEmpty()) {
            onMessage?.invoke("Type a face lens ID.")
            return
        }
        onMessage?.invoke(null)
        lens.attach(id)
    }

    /** Plays a screen effect: `cue` downloads it first if needed, then the cue plays that version. */
    fun play(effectId: String) {
        val id = clean(effectId)
        if (id.isEmpty()) {
            onMessage?.invoke("Type a screen effect ID.")
            return
        }
        onMessage?.invoke("Getting the effect ready…")
        cueCall?.cancel()
        cueCall = effects.cue(id) { result ->
            cueCall = null
            when (result) {
                is MirageResult.Success -> {
                    onMessage?.invoke(null)
                    effects.play(result.value)
                }
                is MirageResult.Failure -> onMessage?.invoke(describe(result.error))
            }
        }
    }

    override fun close() {
        cueCall?.cancel()
        cueCall = null
        lens.close()
        effects.close()
    }

    companion object {
        /** A key or an ID as pasted, without the spaces and line breaks around it. */
        fun clean(text: String): String = text.trim()

        /** Later SDK versions may add cases to the SDK's error and state types: each `when` keeps an `else`. */
        fun describe(error: MirageError): String = when (error) {
            MirageError.InvalidAPIKey -> "The API key is wrong or revoked. Check it on the editor's Developers page."
            MirageError.ProductDisabled -> "This product is switched off for the workspace."
            MirageError.NotFound -> "No published item has this ID for this key."
            MirageError.Unavailable -> "The Mirage API cannot be reached. Check the connection and try again."
            is MirageError.EngineTooOld -> "This item needs a newer SDK (engine ${error.required})."
            is MirageError.UnsupportedFormat -> "This item's format (${error.version}) is newer than this SDK reads."
            is MirageError.InvalidPackage -> "The download was damaged (${error.issues.size} problems)."
            else -> "Something went wrong."
        }

        fun describe(state: MirageLensPlayer.State): String = when (state) {
            MirageLensPlayer.State.Idle -> "No lens."
            MirageLensPlayer.State.Loading -> "Loading the lens…"
            is MirageLensPlayer.State.Running ->
                if (state.info.isDraft) {
                    "Showing a draft of ${state.info.lensId}."
                } else {
                    "Showing ${state.info.lensId}, version ${state.info.version}."
                }
            is MirageLensPlayer.State.Failed -> describe(state.failure)
            is MirageLensPlayer.State.Dropped -> "The lens was too slow on this phone, so it stopped."
            else -> "The lens is in a state this sample does not know."
        }

        fun describe(failure: MirageLensFailure): String = when (failure) {
            is MirageLensFailure.Load -> describe(failure.error)
            MirageLensFailure.TestLinkExpired -> "The test link has expired."
            is MirageLensFailure.UnsupportedFrame -> "The camera's frames are in a format the SDK does not draw (${failure.pixelFormat})."
            MirageLensFailure.GraphicsUnavailable -> "This phone has no OpenGL ES 3.0, so lenses cannot draw."
            else -> "The lens failed."
        }

        fun describe(reason: MiragePlayback.SkipReason): String = when (reason) {
            MiragePlayback.SkipReason.NotReady -> "The effect was not downloaded yet; play it again."
            is MiragePlayback.SkipReason.EngineTooOld -> "This effect needs a newer SDK (engine ${reason.required})."
            MiragePlayback.SkipReason.Failed -> "The effect could not play."
            MiragePlayback.SkipReason.TestLinkExpired -> "The test link has expired."
            MiragePlayback.SkipReason.TestLinkInvalid -> "That is not a screen-effect test link."
            else -> "The effect was skipped."
        }
    }
}
