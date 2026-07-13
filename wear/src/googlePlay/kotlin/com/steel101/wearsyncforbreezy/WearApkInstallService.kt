package com.steel101.wearsyncforbreezy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.WearableListenerService
import com.steel101.wearsyncforbreezy.wear.R
import java.io.File

class WearApkInstallService : WearableListenerService() {
    private val TAG = "WearApkInstallService"
    private val WEAR_APK_PATH = "/wear_apk_delivery"

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == WEAR_APK_PATH) {
            val apkDir = File(this.filesDir, "apks")
            if (!apkDir.exists()) apkDir.mkdirs()
            val apkFile = File(apkDir, "received_wear_app.apk")
            if (apkFile.exists()) apkFile.delete()
            
            val channelClient = com.google.android.gms.wearable.Wearable.getChannelClient(this)
            channelClient.receiveFile(channel, Uri.fromFile(apkFile), false)
            
            channelClient.registerChannelCallback(channel, object : ChannelClient.ChannelCallback() {
                override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                    if (closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL) {
                        Log.d(TAG, "APK received successfully. Size: ${apkFile.length()} bytes")
                        if (apkFile.exists() && apkFile.length() > 0) {
                            // Verify APK before showing notification
                            val pm = this@WearApkInstallService.packageManager
                            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                            if (info != null) {
                                Log.d(TAG, "APK verified: ${info.packageName}, version: ${info.versionCode}")
                                showInstallNotification(apkFile)
                            } else {
                                Log.e(TAG, "Received APK is invalid or corrupted (getPackageArchiveInfo returned null)")
                            }
                        } else {
                            Log.e(TAG, "APK file is empty or does not exist!")
                        }
                    } else {
                        Log.e(TAG, "Failed to receive APK: closeReason=$closeReason, errorCode=$appSpecificErrorCode")
                    }
                }
            })
        }
    }

    private fun showInstallNotification(file: File) {
        val channelId = "apk_install"
        val notificationId = 1001

        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, "App Updates", importance).apply {
                description = "Notifications for app updates"
            }
            notificationManager?.createNotificationChannel(channel)
        }

        // Use this (Context) for FileProvider
        val contentUri = FileProvider.getUriForFile(
            this,
            "${this.packageName}.fileprovider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            // Add explicitly for some Wear OS versions
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update Available")
            .setContentText("Tap to install the latest version of Wear Sync.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager?.notify(notificationId, builder.build())
        }
    }
}
