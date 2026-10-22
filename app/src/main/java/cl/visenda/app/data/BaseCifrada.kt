package cl.visenda.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Base de datos cifrada con SQLCipher (AES-256). La clave es azar de 32 bytes, guardada envuelta por una llave
 * del Android Keystore: no sale del teléfono ni viaja en los respaldos (que van cifrados con la contraseña del usuario).
 */
object BaseCifrada {
    private const val ALIAS = "visenda_base"
    private const val PREFS = "seguridad"
    private val CABECERA_PLANA = "SQLite format 3\u0000".toByteArray()

    init { System.loadLibrary("sqlcipher") }

    /** Clave de la base en hexadecimal; se crea la primera vez. */
    @Synchronized
    fun clave(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardada = prefs.getString("clave_base", null)
        if (guardada != null) {
            val (iv, cifrada) = guardada.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, iv)) }
            return c.doFinal(cifrada).joinToString("") { "%02x".format(it) }
        }
        val nueva = ByteArray(32).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, llave()) }
        val texto = listOf(c.iv, c.doFinal(nueva)).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
        check(prefs.edit().putString("clave_base", texto).commit()) { "No se pudo guardar la clave de la base." }
        return nueva.joinToString("") { "%02x".format(it) }
    }

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }

    fun esPlana(archivo: File) = archivo.exists() && archivo.length() >= 16 &&
        archivo.inputStream().use { e -> ByteArray(16).also { e.read(it) } }.contentEquals(CABECERA_PLANA)

    /**
     * Copia una base sin cifrar a [destino] cifrada con [clave]. Se escribe en un temporal y recién al final
     * se renombra: si algo falla, [destino] no cambia.
     */
    fun cifrar(plana: File, destino: File, clave: String) {
        val temporal = File(destino.path + ".cifrando").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(plana, "", null, null).use { db ->
            db.execSQL("ATTACH DATABASE '${temporal.path.replace("'", "''")}' AS cifrada KEY '$clave'")
            db.rawQuery("SELECT sqlcipher_export('cifrada')", arrayOf()).use { it.moveToFirst() }
            // sqlcipher_export no copia la versión del esquema, que Room usa para saber si migrar.
            db.execSQL("PRAGMA cifrada.user_version = ${db.version}")
            db.execSQL("DETACH DATABASE cifrada")
        }
        reemplazar(temporal, destino)
    }

    /** Lo contrario de [cifrar]: deja en [destino] una copia sin cifrar (para el respaldo, que la cifra con la contraseña). */
    fun descifrar(cifrada: File, clave: String, destino: File) {
        destino.delete()
        SQLiteDatabase.openOrCreateDatabase(cifrada, clave, null, null).use { db ->
            db.execSQL("ATTACH DATABASE '${destino.path.replace("'", "''")}' AS plana KEY ''")
            db.rawQuery("SELECT sqlcipher_export('plana')", arrayOf()).use { it.moveToFirst() }
            db.execSQL("PRAGMA plana.user_version = ${db.version}")
            db.execSQL("DETACH DATABASE plana")
        }
    }

    /** Hito 4: la base de antes estaba sin cifrar. Se convierte una sola vez, guardando la original hasta terminar. */
    fun migrarSiHaceFalta(context: Context) {
        val base = context.getDatabasePath(NOMBRE_BASE)
        if (!esPlana(base)) return
        val anterior = File(base.path + ".sin-cifrar")
        anterior.delete()
        check(base.renameTo(anterior)) { "No se pudo preparar el cifrado de la base." }
        val extras = listOf("-wal", "-shm").map { File(base.path + it) to File(anterior.path + it) }
        extras.forEach { (de, a) -> a.delete(); if (de.exists()) de.renameTo(a) } // el registro WAL va con su base
        try {
            cifrar(anterior, base, clave(context))
            listOf(anterior).plus(extras.map { it.second }).forEach { it.delete() }
        } catch (e: Exception) {
            base.delete()
            anterior.renameTo(base)
            extras.forEach { (de, a) -> if (a.exists()) a.renameTo(de) }
            throw e
        }
    }

    private fun reemplazar(nuevo: File, destino: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(destino.path + it).delete() }
        check(nuevo.renameTo(destino)) { "No se pudo reemplazar la base." }
    }
}
