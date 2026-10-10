package com.laarasoft.frontend

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.laarasoft.frontend.config.di.initKoin

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Frontend",
    ) {
        App()
    }
}