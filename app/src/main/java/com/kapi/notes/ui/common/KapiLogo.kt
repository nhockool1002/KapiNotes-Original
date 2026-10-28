package com.kapi.notes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The exact glyph from `ic_launcher_foreground.xml` (the app's actual launcher icon mark),
 * reused everywhere else the brand mark appears (splash, sidebar, About) instead of a plain bold
 * "K" string - keeps the logo visually identical across the app and the home-screen icon. */
val KapiLogoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "KapiLogoMark",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(34f, 24f)
            lineTo(34f, 84f)
            lineTo(44f, 84f)
            lineTo(44f, 58f)
            lineTo(70f, 84f)
            lineTo(84f, 84f)
            lineTo(54f, 54f)
            lineTo(82f, 24f)
            lineTo(68f, 24f)
            lineTo(44f, 50f)
            lineTo(44f, 24f)
            close()
        }
    }.build()
}

/** The app's brand mark: [KapiLogoIcon] on a rounded, colored tile. */
@Composable
fun KapiLogo(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = Color.White,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    Box(
        modifier.clip(shape).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            KapiLogoIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.fillMaxSize(0.6f)
        )
    }
}
