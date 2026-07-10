package com.steel101.wearsyncforbreezy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.steel101.wearsyncforbreezy.WeatherSyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val WEAR_CAPABILITY = "wear_sync_app"
private const val WEAR_APK_PATH = "/wear_apk_delivery"

@Composable
fun SetupInstructions(showLoading: Boolean = true, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(true) }
    var needsInstall by remember { mutableStateOf(false) }
    var isUpdate by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf("") }
    var connectedNodeId by remember { mutableStateOf<String?>(null) }
    var showSideloadInstructions by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }

    if (!visible) return

    LaunchedEffect(Unit) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isNotEmpty()) {
                val capabilityInfo = Wearable.getCapabilityClient(context)
                    .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                
                val nodesWithApp = capabilityInfo.nodes
                val targetNode = nodes.firstOrNull { n -> nodesWithApp.any { it.id == n.id } } ?: nodes[0]
                connectedNodeId = targetNode.id
                val nodeId = connectedNodeId!!

                var watchVersion: Long? = null
                val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
                    if (messageEvent.path == "/version_info") {
                        watchVersion = String(messageEvent.data).toLongOrNull()
                    }
                }
                Wearable.getMessageClient(context).addListener(listener)
                
                try {
                    Wearable.getMessageClient(context).sendMessage(nodeId, "/request_version", null).await()
                    for (i in 1..40) {
                        if (watchVersion != null) break
                        delay(100)
                    }
                } finally {
                    Wearable.getMessageClient(context).removeListener(listener)
                }

                val phoneVersion = context.packageManager.getPackageInfo(context.packageName, 0).let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
                }

                if (watchVersion != null) {
                    if (watchVersion!! < phoneVersion) {
                        needsInstall = true
                        isUpdate = true
                    }
                } else {
                    val hasCapability = nodesWithApp.any { it.id == nodeId }
                    if (!hasCapability) {
                        needsInstall = true
                        isUpdate = false
                    } else {
                        needsInstall = true
                        isUpdate = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FlavorExtras", "Check failed", e)
        }
        isChecking = false
    }

    if (isChecking) {
        if (showLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Checking Watch...") },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        }
    } else if (showSideloadInstructions) {
        SideloadInstructionsDialog(onDismiss = {
            visible = false
            onDismiss()
        })
    } else if (needsInstall) {
        AlertDialog(
            onDismissRequest = {
                visible = false
                onDismiss()
            },
            title = { Text(if (isUpdate) "Update Available" else "Watch App Required") },
            text = {
                Column {
                    Text(if (isUpdate) 
                        "A newer version of the companion app is available for your watch." 
                        else "The companion app is not installed on your watch. It must be manually installed first.")
                    if (installStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(installStatus, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            installStatus = "Preparing APK..."
                            try {
                                val nodeId = connectedNodeId ?: return@launch
                                val apkFile = withContext(Dispatchers.IO) {
                                    val file = File(context.cacheDir, "wear_companion.apk")
                                    context.assets.open("wear_companion.apk").use { input ->
                                        FileOutputStream(file).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    file
                                }
                                
                                installStatus = "Connecting to Watch..."
                                val channelClient = Wearable.getChannelClient(context)
                                val channel = channelClient.openChannel(nodeId, WEAR_APK_PATH).await()
                                
                                installStatus = "Sending APK..."
                                channelClient.sendFile(channel, Uri.fromFile(apkFile)).await()
                                
                                installStatus = "Sent! Check your watch to install."
                                delay(2000)
                                visible = false
                                onDismiss()
                            } catch (e: Exception) {
                                Log.e("FlavorExtras", "Failed to send APK", e)
                                installStatus = "Error: ${e.message}"
                                if (!isUpdate) {
                                    delay(1000)
                                    showSideloadInstructions = true
                                }
                            }
                        }
                    },
                    enabled = (isUpdate && (installStatus.isEmpty() || installStatus.startsWith("Error")))
                ) {
                    Text(if (isUpdate) "Update Watch" else "Install (Requires App)")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!isUpdate) {
                        showSideloadInstructions = true
                    } else {
                        visible = false
                        onDismiss()
                    }
                }) {
                    Text(if (isUpdate) "Maybe Later" else "Sideload Instructions")
                }
            }
        )
    } else {
        LaunchedEffect(Unit) { 
            visible = false
            onDismiss() 
        }
    }
}

@Composable
fun SideloadInstructionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch App Sideload") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("To sync weather, you must sideload the companion app on your watch:")
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("1. Download the Wear APK", fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/steel101/Wear-Sync-for-Breezy/releases"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Open GitHub Releases")
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
}

suspend fun getWatchStatus(context: Context): String {
    return try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) {
            "Watch Disconnected"
        } else {
            val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            
            val nodesWithApp = capabilityInfo.nodes
            val activeNode = nodes.firstOrNull { n -> nodesWithApp.any { it.id == n.id } } ?: nodes[0]
            val nodeName = activeNode.displayName
            val installed = nodesWithApp.any { it.id == activeNode.id }
            
            if (!installed) {
                "Watch Connected: $nodeName (App Missing)"
            } else {
                var watchVersion: String? = null
                val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
                    if (messageEvent.path == "/version_info") {
                        watchVersion = String(messageEvent.data)
                    }
                }
                Wearable.getMessageClient(context).addListener(listener)
                try {
                    Wearable.getMessageClient(context).sendMessage(activeNode.id, "/request_version", null).await()
                    for (i in 1..25) {
                        if (watchVersion != null) break
                        delay(100)
                    }
                } finally {
                    Wearable.getMessageClient(context).removeListener(listener)
                }
                
                if (watchVersion != null) {
                    "Watch Connected: $nodeName (v$watchVersion)"
                } else {
                    "Watch Connected: $nodeName (App Installed)"
                }
            }
        }
    } catch (e: Exception) {
        "Status Unknown"
    }
}

fun startSyncService(context: Context) {
}
