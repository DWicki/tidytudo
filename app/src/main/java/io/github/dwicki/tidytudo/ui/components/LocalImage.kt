package io.github.dwicki.tidytudo.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a JPEG from app storage, decoded off the main thread. [maxSize] downsamples thumbnails
 * so a list of photos does not hold full-size bitmaps (no image library, C-3).
 */
@Composable
fun LocalImage(file: File, contentDescription: String?, modifier: Modifier = Modifier, maxSize: Int = 0) {
    var image by remember(file, maxSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file, maxSize) {
        image = withContext(Dispatchers.IO) { decode(file, maxSize) }
    }
    val current = image
    if (current == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
        Image(current, contentDescription, modifier, contentScale = ContentScale.Crop)
    }
}

private fun decode(file: File, maxSize: Int): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    if (maxSize > 0) {
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    }
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
