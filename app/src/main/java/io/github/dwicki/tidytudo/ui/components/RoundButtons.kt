package io.github.dwicki.tidytudo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A round button that floats over the map: raised, with a shadow, so it reads on every room colour.
 * [content] replaces the icon, e.g. for a "×2" label.
 */
@Composable
fun MapRoundButton(
    icon: ImageVector?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    size: Dp = MAP_BUTTON,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = CircleShape,
        color = container,
        contentColor = contentColor,
        shadowElevation = 3.dp,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(size * 0.4f), strokeWidth = 2.dp, color = LocalContentColor.current)
                content != null -> content()
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.45f))
            }
        }
    }
}

val MAP_BUTTON = 44.dp
val MAP_MAIN_BUTTON = 60.dp
private const val DISABLED_ALPHA = 0.5f
