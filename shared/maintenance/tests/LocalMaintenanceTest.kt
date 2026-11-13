package com.kovelyx.maintenance

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val password = "Mot de passe de test 2026!".toCharArray()
private val scope = maintenanceScope("mobile", "account-a")
private var passed = 0
private fun test(name: String, action: () -> Unit) {
    try { action(); passed++; println("PASS $name") }
    catch (error: Throwable) { System.err.println("FAIL $name"); throw error }
}
private fun refused(action: () -> Unit) { check(runCatching(action).isFailure) { "Unsafe operation accepted" } }
private class Fixture : AutoCloseable {
    val folder = Files.createTempDirectory("kovelyx-maintenance-test-").toFile().canonicalFile
    val data = File(folder, "data").apply { mkdirs() }
    val work = File(folder, "work")
    val cache = File(folder, "cache").apply { mkdirs() }
    val engine = LocalMaintenance(work, mapOf("prefs" to data), { root, path -> root == "prefs" && path.endsWith(".properties") }, mapOf("images" to cache), isSymbolicLink = { Files.isSymbolicLink(it.toPath()) })
    fun put(path: String, value: String): File = File(data, path).apply { parentFile.mkdirs(); writeText(value) }
    fun backup(): ByteArray = ByteArrayOutputStream().also { engine.export(it, password, scope) }.toByteArray()
    fun stage(bytes: ByteArray, owner: String = scope, secret: CharArray = password) = engine.stage(bytes.inputStream(), secret, owner, "session-a")
    override fun close() { folder.deleteRecursively() }
}
private fun crafted(vararg entries: Triple<String, String, ByteArray>, count: Int = entries.size, trailing: Boolean = false): ByteArray {
    val plain = ByteArrayOutputStream()
    DataOutputStream(plain).use { out ->
        out.writeUTF(scope); out.writeInt(count)
        entries.forEach { (root, path, bytes) -> out.writeUTF(root); out.writeUTF(path); out.writeLong(bytes.size.toLong()); out.write(bytes) }
        if (trailing) out.writeByte(42)
    }
    val salt = ByteArray(16) { it.toByte() }; val nonce = ByteArray(4) { (it + 20).toByte() }
    val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(password, salt, 600000, 256)).encoded
    val key = SecretKeySpec(raw, "AES"); raw.fill(0)
    return ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { out ->
            out.writeUTF("KOVELYX-BACKUP-1"); out.write(salt); out.write(nonce)
            var index = 0L
            fun chunk(bytes: ByteArray) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteBuffer.allocate(12).put(nonce).putLong(index).array()))
                cipher.updateAAD("KOVELYX-BACKUP-1".toByteArray())
                cipher.updateAAD(ByteBuffer.allocate(12).putLong(index++).putInt(bytes.size).array())
                out.writeInt(bytes.size); out.write(cipher.doFinal(bytes))
            }
            plain.toByteArray().asList().chunked(65536).forEach { chunk(it.toByteArray()) }
            chunk(byteArrayOf())
        }
    }.toByteArray()
}

