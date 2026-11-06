package pe.cruzroja.convocatorias.operations.domain;

/** El cierre es inmutable; los movimientos posteriores se agregan, no sobrescriben el original. */
public record MonthlyBalance(double earned,double originalDeduction,double originalHours,double deductionDelta,boolean exempt) {
    public double deductions() { return exempt?0:round(originalDeduction+deductionDelta); }
    public double adjustment() { return exempt?originalDeduction:round(-deductionDelta); }
    public double hours() { return round(originalHours+adjustment()); }
    private static double round(double value) { return Math.round(value*10000.0)/10000.0; }
}
