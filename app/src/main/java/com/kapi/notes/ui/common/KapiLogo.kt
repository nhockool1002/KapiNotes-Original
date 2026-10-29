package com.kapi.notes.ui.common

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.kapi.notes.ui.theme.AccentCoral
import com.kapi.notes.ui.theme.AccentYellow
import com.kapi.notes.ui.theme.Kapi100
import com.kapi.notes.ui.theme.Kapi500

/** The exact glyph from `ic_launcher_foreground.xml` (the app's actual launcher icon mark),
 * reused everywhere else the brand mark appears (sidebar, About) instead of a plain bold
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

/** The app's brand mark: [KapiLogoIcon] on a rounded, colored tile. Used where the mark needs
 * to read clearly at small sizes (sidebar header, dialog titles). */
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

/** The full, multi-color brand mark from the brand guidelines: a white "document" tile with a
 * folded corner, the bold two-stroke "K" mark, and the two highlighter accent dots (yellow +
 * coral). Used at larger sizes where the extra detail reads clearly (splash screen, About
 * screen) - the simpler single-tint [KapiLogo] is used everywhere else (sidebar, small icons). */
@Composable
fun KapiBrandMark(
    modifier: Modifier = Modifier,
    documentColor: Color = Color.White,
    foldColor: Color = Kapi100,
    markColor: Color = Kapi500,
    dotYellow: Color = AccentYellow,
    dotCoral: Color = AccentCoral
) {
    Canvas(modifier) {
        val scale = size.minDimension / 120f

        fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)

        // Document base (rounded rect, viewBox coords x:20-100, y:15-105).
        drawRoundRect(
            color = documentColor,
            topLeft = pt(20f, 15f),
            size = Size(80f * scale, 90f * scale),
            cornerRadius = CornerRadius(12f * scale, 12f * scale)
        )

        // Folded corner.
        val fold = Path().apply {
            moveTo(pt(100f, 45f).x, pt(100f, 45f).y)
            lineTo(pt(70f, 15f).x, pt(70f, 15f).y)
            lineTo(pt(70f, 33f).x, pt(70f, 33f).y)
            cubicTo(
                pt(70f, 39.6274f).x, pt(70f, 39.6274f).y,
                pt(75.3726f, 45f).x, pt(75.3726f, 45f).y,
                pt(82f, 45f).x, pt(82f, 45f).y
            )
            close()
        }
        drawPath(fold, color = foldColor)

        // The "K" mark.
        val kMark = Path().apply {
            moveTo(pt(45f, 40f).x, pt(45f, 40f).y)
            lineTo(pt(45f, 80f).x, pt(45f, 80f).y)
            moveTo(pt(45f, 60f).x, pt(45f, 60f).y)
            lineTo(pt(65f, 40f).x, pt(65f, 40f).y)
            moveTo(pt(45f, 60f).x, pt(45f, 60f).y)
            lineTo(pt(70f, 80f).x, pt(70f, 80f).y)
        }
        drawPath(
            kMark,
            color = markColor,
            style = Stroke(width = 12f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Accent dots.
        drawCircle(color = dotYellow, radius = 4f * scale, center = pt(85f, 70f))
        drawCircle(color = dotCoral, radius = 4f * scale, center = pt(85f, 82f))
    }
}
