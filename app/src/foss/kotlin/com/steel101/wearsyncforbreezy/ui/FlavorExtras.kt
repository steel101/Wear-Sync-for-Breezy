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
import androidx.core.content.ContextCompat
import com.steel101.wearsyncforbreezy.WeatherSyncViewModel
import com.steel101.wearsyncforbreezy.MqttFossPhoneListenerService
import com.steel101.wearsyncforbreezy.FossBluetoothPhoneListenerService
import com.steel101.wearsyncforbreezy.sync.SyncMode
import com.steel101.wearsyncforbreezy.sync.FossBluetoothSyncManager
import java.util.Locale

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SetupInstructions(showLoading: Boolean = true, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(showLoading) }
    if (visible) {
        SetupInstructionsDialog(onDismiss = {
            visible = false
            onDismiss()
        })
    }
}

@Composable
fun SetupInstructionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch App Setup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("To sync weather, you must sideload the companion app on your watch:")
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("1. Download the Wear APK", fontWeight = FontWeight.Bold)
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
                        Text("Save to Phone")
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("2. Enable Developer Mode", fontWeight = FontWeight.Bold)
                Text("On your Watch: Settings > System > About > Tap 'Build number' 7 times.")
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("3. Enable ADB & Wi-Fi Debugging", fontWeight = FontWeight.Bold)
                Text("On your Watch: Settings > Developer options > Enable 'ADB debugging' and 'Debug over Wi-Fi'. Note the IP address.")
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("4. Sideload the APK", fontWeight = FontWeight.Bold)
                Text("On your Phone: Install 'Bugjaeger' from Play Store. Use it to connect to your watch's IP and install the APK you downloaded.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun FlavorSettings(viewModel: WeatherSyncViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }
    var watchId by remember { mutableStateOf(prefs.getString("watch_id", "") ?: "") }
    var syncMode by remember { mutableStateOf(prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name) }
    var installStatus by remember { mutableStateOf("") }
    
    val watchVersionCode by viewModel.watchVersionCode.collectAsState()
    val currentVersionCode = com.steel101.wearsyncforbreezy.shared.BuildConfig.VERSION_CODE
    val isUpToDate = watchVersionCode >= currentVersionCode
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("FOSS Sync Settings", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Sync Mode", fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    RadioButton(
                        selected = syncMode == mode.name,
                        onClick = {
                            syncMode = mode.name
                            prefs.edit().putString("sync_mode", mode.name).apply()
                            startSyncService(context)
                        }
                    )
                    Text(mode.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (syncMode != SyncMode.BLUETOOTH.name) {
            TextField(
                value = watchId,
                onValueChange = {
                    watchId = it
                    prefs.edit().putString("watch_id", it).apply()
                },
                label = { Text("MQTT Watch ID (from Watch app)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Device Actions", fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                if (isUpToDate) {
                    showConfirmDialog = true
                } else {
                    performPushUpdate(context, scope, { installStatus = it }, { installStatus = it })
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (installStatus.isEmpty() || !installStatus.contains("..."))
        ) {
            Text(if (isUpToDate) "Watch App is Up to Date" else "Push Update to Watch (BT)")
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("App Up to Date") },
                text = { Text("The watch app version matches the phone. Do you want to push the update anyway?") },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirmDialog = false
                        performPushUpdate(context, scope, { installStatus = it }, { installStatus = it })
                    }) {
                        Text("Push Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (installStatus.isNotEmpty()) {
            Text(installStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        var showInstructions by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showInstructions = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Show Sideload Instructions")
        }

        if (showInstructions) {
            SetupInstructionsDialog(onDismiss = { showInstructions = false })
        }
    }
}

suspend fun getWatchStatus(context: Context): String {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    val watchId = prefs.getString("watch_id", "")
    val mode = prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name
    
    return when (mode) {
        SyncMode.MQTT.name -> if (watchId.isNullOrEmpty()) "Watch ID not set" else "MQTT Active ($watchId)"
        SyncMode.BLUETOOTH.name -> "FOSS Bluetooth Active"
        else -> "Auto Mode (FOSS BT preferred)"
    }
}

fun startSyncService(context: Context) {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    val mode = prefs.getString("sync_mode", SyncMode.AUTO.name)
    
    val mqttIntent = Intent(context, MqttFossPhoneListenerService::class.java)
    val btIntent = Intent(context, FossBluetoothPhoneListenerService::class.java)
    
    if (mode == SyncMode.BLUETOOTH.name) {
        context.stopService(mqttIntent)
        ContextCompat.startForegroundService(context, btIntent)
    } else if (mode == SyncMode.MQTT.name) {
        context.stopService(btIntent)
        ContextCompat.startForegroundService(context, mqttIntent)
    } else {
        ContextCompat.startForegroundService(context, mqttIntent)
        ContextCompat.startForegroundService(context, btIntent)
    }
}

private fun performPushUpdate(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onStatusChange: (String) -> Unit,
    onProgress: (String) -> Unit
) {
    scope.launch {
        Log.d("FlavorExtras", "Push Update started")
        onStatusChange("Preparing APK...")
        try {
            val apkFile = withContext(Dispatchers.IO) {
                Log.d("FlavorExtras", "Opening asset wear_companion.apk")
                val file = File(context.cacheDir, "wear_foss_companion.apk")
                context.assets.open("wear_companion.apk").use { input ->
                    val size = input.available()
                    Log.d("FlavorExtras", "Asset size: $size")
                    if (size < 100000) { // APKs should be larger than 100KB
                        throw Exception("APK in assets is too small or missing ($size bytes)")
                    }
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("FlavorExtras", "APK prepared at ${file.absolutePath}")
                file
            }
            
            onStatusChange("Checking Bluetooth...")
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            if (bluetoothManager.adapter?.isEnabled != true) {
                onStatusChange("Error: Bluetooth is off")
                return@launch
            }

            onStatusChange("Sending via Bluetooth...")
            Log.d("FlavorExtras", "Calling sendApkToWatch")
            val success = FossBluetoothSyncManager.sendApkToWatch(context, apkFile) { progress ->
                onProgress("Sending: $progress%")
            }
            if (success) {
                onStatusChange("Update sent! Check watch.")
            } else {
                onStatusChange("Failed. Ensure Watch app is open and paired.")
            }
        } catch (e: Exception) {
            Log.e("FlavorExtras", "Update failed", e)
            onStatusChange("Error: ${e.message}")
        }
    }
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
            // Check permission for older versions
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
