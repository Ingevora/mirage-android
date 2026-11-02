// Mirrors Distribution/Example/MirageExample/ExampleCamera.swift (iOS): the front camera, every frame through the
// lens player on the camera's own thread.
package com.ingevora.mirage.sample

import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import com.ingevora.mirage.MirageFrame
import com.ingevora.mirage.MirageLensPlayer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX's front camera at about 720p, bound to [activity]'s lifecycle: `ImageAnalysis` keeping only the latest
 * frame, each frame handed to [lens] on the analysis thread with the rotation that turns it upright. The self-view
 * shows what the player draws. [onProblem] may be called on any thread. Start on the main thread.
 */
internal class SampleCamera(
    private val activity: ComponentActivity,
    private val lens: MirageLensPlayer,
    private val onProblem: (String) -> Unit,
) : AutoCloseable {
    private val analysisThread: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "sample-camera") }
    private val mainThread = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }

    @Volatile
    private var reportedBadFrame = false

    fun start() {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val provider = try {
                future.get()
            } catch (error: Exception) {
                onProblem("The camera did not start: ${error.message}")
                return@addListener
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    // 16:9 at 720p or the closest below: CameraX's default is 4:3, which gave 1440 × 1080 on a low-end phone.
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                        )
                        .build(),
                )
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisThread) { image -> draw(image) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (error: Exception) {
                onProblem("This device has no front camera the sample can open.")
            }
        }, mainThread)
    }

    /** On the analysis thread: one frame through the lens, then the frame goes back to CameraX. */
    private fun draw(image: ImageProxy) {
        try {
            val planes = image.planes
            val frame = MirageFrame.yuv(image.width, image.height, plane(planes[0]), plane(planes[1]), plane(planes[2]))
            val drawn = lens.process(
                frame,
                image.imageInfo.timestamp,
                MirageFrame.Orientation.fromRotationDegrees(image.imageInfo.rotationDegrees),
            )
            // A video app sends `drawn` here. Close it once read: an SDK-owned frame goes back to the pool (on your
            // own frame, close does nothing).
            drawn.close()
        } catch (error: IllegalArgumentException) {
            if (!reportedBadFrame) {
                reportedBadFrame = true
                onProblem("The camera's frames have a layout the SDK does not read: ${error.message}")
            }
        } finally {
            image.close() // only after process has returned
        }
    }

    private fun plane(plane: ImageProxy.PlaneProxy): MirageFrame.Plane = MirageFrame.Plane(plane.buffer, plane.rowStride, plane.pixelStride)

    override fun close() {
        analysisThread.shutdown()
    }
}
