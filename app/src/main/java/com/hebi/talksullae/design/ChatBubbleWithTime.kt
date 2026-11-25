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
import androidx.compose.ui.unit.dp

@Composable
internal fun ChatBubbleWithTime(text: String, time: String?, own: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 200.dp || LocalDensity.current.fontScale > 1.3f || (time?.length ?: 0) > 8
        val bubble: @Composable (Modifier) -> Unit = { modifier ->
            Surface(modifier=modifier,
                color=if(own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor=if(own) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape=RoundedCornerShape(8.dp)) {
                Text(text, modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),
                    style=MaterialTheme.typography.bodyMedium)
            }
        }
        val timestamp: @Composable () -> Unit = {
            if (time != null) Text(time, style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stacked || time == null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment=if(own) Alignment.End else Alignment.Start,
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                bubble(Modifier)
                timestamp()
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=if(own) Arrangement.spacedBy(6.dp, Alignment.End)
                    else Arrangement.spacedBy(6.dp, Alignment.Start),
                verticalAlignment=Alignment.Bottom) {
                if (own) timestamp()
                bubble(Modifier.weight(1f, fill=false))
                if (!own) timestamp()
            }
        }
    }
}
