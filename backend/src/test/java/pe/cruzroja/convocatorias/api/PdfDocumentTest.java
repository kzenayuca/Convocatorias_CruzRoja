package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.shared.web.PdfDocument;

class PdfDocumentTest {
    @Test
    void downloadIsAReadableMultiPagePdfNotAPrintDialog() throws Exception {
        var lines = new ArrayList<String>();
        for (int i = 0; i < 140; i++)
            lines.add(
                    "Línea "
                            + i
                            + " - Niño, información, programación y devolución. "
                            + "W".repeat(140));
        var response = PdfDocument.download("prueba.pdf", "Programación diaria", lines);
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment"));
        try (var pdf = Loader.loadPDF(response.getBody())) {
            assertTrue(pdf.getNumberOfPages() > 1);
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Programación diaria"));
            assertTrue(text.contains("Línea 139"));
        }
    }
}
