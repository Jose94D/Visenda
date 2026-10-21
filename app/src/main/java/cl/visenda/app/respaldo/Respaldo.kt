package cl.visenda.app.respaldo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// RN-13. Archivo .visenda: "VISENDA1" | sal (16) | iteraciones (int) | iv (12) | AES-256-GCM(contenido).
// La cabecera va como dato autenticado: cambiar las iteraciones o el iv rompe la verificación.
// Contenido: largo del manifiesto (int) | manifiesto (Properties, UTF-8) | base de datos SQLite.

private val MARCA = "VISENDA1".toByteArray()
const val MINIMO_CONTRASENA = 8
private const val ITERACIONES = 600_000 // PBKDF2-HMAC-SHA256, lo que recomienda OWASP
private const val MAXIMO_ITERACIONES = 10_000_000 // un archivo ajeno no puede dejar al teléfono calculando para siempre

data class Manifiesto(val versionApp: String, val esquema: Int, val fecha: Long, val clientes: Int, val regiones: List<String>)

class RespaldoInvalido(mensaje: String) : Exception(mensaje)

fun cifrarRespaldo(manifiesto: Manifiesto, baseDatos: ByteArray, contrasena: CharArray, iteraciones: Int = ITERACIONES): ByteArray {
    require(contrasena.size >= MINIMO_CONTRASENA) { "La contraseña debe tener al menos $MINIMO_CONTRASENA caracteres." }
    val azar = SecureRandom()
    val sal = ByteArray(16).also(azar::nextBytes)
    val iv = ByteArray(12).also(azar::nextBytes)
    val cabecera = ByteArrayOutputStream().also { b -> DataOutputStream(b).run { write(MARCA); write(sal); writeInt(iteraciones); write(iv) } }.toByteArray()

    val props = Properties().apply {
        setProperty("versionApp", manifiesto.versionApp)
        setProperty("esquema", manifiesto.esquema.toString())
        setProperty("fecha", manifiesto.fecha.toString())
        setProperty("clientes", manifiesto.clientes.toString())
        setProperty("regiones", manifiesto.regiones.joinToString(","))
    }
    val textoManifiesto = ByteArrayOutputStream().also { props.store(java.io.OutputStreamWriter(it, Charsets.UTF_8), null) }.toByteArray()
    val contenido = ByteArrayOutputStream().also { b -> DataOutputStream(b).run { writeInt(textoManifiesto.size); write(textoManifiesto); write(baseDatos) } }.toByteArray()

    val cifrador = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, clave(contrasena, sal, iteraciones), GCMParameterSpec(128, iv))
        updateAAD(cabecera)
    }
    return cabecera + cifrador.doFinal(contenido)
}

/** Devuelve el manifiesto y la base de datos. Falla con RespaldoInvalido si no es un respaldo, la contraseña no calza o el archivo fue alterado. */
fun descifrarRespaldo(archivo: ByteArray, contrasena: CharArray): Pair<Manifiesto, ByteArray> {
    val largoCabecera = MARCA.size + 16 + 4 + 12
    if (archivo.size < largoCabecera + 16 || !archivo.copyOfRange(0, MARCA.size).contentEquals(MARCA)) throw RespaldoInvalido("El archivo no es un respaldo de Visenda.")
    val entrada = DataInputStream(ByteArrayInputStream(archivo, MARCA.size, largoCabecera - MARCA.size))
    val sal = ByteArray(16).also(entrada::readFully)
    val iteraciones = entrada.readInt()
    val iv = ByteArray(12).also(entrada::readFully)
    if (iteraciones !in 1..MAXIMO_ITERACIONES) throw RespaldoInvalido("El archivo de respaldo está dañado.")

    val contenido = try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, clave(contrasena, sal, iteraciones), GCMParameterSpec(128, iv))
            updateAAD(archivo, 0, largoCabecera)
            doFinal(archivo, largoCabecera, archivo.size - largoCabecera)
        }
    } catch (e: AEADBadTagException) {
        throw RespaldoInvalido("Contraseña incorrecta, o el archivo está dañado.")
    }
    val datos = DataInputStream(ByteArrayInputStream(contenido))
    val largoManifiesto = datos.readInt()
    if (largoManifiesto !in 0..contenido.size - 4) throw RespaldoInvalido("El archivo de respaldo está dañado.")
    val props = Properties().apply { load(java.io.InputStreamReader(ByteArrayInputStream(contenido, 4, largoManifiesto), Charsets.UTF_8)) }
    val baseDatos = contenido.copyOfRange(4 + largoManifiesto, contenido.size)
    if (!baseDatos.copyOfRange(0, minOf(16, baseDatos.size)).contentEquals("SQLite format 3\u0000".toByteArray())) throw RespaldoInvalido("El respaldo no contiene una base de datos válida.")
    val manifiesto = runCatching {
        Manifiesto(
            props.getProperty("versionApp"), props.getProperty("esquema").toInt(), props.getProperty("fecha").toLong(),
            props.getProperty("clientes").toInt(), props.getProperty("regiones").split(',').filter { it.isNotEmpty() },
        )
    }.getOrElse { throw RespaldoInvalido("El archivo de respaldo está dañado.") }
    return manifiesto to baseDatos
}

private fun clave(contrasena: CharArray, sal: ByteArray, iteraciones: Int): SecretKeySpec {
    val spec = PBEKeySpec(contrasena, sal, iteraciones, 256)
    try {
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}
