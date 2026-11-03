package com.orbit.launcher.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The step-one entry point; the interactive home is introduced with the home feature. */
@Composable
fun LauncherFoundationScreen(
    isDefaultHome: Boolean,
    homeRequests: Int,
    onChooseDefault: () -> Unit,
) {
    var skippedSetup by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(homeRequests) {
        if (homeRequests > 0) skippedSetup = false
    }
    val showSetup = !isDefaultHome && !skippedSetup

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.orbit_wordmark), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OrbitEmblem()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (showSetup) R.string.setup_eyebrow else R.string.home_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (showSetup) R.string.setup_title else R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (showSetup) R.string.setup_description else R.string.home_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            if (showSetup) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.setup_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            if (!isDefaultHome) {
                Button(onClick = onChooseDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (showSetup) R.string.choose_home else R.string.set_default_home))
                }
                if (showSetup) {
                    TextButton(onClick = { skippedSetup = true }) {
                        Text(stringResource(R.string.continue_later))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitEmblem() {
    val ring = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(210.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(ring.copy(alpha = 0.14f), radius = size.minDimension * 0.46f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.25f), radius = size.minDimension * 0.32f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.4f), radius = size.minDimension * 0.18f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.13f), radius = size.minDimension * 0.105f, center = center)
        drawCircle(Color(0xFFE9ECF8), radius = size.minDimension * 0.025f, center = center)
        drawCircle(ring, radius = size.minDimension * 0.025f, center = Offset(size.width * 0.82f, size.height * 0.17f))
        drawCircle(ring.copy(alpha = 0.65f), radius = size.minDimension * 0.018f, center = Offset(size.width * 0.18f, size.height * 0.5f))
        drawCircle(ring.copy(alpha = 0.8f), radius = size.minDimension * 0.022f, center = Offset(size.width * 0.68f, size.height * 0.76f))
    }
}
