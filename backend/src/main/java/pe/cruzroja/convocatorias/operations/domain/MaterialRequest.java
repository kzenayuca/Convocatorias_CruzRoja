package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.util.UUID;

public record MaterialRequest(
        UUID id, int requested, int received, boolean hasReceipt, boolean returned) {
    public void requireHandover(String action, int quantity) {
        valid(quantity >= 0, "Cantidad no válida.");
        if ("RECEPCION".equals(action))
            valid(
                    !hasReceipt && quantity > 0 && quantity <= requested,
                    "La recepción ya se registró o la cantidad supera lo solicitado.");
        else {
            valid(
                    "DEVOLUCION".equals(action) && hasReceipt && !returned,
                    "Registra primero la recepción; la devolución no puede repetirse.");
            valid(quantity <= received, "No puedes devolver más unidades de las recibidas.");
        }
    }
}
