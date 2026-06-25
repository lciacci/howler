package com.example.howler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.howler.audio.AudioEngine
import com.example.howler.audio.Calibration
import com.example.howler.audio.CalibrationStore
import com.example.howler.audio.manualOffset
import com.example.howler.audio.resolveCalibration
import com.example.howler.audio.splFromDbfs
import com.example.howler.ui.theme.HowlerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    if (granted) {
                        MeterScreen(Modifier.padding(pad))
                    } else {
                        Centered("Microphone permission required.", Modifier.padding(pad))
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
    var dbfs by remember { mutableFloatStateOf(-160f) }
    var maxDbfs by remember { mutableFloatStateOf(-160f) }
    var over by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(true) }
    var weightingA by remember { mutableStateOf(true) }  // A is the SLM default
    var fast by remember { mutableStateOf(true) }         // Fast (125 ms) default
    // Tier-1 device sensitivity intentionally not auto-trusted (see Calibration.kt);
    // resolver uses the stored manual point or stays uncalibrated.
    var cal by remember { mutableStateOf<Calibration>(resolveCalibration(store.loadManual(), null)) }
    var showDialog by remember { mutableStateOf(false) }

    // A stream opened while the device is dozing/locked stays silenced for its
    // lifetime (verified on Pixel 10 Pro XL). Tie start/stop to RESUME/PAUSE so a
    // fresh stream opens every time the app returns to foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> started = AudioEngine.nativeStart()
                Lifecycle.Event.ON_PAUSE -> AudioEngine.nativeStop()
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
    // Max-hold units depend on weighting + time response — clear it when either changes.
    LaunchedEffect(weightingA, fast) { AudioEngine.nativeResetMax(); maxDbfs = -160f }
    LaunchedEffect(started, weightingA) {
        while (started) {
            dbfs = if (weightingA) AudioEngine.nativeLevelDbfsA() else AudioEngine.nativeLevelDbfs()
            maxDbfs = if (weightingA) AudioEngine.nativeMaxDbfsA() else AudioEngine.nativeMaxDbfs()
            over = AudioEngine.nativeOverRange()
            delay(50)
        }
    }

    val (big, caption) = readout(cal, dbfs, over, weightingA, fast)
    val maxLine = cal.splFromDbfs(maxDbfs)?.let { "Max %.1f".format(it) }
        ?: "Max %.1f".format(maxDbfs)
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!started) {
            Text("Audio stream failed to open.", textAlign = TextAlign.Center)
            return@Column
        }
        Text(big, style = MaterialTheme.typography.displayLarge)
        Text(caption, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (maxDbfs > -160f) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(maxLine, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { AudioEngine.nativeResetMax(); maxDbfs = -160f }) { Text("Reset") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { weightingA = true }, enabled = !weightingA) { Text("A") }
            TextButton(onClick = { weightingA = false }, enabled = weightingA) { Text("Z") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { fast = true }, enabled = !fast) { Text("Fast") }
            TextButton(onClick = { fast = false }, enabled = fast) { Text("Slow") }
        }
        Button(onClick = { showDialog = true }) {
            Text(if (cal is Calibration.Uncalibrated) "Calibrate" else "Recalibrate")
        }
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

/** Big readout + caption for the current calibration + measurement. */
private fun readout(cal: Calibration, dbfs: Float, over: Boolean, weightingA: Boolean, fast: Boolean): Pair<String, String> {
    if (over) return "OVER" to "over-range — source exceeds full scale, reading invalid"
    val w = if (weightingA) "A" else "Z"
    val t = if (fast) "F" else "S"
    return when (val spl = cal.splFromDbfs(dbfs)) {
        null -> "%.1f".format(dbfs) to "dBFS($w$t) · uncalibrated (relative only)"
        else -> "%.1f".format(spl) to "dB($w$t) SPL · calibrated (≈±2 dB at best)"
    }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual calibration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hold your reference meter at a steady source, then enter its reading.")
                Text("Phone now: %.1f dBFS".format(currentDbfs))
                OutlinedTextField(refSplText, { refSplText = it }, label = { Text("Reference dB SPL") })
                OutlinedTextField(meterText, { meterText = it }, label = { Text("Reference meter class") })
            }
        },
        confirmButton = {
            TextButton(enabled = refSpl != null, onClick = { onSave(refSpl!!, meterText) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("Clear") }
        },
    )
}

@Composable
private fun Centered(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(text, textAlign = TextAlign.Center) }
}
