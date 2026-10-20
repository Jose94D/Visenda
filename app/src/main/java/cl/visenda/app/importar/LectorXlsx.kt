package cl.visenda.app.importar

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lee la primera hoja de un .xlsx sin librerías: un .xlsx es un ZIP con archivos XML.
 * Devuelve las filas como texto; las celdas vacías intermedias quedan como "".
 * shortcut: no lee estilos, así que las fechas llegan como número de serie (usar [fechaExcel]); basta para el kardex.
 */
object LectorXlsx {
    private const val MAX_BYTES_POR_ARCHIVO = 50_000_000 // el archivo viene del usuario: evita ZIPs maliciosos

    class FormatoInvalido(mensaje: String) : Exception(mensaje)

    fun leer(entrada: InputStream): List<List<String>> {
        val archivos = descomprimir(entrada)
        val libro = xml(archivos["xl/workbook.xml"] ?: throw FormatoInvalido("No es un archivo .xlsx"))
        val primeraHoja = libro.getElementsByTagName("sheet").item(0) as? Element
            ?: throw FormatoInvalido("El libro no tiene hojas")
        val relId = primeraHoja.getAttribute("r:id")
        val relaciones = archivos["xl/_rels/workbook.xml.rels"]?.let(::xml)?.getElementsByTagName("Relationship")
        val destino = (0 until (relaciones?.length ?: 0)).map { relaciones!!.item(it) as Element }
            .firstOrNull { it.getAttribute("Id") == relId }?.getAttribute("Target") ?: "worksheets/sheet1.xml"
        val rutaHoja = if (destino.startsWith("/")) destino.drop(1) else "xl/$destino"
        val hoja = xml(archivos[rutaHoja] ?: throw FormatoInvalido("Falta la hoja $rutaHoja"))
        val compartidos = archivos["xl/sharedStrings.xml"]?.let(::textosCompartidos).orEmpty()

        val filas = hoja.getElementsByTagName("row")
        return (0 until filas.length).map { i ->
            val celdas = (filas.item(i) as Element).getElementsByTagName("c")
            val fila = mutableListOf<String>()
            for (j in 0 until celdas.length) {
                val c = celdas.item(j) as Element
                val columna = c.getAttribute("r").takeIf { it.isNotEmpty() }?.let(::indiceColumna) ?: fila.size
                while (fila.size < columna) fila += ""
                fila += valor(c, compartidos)
            }
            fila
        }.dropLastWhile { fila -> fila.all { it.isBlank() } }
    }

    /** Número de serie de Excel (días desde 1899-12-30) a fecha. */
    fun fechaExcel(serie: String): LocalDate? = serie.toDoubleOrNull()?.let { LocalDate.of(1899, 12, 30).plusDays(it.toLong()) }

    /** "A" → 0, "Z" → 25, "AA" → 26; ignora la parte numérica de "AB12". */
    fun indiceColumna(referencia: String): Int =
        referencia.takeWhile { it.isLetter() }.uppercase().fold(0) { acc, letra -> acc * 26 + (letra - 'A' + 1) } - 1

    private fun valor(c: Element, compartidos: List<String>): String {
        fun hijo(tag: String) = c.getElementsByTagName(tag).item(0)?.textContent
        return when (c.getAttribute("t")) {
            "s" -> hijo("v")?.toIntOrNull()?.let { compartidos.getOrNull(it) }
            "inlineStr" -> (0 until c.getElementsByTagName("t").length).joinToString("") { c.getElementsByTagName("t").item(it).textContent }
            "b" -> if (hijo("v") == "1") "VERDADERO" else "FALSO"
            else -> hijo("v")?.let { v -> v.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && !v.contains('E') }?.toLong()?.toString() ?: v }
        }.orEmpty().trim()
    }

    /** Cada <si> puede tener texto con formato en varios <t>: se concatenan. */
    private fun textosCompartidos(bytes: ByteArray): List<String> {
        val si = xml(bytes).getElementsByTagName("si")
        return (0 until si.length).map { i ->
            val ts = (si.item(i) as Element).getElementsByTagName("t")
            (0 until ts.length).joinToString("") { ts.item(it).textContent }
        }
    }

    private fun descomprimir(entrada: InputStream): Map<String, ByteArray> {
        val archivos = mutableMapOf<String, ByteArray>()
        ZipInputStream(entrada).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                if (!entrada.name.endsWith(".xml") && !entrada.name.endsWith(".rels")) continue
                val salida = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    salida.write(buffer, 0, n)
                    if (salida.size() > MAX_BYTES_POR_ARCHIVO) throw FormatoInvalido("El archivo es demasiado grande")
                }
                archivos[entrada.name] = salida.toByteArray()
            }
        }
        if (archivos.isEmpty()) throw FormatoInvalido("No es un archivo .xlsx")
        return archivos
    }

    private fun xml(bytes: ByteArray): Document {
        val fabrica = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            // Sin DTD ni entidades externas: el XML viene de un archivo del usuario.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return runCatching { fabrica.newDocumentBuilder().parse(bytes.inputStream()) }
            .getOrElse { throw FormatoInvalido("El archivo está dañado") }
    }
}
