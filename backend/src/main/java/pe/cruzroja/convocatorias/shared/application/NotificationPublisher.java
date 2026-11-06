package pe.cruzroja.convocatorias.shared.application;

import java.util.UUID;

/** Puerto de salida compartido; la entrega persistente participa en la transacción del caso de uso. */
public interface NotificationPublisher {
    void publish(UUID recipient,String type,String message,String link);
}
