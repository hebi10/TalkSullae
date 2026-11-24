package com.hebi.talksullae.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.hebi.talksullae.R

@Composable
fun ProfileAvatar(avatar: String?, modifier: Modifier = Modifier) {
    val resource = when (avatar) {
        "부엉이" -> R.drawable.avatar_owl
        "토끼" -> R.drawable.avatar_rabbit
        "고양이" -> R.drawable.avatar_cat
        "강아지" -> R.drawable.avatar_dog
        "곰" -> R.drawable.avatar_bear
        "여우" -> R.drawable.avatar_fox
        "판다" -> R.drawable.avatar_panda
        "펭귄" -> R.drawable.avatar_penguin
        else -> null
    }
    if (resource == null) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Icon(painterResource(resource), contentDescription = null, modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurface)
    }
}
