package com.steel101.wearsyncforbreezy.sync

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

import android.sun.security.x509.*

object AdbInstaller {
    private const val TAG = "AdbInstaller"

    private class AdbManager(
        private val privateKey: PrivateKey,
        private val certificate: Certificate
    ) : AbsAdbConnectionManager() {
        override fun getPrivateKey(): PrivateKey = privateKey
        override fun getCertificate(): Certificate = certificate
        override fun getDeviceName(): String = "BreezyPhone"
        
        var currentIp: String? = null
        var currentPort: Int? = null
        var isAdbConnected: Boolean = false

        init {
            api = Build.VERSION.SDK_INT
        }

        fun connectRobustly(ip: String, port: Int): Boolean {
            if (isAdbConnected && currentIp == ip && currentPort == port) {
                Log.d("AdbInstaller", "Already connected to $ip:$port, reusing session")
                return true
            }

            if (isAdbConnected) {
                try { disconnect() } catch (e: Exception) {}
            }

            return try {
                if (super.connect(ip, port)) {
                    currentIp = ip
                    currentPort = port
                    isAdbConnected = true
                    true
                } else {
                    isAdbConnected = false
                    false
                }
            } catch (e: Exception) {
                Log.e("AdbInstaller", "Connect exception", e)
                isAdbConnected = false
                false
            }
        }

        override fun disconnect() {
            super.disconnect()
            isAdbConnected = false
            currentIp = null
            currentPort = null
        }
    }

    @Volatile
    private var adbManager: AdbManager? = null

    private fun getOrCreateManager(context: Context): AdbManager {
        adbManager?.let { return it }
        
        val keyDir = File(context.filesDir, ".adb")
        if (!keyDir.exists()) keyDir.mkdirs()
        
        val keyFile = File(keyDir, "adb_key")
        val certFile = File(keyDir, "adb_cert")
        
        val loadedPair = if (keyFile.exists() && certFile.exists()) {
            try {
                val keyBytes = keyFile.readBytes()
                val certBytes = certFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                KeyPair(cert.publicKey, privateKey) to cert
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load keys, regenerating...", e)
                keyFile.delete()
                certFile.delete()
                null
            }
        } else null

        val (keyPair, certificate) = loadedPair ?: run {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val kp = generator.generateKeyPair()
            val cert = generateCertificate(kp)
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
            kp to cert
        }

        return AdbManager(keyPair.private, certificate).also { adbManager = it }
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val privkey = keyPair.private
        val info = X509CertInfo()
        val from = Date()
        val to = Date(from.time + 365L * 86400000L)
        val interval = CertificateValidity(from, to)
        val sn = BigInteger(64, SecureRandom())
        val owner = X500Name("CN=BreezyWeather, O=Breezy, C=US")

        info.set(X509CertInfo.VALIDITY, interval)
        info.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(sn))
        info.set(X509CertInfo.SUBJECT, CertificateSubjectName(owner))
        info.set(X509CertInfo.ISSUER, CertificateIssuerName(owner))
        info.set(X509CertInfo.KEY, CertificateX509Key(keyPair.public))
        info.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
        
        val algo = AlgorithmId.get("SHA256withRSA")
        info.set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(algo))

        val cert = X509CertImpl(info)
        cert.sign(privkey, "SHA256withRSA")
        return cert
    }

    suspend fun pairWatch(context: Context, ip: String, port: Int, pairingCode: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pairing with $ip:$port using code $pairingCode...")
            val manager = getOrCreateManager(context)
            manager.disconnect()
            if (manager.pair(ip, port, pairingCode)) {
                Result.success("Pairing successful!")
            } else {
                Result.failure(Exception("Pairing failed. Check the code and port."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing error", e)
            Result.failure(e)
        }
    }

    suspend fun installToWatch(context: Context, ip: String, port: Int, apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to $ip:$port for installation...")
            val manager = getOrCreateManager(context)
            
            if (!manager.connectRobustly(ip, port)) {
                return@withContext Result.failure(Exception("Connection failed. Are you paired?"))
            }

            Log.d(TAG, "Installing ${apkFile.name}...")
            
            val size = apkFile.length()
            manager.openStream("exec:pm install -r -g -S $size").use { stream ->
                apkFile.inputStream().use { input ->
                    stream.openOutputStream().use { output ->
                        val buffer = ByteArray(1024 * 64)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }

            Result.success("Installation finished! Check your watch.")
        } catch (e: Exception) {
            Log.e(TAG, "ADB Installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun openWatchApp(context: Context, ip: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manager = getOrCreateManager(context)
            if (!manager.connectRobustly(ip, port)) {
                return@withContext Result.failure(Exception("Connection failed for opening app."))
            }
            val packageName = "com.steel101.wearsyncforbreezy"
            val activityName = "com.steel101.wearsyncforbreezy.MainActivity"
            
            manager.openStream("shell:am start -W -n $packageName/$activityName").use { stream ->
                delay(4000)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open watch app: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(context: Context, ip: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manager = getOrCreateManager(context)
            if (manager.connectRobustly(ip, port)) {
                delay(500)
                Result.success(Unit)
            } else {
                Result.failure(Exception("ADB Connect failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getApkVersion(context: Context, apkFile: File): Int = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            info?.versionCode ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
