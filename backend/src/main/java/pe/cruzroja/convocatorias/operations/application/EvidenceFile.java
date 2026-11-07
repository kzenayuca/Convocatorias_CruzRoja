package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Archivo privado; no conserva el nombre ni el tipo MIME proporcionados por el navegador. */
public record EvidenceFile(byte[] bytes, String type) {
    public EvidenceFile {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public static EvidenceFile inspect(byte[] bytes, ImageInspector inspector) {
        if (bytes == null) return null;
        valid(
                bytes.length > 0 && bytes.length <= 10 * 1024 * 1024,
                "La evidencia debe ser una imagen JPG/PNG de hasta 10 MB.");
        return new EvidenceFile(bytes, inspector.validate(bytes));
    }
}
