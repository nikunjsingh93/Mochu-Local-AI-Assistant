package com.nikunj.gemmabuilder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Mochu's face on its sky-blue tile, the same art as the launcher icon. */
@Composable
fun MochuLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Color(0xFFBFE6FF)),
        contentAlignment = Alignment.Center
    ) {
        // The launcher art leaves room for icon masks, so scale it up to fill the tile.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Mochu",
            modifier = Modifier.requiredSize(size * 1.35f)
        )
    }
}
