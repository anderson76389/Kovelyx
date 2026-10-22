package com.nuvio.app.features.profiles

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android OpenDocument : aucune permission READ_EXTERNAL_STORAGE, aucune
 * dépendance à la persistance du document d'origine, aucun média perdu si
 * l'application de galerie supprime le fichier choisi.
 */
internal actual object ProfileLocalImages {
    private const val MAX_BYTES = 20L * 1024L * 1024L
    private var appContext: Context? = null
    val mediaContext: Context? get() = appContext
    private var chooser: ActivityResultLauncher<Array<String>>? = null
    private var awaiting: Continuation<Uri?>? = null

    actual val supported: Boolean get() = chooser != null && appContext != null

    fun attach(activity: AppCompatActivity) {
        appContext = activity.applicationContext
        chooser = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = awaiting
            awaiting = null
            pending?.resume(uri)
        }
    }

    actual suspend fun chooseImage(background: Boolean): String? {
        check(supported) { "Sélecteur de média indisponible" }
        val picked = suspendCancellableCoroutine<Uri?> { continuation ->
            check(awaiting == null) { "Un import est déjà en cours" }
            awaiting = continuation
            continuation.invokeOnCancellation { if(awaiting === continuation) awaiting = null }
            chooser!!.launch(arrayOf("image/*", "video/*"))
        } ?: return null
        return withContext(Dispatchers.IO) {
            val context = checkNotNull(appContext)
            val resolver = context.contentResolver
            resolver.query(picked,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                if(cursor.moveToFirst()) {
                    val size = cursor.getLong(0)
                    require(size <= MAX_BYTES) { "Fichier trop volumineux (20 Mo maximum)." }
                }
            }
            val directory = File(context.filesDir,"kovelyx/profile-media").apply { mkdirs() }
            val temporary = File.createTempFile("import-", ".pending",directory)
            try {
                resolver.openInputStream(picked)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer=ByteArray(16384)
                        var total=0L
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            total+=count
                            require(total <= MAX_BYTES) { "Fichier trop volumineux (20 Mo maximum)." }
                            output.write(buffer,0,count)
                        }
                    }
                } ?: error("Le document n'est plus accessible")
                val header=ByteArray(16)
                temporary.inputStream().use { it.read(header) }
                fun ascii(start:Int,len:Int) = String(header,start,len,Charsets.US_ASCII)
                val extension = when {
                    header[0]==0xff.toByte() && header[1]==0xd8.toByte() -> "jpg"
                    header.slice(0..7)==listOf<Byte>(-119,80,78,71,13,10,26,10) -> "png"
                    ascii(0,4)=="GIF8" -> "gif"
                    ascii(0,4)=="RIFF" && ascii(8,4)=="WEBP" -> "webp"
                    ascii(4,4)=="ftyp" -> "mp4"
                    header.slice(0..3)==listOf<Byte>(0x1a,0x45,0xdf.toByte(),0xa3.toByte()) -> "webm"
                    else -> error("Format non pris en charge : JPG, PNG, GIF, WebP, MP4 ou WebM.")
                }
                val dest = File(directory, (if(background) "background-" else "avatar-")+
                    UUID.randomUUID().toString()+".$extension")
                check(temporary.renameTo(dest)) { "Impossible d'enregistrer le média importé" }
                dest.toURI().toString()
            } finally {
                temporary.delete()
            }
        }
    }

    actual fun isManagedUri(url: String): Boolean = runCatching {
        val context=appContext ?: return false
        val file=File(java.net.URI(url)).canonicalFile
        val parent=File(context.filesDir,"kovelyx/profile-media").canonicalFile
        file.parentFile==parent && file.isFile && file.length() in 1..MAX_BYTES
    }.getOrDefault(false)

    private fun key(userId:String, profileIndex:Int, background:Boolean):String {
        val hash=MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return "$hash.$profileIndex."+(if(background) "background" else "avatar")
    }

    actual fun savedImage(userId:String,profileIndex:Int,background:Boolean):String? =
        appContext?.getSharedPreferences("kovelyx_profile_media",Context.MODE_PRIVATE)
            ?.getString(key(userId,profileIndex,background),null)?.takeIf(::isManagedUri)

    actual fun saveImage(userId:String,profileIndex:Int,background:Boolean,url:String?) {
        appContext?.getSharedPreferences("kovelyx_profile_media",Context.MODE_PRIVATE)
            ?.edit()?.putString(key(userId,profileIndex,background),url?.takeIf(::isManagedUri))?.apply()
    }
}
