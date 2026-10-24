package cl.visenda.app.importar

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Plantilla de kardex que el usuario descarga desde la guía: las columnas que Visenda reconoce sola y filas ficticias.
 * La Dra. Muñoz tiene dos consultas (una fila por consulta, mismo RUT) para mostrar cómo se agrupan.
 */
val PLANTILLA_KARDEX: List<List<String>> = listOf(
    listOf("Código", "Nombre", "RUT", "Especialidad", "Categoría", "Lugar", "Dirección", "Comuna", "Horario", "Teléfono", "Cumpleaños"),
    listOf("C-001", "Dra. Ana Muñoz (ejemplo)", "11.111.111-1", "Pediatría", "A", "Centro Médico Norte", "Calle Ejemplo 100, Of. 401", "Concepción", "Lun-Vie 9:00-13:00", "+56 9 1111 1111", "15-03"),
    listOf("C-001", "Dra. Ana Muñoz (ejemplo)", "11.111.111-1", "Pediatría", "A", "Consulta Particular", "Calle Ejemplo 250", "Talcahuano", "Lun, Mié 15-19", "+56 9 1111 1111", "15-03"),
    listOf("C-002", "Dr. Pedro Soto (ejemplo)", "22.222.222-2", "Cardiología", "B", "", "Calle Ejemplo 500", "Chillán", "Martes 08:30 - 12:30", "", ""),
)

/** Escribe un .xlsx mínimo de una hoja, con todas las celdas como texto. Lo lee Excel, Google Sheets y LectorXlsx. */
fun escribirXlsx(filas: List<List<String>>, salida: OutputStream) {
    fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun columna(i: Int): String = if (i < 26) ('A' + i).toString() else columna(i / 26 - 1) + ('A' + i % 26)
    val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val rels = "http://schemas.openxmlformats.org/package/2006/relationships"
    val ancho = filas.maxOfOrNull { it.size } ?: 0
    val hoja = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="$main">""")
        if (ancho > 0) append("""<cols><col min="1" max="$ancho" width="22" customWidth="1"/></cols>""")
        append("<sheetData>")
        filas.forEachIndexed { f, fila ->
            append("""<row r="${f + 1}">""")
            fila.forEachIndexed { c, texto ->
                if (texto.isNotEmpty()) append("""<c r="${columna(c)}${f + 1}" t="inlineStr"><is><t xml:space="preserve">${esc(texto)}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }
    val partes = linkedMapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$rels"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="$main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Kardex" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$rels"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
        "xl/worksheets/sheet1.xml" to hoja,
    )
    ZipOutputStream(salida).use { zip ->
        partes.forEach { (nombre, xml) ->
            zip.putNextEntry(ZipEntry(nombre))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
