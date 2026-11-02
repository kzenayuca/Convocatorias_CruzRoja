package pe.cruzroja.convocatorias.inventory.domain;

import java.util.UUID;

public class Loan {
    private final UUID id;
    private final Material material;
    private final int quantity;
    private LoanStatus status = LoanStatus.PRESTADO;

    public Loan(UUID id, Material material, int quantity) {
        if (id == null || material == null || quantity <= 0) throw new IllegalArgumentException("Préstamo inválido");
        material.reserve(quantity);
        this.id = id;
        this.material = material;
        this.quantity = quantity;
    }

    public void returnMaterial() { finish(LoanStatus.DEVUELTO, false); }
    public void markDamaged() { finish(LoanStatus.DANADO, true); }
    public void markLost() { finish(LoanStatus.PERDIDO, true); }

    private void finish(LoanStatus next, boolean deductStock) {
        if (status.isFinal()) throw new IllegalStateException("El préstamo ya está cerrado");
        if (deductStock) material.lose(quantity); else material.release(quantity);
        status = next;
    }

    public UUID id() { return id; }
    public LoanStatus status() { return status; }
}
