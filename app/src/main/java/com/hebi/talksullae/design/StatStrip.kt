package com.hebi.talksullae.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun StatStrip(stats: List<Pair<String, String>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { (value, label) ->
                    StatTile(value, label, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { (value, label) ->
                    StatTile(value, label, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Surface(modifier=modifier, color=MaterialTheme.colorScheme.surfaceVariant,
        contentColor=MaterialTheme.colorScheme.onSurface, shape=RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal=8.dp, vertical=12.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(value, style=MaterialTheme.typography.titleMedium, textAlign=TextAlign.Center,
                color=MaterialTheme.colorScheme.onSurface)
            Text(label, style=MaterialTheme.typography.bodySmall, textAlign=TextAlign.Center,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
