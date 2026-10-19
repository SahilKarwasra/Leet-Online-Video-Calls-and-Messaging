package com.laarasoft.frontend.core.utils

import androidx.compose.ui.platform.ClipEntry

// ClipEntry has no common constructor, so each platform builds its own.
expect fun plainTextClipEntry(text: String): ClipEntry
