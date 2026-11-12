# IngevoraMirage for Android

On-device lens engine: face lenses drawn into camera frames, and screen effects drawn above your app's UI.
Lenses and effects are designed in the IngevoraMirage editor and downloaded as data, so new ones reach your app
without an app release.

This repository holds the SDK as a Maven repository (an AAR, served by GitHub Pages) and a sample app.
Version **0.4.0**.

## Requirements

- Android 7.1.1 (API 25) or later. The Mirage API's certificates chain to ISRG Root X1, which Android 7.0 does
  not trust.
- If your app is written in Kotlin: Kotlin 2.3 or later (the SDK is built with Kotlin 2.4.20, and a Kotlin
  compiler reads the metadata of at most the next version).
- The sample builds with the Android Gradle Plugin 9.4.1 and Gradle 9.8.0 (the wrapper is included), on Android
  Studio's JDK.
- An IngevoraMirage workspace and its API key: sign up at https://ingevoramirage.ingevora.com, then find the key on
  your workspace's **Developers** page.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://ingevora.github.io/mirage-android/maven") { content { includeGroup("com.ingevora") } }
    }
}

// app/build.gradle.kts
dependencies { implementation("com.ingevora:mirage:0.4.0") }
```

No account or token is needed. The SDK's manifest brings `android.permission.INTERNET` into your app; nothing else
to declare.

**What it adds to your app.** Face tracking uses Google's ML Kit face detection with its bundled model
(`com.google.mlkit:face-detection` 16.1.7), the SDK's one third-party dependency. It needs no Google Play services
on the device. Uncompressed, per ABI:

| ABI | ML Kit's native library |
|---|---|
| arm64-v8a | 8.1 MiB |
| armeabi-v7a | 5.1 MiB |
| x86 | 9.2 MiB |
| x86_64 | 9.2 MiB |

plus 4.0 MiB of models for every ABI. An App Bundle ships each phone its own ABI only. ML Kit's own manifest adds
`ACCESS_NETWORK_STATE`, ML Kit's start-up provider and a job service.

**R8:** nothing to add. The AAR is a release build that is not minified: your app's R8 shrinks it with your code,
your crash reports show readable SDK frames, and the AAR carries the consumer rules it needs.

## Usage

```kotlin
// One client for the app, with your workspace's API key. Any thread; keep it (for example in your Application).
val mirage = MirageClient(applicationContext, MirageClient.Configuration(apiKey = "mk_…"))

// Prefetch what the app will show, so it starts without waiting. onResult runs on the main thread, once.
val call: MirageCancellable = mirage.prefetchEffect("7c1e9a2b-4d3f-4a6e-8b5c-2f9d0e1a3b4c") { result ->
    when (result) {
        is MirageResult.Success -> Unit // result.value: id, version, durationMs, source
        is MirageResult.Failure -> when (result.error) {
            MirageError.InvalidAPIKey -> showKeyHelp()
            MirageError.NotFound -> showMissing()
            else -> showRetry() // later versions may add cases: keep an else branch
        }
    }
}
```

- **Threads:** `prefetchLens`, `prefetchEffect` and `clearCache` may be called from any thread; results come back on
  the main thread. `call.cancel()` stops the work, and a cancelled call never calls back.
- **No coroutines in the SDK:** it depends on no coroutine library. A coroutine app wraps a call in
  `suspendCancellableCoroutine` and wires its cancellation to `cancel()`.
- **Fallbacks:** when the newest version cannot be used (offline, a damaged download), the newest version already on
  the device is used instead.
- **Types may grow:** a later version may add cases to `MirageError`, `MirageLensFailure`, `MirageLensPlayer.State`,
  `MiragePlayback.State`, `MiragePlayback.Ending`, `MiragePlayback.SkipReason`, `MirageItemInfo.Source` and
  `MirageEffectPlayer.Settings.Motion`. Kotlin requires a `when` over one of them to be exhaustive, so a `when`
  without `else` would stop compiling when a case is added: keep an `else ->` branch.

### Screen effects

```kotlin
// One player per screen that shows effects; keep it where it outlives the views (a ViewModel). Main thread.
val player = MirageEffectPlayer(mirage)

// A full-size overlay above the activity's content. It passes every touch through.
val overlay = MirageEffectOverlay.show(this, player)        // overlay.remove() takes it away
// In your own layout: addView(MirageEffectView(context, player)) above the content.

player.play("7c1e9a2b-4d3f-4a6e-8b5c-2f9d0e1a3b4c")         // plays at once what is stored
player.fire("celebrate")                                     // a named event, for every running effect
player.onEnd = { playback ->
    if (playback.state == MiragePlayback.State.Ended(MiragePlayback.Ending.Finished)) {
        // the effect finished
    }
}

