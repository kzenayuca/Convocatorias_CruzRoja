package pe.cruzroja.convocatorias.operations.application;

/** Decodificación de imágenes es un detalle de infraestructura, no una regla de cupos. */
public interface ImageInspector { String validate(byte[] bytes); }
