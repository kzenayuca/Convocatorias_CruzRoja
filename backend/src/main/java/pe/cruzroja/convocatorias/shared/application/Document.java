package pe.cruzroja.convocatorias.shared.application;

import java.util.List;

/** Modelo de salida independiente de PDFBox y HTTP. */
public record Document(String filename, String title, List<String> lines) {
    public Document {
        lines = List.copyOf(lines);
    }
}
