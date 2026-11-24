package pe.cruzroja.convocatorias.operations.application;

import java.time.YearMonth;

/** Contrato mínimo para saber si un mes ya fue aprobado. */
public interface MonthlyReportClosureRepository {
    boolean closed(YearMonth month);
}
