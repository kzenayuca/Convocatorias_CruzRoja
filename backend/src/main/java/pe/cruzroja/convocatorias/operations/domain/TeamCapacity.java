package pe.cruzroja.convocatorias.operations.domain;

/** Personas y requisitos dentro de un único total; las funciones no agregan plazas. */
public record TeamCapacity(int total,int graduates,int radio,int pilots) {
    public TeamCapacity {
        if(total<1 || graduates<0 || graduates>total || radio<0 || pilots<0 || radio+pilots>total)
            throw new IllegalArgumentException("Cupos incompatibles con el total de voluntarios.");
    }
    public boolean fits(int people,int graduated,int radioPeople,int pilotPeople) {
        return people<=total && people-graduated<=total-graduates
            && radioPeople<=radio && pilotPeople<=pilots
            && people-radioPeople-pilotPeople<=total-radio-pilots;
    }
    public boolean complete(int people,int graduated,int radioPeople,int pilotPeople) {
        return people==total && graduated>=graduates && radioPeople>=radio && pilotPeople>=pilots;
    }
}
