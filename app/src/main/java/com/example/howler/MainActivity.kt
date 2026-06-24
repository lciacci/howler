package com.example.howler

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.howler.audio.InputRung
import com.example.howler.audio.MicProbe
import com.example.howler.audio.OpenAttempt
import com.example.howler.audio.ProbeResult
import com.example.howler.audio.confirmedRung
import com.example.howler.audio.inputRung
import com.example.howler.audio.runInputProbe
import com.example.howler.audio.sensitivityVerdict
import com.example.howler.ui.theme.HowlerTheme

private const val TAG = "HowlerProbe"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val device = probeDevice()
        android.util.Log.i(TAG, device.toString())
        setContent {
            HowlerTheme {
                val context = LocalContext.current
                var input by remember { mutableStateOf<List<OpenAttempt>?>(null) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    input = if (granted) runInputProbe()
                    else listOf(OpenAttempt("(denied)", false, 0, 0, "", 0, "RECORD_AUDIO denied"))
                    android.util.Log.i(TAG, "inputProbe=$input")
                }
                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        input = runInputProbe()
                        android.util.Log.i(TAG, "inputProbe=$input")
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    ProbeReport(device, input, Modifier.padding(pad))
                }
            }
        }
    }

    private fun probeDevice(): ProbeResult {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(
            AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED
        ).let { it == "true" || it == "1" }
        val sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val fpb = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
        return runCatching { am.microphones }.fold(
            onSuccess = { mics -> result(unprocessed, sr, fpb, mics.map { it.toProbe() }, null) },
            onFailure = { e -> result(unprocessed, sr, fpb, emptyList(), e.message ?: "$e") },
        )
    }

    private fun result(
        unprocessed: Boolean, sr: Int?, fpb: Int?, mics: List<MicProbe>, err: String?,
    ) = ProbeResult(
        device = "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}",
        unprocessedSupported = unprocessed,
        outputSampleRateHz = sr,
        outputFramesPerBuffer = fpb,
        microphones = mics,
        micError = err,
    )
}

private fun MicrophoneInfo.toProbe(): MicProbe {
    val s = sensitivity
    return MicProbe(
        description = description.ifBlank { "(no description)" },
        location = micLocation(location),
        sensitivityDbFs = if (s == MicrophoneInfo.SENSITIVITY_UNKNOWN) null else s,
    )
}

private fun micLocation(loc: Int): String = when (loc) {
    MicrophoneInfo.LOCATION_MAINBODY -> "main body"
    MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> "main body (movable)"
    MicrophoneInfo.LOCATION_PERIPHERAL -> "peripheral"
    else -> "unknown"
}

@Composable
private fun ProbeReport(device: ProbeResult, input: List<OpenAttempt>?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Howler — STEP ZERO probe", style = MaterialTheme.typography.titleLarge)
        Text(device.device, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Probe A — UNPROCESSED (authoritative)", style = MaterialTheme.typography.titleMedium)
        Text("property hint: ${yesNo(device.unprocessedSupported)} → ${rungLabel(inputRung(device.unprocessedSupported))}")
        InputProbeSection(input)
        Text("framework: ${device.outputSampleRateHz ?: "?"} Hz · ${device.outputFramesPerBuffer ?: "?"} frames/buf")
        HorizontalDivider()
        Text("Probe B — getSensitivity()", style = MaterialTheme.typography.titleMedium)
        Text(sensitivityVerdict(device.microphones.map { it.sensitivityDbFs }))
        device.micError?.let { Text("getMicrophones() error: $it") }
        device.microphones.forEachIndexed { i, m -> MicRow(i, m) }
    }
}

@Composable
private fun InputProbeSection(input: List<OpenAttempt>?) {
    if (input == null) {
        Text("opening sources… (grant mic permission)")
        return
    }
    Text("CONFIRMED: ${confirmedRung(input)}", style = MaterialTheme.typography.bodyLarge)
    input.forEach { a ->
        val detail = if (a.opened) "OPEN · ${a.sampleRate} Hz · ${a.channels}ch · ${a.framesRead} frames"
        else "FAILED · ${a.error ?: "?"}"
        Text("• ${a.sourceName}: $detail", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MicRow(index: Int, m: MicProbe) {
    val sens = m.sensitivityDbFs?.let { "%.1f dBFS".format(it) } ?: "unpopulated"
    Text("• [$index] ${m.description} · ${m.location} · $sens",
        style = MaterialTheme.typography.bodyMedium)
}

private fun rungLabel(rung: InputRung): String = when (rung) {
    InputRung.UNPROCESSED -> "UNPROCESSED"
    InputRung.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
}

private fun yesNo(b: Boolean): String = if (b) "YES" else "NO"
