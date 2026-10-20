package com.laarasoft.frontend.presentation.componenet

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.laarasoft.frontend.config.media.AndroidScreenGrant
import com.laarasoft.frontend.domain.models.ScreenCaptureGrant

@Composable
actual fun rememberScreenShareLauncher(onGranted: (ScreenCaptureGrant) -> Unit): ScreenShareLauncher {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            currentOnGranted(AndroidScreenGrant(data))
        }
    }
    return remember(launcher) {
        object : ScreenShareLauncher {
            override val isSupported = true
            override fun launch() {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                launcher.launch(manager.createScreenCaptureIntent())
            }
        }
    }
}
