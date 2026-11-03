package com.ayoubzulfiqar.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.ayoubzulfiqar.gallery.data.datasource.AndroidPlatformMediaDataSource
import com.ayoubzulfiqar.gallery.platform.AndroidPlatformActionHandler
import com.ayoubzulfiqar.gallery.security.AndroidMetadataStripper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val platformDataSource = remember {
                AndroidPlatformMediaDataSource(contentResolver)
            }
            val platformActionHandler = remember {
                AndroidPlatformActionHandler(this@MainActivity)
            }
            val metadataStripper = remember {
                AndroidMetadataStripper(this@MainActivity)
            }
            App(
                platformDataSource = platformDataSource,
                platformActionHandler = platformActionHandler,
                metadataStripper = metadataStripper
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
