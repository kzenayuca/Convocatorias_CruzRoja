package pe.cruzroja.convocatorias.api;

/** Reloj exclusivo de los fixtures HTTP históricos; no forma parte del núcleo de producción. */
final class Store {
    private Store() { }
    static java.time.LocalDateTime now() { return java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")); }
}
