package cl.visenda.app.mapas

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Lector mínimo de .tar (ustar/pax) para los paquetes de rutas: solo archivos normales, sin enlaces.
 * El archivo viene de internet, así que se rechaza todo nombre que pueda escribir fuera de [destino].
 * Devuelve las rutas relativas extraídas.
 */
fun extraerTar(entrada: InputStream, destino: File): List<String> {
    val raiz = destino.canonicalFile
    val extraidos = mutableListOf<String>()
    val cabecera = ByteArray(512)
    while (true) {
        if (!leerCompleto(entrada, cabecera)) break
        if (cabecera.all { it == 0.toByte() }) break // fin del archivo
        val nombre = texto(cabecera, 0, 100).let { n -> texto(cabecera, 345, 155).let { p -> if (p.isEmpty()) n else "$p/$n" } }
        val tamano = texto(cabecera, 124, 12).trim().ifEmpty { "0" }.toLong(8)
        val tipo = cabecera[156].toInt().toChar()
        val relleno = (512 - tamano % 512) % 512
        when (tipo) {
            '0', '\u0000' -> {
                if (nombre.startsWith("/") || nombre.split('/').any { it == ".." }) throw IOException("Nombre no permitido en el paquete: $nombre")
                val archivo = File(raiz, nombre).canonicalFile
                if (!archivo.path.startsWith(raiz.path + File.separator)) throw IOException("Nombre no permitido en el paquete: $nombre")
                archivo.parentFile!!.mkdirs()
                archivo.outputStream().use { copiar(entrada, it, tamano) }
                saltar(entrada, relleno)
                extraidos += nombre
            }
            '5', 'x', 'g' -> saltar(entrada, tamano + relleno) // carpetas y cabeceras pax: nada que escribir
            else -> throw IOException("Tipo de entrada no soportado en el paquete: $tipo")
        }
    }
    return extraidos
}

private fun texto(b: ByteArray, desde: Int, largo: Int) =
    String(b, desde, largo, Charsets.UTF_8).substringBefore('\u0000')

private fun leerCompleto(entrada: InputStream, buf: ByteArray): Boolean {
    var leidos = 0
    while (leidos < buf.size) {
        val n = entrada.read(buf, leidos, buf.size - leidos)
        if (n < 0) return if (leidos == 0) false else throw IOException("Paquete cortado")
        leidos += n
    }
    return true
}

private fun copiar(entrada: InputStream, salida: java.io.OutputStream, bytes: Long) {
    val buf = ByteArray(64 * 1024)
    var quedan = bytes
    while (quedan > 0) {
        val n = entrada.read(buf, 0, minOf(buf.size.toLong(), quedan).toInt())
        if (n < 0) throw IOException("Paquete cortado")
        salida.write(buf, 0, n)
        quedan -= n
    }
}

private fun saltar(entrada: InputStream, bytes: Long) {
    var quedan = bytes
    while (quedan > 0) {
        val n = entrada.skip(quedan)
        if (n <= 0) { if (entrada.read() < 0) throw IOException("Paquete cortado") else quedan-- } else quedan -= n
    }
}
