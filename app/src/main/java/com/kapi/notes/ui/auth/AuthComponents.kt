package com.kapi.notes.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kapi.notes.R
import com.kapi.notes.data.repository.BackendConnectivity
import com.kapi.notes.ui.common.KapiBrandMark
import com.kapi.notes.ui.theme.AccentCoral
import com.kapi.notes.ui.theme.Kapi100
import com.kapi.notes.ui.theme.Kapi500
import com.kapi.notes.ui.theme.Kapi600

/** Shared chrome for Login/Signup: the same Kapi Teal gradient + brand mark as [com.kapi.notes.ui.splash.SplashScreen]
 * (so these screens read as one continuous brand moment coming out of Splash), with a back
 * button and a short headline, then [content] laid out in a card below it. */
@Composable
fun AuthScaffold(
    headline: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Kapi500, Kapi600)))
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(Modifier.height(36.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    KapiBrandMark(modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    headline,
                    color = Color.White,
                    fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.splash_tagline),
                    color = Kapi100,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            content()
        }
    }
}

/** Non-blocking: the account system is optional, so this only ever informs, never gates the
 * rest of the form - [onRetry] lets the user re-ping without leaving the screen. */
@Composable
fun ConnectivityBanner(connectivity: BackendConnectivity, onRetry: () -> Unit) {
    if (connectivity != BackendConnectivity.OFFLINE) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentCoral.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = AccentCoral,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.auth_offline_message),
                style = MaterialTheme.typography.labelSmall,
                color = AccentCoral,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
    Spacer(Modifier.height(12.dp))
}