// Several phones: the sender makes a cue, your app carries its JSON, every phone plays it.
player.cue("7c1e9a2b-4d3f-4a6e-8b5c-2f9d0e1a3b4c") { result ->
    if (result is MirageResult.Success) send(result.value.toJson())
}
MirageEffectCue.fromJson(received)?.let { player.play(it) }

player.close()                                               // when the screen is done
```

- Use the player and its views on the main thread (checked); it draws on a thread of its own.
- A play never waits: an effect not downloaded yet ends `Skipped(NotReady)` and its download starts, so a later
  play works. `cue` downloads first.
- At most 4 effects play at once. The system's "Remove animations" setting is honoured.

### Face lenses

```kotlin
// One player per camera session; keep it, and close() it when done. Main thread.
val lens = MirageLensPlayer(mirage)
lens.onStateChange = { state -> showState(state) }             // main thread
lens.attach("3f2b8c1e-6d4a-4f7e-9b2c-8a1d5e6f7a90")           // stored at once, else downloaded

// The self-view: mirrored, filling the view.
container.addView(MirageLensPreviewView(context, lens))
```

Feed it from CameraX's `ImageAnalysis` (the SDK does not depend on CameraX):

```kotlin
val analysis = ImageAnalysis.Builder()
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
analysis.setAnalyzer(cameraExecutor) { image ->
    val planes = image.planes
    val frame = MirageFrame.yuv(
        image.width, image.height,
        MirageFrame.Plane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride),
        MirageFrame.Plane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride),
        MirageFrame.Plane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride),
    )
    val drawn = lens.process(
        frame,
        image.imageInfo.timestamp,
        MirageFrame.Orientation.fromRotationDegrees(image.imageInfo.rotationDegrees),
    )
    send(drawn)     // read it before closing it
    drawn.close()   // an SDK-owned frame goes back to the pool; on your own frame it does nothing
    image.close()   // only after process has returned
}
```

(`MirageFrame.of(image)` takes an `android.media.Image` directly, such as CameraX's `imageProxy.image`.)

- `attach`, `detach`, `fire`, `state` and `close` on the main thread; `process` on one thread at a time, never the
  main thread.
- Frames: `YUV_420_888` (I420, NV12, NV21, padded rows) or `RGBA_8888`, with the orientation that turns them
  upright. The drawn frame has the same size, format, colour and orientation. Sent frames are never mirrored.
- The SDK finds the face with ML Kit. If your app tracks faces already, pass yours
  (`process(frame, timeNanos, orientation, face)` with a `MirageFace`) so detection never runs twice.
- A lens never costs your app a frame: with no lens, on any failure, or when the phone is too slow for it, the
  frame passes through untouched, and `state` says why.

## Drafts

Test links work only in your app's **debug** builds: `lens.attachTestLink(link)` and `player.playTestLink(link)`
show a draft from the editor, with a "TEST — draft" badge. A release build refuses them without a network call.
Otherwise, try a draft on the editor's phone test page: its QR code opens the draft on your phone's camera, in the
browser.

## Privacy and Google Play's Data safety form

- **The SDK itself collects no data.** Frames never leave the device. Its only network calls go to the Mirage API,
  with your API key and the item IDs your app asks for, and to the downloads of those items' files.
- **ML Kit's bundled face detection sends diagnostics to Google.** Google's
  [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) lists device
  information, application information, per-installation identifiers that Google says are not meant to identify a
  user or a device, performance metrics, the API configuration, input and output sizes, the feature version, event
  types and error codes. Google says it uses them for diagnostics and usage analytics, encrypts them in transit and
  does not share them with third parties.
- **You answer your app's Data safety form.** Read Google's page for ML Kit's part; Google holds the app developer
  responsible for the answers. This README gives no answers for your app.

## The sample app

`sample/` shows the whole integration in three short files:

- `SampleModel.kt` makes the client and both players once you type the API key; the key stays in memory and is
  never saved. It turns every error and state into a sentence, each `when` with its `else ->`.
- `SampleCamera.kt` runs CameraX's front camera at about 720p and hands every frame to `process` on its analysis
  thread.
- `SampleActivity.kt` shows the self-view (`MirageLensPreviewView`), plays effects in a `MirageEffectView` above
  everything, and has the fields for a face lens ID and a screen effect ID.

Clone this repository, open it in Android Studio and run `sample` (or `./gradlew :sample:installDebug`). It
resolves the SDK from the `maven/` folder in this repository. Paste your API key, then the ID of a published face
lens or screen effect. The release build is minified like a real app's; it signs with your machine's debug key.

## License

Use of IngevoraMirage is governed by the IngevoraMirage Terms: https://ingevoramirage.ingevora.com/terms
