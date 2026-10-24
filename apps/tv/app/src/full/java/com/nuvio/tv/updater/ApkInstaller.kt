package com.nuvio.tv.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nuvio.tv.BuildConfig
import java.io.File

object ApkInstaller {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
    }

    /** Verify the actual archive before Android Package Installer takes over.
     *  CI Debug signatures change between runs and cannot upgrade each other.
     *  An absent archive signature is left to Android's own final verification.
     */
    @Suppress("DEPRECATION")
    private fun verifyUpdateArchive(context: Context, apkFile: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("APK invalide : Android ne reconnaît pas son manifeste.")
        check(candidate.packageName == context.packageName) {
            "Cet APK concerne ${candidate.packageName}, pas ${context.packageName}. Choisissez la version Kovelyx adaptée."
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun signatures(info: PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }
            return values?.map { it.toCharsString() }?.toSet().orEmpty()
        }
        val installedSigners = signatures(installed)
        val candidateSigners = signatures(candidate)
        if (installedSigners.isNotEmpty() && candidateSigners.isNotEmpty()) {
            check(installedSigners.intersect(candidateSigners).isNotEmpty()) {
                "Signatures APK différentes : cette préversion Debug ne peut pas remplacer l'installation actuelle. Sauvegardez vos données avant toute réinstallation."
            }
        }
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.longVersionCode
        } else installed.versionCode.toLong()
        val candidateCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.longVersionCode
        } else candidate.versionCode.toLong()
        check(candidateCode >= installedCode) {
            "L'APK téléchargé est plus ancien que Kovelyx installé. Installation annulée."
        }
    }

    fun launchInstall(context: Context, apkFile: File) {
        verifyUpdateArchive(context, apkFile)
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}
