// Mirrors Distribution/Example/MirageExample/ExampleView.swift and ExampleApp.swift (iOS): the one screen.
package com.ingevora.mirage.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ingevora.mirage.MirageEffectView
import com.ingevora.mirage.MirageLensPreviewView

/**
 * First the key field and Start. Then the lens self-view full screen, the screen-effect view above everything, and
 * a bottom panel: the lens state line, a message, a face lens ID with Show lens, a screen effect ID with Play effect.
 */
class SampleActivity : ComponentActivity() {
    private var model: SampleModel? = null
    private var camera: SampleCamera? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showMessage(getString(R.string.camera_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)
        keepPanelsClearOfSystemBars()
        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<EditText>(R.id.api_key).setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                start()
                true
            } else {
                false
            }
        }
        findViewById<Button>(R.id.show_lens).setOnClickListener { model?.show(textOf(R.id.lens_id)) }
        findViewById<Button>(R.id.play_effect).setOnClickListener { model?.play(textOf(R.id.effect_id)) }
    }

    override fun onDestroy() {
        camera?.close()
        camera = null
        model?.close()
        model = null
        super.onDestroy()
    }

    private fun start() {
        if (model != null) return
        val keyField = findViewById<EditText>(R.id.api_key)
        val key = SampleModel.clean(keyField.text.toString())
        if (key.isEmpty()) {
            findViewById<TextView>(R.id.key_message).text = getString(R.string.key_missing)
            return
        }
        keyField.text.clear() // from here on the key lives only in the client, in memory
        val made = SampleModel(this, key)
        model = made
        val fill = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        findViewById<FrameLayout>(R.id.camera).addView(MirageLensPreviewView(this, made.lens), fill)
        findViewById<FrameLayout>(R.id.root).addView(MirageEffectView(this, made.effects), fill)
        val state = findViewById<TextView>(R.id.state)
        state.text = SampleModel.describe(made.lens.state)
        made.lens.onStateChange = { state.text = SampleModel.describe(it) }
        made.onMessage = { showMessage(it) }
        findViewById<View>(R.id.key_panel).visibility = View.GONE
        findViewById<View>(R.id.controls).visibility = View.VISIBLE
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val made = model ?: return
        if (camera != null) return
        camera = SampleCamera(this, made.lens) { problem -> runOnUiThread { showMessage(problem) } }.also { it.start() }
    }

    private fun showMessage(text: String?) {
        val message = findViewById<TextView>(R.id.message)
        message.text = text ?: ""
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun textOf(id: Int): String = findViewById<EditText>(id).text.toString()

    /** The panels stay clear of the status bar, the navigation bar and the keyboard (the window is edge to edge). */
    private fun keepPanelsClearOfSystemBars() {
        val controls = findViewById<View>(R.id.controls)
        val keyPanel = findViewById<View>(R.id.key_panel)
        val pad = (PANEL_PADDING_DP * resources.displayMetrics.density).toInt()
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val (top, bottom) = barsOf(insets)
            controls.setPadding(pad, pad, pad, pad + bottom)
            keyPanel.setPadding(pad, pad + top, pad, pad + bottom)
            insets
        }
    }

    private fun barsOf(insets: WindowInsets): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            bars.top to bars.bottom
        } else {
            legacyBarsOf(insets)
        }

    @Suppress("DEPRECATION")
    private fun legacyBarsOf(insets: WindowInsets): Pair<Int, Int> = insets.systemWindowInsetTop to insets.systemWindowInsetBottom

    private companion object {
        const val PANEL_PADDING_DP = 16
    }
}
