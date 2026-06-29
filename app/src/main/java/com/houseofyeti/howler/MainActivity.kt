package com.houseofyeti.howler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.houseofyeti.howler.R
import com.houseofyeti.howler.ui.theme.Phosphor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.houseofyeti.howler.audio.AudioEngine
import com.houseofyeti.howler.audio.Calibration
import com.houseofyeti.howler.audio.CalibrationStore
import com.houseofyeti.howler.audio.manualOffset
import com.houseofyeti.howler.audio.resolveCalibration
import com.houseofyeti.howler.audio.splFromDbfs
import com.houseofyeti.howler.ui.theme.HowlerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen on while Howler is in front — you watch a meter, you
        // don't tap it. Scoped to window visibility, so Android releases it
        // automatically when the app is backgrounded or closed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HowlerTheme {
                val context = LocalContext.current
                var granted by remember { mutableStateOf(hasMicPermission(context)) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted = it }
                LaunchedEffect(Unit) {
                    if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
                Box(Modifier.fillMaxSize().background(Phosphor.screen)) {
                    if (granted) {
                        MeterScreen()
                    } else {
                        Centered("MIC PERMISSION REQUIRED", Modifier.systemBarsPadding())
                    }
                }
            }
        }
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun MeterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { CalibrationStore(context) }
    val prefs = remember { context.getSharedPreferences("howler_prefs", Context.MODE_PRIVATE) }
    var showFirstRun by remember { mutableStateOf(!prefs.getBoolean("first_run_seen", false)) }
    var dbfs by remember { mutableFloatStateOf(-160f) }
    var maxDbfs by remember { mutableFloatStateOf(-160f) }
    var maxClipped by remember { mutableStateOf(false) }
    var minDbfs by remember { mutableFloatStateOf(200f) }
    var leqDbfs by remember { mutableFloatStateOf(-160f) }
    var over by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(true) }
    var weighting by rememberSaveable { mutableIntStateOf(AudioEngine.WEIGHTING_A) }  // A is the SLM default
    var fast by rememberSaveable { mutableStateOf(true) }        // Fast (125 ms) default
    // Tier-1 device sensitivity intentionally not auto-trusted (see Calibration.kt);
    // resolver uses the stored manual point or stays uncalibrated.
    var cal by remember { mutableStateOf<Calibration>(resolveCalibration(store.loadManual(), null)) }
    var showDialog by remember { mutableStateOf(false) }

    // A stream opened while the device is dozing/locked stays silenced for its
    // lifetime (verified on Pixel 10 Pro XL). Tie start/stop to START/STOP (not
    // RESUME/PAUSE) so a fresh stream opens whenever the app becomes visible, yet
    // keeps running while paused-but-visible (split-screen/multi-window). Clearing
    // `started` on stop halts the polling coroutine so it doesn't drain battery
    // in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = AudioEngine.nativeStart()
                Lifecycle.Event.ON_STOP -> { AudioEngine.nativeStop(); started = false }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AudioEngine.nativeStop()
        }
    }
    LaunchedEffect(fast) { AudioEngine.nativeSetFast(fast) }
    LaunchedEffect(weighting) { AudioEngine.nativeSetWeighting(weighting) }
    // Stat units depend on weighting + time response — clear the displayed values
    // when either changes (the engine resets its own stats on the same triggers).
    LaunchedEffect(weighting, fast) {
        AudioEngine.nativeResetStats(); maxDbfs = -160f; minDbfs = 200f; leqDbfs = -160f
    }
    LaunchedEffect(started) {
        while (started) {
            dbfs = AudioEngine.nativeLevelDbfs()
            maxDbfs = AudioEngine.nativeMaxDbfs()
            maxClipped = AudioEngine.nativeMaxClipped()
            minDbfs = AudioEngine.nativeMinDbfs()
            leqDbfs = AudioEngine.nativeLeqDbfs()
            over = AudioEngine.nativeOverRange()
            delay(50)
        }
    }

    val spl = cal.splFromDbfs(dbfs)
    val displayLevel = spl ?: dbfs
    val wLetter = when (weighting) {
        AudioEngine.WEIGHTING_A -> "A"
        AudioEngine.WEIGHTING_C -> "C"
        else -> "Z"
    }

    Box(modifier.fillMaxSize()) {
        if (!started) {
            InputUnavailable(onRetry = { started = AudioEngine.nativeStart() })
        } else {
            HeadAndGlow(glowT = smoothstep(50f, 88f, displayLevel))
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Segmented(
                        listOf("A" to AudioEngine.WEIGHTING_A, "C" to AudioEngine.WEIGHTING_C, "Z" to AudioEngine.WEIGHTING_Z),
                        weighting, { weighting = it },
                    )
                    OverIndicator(over)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bigText = "%.1f".format(displayLevel)
                    // DSEG glyphs are wide; shrink for longer values so 3-digit SPL
                    // (or a negative dBFS) never clips. 2-digit readings stay huge.
                    val bigSize = when {
                        bigText.length <= 4 -> 108.sp
                        bigText.length == 5 -> 84.sp
                        else -> 66.sp
                    }
                    Text(bigText, color = Phosphor.readout,
                        fontFamily = SegmentFont, fontSize = bigSize, maxLines = 1)
                    Text("dB($wLetter) · ${if (fast) "fast" else "slow"}", color = Phosphor.labelBright,
                        fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Segmented(listOf("FAST" to 1, "SLOW" to 0), if (fast) 1 else 0, { fast = it == 1 })
                    if (maxDbfs > -160f) {
                        StatsRow(maxDbfs, maxClipped, minDbfs, leqDbfs, cal)
                        TextButton(onClick = {
                            AudioEngine.nativeResetStats(); maxDbfs = -160f; minDbfs = 200f; leqDbfs = -160f
                        }) { Text("RESET MAX / MIN / Leq", color = Phosphor.toggleInactiveText, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                    Text(calibrationCaption(cal, wLetter), color = Phosphor.caption,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { showDialog = true })
                }
            }
        }
        ScanlineOverlay(Modifier.fillMaxSize())
    }

    if (showFirstRun) {
        FirstRunDialog(
            onDismiss = {
                prefs.edit().putBoolean("first_run_seen", true).apply()
                showFirstRun = false
            },
            onLearnMore = {
                prefs.edit().putBoolean("first_run_seen", true).apply()
                showFirstRun = false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://houseofyeti.com/howler/")))
            },
        )
    }

    if (showDialog) {
        CalibrateDialog(
            currentDbfs = dbfs,
            onSave = { refSpl, meterClass ->
                val manual = Calibration.Manual(manualOffset(refSpl, dbfs.toDouble()), meterClass)
                store.saveManual(manual)
                cal = manual
                showDialog = false
            },
            onClear = { store.clear(); cal = Calibration.Uncalibrated; showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

private const val HEAD_RATIO = 380f / 505f  // extracted asset aspect

/** DSEG7 Classic — 7-segment LED face for the hero readout (OFL, see res/font). */
private val SegmentFont = FontFamily(Font(R.font.dseg7_classic_bold))

/**
 * Constant phosphor head with the one reactive channel: an amber edge-glow that
 * swells with level, as if something behind the head were lighting up. The glow
 * must read as a backlight halo around the silhouette — never the face glowing.
 *
 * Layers, back to front:
 *   1. glow — a blurred amber silhouette (spread driven by level on API 31+, the
 *      pre-blurred ring asset below that).
 *   2. opaque BLACK head silhouette — masks the glow's interior so only the halo
 *      that spilled past the sharp edge survives. Without this the translucent
 *      head lets the glow bleed through the face.
 *   3. dim amber head — the visible phosphor face, drawn over the black mask.
 */
@Composable
private fun HeadAndGlow(glowT: Float) {
    val glowAlpha = glowT * 0.4f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val head = painterResource(R.drawable.howler_head)
        val headMod = Modifier.fillMaxWidth(0.66f).aspectRatio(HEAD_RATIO)
        if (glowAlpha > 0.001f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Image(
                    head, null,
                    headMod
                        .blur(lerp(14.dp, 40.dp, glowT), BlurredEdgeTreatment.Unbounded)
                        .alpha(glowAlpha),
                    colorFilter = ColorFilter.tint(Phosphor.glow, BlendMode.SrcIn),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Image(
                    painterResource(R.drawable.howler_glow), null,
                    Modifier.fillMaxWidth(0.70f).aspectRatio(HEAD_RATIO).alpha(glowAlpha),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        // Opaque black silhouette — blocks the glow from the head's interior.
        Image(head, null, headMod, colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
            contentScale = ContentScale.Fit)
        // Dim phosphor face on top of the mask.
        Image(head, null, headMod.alpha(0.33f), contentScale = ContentScale.Fit)
    }
}

/** Hermite smoothstep — 0 below edge0, 1 above edge1, eased between. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Bottom calibration caption: accuracy claim + source, or uncalibrated. */
private fun calibrationCaption(cal: Calibration, w: String): String = when (cal) {
    is Calibration.Uncalibrated -> "uncalibrated · relative dBFS · tap to calibrate"
    is Calibration.Manual -> "≈ ±2 dB($w) · tier 2 · ${cal.referenceMeterClass}"
    is Calibration.DeviceReported -> "≈ ±2 dB($w) · tier 1 · device"
}

/** Segmented control: one active cell, brighter than the rest (not greyed). */
@Composable
private fun Segmented(items: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row {
        for ((label, value) in items) {
            val active = value == selected
            Box(
                Modifier
                    .clickable { onSelect(value) }
                    .background(if (active) Phosphor.toggleActiveFill else Color.Transparent)
                    .border(1.dp, if (active) Phosphor.toggleActiveBorder else Phosphor.toggleInactiveBorder)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(label, color = if (active) Phosphor.readout else Phosphor.toggleInactiveText,
                    fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
    }
}

/** The lone red: lit on clip / over-range, dim outline otherwise. */
@Composable
private fun OverIndicator(lit: Boolean) {
    Box(
        Modifier
            .background(if (lit) Phosphor.overBg else Color.Transparent)
            .border(1.dp, if (lit) Phosphor.overBorder else Phosphor.toggleInactiveBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text("OVER", color = if (lit) Phosphor.overText else Phosphor.toggleInactiveBorder,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** MAX / MIN / Leq — dim labels over amber values, three columns. The Max value
 *  carries a red "≥" when the peak came from a clipped block (true level higher). */
@Composable
private fun StatsRow(maxDbfs: Float, maxClipped: Boolean, minDbfs: Float, leqDbfs: Float, cal: Calibration) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        StatCell("MAX", maxDbfs, cal, clipped = maxClipped)
        StatCell("MIN", minDbfs, cal)
        StatCell("Leq", leqDbfs, cal)
    }
}

@Composable
private fun StatCell(label: String, dbfs: Float, cal: Calibration, clipped: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Phosphor.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (clipped) Text("≥", color = Phosphor.overText, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            Text("%.1f".format(cal.splFromDbfs(dbfs) ?: dbfs), color = Phosphor.readout,
                fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        }
    }
}

/** Recoverable "mic unavailable" state — another app likely holds the input. */
@Composable
private fun InputUnavailable(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("INPUT UNAVAILABLE", color = Phosphor.overText,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("the mic is held by another app", color = Phosphor.labelBright,
            fontFamily = FontFamily.Monospace, fontSize = 13.sp, textAlign = TextAlign.Center)
        Box(
            Modifier
                .clickable(onClick = onRetry)
                .background(Phosphor.toggleActiveFill)
                .border(1.dp, Phosphor.toggleActiveBorder)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text("RETRY", color = Phosphor.readout, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        }
    }
}

/** Subtle CRT scanlines over everything — 3px pitch, low alpha. Non-interactive. */
@Composable
private fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pitch = 3.dp.toPx()
        val stroke = 1.dp.toPx()
        val line = Phosphor.scanline.copy(alpha = 0.42f)
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
            y += pitch
        }
    }
}

@Composable
private fun FirstRunDialog(onDismiss: () -> Unit, onLearnMore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Phosphor.screen,
        title = {
            Text("BEFORE YOU MEASURE", color = Phosphor.readout,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Accuracy depends on your device's microphone. Without calibration, readings reflect relative changes reliably — but absolute levels can be 10–15 dB off.",
                    color = Phosphor.labelBright, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                )
                Text(
                    "Tap the label at the bottom of the screen to calibrate against a reference source.",
                    color = Phosphor.labelBright, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("GOT IT", color = Phosphor.readout, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onLearnMore) {
                Text("LEARN MORE", color = Phosphor.caption, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun CalibrateDialog(
    currentDbfs: Float,
    onSave: (referenceSpl: Double, meterClass: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var refSplText by remember { mutableStateOf("") }
    var meterText by remember { mutableStateOf("class-2") }
    val refSpl = refSplText.toDoubleOrNull()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Phosphor.readout, unfocusedTextColor = Phosphor.readout,
        cursorColor = Phosphor.readout,
        focusedBorderColor = Phosphor.toggleActiveBorder, unfocusedBorderColor = Phosphor.toggleInactiveBorder,
        focusedLabelColor = Phosphor.labelBright, unfocusedLabelColor = Phosphor.toggleInactiveText,
    )
    val mono = TextStyle(fontFamily = FontFamily.Monospace, color = Phosphor.readout)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Phosphor.screen,
        title = { Text("MANUAL CALIBRATION", color = Phosphor.readout, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hold your reference meter at a steady source — best near 1 kHz — then enter its reading.",
                    color = Phosphor.labelBright, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("phone now: %.1f dBFS".format(currentDbfs),
                    color = Phosphor.caption, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                OutlinedTextField(refSplText, { refSplText = it }, textStyle = mono, colors = fieldColors,
                    label = { Text("reference dB SPL", fontFamily = FontFamily.Monospace) })
                OutlinedTextField(meterText, { meterText = it }, textStyle = mono, colors = fieldColors,
                    label = { Text("reference meter class", fontFamily = FontFamily.Monospace) })
            }
        },
        confirmButton = {
            TextButton(enabled = refSpl != null, onClick = { onSave(refSpl!!, meterText) }) {
                Text("SAVE", color = if (refSpl != null) Phosphor.readout else Phosphor.toggleInactiveBorder,
                    fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("CLEAR", color = Phosphor.overText, fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun Centered(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(text, color = Phosphor.labelBright, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center) }
}
