package com.example.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader

data class RootCheckResult(
    val isRooted: Boolean,
    val testKeysTriggered: Boolean,
    val suBinaryFound: Boolean,
    val suExecutionSuccessful: Boolean,
    val rxtuxAppsInstalled: Boolean,
    val description: String,
    val scanDetails: List<CheckDetail>
)

data class CheckDetail(
    val name: String,
    val status: Boolean,
    val details: String
)

object RootDetector {

    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    private val ROOT_PACKAGES = arrayOf(
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.topjohnwu.magisk",
        "com.koushikdutta.superuser",
        "com.zachspong.temprootchecker",
        "com.ramdroid.appquarantine",
        "com.devadvance.rootsettings"
    )

    fun detectRoot(context: Context): RootCheckResult {
        val details = mutableListOf<CheckDetail>()

        // 1. Check Build Tags
        val buildTags = Build.TAGS
        val testKeysTriggered = buildTags != null && buildTags.contains("test-keys")
        details.add(
            CheckDetail(
                "Build Tag signature",
                testKeysTriggered,
                if (testKeysTriggered) "Custom keys ('test-keys') detected in build properties: $buildTags" else "Official releases keys ('release-keys') found."
            )
        )

        // 2. Check SU Binary Presence
        var suBinaryFound = false
        var foundPath = ""
        for (path in SU_PATHS) {
            if (File(path).exists()) {
                suBinaryFound = true
                foundPath = path
                break
            }
        }
        details.add(
            CheckDetail(
                "SU Binary location check",
                suBinaryFound,
                if (suBinaryFound) "Found 'su' binary at: $foundPath" else "No default 'su' executables located in standard directories."
            )
        )

        // 3. Check App Packages
        var appsInstalled = false
        val pm = context.packageManager
        val installedApps = mutableListOf<String>()
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                appsInstalled = true
                installedApps.add(pkg)
            } catch (e: Exception) {
                // Not found
            }
        }
        details.add(
            CheckDetail(
                "Root Manager apps scanning",
                appsInstalled,
                if (appsInstalled) "Found Root managerial packages: ${installedApps.joinToString(", ")}" else "No standard root management package matches."
            )
        )

        // 4. Try Execution
        val execSuccess = trySuExecution()
        details.add(
            CheckDetail(
                "Root Execution runtime try",
                execSuccess,
                if (execSuccess) "Process builder successfully spawned and queried the root context." else "Execution of shell commands with elevated privileges was denied."
            )
        )

        val isRooted = testKeysTriggered || suBinaryFound || appsInstalled || execSuccess
        val overallDesc = when {
            execSuccess -> "Root access is active and verified via runtime environment shell execution."
            suBinaryFound || appsInstalled -> "Phone shows strong indicators of root access. Standard files or root utility managers are present."
            testKeysTriggered -> "Phone contains developer signature keys ('test-keys'), which suggests standard custom recovery install."
            else -> "Device appears secure. No root privileges or subversion binary paths detected."
        }

        return RootCheckResult(
            isRooted = isRooted,
            testKeysTriggered = testKeysTriggered,
            suBinaryFound = suBinaryFound,
            suExecutionSuccessful = execSuccess,
            rxtuxAppsInstalled = appsInstalled,
            description = overallDesc,
            scanDetails = details
        )
    }

    private fun trySuExecution(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}
