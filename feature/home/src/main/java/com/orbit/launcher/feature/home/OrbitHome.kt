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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    suggestions: List<LauncherApp>,
    settings: LauncherSettings,
    badgeCounts: Map<String, Int>,
    loadIcon: suspend (LauncherApp, Int) -> Bitmap?,
    onLaunch: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onWidgets: () -> Unit,
    onGroups: () -> Unit,
    onGesture: (String) -> Unit,
) {
    val orbitApps = remember(apps, suggestions) {
        val ranks = suggestions.mapIndexed { index, app -> app.key to index }.toMap()
        apps.filterNot { it.hidden || it.searchOnly }.sortedWith(compareByDescending<LauncherApp> { it.pinned }
            .thenBy { ranks[it.key] ?: Int.MAX_VALUE }.thenByDescending { it.launches }.thenByDescending { it.lastLaunched }.thenBy { it.label.lowercase() }).take(16)
    }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val latestGesture by rememberUpdatedState(onGesture)
    var twoFingerActive by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("O R B I T", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text("YOUR SPACE", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Row { TextButton(onClick = onGroups) { Text("Groups") }; TextButton(onClick = onWidgets) { Text("Widgets") }; TextButton(onClick = onSettings) { Text("Settings") } }
        }
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 480.dp).aspectRatio(1f)
                .pointerInput(settings.doubleTap, settings.emptyLongPress) {
                    detectTapGestures(onDoubleTap = { latestGesture(settings.doubleTap) }, onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestGesture(settings.emptyLongPress)
                    })
                }
                .pointerInput(settings.twoFinger) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        twoFingerActive = false
                        do {
                            val event = awaitPointerEvent()
                            if (!twoFingerActive && event.changes.count { it.pressed } >= 2) {
                                twoFingerActive = true
                                latestGesture(settings.twoFinger)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(settings.reduceMotion, settings.swipeUp, settings.swipeDown, settings.swipeLeft, settings.swipeRight) {
                    val tracker = VelocityTracker()
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking(); dragX = 0f; dragY = 0f; scope.launch { rotation.stop() } },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x / size.width.coerceAtLeast(1) * 360f
                            if (twoFingerActive) return@detectDragGestures
                            if (kotlin.math.abs(dragY) > size.height * .16f && kotlin.math.abs(dragY) > kotlin.math.abs(dragX)) {
                                latestGesture(if (dragY < 0) settings.swipeUp else settings.swipeDown); return@detectDragGestures
                            }
                            if (kotlin.math.abs(dragX) > size.width * .4f) {
                                val action = if (dragX < 0) settings.swipeLeft else settings.swipeRight
                                if (action != "none") { latestGesture(action); return@detectDragGestures }
                            }
                            if (!settings.reduceMotion) scope.launch {
                                rotation.animateDecay(velocity.coerceIn(-720f, 720f), exponentialDecay(frictionMultiplier = 2.5f))
                            }
                        },
                        onDrag = { change, drag ->
                            dragX += drag.x
                            dragY += drag.y
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            if (!twoFingerActive && kotlin.math.abs(dragX) >= kotlin.math.abs(dragY)) scope.launch { rotation.snapTo(rotation.value + drag.x / size.width * 240f) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val ringColor = MaterialTheme.colorScheme.primary
            OrbitGlow(Modifier.fillMaxSize(), ringColor, settings.reduceMotion)
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
                        AppOrb(app, settings, if (app.isWorkProfile) 0 else badgeCounts[app.packageName] ?: 0, loadIcon, onLaunch) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(it) }
                    }
                }
            }
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = onSearch, onLongClick = onSettings)
                    .semantics { contentDescription = "Search apps" },
                contentAlignment = Alignment.Center,
            ) { Text("⌕", fontSize = 34.sp, color = MaterialTheme.colorScheme.onPrimary) }
        }
        Spacer(Modifier.weight(1f))
        Text("DRAG TO ROTATE  ·  TAP THE CENTER TO SEARCH", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, textAlign = TextAlign.Center, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 20.dp))
        TextButton(onClick = onSearch, modifier = Modifier.padding(vertical = 16.dp)) { Text("ALL APPS  ↗") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppOrb(
    app: LauncherApp,
    settings: LauncherSettings,
    badgeCount: Int,
    loadIcon: suspend (LauncherApp, Int) -> Bitmap?,
    onLaunch: (LauncherApp) -> Unit,
    onMenu: (LauncherApp) -> Unit,
) {
    val density = LocalDensity.current
    val pixels = with(density) { settings.iconSize.dp.roundToPx() }.coerceAtLeast(1)
    val icon by produceState<Bitmap?>(null, app.key, pixels, settings.themedIcons, settings.palette, settings.themeMode, settings.dynamicColor, settings.iconPack) { value = loadIcon(app, pixels) }
    val shape = when (settings.iconShape) {
        "squircle" -> RoundedCornerShape(24)
        "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 8)
        "square" -> RoundedCornerShape(0)
        else -> CircleShape
    }
    Column(
        Modifier.width(76.dp).combinedClickable(onClickLabel = "Open ${app.label}", onLongClickLabel = "Actions for ${app.label}", onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(settings.iconSize.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon!!.asImageBitmap(), contentDescription = if (settings.showLabels) null else app.label, modifier = Modifier.fillMaxSize())
            else Text(app.label.take(1), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badgeCount > 0) Text("● $badgeCount", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
        if (settings.showLabels) Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
