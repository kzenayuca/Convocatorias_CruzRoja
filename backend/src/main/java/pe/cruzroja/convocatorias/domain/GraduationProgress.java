package pe.cruzroja.convocatorias.domain;

/** El requisito lo establece jefatura. El saldo negativo representa deuda, no horas logradas. */
public record GraduationProgress(double earned,double deductions,double balance,double debt,
                                 Double required,Double remaining,int pending) {
    public static GraduationProgress of(double earned,double deductions,Double required,int pending) {
        if(!Double.isFinite(earned) || !Double.isFinite(deductions) || earned<0 || deductions<0 || pending<0)
            throw new IllegalArgumentException("Totales de horas no válidos.");
        if(required!=null && (!Double.isFinite(required) || required<=0)) throw new IllegalArgumentException("El requisito debe ser positivo.");
        double balance=Math.round((earned-deductions)*10000)/10000.0;
        return new GraduationProgress(earned,deductions,balance,Math.max(0,-balance),required,
            required==null?null:Math.max(0,required-balance),pending);
    }
}
