package com.orbit.launcher.feature.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherSettings
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrbitHome(
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    loadIcon: suspend (LauncherApp, Int) -> Bitmap?,
    onLaunch: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val orbitApps = remember(apps) {
        apps.filterNot { it.hidden }.sortedWith(compareByDescending<LauncherApp> { it.pinned }
            .thenByDescending { it.launches }.thenByDescending { it.lastLaunched }.thenBy { it.label.lowercase() }).take(16)
    }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Color(0xFF090C15)).safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("O R B I T", color = Color(0xFFB7C7FF), fontSize = 12.sp)
                Text("YOUR SPACE", color = Color(0xFF77819D), fontSize = 10.sp, letterSpacing = 2.sp)
            }
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 480.dp).aspectRatio(1f)
                .pointerInput(settings.reduceMotion) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking(); scope.launch { rotation.stop() } },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x / size.width.coerceAtLeast(1) * 360f
                            if (!settings.reduceMotion) scope.launch {
                                rotation.animateDecay(velocity.coerceIn(-720f, 720f), exponentialDecay(frictionMultiplier = 2.5f))
                            }
                        },
                        onDrag = { change, drag ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            scope.launch { rotation.snapTo(rotation.value + drag.x / size.width * 240f) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val ringColor = Color(0xFF8198F7)
            Canvas(Modifier.fillMaxSize()) {
                listOf(.22f, .36f, .46f).forEach { r ->
                    drawCircle(ringColor.copy(alpha = .18f), radius = size.minDimension * r, style = Stroke(1.dp.toPx()))
                }
                drawCircle(ringColor.copy(alpha = .035f), radius = size.minDimension * .16f)
            }
            val dimension = minOf(maxWidth, maxHeight)
            val radians = rotation.value * PI / 180.0
            orbitApps.forEachIndexed { index, app ->
                key(app.key) {
                    val inner = index < 6
                    val slot = if (inner) index else index - 6
                    val count = if (inner) minOf(6, orbitApps.size) else minOf(10, orbitApps.size - 6)
                    val angle = -PI / 2 + (2 * PI * slot / count.coerceAtLeast(1)) + if (inner) radians else -radians * .7
                    val radius = dimension * if (inner) .22f else .40f
                    val x = radius * cos(angle).toFloat()
                    val y = radius * sin(angle).toFloat()
                    Box(Modifier.offset(x = x, y = y)) {
                        AppOrb(app, settings, loadIcon, onLaunch, onMenu)
                    }
                }
            }
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Color(0xFFB8C7FF))
                    .combinedClickable(onClick = onSearch, onLongClick = onSettings)
                    .semantics { contentDescription = "Search apps" },
                contentAlignment = Alignment.Center,
            ) { Text("⌕", fontSize = 34.sp, color = Color(0xFF182553)) }
        }
        Spacer(Modifier.weight(1f))
        Text("DRAG TO ROTATE  ·  TAP THE CENTER TO SEARCH", color = Color(0xFF78839F), fontSize = 10.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 20.dp))
        TextButton(onClick = onSearch, modifier = Modifier.padding(vertical = 16.dp)) { Text("ALL APPS  ↗") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppOrb(
    app: LauncherApp,
    settings: LauncherSettings,
    loadIcon: suspend (LauncherApp, Int) -> Bitmap?,
    onLaunch: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
) {
    val density = LocalDensity.current
    val pixels = with(density) { settings.iconSize.dp.roundToPx() }.coerceAtLeast(1)
    val icon by produceState<Bitmap?>(null, app.key, pixels) { value = loadIcon(app, pixels) }
    Column(
        Modifier.width(76.dp).combinedClickable(onClickLabel = "Open ${app.label}", onLongClickLabel = "Actions for ${app.label}", onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(settings.iconSize.dp).clip(CircleShape).background(Color(0xFF222A43)), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon!!.asImageBitmap(), contentDescription = if (settings.showLabels) null else app.label, modifier = Modifier.fillMaxSize())
            else Text(app.label.take(1), color = Color.White)
        }
        if (settings.showLabels) Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFE5E9FC), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
