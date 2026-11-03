package com.ayoubzulfiqar.gallery

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ayoubzulfiqar.gallery.platform.AndroidPlatformActionHandler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val graph = (application as GalleryApplication).graph
        // Activity-result launchers must be registered before the activity is started.
        graph.attach(this)
        val actions = AndroidPlatformActionHandler(this, graph.metadataStripper)

        setContent {
            App(
                container = graph.container,
                actions = actions,
                onSystemBarsDark = ::applySystemBarStyle,
                onSensitiveContentVisible = ::setSecureWindow
            )
        }
        // The theme's window background only prevents a flash before the first frame. Once the
        // opaque Compose canvas is drawing, keeping it means every pixel is filled twice per frame.
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    private fun applySystemBarStyle(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** Blocks screenshots/recording and blanks the recents thumbnail while vault content is shown. */
    private fun setSecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
