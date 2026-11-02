package pe.cruzroja.convocatorias.inventory.domain; public enum LoanStatus { PRESTADO, DEVUELTO, DANADO, PERDIDO; public boolean isFinal(){return this!=PRESTADO;} }
