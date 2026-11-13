package com.kovelyx.maintenance

import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Local-only, bounded backups. No credentials, executable code or user downloads. */
class LocalMaintenance(
    private val workspace: File,
    private val roots: Map<String, File>,
    private val allowed: (String, String) -> Boolean,
    private val caches: Map<String, File> = emptyMap(),
    private val isSymbolicLink: (File) -> Boolean,
) {
    companion object {
        private const val MAGIC = "KOVELYX-BACKUP-1"
        private const val MAX_FILES = 10000
        private const val MAX_BYTES = 512L * 1024 * 1024
        private const val MAX_FILE = 64L * 1024 * 1024
        private const val ITERATIONS = 600000
    }
    private val pending get() = File(workspace, "pending")
    private fun local(file: File): File {
        require(file.absoluteFile == file.canonicalFile &&
            generateSequence(file.absoluteFile) { it.parentFile }.none(isSymbolicLink)) { "Chemin ou lien non autorisé" }
        return file
    }
    private fun target(root: String, path: String): File {
        require(path.isNotEmpty() && path.length <= 1024 && !path.contains('\\'))
        require(path.split('/').all { it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")) && !it.endsWith(".") })
        require(allowed(root, path)) { "Contenu non autorisé dans la sauvegarde" }
        val base = local(roots[root] ?: error("Type de données inconnu"))
        return local(File(base, path)).also {
            require(it.path.startsWith(base.path + File.separator))
        }
    }
    private fun erase(file: File) {
        local(file)
        if (!file.exists()) return
        if (file.isDirectory) (file.listFiles() ?: error("Dossier inaccessible")).forEach(::erase)
        check(file.delete()) { "Suppression impossible" }
    }
    private fun walk(root: File, visit: (File) -> Unit) {
        if (!root.exists()) return
        local(root)
        if (root.isDirectory) {
            val children = root.listFiles() ?: error("Dossier inaccessible")
            children.sortedBy { it.name }.forEach { walk(it, visit) }
        } else if (root.isFile) visit(root)
        else error("Type de fichier non autorisé")
    }
    fun bytes(root: File): Long {
        var total = 0L
        walk(root) { total = Math.addExact(total, it.length()) }
        return total
    }
    fun cacheSizes(): Map<String, Long> = caches.mapValues { bytes(it.value) }
    fun scheduleCacheClear(key: String) {
        require(caches.containsKey(key))
        local(workspace).mkdirs()
        val flag = File(workspace, "clear-$key")
        local(flag).writeText("1")
    }
    @Synchronized fun cancelRestore() { erase(pending) }
    fun hasPendingRestore(): Boolean = File(pending, "ready").isFile

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 12) { "Utilise au moins 12 caractères" }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        return try { SecretKeySpec(raw, "AES") } finally { raw.fill(0) }
    }
    private fun chunkCipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, sequence: Long, size: Int): Cipher {
        val iv = java.nio.ByteBuffer.allocate(12).put(nonce).putLong(sequence).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(MAGIC.toByteArray(Charsets.UTF_8))
            updateAAD(java.nio.ByteBuffer.allocate(12).putLong(sequence).putInt(size).array())
        }
    }
    private inner class SealedOutput(output: OutputStream, private val key: SecretKeySpec, private val nonce: ByteArray) : OutputStream() {
        private val output = DataOutputStream(output)
        private val buffer = ByteArray(65536)
        private var used = 0
        private var sequence = 0L
        private fun seal() {
            output.writeInt(used)
            output.write(chunkCipher(Cipher.ENCRYPT_MODE, key, nonce, sequence++, used).doFinal(buffer, 0, used))
            used = 0
        }
        override fun write(value: Int) { buffer[used++] = value.toByte(); if (used == buffer.size) seal() }
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            var offset = off; var left = len
            while (left > 0) {
                val n = minOf(left, buffer.size - used)
                bytes.copyInto(buffer, used, offset, offset + n); used += n; offset += n; left -= n
                if (used == buffer.size) seal()
            }
        }
        override fun close() {
            try { if (used > 0) seal(); seal(); output.flush() } finally { buffer.fill(0); output.close() }
        }
    }

    private fun copyExact(input: InputStream, output: OutputStream, size: Long) {
        require(size in 0..MAX_FILE) { "Fichier trop volumineux" }
        val buffer = ByteArray(65536)
        var left = size
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (n < 0) throw EOFException("Sauvegarde incomplète")
            output.write(buffer, 0, n)
            left -= n
        }
    }
    /** Never writes the unencrypted backup to disk. Caller publishes only on success. */
    @Synchronized fun export(output: OutputStream, password: CharArray, scope: String) {
        require(scope.isNotBlank())
        val entries = mutableListOf<Triple<String, String, File>>()
        roots.forEach { (name, root) ->
            walk(root) { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (allowed(name, path)) {
                    target(name, path)
                    entries.add(Triple(name, path, file))
                    require(entries.size <= MAX_FILES)
                }
            }
        }
        require(entries.isNotEmpty()) { "Aucune donnée à sauvegarder" }
        val stamps = entries.map { it.third.length() to it.third.lastModified() }
        require(stamps.sumOf { it.first } <= MAX_BYTES)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(4).also(SecureRandom()::nextBytes)
        val header = DataOutputStream(output)
        header.writeUTF(MAGIC); header.write(salt); header.write(iv); header.flush()
        DataOutputStream(SealedOutput(output, key(password, salt), iv)).use { data ->
            data.writeUTF(scope); data.writeInt(entries.size)
            entries.forEachIndexed { index, (name, path, file) ->
                val (size, stamp) = stamps[index]
                require(file.length() == size && file.lastModified() == stamp) { "Données modifiées pendant la sauvegarde, réessaie" }
                data.writeUTF(name); data.writeUTF(path); data.writeLong(size)
                file.inputStream().use { input ->
                    copyExact(input, data, size)
                    require(input.read() == -1) { "Données modifiées pendant la sauvegarde, réessaie" }
                }
                require(file.length() == size && file.lastModified() == stamp) {
                    "Données modifiées pendant la sauvegarde, réessaie"
                }
            }
            entries.forEachIndexed { index, entry ->
                require(entry.third.length() == stamps[index].first && entry.third.lastModified() == stamps[index].second) {
                    "Données modifiées pendant la sauvegarde, réessaie"
                }
            }
            val current = mutableSetOf<Pair<String, String>>()
            roots.forEach { (name, root) -> walk(root) { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (allowed(name, path)) current.add(name to path)
            } }
            require(current == entries.map { it.first to it.second }.toSet()) { "Données modifiées pendant la sauvegarde, réessaie" }
        }
    }

    /** Authenticate BEFORE parsing or making any live write. Scope binds account + app kind. */
    @Synchronized fun stage(input: InputStream, password: CharArray, scope: String, identity: String) {
        require(!File(pending, "applying").exists())
        local(workspace).mkdirs()
        val incoming = local(File(workspace, "incoming"))
        erase(incoming); check(incoming.mkdirs())
        try {
            val encrypted = DataInputStream(input)
            require(encrypted.readUTF() == MAGIC) { "Format non pris en charge" }
            val salt = ByteArray(16).also(encrypted::readFully)
            val iv = ByteArray(4).also(encrypted::readFully)
            val plain = File(incoming, "payload")
            val secret = key(password, salt)
            var sequence = 0L
            var total = 0L
            // Every bounded chunk is explicitly authenticated. The final empty authenticated
            // chunk and EOF reject truncation, reordering and appended ciphertext.
            plain.outputStream().use { out ->
                while (true) {
                    val size = encrypted.readInt(); require(size in 0..65536)
                    total += size; require(total <= MAX_BYTES + 16 * 1024 * 1024)
                    val sealed = ByteArray(size + 16).also(encrypted::readFully)
                    val chunk = chunkCipher(Cipher.DECRYPT_MODE, secret, iv, sequence++, size).doFinal(sealed)
                    if (size == 0) { require(encrypted.read() == -1); break }
                    out.write(chunk); chunk.fill(0)
                }
            }
            val manifest = File(incoming, "manifest")
            DataInputStream(plain.inputStream().buffered()).use { data ->
                require(data.readUTF() == scope) { "Sauvegarde d'un autre compte ou d'une autre version d'application" }
                val count = data.readInt()
                require(count in 1..MAX_FILES)
                val seen = mutableSetOf<String>()
                var total = 0L
                DataOutputStream(manifest.outputStream()).use { list ->
                    list.writeUTF(identity); list.writeInt(count)
                    repeat(count) { i ->
                        val root = data.readUTF(); val path = data.readUTF()
                        target(root, path)
                        require(seen.add("$root/$path".lowercase(java.util.Locale.ROOT))) { "Fichier en double" }
                        val size = data.readLong()
                        require(size in 0..MAX_FILE)
                        total += size; require(total <= MAX_BYTES)
                        list.writeUTF(root); list.writeUTF(path)
                        File(incoming, "$i.new").outputStream().use { copyExact(data, it, size) }
                    }
                    require(data.read() == -1) { "Données supplémentaires interdites" }
                }
            }
            check(plain.delete())
            // Do not discard an earlier valid pending restore when the new import fails.
            erase(pending)
            check(incoming.renameTo(pending))
            File(pending, "ready").writeText("1")
        } finally { erase(incoming) }
    }

    /** Call at process startup before preferences, DataStore, player or image loader. */
    @Synchronized fun startup(identity: String): String? {
        local(workspace)
        if (File(pending, "committed").exists()) erase(pending)
        if (File(pending, "applying").exists()) {
            rollback()
            erase(pending)
            return "Restauration interrompue : données précédentes conservées"
        }
        var result: String? = null
        if (hasPendingRestore()) {
            try {
                val entries = manifest(identity)
                // Save EVERY previous file before starting any replacement.
                entries.forEachIndexed { i, file ->
                    if (file.exists()) {
                        require(file.isFile)
                        file.copyTo(File(pending, "$i.old"), overwrite = true)
                    } else File(pending, "$i.absent").writeText("1")
                }
                File(pending, "applying").writeText("1")
                entries.forEachIndexed { i, file -> replace(File(pending, "$i.new"), file) }
                File(pending, "committed").writeText("1")
                result = "Sauvegarde restaurée"
                erase(pending)
            } catch (error: Exception) {
                if (File(pending, "applying").exists()) rollback()
                erase(pending)
                result = "Restauration refusée : données précédentes conservées"
            }
        }
        caches.forEach { (key, root) ->
            val flag = local(File(workspace, "clear-$key"))
            if (flag.exists()) {
                // Symlinks and directories outside the explicit cache roots are never followed.
                try { walk(root) { check(it.delete()) } }
                catch (_: Exception) { result = listOfNotNull(result, "Nettoyage incomplet : un fichier temporaire est inaccessible").joinToString("\n") }
                finally { flag.delete() }
            }
        }
        return result
    }
    private fun manifest(identity: String? = null): List<File> =
        DataInputStream(local(File(pending, "manifest")).inputStream()).use { list ->
            val savedIdentity = list.readUTF()
            if (identity != null) require(savedIdentity == identity) { "Le compte a changé depuis l'import" }
            val count = list.readInt(); require(count in 1..MAX_FILES)
            List(count) { target(list.readUTF(), list.readUTF()) }.also { require(list.read() == -1) }
        }
    private fun rollback() {
        manifest().forEachIndexed { i, file ->
            val old = File(pending, "$i.old")
            if (old.isFile) replace(old, file)
            else if (File(pending, "$i.absent").exists() && file.exists()) check(file.delete())
            else require(File(pending, "$i.absent").exists()) { "Copie de secours absente" }
        }
    }
    private fun replace(source: File, target: File) {
        local(target); target.parentFile!!.mkdirs()
        val temp = local(File(target.parentFile, target.name + ".maintenance-tmp"))
        source.inputStream().use { input ->
            FileOutputStream(temp).use { out -> input.copyTo(out, 65536); out.fd.sync() }
        }
        // The journal protects the replacement even on Windows where rename cannot overwrite.
        if (target.exists()) check(target.delete()) { "Remplacement impossible" }
        check(temp.renameTo(target)) { "Remplacement impossible" }
    }

}

fun maintenanceScope(platform: String, account: String): String =
    platform + ":" + MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }

/** Excludes device-bound keys, login sessions, downloads and stale outboxes. */
fun maintenancePreferenceAllowed(path: String): Boolean {
    val name = path.lowercase()
    return listOf("auth", "session", "credential", "token", "pin", "nuvio_servers", "media_servers", "server_configuration", "sync_identity", "sync_client", "pending_mutation", "download", "updater")
        .none(name::contains) && !name.endsWith(".bak")
}
