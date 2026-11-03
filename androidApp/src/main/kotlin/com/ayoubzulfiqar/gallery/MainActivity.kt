package com.ayoubzulfiqar.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.ayoubzulfiqar.gallery.data.datasource.AndroidPlatformMediaDataSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val platformDataSource = remember {
                AndroidPlatformMediaDataSource(contentResolver)
            }
            App(platformDataSource = platformDataSource)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
