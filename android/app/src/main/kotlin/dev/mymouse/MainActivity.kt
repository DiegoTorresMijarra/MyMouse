package dev.mymouse

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.mymouse.core.gesture.GestureInterpreter
import dev.mymouse.core.gesture.TrackpadConfig
import dev.mymouse.core.protocol.Protocol

class MainActivity : Activity() {

    private lateinit var connection: HostConnection
    private lateinit var trackpad: TrackpadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO (M4): cargar TrackpadConfig desde ajustes persistidos.
        val interpreter = GestureInterpreter(TrackpadConfig())
        connection = HostConnection(port = Protocol.DEFAULT_PORT)
        trackpad = TrackpadView(this, interpreter, connection::send)
        connection.onConnectionChanged = { connected -> trackpad.post { trackpad.connected = connected } }

        setContentView(trackpad)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        connection.start()
    }

    override fun onPause() {
        connection.stop()
        super.onPause()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
