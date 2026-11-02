package pe.cruzroja.convocatorias.inventory.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryTest {
    @Test void reserve_masQueDisponible_lanzaExcepcion() {
        var material = new Material(UUID.randomUUID(), "Botiquín", "Salud", 10, 8);
        assertThrows(IllegalArgumentException.class, () -> material.reserve(3));
    }

    @Test void returnMaterial_prestamoActivo_liberaStock() {
        var material = new Material(UUID.randomUUID(), "Radio", "Comunicación", 10, 0);
        var loan = new Loan(UUID.randomUUID(), material, 2);
        loan.returnMaterial();
        assertAll(() -> assertEquals(10, material.available()), () -> assertEquals(LoanStatus.DEVUELTO, loan.status()));
    }

    @Test void markLost_prestamoActivo_descuentaTotal() {
        var material = new Material(UUID.randomUUID(), "Chaleco", "Protección", 10, 0);
        var loan = new Loan(UUID.randomUUID(), material, 2);
        loan.markLost();
        assertEquals(8, material.total());
    }
}