fun main() {
    test("encrypted round trip, deferred apply, cache and unrelated file preserved") { Fixture().use { f ->
        val original = f.put("library.properties", "bibliotheque=film-serie-vu-progression")
        f.put("profiles.properties", "profils=1,2,3")
        val backup = f.backup()
        check(!String(backup).contains("bibliotheque"))
        original.writeText("current")
        f.put("user-video.mp4", "do-not-delete")
        File(f.cache, "poster").writeText("cache")
        f.stage(backup)
        check(original.readText() == "current" && f.engine.hasPendingRestore())
        check(f.engine.startup("session-a") == "Sauvegarde restaurée")
        check(original.readText().contains("film-serie"))
        check(File(f.data, "user-video.mp4").readText() == "do-not-delete")
        check(File(f.cache, "poster").readText() == "cache")
        check(!f.engine.hasPendingRestore())
    } }
    test("randomized ciphertext") { Fixture().use { f -> f.put("a.properties", "a=b"); check(!f.backup().contentEquals(f.backup())) } }
    test("multi-chunk binary fidelity") { Fixture().use { f ->
        val data = ByteArray(200000) { (it * 17).toByte() }
        val file = f.put("a.properties", ""); file.writeBytes(data)
        val backup = f.backup(); file.writeText("changed"); f.stage(backup); f.engine.startup("session-a")
        check(file.readBytes().contentEquals(data))
    } }
    test("concurrent writes to an already-exported preference refuse the backup") { Fixture().use { f ->
        val first = f.put("a.properties", "before")
        f.put("b.properties", "x".repeat(200000))
        var changed = false
        val out = object : ByteArrayOutputStream() {
            override fun write(buffer: ByteArray, offset: Int, count: Int) {
                super.write(buffer, offset, count)
                if (!changed && size() > 65536) {
                    changed = true; first.writeText("changed"); first.setLastModified(System.currentTimeMillis() + 5000)
                }
            }
        }
        refused { f.engine.export(out, password, scope) }; check(changed)
    } }
    test("large backup streams within a 64-MiB heap") { Fixture().use { f ->
        val file = f.put("large.properties", "")
        val block = ByteArray(65536) { (it * 29).toByte() }
        file.outputStream().use { out -> repeat(384) { out.write(block) } }
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        val before = digest(file)
        val archive = File(f.folder, "backup.kovbackup")
        archive.outputStream().use { f.engine.export(it, password, scope) }
        file.writeText("current")
        archive.inputStream().use { f.engine.stage(it, password, scope, "session-a") }
        f.engine.startup("session-a")
        check(digest(file) == before && file.length() == 24L * 1024 * 1024)
    } }
    test("wrong password keeps existing data and previous valid pending restore") { Fixture().use { f ->
        f.put("a.properties", "old"); val bytes = f.backup(); f.stage(bytes)
        f.put("a.properties", "current")
        refused { f.stage(bytes, secret = "un autre mot de passe".toCharArray()) }
        check(File(f.data, "a.properties").readText() == "current" && f.engine.hasPendingRestore())
    } }
    test("another account refused") { Fixture().use { f -> f.put("a.properties", "old"); refused { f.stage(f.backup(), maintenanceScope("mobile", "account-b")) }; check(!f.engine.hasPendingRestore()) } }
    test("another application kind refused") { Fixture().use { f -> f.put("a.properties", "old"); refused { f.stage(f.backup(), maintenanceScope("tv", "account-a")) } } }
    test("same logical account restores with a fresh device session") { Fixture().use { f ->
        val file = f.put("a.properties", "saved")
        val bytes = f.backup(); file.writeText("current")
        f.engine.stage(bytes.inputStream(), password, maintenanceScope("mobile", "account-a"), "fresh-session")
        check(f.engine.startup("fresh-session") == "Sauvegarde restaurée")
        check(file.readText() == "saved")
    } }
    test("account switch after staging refuses writes") { Fixture().use { f ->
        val file = f.put("a.properties", "old"); val bytes = f.backup(); file.writeText("current"); f.stage(bytes)
        check(f.engine.startup("session-b")!!.contains("refusée")); check(file.readText() == "current")
    } }
    test("truncation at header, body and final authentication tag") { Fixture().use { f ->
        f.put("a.properties", "old"); val bytes = f.backup()
        for (size in listOf(0, 1, 12, 33, bytes.size - 20, bytes.size - 1)) refused { f.stage(bytes.copyOf(size)) }
        check(!f.engine.hasPendingRestore())
    } }
    test("ciphertext tampering refused before any live write") { Fixture().use { f ->
        f.put("a.properties", "current"); val bytes = f.backup(); bytes[bytes.size - 25] = (bytes[bytes.size - 25].toInt() xor 1).toByte()
        refused { f.stage(bytes) }; check(File(f.data, "a.properties").readText() == "current")
    } }
    test("appended ciphertext refused") { Fixture().use { f -> f.put("a.properties", "old"); refused { f.stage(f.backup() + byteArrayOf(0)) } } }
    test("negative and excessive file counts refused") { Fixture().use { f -> for (n in listOf(-1, 0, 10001)) refused { f.stage(crafted(count = n)) } } }
    for (path in listOf("../outside.properties", "/absolute.properties", "folder/../../outside.properties", "C:/outside.properties", "folder\\outside.properties", "./file.properties", "folder//file.properties")) {
        test("path traversal refused: $path") { Fixture().use { f -> refused { f.stage(crafted(Triple("prefs", path, "bad".toByteArray()))) }; check(!f.engine.hasPendingRestore()) } }
    }
    test("unknown root refused") { Fixture().use { f -> refused { f.stage(crafted(Triple("unknown", "a.properties", byteArrayOf()))) } } }
    test("unapproved file type refused") { Fixture().use { f -> refused { f.stage(crafted(Triple("prefs", "library.exe", byteArrayOf()))) } } }
    test("duplicate and case-colliding file names refused") { Fixture().use { f ->
        val entry = Triple("prefs", "a.properties", byteArrayOf())
        refused { f.stage(crafted(entry, entry)) }
        refused { f.stage(crafted(entry, Triple("prefs", "A.properties", byteArrayOf()))) }
    } }
    test("authenticated trailing payload refused") { Fixture().use { f -> refused { f.stage(crafted(Triple("prefs", "a.properties", byteArrayOf()), trailing = true)) } } }
    test("source larger than per-file limit refused") { Fixture().use { f ->
        RandomAccessFile(f.put("big.properties", ""), "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        refused { f.backup() }
    } }
    test("short password refused") { Fixture().use { f -> f.put("a.properties", "a"); refused { f.engine.export(ByteArrayOutputStream(), "short".toCharArray(), scope) } } }
    test("cancel pending restore preserves live data") { Fixture().use { f ->
        val file = f.put("a.properties", "old"); val backup = f.backup(); file.writeText("current"); f.stage(backup)
        f.engine.cancelRestore(); check(f.engine.startup("session-a") == null && file.readText() == "current")
    } }
    test("failure after first replacement rolls back the entire transaction") { Fixture().use { f ->
        val first = f.put("a.properties", "before")
        f.put("blocked", "personal file")
        f.stage(crafted(Triple("prefs", "a.properties", "after".toByteArray()), Triple("prefs", "blocked/b.properties", "b".toByteArray())))
        check(f.engine.startup("session-a")!!.contains("refusée"))
        check(first.readText() == "before" && File(f.data, "blocked").readText() == "personal file")
    } }
    test("interrupted apply recovers original and removes newly created files") { Fixture().use { f ->
        val first = f.put("a.properties", "before"); val backup = crafted(Triple("prefs", "a.properties", "after".toByteArray()), Triple("prefs", "b.properties", "new".toByteArray()))
        f.stage(backup)
        val pending = File(f.work, "pending")
        first.copyTo(File(pending, "0.old")); File(pending, "1.absent").writeText("1")
        File(pending, "applying").writeText("1"); first.writeText("partial"); f.put("b.properties", "new")
        check(f.engine.startup("session-a")!!.contains("interrompue"))
        check(first.readText() == "before" && !File(f.data, "b.properties").exists())
    } }
    test("cache cleanup is deferred, repeatable and protects libraries") { Fixture().use { f ->
        val library = f.put("a.properties", "keep"); File(f.cache, "nested").mkdirs(); File(f.cache, "nested/image").writeText("image")
        f.engine.scheduleCacheClear("images"); check(f.engine.bytes(f.cache) == 5L)
        f.engine.startup("session-a"); check(f.engine.bytes(f.cache) == 0L && library.readText() == "keep")
        check(f.engine.startup("session-a") == null)
        refused { f.engine.scheduleCacheClear("../data") }
    } }
    test("symlinks never escape an approved cache root") { Fixture().use { f ->
        val outside = f.put("a.properties", "private")
        Files.createSymbolicLink(File(f.cache, "link").toPath(), outside.toPath())
        f.engine.scheduleCacheClear("images"); check(f.engine.startup("session-a")!!.contains("Nettoyage incomplet"))
        check(outside.readText() == "private")
    } }
    test("restoration symlink refused") { Fixture().use { f ->
        val outside = File(f.folder, "outside").apply { writeText("private") }
        Files.createSymbolicLink(File(f.data, "a.properties").toPath(), outside.toPath())
        refused { f.stage(crafted(Triple("prefs", "a.properties", "bad".toByteArray()))) }
        check(outside.readText() == "private")
    } }
    test("preference policy excludes sessions credentials downloads and stale outboxes") {
        for (name in listOf("nuvio_auth.xml", "nuvio_servers.xml", "media_servers.xml", "server_configuration.xml", "server_credentials.properties", "kovelyx_secure_cloudflare_auth.xml", "kovelyx_cloudflare_session.xml", "nuvio_sync_client_identity.xml", "downloads.preferences_pb", "profile_1_credentials.xml", "profile_pin.xml", "watch_state_pending_mutations.preferences_pb")) check(!maintenancePreferenceAllowed(name))
        for (name in listOf("nuvio_library.properties", "profile_settings.preferences_pb", "nuvio_avatars.xml", "nuvio_plugins.xml", "nuvio_watch_progress.xml")) check(maintenancePreferenceAllowed(name))
    }
    println("$passed maintenance checks passed")
}
