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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.steel101.wearsyncforbreezy.BuildConfig
import com.steel101.wearsyncforbreezy.sync.SyncMode
import com.steel101.wearsyncforbreezy.sync.FossBluetoothSyncManager
import java.util.Locale

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewModelScope
import com.steel101.wearsyncforbreezy.AdbNetworkScanner
import com.steel101.wearsyncforbreezy.sync.AdbInstaller
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object CompanionApkManager {
    private const val REPO_PATH = "steel101/Wear-Sync-for-Breezy"
    private const val FILE_NAME = "Wear-FOSS-Release.apk"
    
    fun getCachedApk(context: Context): File {
        return File(context.cacheDir, "companion_wear_app.apk")
    }

    suspend fun downloadFromGithub(
        context: Context, 
        versionName: String, 
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = "https://github.com/$REPO_PATH/releases/download/$versionName/$FILE_NAME"
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val retryUrl = "https://github.com/$REPO_PATH/releases/download/v$versionName/$FILE_NAME"
                return@withContext downloadFromUrl(context, URL(retryUrl), onProgress)
            }
            
            downloadFromUrl(context, url, onProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFromUrl(context: Context, url: URL, onProgress: (Int) -> Unit): Result<File> {
        return try {
            val connection = url.openConnection() as HttpURLConnection
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return Result.failure(IOException("Server returned code ${connection.responseCode} for ${url}"))
            }

            val fileLength = connection.contentLength
            val targetFile = getCachedApk(context)
            
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val data = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress((total * 100 / fileLength).toInt())
                        }
                        output.write(data, 0, count)
                    }
                }
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveFromUri(context: Context, uri: Uri): Result<File> {
        return try {
            val targetFile = getCachedApk(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(IOException("Could not open URI"))
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun DownloadWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("External Download Warning") },
        text = {
            Text(
                "You are about to download the Wear OS companion app directly from GitHub. " +
                "Please note that this specific companion binary has not been scanned or verified by the IzzyOnDroid repository team. " +
                "Do you wish to proceed?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Proceed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetupInstructions(showLoading: Boolean = true, onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    if (showLoading) {
        SetupInstructionsDialog(onDismiss = onDismiss, viewModel = viewModel)
    }
}

@Composable
fun SetupInstructionsDialog(onDismiss: () -> Unit, viewModel: WeatherSyncViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdbWizard by rememberSaveable { mutableStateOf(false) }
    var showDownloadWarning by rememberSaveable { mutableStateOf(false) }
    
    var apkFile by remember { mutableStateOf<File?>(if (CompanionApkManager.getCachedApk(context).exists()) CompanionApkManager.getCachedApk(context) else null) }
    var statusMessage by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    val startDownload = {
        showDownloadWarning = false
        isWorking = true
        statusMessage = "Downloading from GitHub..."
        scope.launch {
            val result = CompanionApkManager.downloadFromGithub(context, BuildConfig.VERSION_NAME) { downloadProgress = it }
            result.onSuccess { file ->
                apkFile = file
                statusMessage = "Download complete!"
            }.onFailure { e ->
                statusMessage = "Download failed: ${e.message}"
            }
            isWorking = false
        }
    }

    if (showDownloadWarning) {
        DownloadWarningDialog(
            onConfirm = { startDownload() },
            onDismiss = { showDownloadWarning = false }
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isWorking = true
            statusMessage = "Copying selected file..."
            scope.launch {
                val result = CompanionApkManager.saveFromUri(context, it)
                result.onSuccess { file ->
                    apkFile = file
                    statusMessage = "APK ready!"
                }.onFailure { e ->
                    statusMessage = "Error: ${e.message}"
                }
                isWorking = false
            }
        }
    }

    if (showAdbWizard) {
        AdbWizardDialog(
            apkFile = apkFile!!,
            onDismiss = { showAdbWizard = false }, 
            onComplete = {
                showAdbWizard = false
                onDismiss()
            }, 
            viewModel = viewModel
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Watch App Setup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("To sync weather, you must sideload the companion app on your watch.")
                Spacer(modifier = Modifier.height(16.dp))

                Text("Step 1: Get the Companion APK", fontWeight = FontWeight.Bold)
                if (apkFile == null) {
                    Text("Select how to retrieve the watch app binary:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isWorking) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            if (downloadProgress > 0) {
                                Text("$downloadProgress%", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Button(
                            onClick = { showDownloadWarning = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Download from GitHub")
                        }
                        
                        OutlinedButton(
                            onClick = { filePicker.launch("application/vnd.android.package-archive") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Select Local APK File")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Icon(Icons.Default.CheckCircle, "Ready", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("APK is ready for installation", color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { apkFile = null; CompanionApkManager.getCachedApk(context).delete() }) {
                            Text("Clear")
                        }
                    }
                }

                if (statusMessage.isNotEmpty() && !isWorking) {
                    Text(statusMessage, color = if (statusMessage.contains("Error") || statusMessage.contains("failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                         fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Step 2: Install to Watch", fontWeight = FontWeight.Bold)
                Button(
                    onClick = { showAdbWizard = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = apkFile != null && !isWorking
                ) {
                    Text("Start ADB Installer")
                }
                
                TextButton(
                    onClick = {
                        apkFile?.let { saveApkToDownloads(context, it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apkFile != null
                ) {
                    Text("Save APK to Downloads")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Helpful Links", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/steel101/Wear-Sync-for-Breezy/releases"))
                    context.startActivity(intent)
                }) {
                    Text("Manual GitHub Release Page", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text("Close")
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
fun AdbWizardDialog(apkFile: File, onDismiss: () -> Unit, onComplete: () -> Unit = {}, viewModel: WeatherSyncViewModel? = null) {
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    status = "Scanning network..."
                                    val scanner = AdbNetworkScanner()
                                    // Scan a wider range of likely ADB ports
                                    scanner.scanSubnet(ports = listOf(5555, 5556, 5557, 5558)) { foundIp: String, foundPort: Int ->
                                        ipAddress = foundIp
                                        connectPort = foundPort.toString()
                                        status = "Found device at $foundIp"
                                    }
                                    isScanning = false
                                    if (status == "Scanning network...") status = "No device found. Ensure ADB is enabled on the watch."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning && !isWorking
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Text("Scan for Watch")
                            }
                        }
                        if (status.isNotEmpty()) {
                            Text(status, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), 
                                 color = if (status.contains("Found")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    2 -> {
                        Text("Step 2: Enter Pairing Info", fontWeight = FontWeight.Bold)
                        Text("On Watch: Tap 'Pair new device'. Enter the Pairing Port and Code shown:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
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
                                            val ports = (37000..45000).toList()
                                            ports.chunked(100).forEach { batch ->
                                                if (pairingPort.isNotEmpty()) return@forEach
                                                coroutineScope {
                                                    for (port in batch) {
                                                        launch {
                                                            try {
                                                                java.net.Socket().use { socket ->
                                                                    socket.connect(java.net.InetSocketAddress(ipAddress, port), 150)
                                                                    pairingPort = port.toString()
                                                                    status = "Found pairing port: $port"
                                                                    this@coroutineScope.cancel()
                                                                }
                                                            } catch (e: Exception) {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        isScanning = false
                                        if (pairingPort.isEmpty()) status = "Port not found. Enter manually."
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
                        Text("Go back to the main Wireless Debugging screen on the watch. Enter the port shown there (usually 5555):", style = MaterialTheme.typography.bodySmall)
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
                                        val ports = listOf(5555) + (37000..45000).toList()
                                        withContext(Dispatchers.IO) {
                                            ports.chunked(100).forEach { batch ->
                                                if (status.contains("Found")) return@forEach
                                                coroutineScope {
                                                    for (port in batch) {
                                                        launch {
                                                            try {
                                                                java.net.Socket().use { socket ->
                                                                    socket.connect(java.net.InetSocketAddress(ipAddress, port), 150)
                                                                    connectPort = port.toString()
                                                                    status = "Found connection port: $port"
                                                                    this@coroutineScope.cancel()
                                                                }
                                                            } catch (e: Exception) {}
                                                        }
                                                    }
                                                }
                                            }
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
                                    val result = AdbInstaller.installToWatch(
                                        context, ipAddress, connectPort.toIntOrNull() ?: 5555, apkFile
                                    )
                                    if (result.isSuccess) {
                                        status = "Installation successful!"
                                        delay(800)
                                        onComplete()

                                        viewModel?.let { vm ->
                                            vm.viewModelScope.launch {
                                                delay(2000)
                                                Log.d("FlavorExtras", "Opening watch app...")
                                                AdbInstaller.openWatchApp(context, ipAddress, connectPort.toIntOrNull() ?: 5555)
                                                
                                                delay(5000)
                                                Log.d("FlavorExtras", "Syncing weather...")
                                                vm.fetchAndSync(context)
                                            }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE) }
    var watchId by remember { mutableStateOf(prefs.getString("watch_id", "") ?: "") }
    var syncMode by remember { mutableStateOf(prefs.getString("sync_mode", SyncMode.AUTO.name) ?: SyncMode.AUTO.name) }
    var installStatus by remember { mutableStateOf("") }
    
    val watchVersionCode by viewModel.watchVersionCode.collectAsState()
    val currentVersionCode = BuildConfig.VERSION_CODE
    val isUpToDate = watchVersionCode >= currentVersionCode
    var showConfirmDialog by remember { mutableStateOf(false) }

    val apkFile = remember { CompanionApkManager.getCachedApk(context) }
    val hasApk = apkFile.exists()
    var showDownloadWarning by rememberSaveable { mutableStateOf(false) }

    val startDownload = {
        showDownloadWarning = false
        installStatus = "Downloading..."
        scope.launch {
            CompanionApkManager.downloadFromGithub(context, BuildConfig.VERSION_NAME) { /* progress */ }
                .onSuccess { installStatus = "Download complete!" }
                .onFailure { installStatus = "Download failed: ${it.message}" }
        }
    }

    if (showDownloadWarning) {
        DownloadWarningDialog(
            onConfirm = { startDownload() },
            onDismiss = { showDownloadWarning = false }
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                installStatus = "Copying APK..."
                CompanionApkManager.saveFromUri(context, it).onSuccess {
                    installStatus = "APK ready for push."
                }.onFailure { e ->
                    installStatus = "Error: ${e.message}"
                }
            }
        }
    }

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
        
        if (!hasApk) {
            Text("Watch APK not found. Download it first to enable push update.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Button(
                    onClick = { showDownloadWarning = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Download")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { filePicker.launch("application/vnd.android.package-archive") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select File")
                }
            }
        }

        Button(
            onClick = {
                if (isUpToDate) {
                    showConfirmDialog = true
                } else {
                    performPushUpdate(context, scope, apkFile, { installStatus = it }, { installStatus = it }, viewModel)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasApk && (installStatus.isEmpty() || !installStatus.contains("..."))
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
                        performPushUpdate(context, scope, apkFile, { installStatus = it }, { installStatus = it }, viewModel)
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
            SetupInstructionsDialog(onDismiss = { showInstructions = false }, viewModel = viewModel)
        }
    }
}

fun getWatchStatus(context: Context): String {
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
    
    when (mode) {
        SyncMode.BLUETOOTH.name -> {
            context.stopService(mqttIntent)
            ContextCompat.startForegroundService(context, btIntent)
        }
        SyncMode.MQTT.name -> {
            context.stopService(btIntent)
            ContextCompat.startForegroundService(context, mqttIntent)
        }
        else -> {
            ContextCompat.startForegroundService(context, mqttIntent)
            ContextCompat.startForegroundService(context, btIntent)
        }
    }
}

private fun performPushUpdate(
    context: Context,
    scope: CoroutineScope,
    apkFile: File,
    onStatusChange: (String) -> Unit,
    onProgress: (String) -> Unit,
    viewModel: WeatherSyncViewModel? = null
) {
    scope.launch {
        Log.d("FlavorExtras", "Push Update started")
        if (!apkFile.exists()) {
            onStatusChange("Error: APK file missing")
            return@launch
        }
        
        try {
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
                // Optimistically update version so button greys out
                viewModel?.updateWatchVersion(BuildConfig.VERSION_CODE)
            } else {
                onStatusChange("Failed. Ensure Watch app is open and paired.")
            }
        } catch (e: Exception) {
            Log.e("FlavorExtras", "Update failed", e)
            onStatusChange("Error: ${e.message}")
        }
    }
}

private fun saveApkToDownloads(context: Context, apkFile: File) {
    val fileName = "WearSync-v${BuildConfig.VERSION_NAME}.apk"
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
                apkFile.inputStream().use { input ->
                    resolver.openOutputStream(uri).use { output ->
                        if (output != null) {
                            input.copyTo(output)
                        }
                    }
                }
                android.widget.Toast.makeText(context, "Saved to Downloads: $fileName", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            // ... storage permission check already in original code if needed
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadsDir, fileName)

            apkFile.inputStream().use { input ->
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
