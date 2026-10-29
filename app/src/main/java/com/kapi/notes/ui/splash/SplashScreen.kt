package com.kapi.notes.ui.splash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kapi.notes.ui.common.KapiBrandMark
import com.kapi.notes.ui.theme.Kapi100
import com.kapi.notes.ui.theme.Kapi500
import com.kapi.notes.ui.theme.Kapi600
import kotlinx.coroutines.delay

/**
 * Self-driving splash: animates the brand mark in (bouncy scale-up + fade), holds briefly,
 * then calls [onFinished] so the nav graph can pop it off the back stack and land on the
 * dashboard - no system SplashScreen API involved, so the animation is fully our own.
 *
 * Styled per the brand guidelines: Kapi Teal gradient background, white document-tile brand
 * mark with the folded corner + "K" stroke + highlighter accent dots, Outfit for the wordmark.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splashScale"
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "splashMarkAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 250),
        label = "splashTextAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1400)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Kapi500, Kapi600))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = markAlpha
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                KapiBrandMark(modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            ) {
                Text(
                    "Kapi Notes",
                    color = Color.White,
                    fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ghi chú tinh gọn. Tư duy mở rộng.",
                    color = Kapi100,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
