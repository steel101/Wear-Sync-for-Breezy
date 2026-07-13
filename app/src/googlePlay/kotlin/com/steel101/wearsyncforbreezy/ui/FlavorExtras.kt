package com.steel101.wearsyncforbreezy.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steel101.wearsyncforbreezy.WeatherSyncViewModel
import java.util.Locale

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SetupInstructions(showLoading: Boolean = true, onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    var visible by remember { mutableStateOf(showLoading) }
    if (visible) {
        SetupInstructionsDialog(onDismiss = {
            visible = false
            onDismiss()
        }, viewModel = viewModel)
    }
}

@Composable
fun SetupInstructionsDialog(onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    val context = LocalContext.current
    var showAdbWizard by remember { mutableStateOf(false) }

    if (showAdbWizard) {
        AdbWizardDialog(onDismiss = { showAdbWizard = false }, onComplete = {
            showAdbWizard = false
            onDismiss()
        }, viewModel = viewModel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch App Setup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("To sync weather, you must sideload the companion app on your watch:")
                Spacer(modifier = Modifier.height(16.dp))

                Text("Option A: Automatic (Recommended)", fontWeight = FontWeight.Bold)
                Text("Let this app install it for you using Wireless ADB.")
                Button(
                    onClick = { showAdbWizard = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Start ADB Installer")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Option B: Manual", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/steel101/Wear-Sync-for-Breezy/releases"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GitHub")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            saveApkToDownloads(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save APK")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("2. Enable Developer Mode", fontWeight = FontWeight.Bold)
                Text("On your Watch: Settings > System > About > Tap 'Build number' 7 times.")
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("3. Install via Bugjaeger", fontWeight = FontWeight.Bold)
                Text("Use Bugjaeger to install the APK you saved to your phone.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AdbWizardDialog(onDismiss: () -> Unit, onComplete: () -> Unit = {}, viewModel: WeatherSyncViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var ipAddress by remember { mutableStateOf("192.168.1.") }
    var connectPort by remember { mutableStateOf("5555") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADB Installer - Step $step") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    1 -> {
                        Text("1. Connect both devices to the same Wi-Fi.", fontWeight = FontWeight.Bold)
                        Text("2. On Watch: Settings > System > About > Tap 'Build number' 7 times.")
                        Text("3. On Watch: Settings > Developer options > Enable 'ADB debugging' and 'Wireless debugging'.")
                        Text("4. Tap 'Wireless debugging' to see the IP and Port.")
                    }
                    2 -> {
                        Text("Step 2: Enter Pairing Info", fontWeight = FontWeight.Bold)
                        Text("On Watch: Tap 'Pair new device'. Enter the Pairing Port and Code shown:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text("Pairing Code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pairingPort,
                            onValueChange = { pairingPort = it },
                            label = { Text("Pairing Port (NOT 5555)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (status.isNotEmpty()) {
                            Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    3 -> {
                        Text("Step 3: Enter Connection Port", fontWeight = FontWeight.Bold)
                        Text("Go back to the main Wireless Debugging screen on the watch. Enter the port shown there (usually 5555):")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = connectPort,
                            onValueChange = { connectPort = it },
                            label = { Text("Connection Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    4 -> {
                        Text("Ready to install!", fontWeight = FontWeight.Bold)
                        Text("This will beam the watch app directly to your device.")
                        if (status.isNotEmpty()) {
                            Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (step) {
                    1 -> step = 2
                    2 -> {
                        scope.launch {
                            status = "Pairing..."
                            val result = com.steel101.wearsyncforbreezy.sync.AdbInstaller.pairWatch(
                                context, ipAddress, pairingPort.toIntOrNull() ?: 0, pairingCode
                            )
                            status = result.fold(
                                onSuccess = { "Success! Tap Next." },
                                onFailure = { "Pairing failed: ${it.message}" }
                            )
                            if (result.isSuccess) {
                                delay(1000)
                                step = 3
                            }
                        }
                    }
                    3 -> step = 4
                    4 -> {
                        scope.launch {
                            status = "Installing..."
                            try {
                                val apkFile = withContext(Dispatchers.IO) {
                                    val file = File(context.cacheDir, "wear_adb_install.apk")
                                    context.assets.open("wear_companion.apk").use { it.copyTo(file.outputStream()) }
                                    file
                                }
                                val result = com.steel101.wearsyncforbreezy.sync.AdbInstaller.installToWatch(
                                    context, ipAddress, connectPort.toIntOrNull() ?: 5555, apkFile
                                )
                                if (result.isSuccess) {
                                    status = "Installation successful! Closing setup..."
                                    delay(500)
                                    onComplete()


                                    scope.launch {
                                        delay(5000)
                                        Log.d("FlavorExtras", "Opening watch app...")
                                        com.steel101.wearsyncforbreezy.sync.AdbInstaller.openWatchApp(
                                            context, ipAddress, connectPort.toIntOrNull() ?: 5555
                                        )
                                        
                                        delay(5000)
                                        Log.d("FlavorExtras", "Syncing weather...")
                                        viewModel?.fetchAndSync(context)
                                    }
                                } else {
                                    status = "Error: ${result.exceptionOrNull()?.message}"
                                }
                            } catch (e: Exception) {
                                status = "Failed: ${e.message}"
                            }
                        }
                    }
                }
            }) {
                Text(when (step) {
                    2 -> "Pair"
                    4 -> "Install"
                    else -> "Next"
                })
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step > 1) step-- else onDismiss() }) {
                Text(if (step > 1) "Back" else "Cancel")
            }
        }
    )
}

@Composable
fun FlavorSettings(viewModel: WeatherSyncViewModel) {
    var showInstructions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Google Play Sync Settings", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Weather is synced automatically via Google Play Services.")
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { showInstructions = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Show Sideload Instructions")
        }

        if (showInstructions) {
            SetupInstructionsDialog(onDismiss = { showInstructions = false }, viewModel = viewModel)
        }
    }
}

suspend fun getWatchStatus(context: Context): String {
    return "Google Play Services Active"
}

fun startSyncService(context: Context) {
    // Google Play Services uses WearableListenerService which is auto-started
}

private fun saveApkToDownloads(context: Context) {
    val fileName = "${com.steel101.wearsyncforbreezy.shared.BuildConfig.VERSION_CODE}.watch.apk"
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                context.assets.open("wear_companion.apk").use { input ->
                    resolver.openOutputStream(uri).use { output ->
                        if (output != null) {
                            input.copyTo(output)
                        }
                    }
                }
                android.widget.Toast.makeText(context, "Saved to Downloads: $fileName", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(context, "Please grant storage permission in settings to save the APK", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadsDir, fileName)

            context.assets.open("wear_companion.apk").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            android.widget.Toast.makeText(context, "Saved to Downloads: $fileName", android.widget.Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Log.e("FlavorExtras", "Failed to save APK", e)
        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
