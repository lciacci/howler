package com.example.howler

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.howler.audio.AudioEngine
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

private fun hasMicPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun MeterScreen(modifier: Modifier = Modifier) {
    var dbfs by remember { mutableFloatStateOf(-160f) }
    var over by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(true) }

    // NOTE: a stream opened while the device is dozing/locked stays silenced for
    // its lifetime (verified on Pixel 10 Pro XL). TODO: restart on lifecycle
    // resume so backgrounding then returning yields a fresh, unsilenced stream.
    DisposableEffect(Unit) {
        started = AudioEngine.nativeStart()
        onDispose { AudioEngine.nativeStop() }
    }
    LaunchedEffect(started) {
        while (started) {
            dbfs = AudioEngine.nativeLevelDbfs()
            over = AudioEngine.nativeOverRange()
            delay(50)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!started) {
            Text("Audio stream failed to open.", textAlign = TextAlign.Center)
            return@Column
        }
        Text(
            text = if (over) "OVER" else "%.1f".format(dbfs),
            style = MaterialTheme.typography.displayLarge,
        )
        Text("dBFS · uncalibrated (relative)", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Centered(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(text, textAlign = TextAlign.Center) }
}
