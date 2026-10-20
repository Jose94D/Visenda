package cl.visenda.app.importar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LectorXlsxTest {
    private fun xlsx(vararg archivos: Pair<String, String>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            archivos.forEach { (nombre, contenido) ->
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido.toByteArray())
                zip.closeEntry()
            }
        }
        return salida.toByteArray()
    }

    // La hoja no se llama sheet1.xml: obliga a seguir workbook.xml → relaciones, como hace Excel con hojas reordenadas.
    private val libro = """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets><sheet name="Kardex" sheetId="1" r:id="rId7"/></sheets></workbook>"""
    private val relaciones = """<Relationships>
        <Relationship Id="rId7" Target="worksheets/hoja.xml"/></Relationships>"""
    private val compartidos = """<sst><si><t>Nombre</t></si><si><t>Dirección</t></si>
        <si><r><t>Dra. Valentina </t></r><r><t>Rojas</t></r></si><si><t>Lincoyán 470, Local 4</t></si></sst>"""
    private val hoja = """<worksheet><sheetData>
        <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="inlineStr"><is><t>Cumpleaños</t></is></c></row>
        <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>15.0</v></c><c r="D2"><v>46312</v></c></row>
        <row r="3"></row>
        </sheetData></worksheet>"""

    @Test
    fun leeTextosCompartidosEnLineaNumerosYCeldasSaltadas() {
        val bytes = xlsx(
            "xl/workbook.xml" to libro, "xl/_rels/workbook.xml.rels" to relaciones,
            "xl/sharedStrings.xml" to compartidos, "xl/worksheets/hoja.xml" to hoja,
        )
        val filas = LectorXlsx.leer(bytes.inputStream())
        assertEquals(
            listOf(
                listOf("Nombre", "Dirección", "", "Cumpleaños"),
                listOf("Dra. Valentina Rojas", "Lincoyán 470, Local 4", "15", "46312"),
            ),
            filas,
        )
        assertEquals(LocalDate.of(2026, 10, 17), LectorXlsx.fechaExcel(filas[1][3]))
    }

    @Test
    fun indiceDeColumna() {
        assertEquals(0, LectorXlsx.indiceColumna("A1"))
        assertEquals(25, LectorXlsx.indiceColumna("Z9"))
        assertEquals(27, LectorXlsx.indiceColumna("AB12"))
    }

    @Test(expected = LectorXlsx.FormatoInvalido::class)
    fun rechazaArchivosQueNoSonXlsx() {
        LectorXlsx.leer("esto no es un zip".byteInputStream())
    }
}
