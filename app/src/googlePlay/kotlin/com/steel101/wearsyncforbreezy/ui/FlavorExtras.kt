package com.steel101.wearsyncforbreezy.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewModelScope
import com.steel101.wearsyncforbreezy.AdbNetworkScanner
import com.steel101.wearsyncforbreezy.sync.AdbInstaller
import com.steel101.wearsyncforbreezy.sync.GooglePlaySyncManager
import com.steel101.wearsyncforbreezy.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Composable
fun SetupInstructions(showLoading: Boolean = true, onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    if (showLoading) {
        SetupInstructionsDialog(onDismiss = onDismiss, viewModel = viewModel)
    }
}

suspend fun checkIfSetupRequired(context: Context, watchVersionCode: Int): Boolean = withContext(Dispatchers.IO) {
    return@withContext watchVersionCode in 0 until BuildConfig.VERSION_CODE
}

@Composable
fun SetupInstructionsDialog(onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdbWizard by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var pushCooldown by remember { mutableIntStateOf(0) }
    var apkVersion by remember { mutableIntStateOf(0) }
    
    val apkFile = remember {
        val file = File(context.cacheDir, "wear_adb_install.apk")
        var shouldExtract = !file.exists()
        if (file.exists()) {
            val existingVersion = try {
                val pm = context.packageManager
                val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, 0)
                }
                info?.versionCode ?: 0
            } catch (e: Exception) { 0 }

            if (existingVersion != BuildConfig.VERSION_CODE) {
                shouldExtract = true
            }
        }
        
        if (shouldExtract) {
            try {
                context.assets.open("wear_companion.apk").use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("FlavorExtras", "Extracted wear_companion.apk (v${BuildConfig.VERSION_CODE})")
            } catch (e: Exception) { 
                Log.e("FlavorExtras", "Failed to extract asset", e) 
            }
        }
        file
    }

    if (showAdbWizard) {
        AdbWizardDialog(
            onDismiss = { showAdbWizard = false }, 
            onComplete = {
                showAdbWizard = false
                onDismiss()
            }, 
            viewModel = viewModel
        )
    }

    val watchVersionCode by (viewModel?.watchVersionCode ?: remember { MutableStateFlow(-1) }).collectAsState()

    AlertDialog(
        onDismissRequest = { 
            val isWatchUpToDate = watchVersionCode >= BuildConfig.VERSION_CODE
            if (isWatchUpToDate && !isWorking) onDismiss() 
        },
        title = { Text("Watch App Update Required") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val isWatchUpToDate = watchVersionCode >= BuildConfig.VERSION_CODE
                if (!isWatchUpToDate) {
                    Text(
                        "A new version of the watch app (v${BuildConfig.VERSION_CODE}) is required for full compatibility. Your watch is currently running v${if (watchVersionCode > 0) watchVersionCode else "unknown"}.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("To sync weather, you must sideload the latest companion app on your watch.")
                Spacer(modifier = Modifier.height(16.dp))

                Text("Option 1: Push Update", fontWeight = FontWeight.Bold)
                Text("Beams the update directly to your watch over Bluetooth.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        isWorking = true
                        statusMessage = "Pushing update to watch..."
                        pushCooldown = 10
                        scope.launch {
                            val success = GooglePlaySyncManager.sendApkToWatch(context, apkFile)
                            if (success) {
                                statusMessage = "Update sent! Check your watch for an install notification."
                                viewModel?.updateWatchVersion(context, BuildConfig.VERSION_CODE)
                            } else {
                                statusMessage = "Push failed. Ensure watch is connected and Bluetooth is on."
                            }
                            isWorking = false
                            
                            // Keep it greyed out for exactly 10 seconds total
                            while (pushCooldown > 0) {
                                delay(1000)
                                pushCooldown--
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !isWorking && pushCooldown == 0
                ) {
                    if (pushCooldown > 0) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending to Watch (${pushCooldown}s)")
                    } else {
                        if (isWorking && statusMessage.contains("Pushing")) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Push Update to Watch")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Option 2: Wireless ADB", fontWeight = FontWeight.Bold)
                Text("Reliable installation if Bluetooth push fails.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { showAdbWizard = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !isWorking && pushCooldown == 0
                ) {
                    Text("Start ADB Installer")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Option 3: Manual Installation", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/steel101/Wear-Sync-for-Breezy/releases"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Releases")
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

                if (statusMessage.isNotEmpty()) {
                    Text(statusMessage, color = if (statusMessage.contains("Error") || statusMessage.contains("failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                         fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            val isWatchUpToDate = watchVersionCode >= BuildConfig.VERSION_CODE
            if (isWatchUpToDate) {
                TextButton(onClick = onDismiss, enabled = !isWorking) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
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
    var isScanning by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = {
            Column {
                Text("ADB Installer - Step $step")
                LinearProgressIndicator(
                    progress = { step / 4f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    1 -> {
                        Text("Setup Instructions", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        InstructionStep(1, "Connect both devices to the same Wi-Fi.")
                        InstructionStep(2, "On Watch: Settings > System > About > Tap 'Build number' 7 times.")
                        InstructionStep(3, "On Watch: Settings > Developer options > Enable 'ADB debugging' and 'Wireless debugging'.")
                        InstructionStep(4, "Tap 'Wireless debugging' to see the IP and Port.")
                        InstructionStep(5, "Tap Pair new device.")

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    status = "Scanning for IP..."
                                    val scanner = AdbNetworkScanner()
                                    
                                    val discoveryJob = launch {
                                        scanner.discoverWatch(context) { foundIp, _ ->
                                            ipAddress = foundIp
                                            isScanning = false
                                            status = "" 
                                            step = 2 
                                            this@launch.cancel()
                                        }
                                    }
                                    
                                    val subnetJob = launch {
                                        val quickPorts = listOf(5555, 37000, 44000)
                                        scanner.scanSubnet(ports = quickPorts) { foundIp, _ ->
                                            if (isScanning) {
                                                ipAddress = foundIp
                                                isScanning = false
                                                status = ""
                                                step = 2 
                                                this@launch.cancel()
                                            }
                                        }
                                    }
                                    
                                    withTimeoutOrNull(8000) {
                                        while(isScanning) { delay(100) }
                                    }
                                    
                                    discoveryJob.cancel()
                                    subnetJob.cancel()
                                    isScanning = false
                                    if (step == 1 && status == "Scanning for IP...") {
                                        status = "No IP found. Try entering it manually."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning && !isWorking
                        ) {
                            if (isScanning && status.contains("IP")) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning for IP...")
                            } else {
                                Text("Scan for IP")
                            }
                        }

                        if (status.isNotEmpty() && step == 1) {
                            Text(status, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), 
                                 color = if (status.contains("Found")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    2 -> {
                        Text("Step 2: Enter Pairing Info", fontWeight = FontWeight.Bold)
                        Text("On Watch: Tap 'Pair new device'. Enter the Pairing Port and Code shown:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        val hasKeys = remember { File(context.filesDir, ".adb/adb_key").exists() }
                        if (hasKeys && ipAddress.isNotEmpty() && !ipAddress.endsWith(".")) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isWorking = true
                                        status = "Attempting to reconnect..."

                                        var foundPort = -1
                                        status = "Scanning for port..."
                                        withContext(Dispatchers.IO) {
                                            val allPorts = (30000..65535).toList()
                                            val semaphore = kotlinx.coroutines.sync.Semaphore(800)
                                            try {
                                                coroutineScope {
                                                    for (port in allPorts) {
                                                        launch {
                                                            semaphore.withPermit {
                                                                try {
                                                                    java.net.Socket().use { socket ->
                                                                        socket.connect(java.net.InetSocketAddress(ipAddress, port), 900)
                                                                        foundPort = port
                                                                        this@coroutineScope.cancel()
                                                                    }
                                                                } catch (e: Exception) {}
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: CancellationException) {}
                                        }

                                        if (foundPort != -1) {
                                            status = "Found port $foundPort. Connecting..."
                                            connectPort = foundPort.toString()
                                            val result = AdbInstaller.testConnection(context, ipAddress, foundPort)
                                            if (result.isSuccess) {
                                                status = "Reconnected successfully!"
                                                delay(800)
                                                step = 4
                                            } else {
                                                status = "Connection failed. Please pair again."
                                            }
                                        } else {
                                            val standardPorts = listOf(5555, 37000, 44000)
                                            var success = false
                                            for (port in standardPorts) {
                                                status = "Checking port $port..."
                                                val result = AdbInstaller.testConnection(context, ipAddress, port)
                                                if (result.isSuccess) {
                                                    connectPort = port.toString()
                                                    success = true
                                                    break
                                                }
                                            }
                                            
                                            if (success) {
                                                status = "Reconnected!"
                                                delay(800)
                                                step = 4
                                            } else {
                                                status = "Could not find active watch. Ensure Wireless Debugging is on."
                                            }
                                        }
                                        isWorking = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                enabled = !isScanning && !isWorking
                            ) {
                                if (isWorking && status.contains("Scanning")) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Finding Watch...")
                                } else {
                                    Text("Try Quick Reconnect")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text("Pairing Code") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isWorking
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = pairingPort,
                                onValueChange = { pairingPort = it },
                                label = { Text("Pairing Port") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isWorking
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isScanning = true
                                        status = "Searching for pairing port..."
                                        withContext(Dispatchers.IO) {
                                            val ports = (30000..65535).toList()
                                            val semaphore = kotlinx.coroutines.sync.Semaphore(800)
                                            try {
                                                coroutineScope {
                                                    for (port in ports) {
                                                        if (port.toString() == connectPort) continue
                                                        
                                                        launch {
                                                            semaphore.withPermit {
                                                                try {
                                                                    java.net.Socket().use { socket ->
                                                                        socket.connect(java.net.InetSocketAddress(ipAddress, port), 900)
                                                                        pairingPort = port.toString()
                                                                        status = "Found pairing port: $port"
                                                                        this@coroutineScope.cancel()
                                                                    }
                                                                } catch (e: Exception) {}
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: CancellationException) {}
                                        }
                                        isScanning = false
                                        if (pairingPort.isEmpty()) status = "Port not found. Make sure 'Pair new device' is open."
                                    }
                                },
                                enabled = !isScanning && !isWorking && ipAddress.isNotEmpty() && !ipAddress.endsWith(".")
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Scan")
                                }
                            }
                        }
                        if (status.isNotEmpty()) {
                            Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    3 -> {
                        Text("Step 3: Enter Connection Port", fontWeight = FontWeight.Bold)
                        Text("Go back to the main Wireless Debugging screen on the watch. Enter the port shown there (under IP address):", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = connectPort,
                                onValueChange = { connectPort = it },
                                label = { Text("Connection Port") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isWorking
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isScanning = true
                                        status = "Searching for connection port..."
                                        withContext(Dispatchers.IO) {
                                            val allPorts = (30000..65535).toList()
                                            val semaphore = kotlinx.coroutines.sync.Semaphore(800)
                                            try {
                                                coroutineScope {
                                                    for (port in allPorts) {
                                                        launch {
                                                            semaphore.withPermit {
                                                                try {
                                                                    java.net.Socket().use { socket ->
                                                                        socket.connect(java.net.InetSocketAddress(ipAddress, port), 900)
                                                                        connectPort = port.toString()
                                                                        status = "Found connection port: $port"
                                                                        this@coroutineScope.cancel()
                                                                    }
                                                                } catch (e: Exception) {}
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: CancellationException) {}
                                        }
                                        isScanning = false
                                        if (!status.contains("Found")) status = "Port not found. Enter manually."
                                    }
                                },
                                enabled = !isScanning && !isWorking && ipAddress.isNotEmpty() && !ipAddress.endsWith(".")
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Scan")
                                }
                            }
                        }
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
            Button(
                onClick = {
                    when (step) {
                        1 -> step = 2
                        2 -> {
                            scope.launch {
                                isWorking = true
                                status = "Pairing..."
                                val result = AdbInstaller.pairWatch(
                                    context, ipAddress, pairingPort.toIntOrNull() ?: 0, pairingCode
                                )
                                status = result.fold(
                                    onSuccess = { "Success! Tap Next." },
                                    onFailure = { "Pairing failed: ${it.message}" }
                                )
                                isWorking = false
                                if (result.isSuccess) {
                                    delay(800)
                                    step = 3
                                }
                            }
                        }
                        3 -> step = 4
                        4 -> {
                            scope.launch {
                                isWorking = true
                                status = "Installing..."
                                try {
                                    val apkFile = withContext(Dispatchers.IO) {
                                        val file = File(context.cacheDir, "wear_adb_install.apk")
                                        if (!file.exists()) {
                                            context.assets.open("wear_companion.apk").use { it.copyTo(file.outputStream()) }
                                        }
                                        file
                                    }
                                    val result = AdbInstaller.installToWatch(
                                        context, ipAddress, connectPort.toIntOrNull() ?: 5555, apkFile
                                    )
                                    if (result.isSuccess) {
                                        status = "Installation successful!"
                                        
                                        delay(5000) 
                                        
                                        status = "Opening watch app..."
                                        val launchResult = AdbInstaller.openWatchApp(context, ipAddress, connectPort.toIntOrNull() ?: 5555)
                                        
                                        if (launchResult.isSuccess) {
                                            status = "Installation complete! Enjoy."
                                            viewModel?.updateWatchVersion(context, BuildConfig.VERSION_CODE)
                                            delay(1000)
                                            onComplete()
                                            viewModel?.fetchAndSync(context)
                                        } else {
                                            status = "Installed, but failed to auto-open. Please open manually."
                                            isWorking = false
                                        }
                                    } else {
                                        status = "Error: ${result.exceptionOrNull()?.message}"
                                        isWorking = false
                                    }
                                } catch (e: Exception) {
                                    status = "Failed: ${e.message}"
                                    isWorking = false
                                }
                            }
                        }
                    }
                },
                enabled = !isWorking && (step != 2 || (pairingCode.length >= 6 && pairingPort.isNotEmpty()))
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(when (step) {
                    2 -> "Pair"
                    4 -> "Install"
                    else -> "Next"
                })
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (step > 1) { step--; status = "" } else onDismiss() },
                enabled = !isWorking
            ) {
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

fun requestWatchVersion(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        GooglePlaySyncManager.requestWatchVersion(context)
    }
}

fun startSyncService(context: Context) {
}

private fun saveApkToDownloads(context: Context) {
    val fileName = "WearSync-vInstaller.apk"
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put("relative_path", android.os.Environment.DIRECTORY_DOWNLOADS)
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
