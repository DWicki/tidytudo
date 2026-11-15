package io.github.dwicki.tidytudo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The shared row language of the app, first used on the home screen: a 40dp round tonal icon, text
 * on one keyline, and the control on the right. Every list in the app uses these, so rows line up
 * the same way on every screen.
 */

/** Horizontal padding of rows inside a card; matches the 20dp inset of card titles. */
val ROW_HORIZONTAL_PADDING = 20.dp

/** An icon on a round tonal background. */
@Composable
fun TonalIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Surface(shape = CircleShape, color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size / 2))
        }
    }
}

/**
 * One row: optional leading [icon] (or custom [leading]), title, optional supporting text, optional
 * extra content [below] the text (a progress bar, weekdays), and an optional [trailing] part.
 * Clickable as a whole when [onClick] is given.
 */
@Composable
fun AppListItem(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = if (supporting == null) 56.dp else 72.dp)
            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 10.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            icon != null -> {
                TonalIcon(icon)
                Spacer(Modifier.width(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = supportingColor)
            }
            below?.invoke()
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** A row with a switch; the whole row toggles it, and TalkBack hears a switch, not a button. */
@Composable
fun SwitchListItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    AppListItem(
        title = title,
        icon = icon,
        supporting = supporting,
        enabled = enabled,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A group of rows in one card, edge to edge, with an optional title in the card-title style. */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = ROW_HORIZONTAL_PADDING, end = ROW_HORIZONTAL_PADDING, top = 8.dp, bottom = 4.dp),
                )
            }
            content()
        }
    }
}

/** Heading above a group of cards, aligned with the card titles. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = ROW_HORIZONTAL_PADDING, top = 8.dp, bottom = 4.dp),
    )
}

private const val DISABLED_ALPHA = 0.5f
