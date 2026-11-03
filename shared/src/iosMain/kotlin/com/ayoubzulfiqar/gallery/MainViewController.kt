package com.ayoubzulfiqar.gallery

import androidx.compose.ui.window.ComposeUIViewController
import com.ayoubzulfiqar.gallery.di.IosAppGraph

fun MainViewController() = ComposeUIViewController { App(container = IosAppGraph.container) }
