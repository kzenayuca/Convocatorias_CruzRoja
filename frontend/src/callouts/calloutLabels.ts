export const positionNames: Record<string, string> = {
    TOTAL: 'Total de voluntarios',
    RESPONSABLE: 'Responsable',
    SOCORRISTA: 'Voluntario · graduado o en formación',
    GRADUADO: 'Voluntario graduado',
    FORMACION: 'En formación · solo reemplazo histórico',
    RADIOPERADOR: 'Radioperador',
    PILOTO: 'Piloto',
};

export const calloutStatusNames: Record<string, string> = {
    BORRADOR: 'Borrador',
    ABIERTA: 'Abierta',
    CERRADA: 'Completa',
    CANCELADA: 'Cancelada',
    FINALIZADA: 'Finalizada',
    PUBLICADA: 'Publicada (histórico)',
    CONFIRMADA: 'Confirmada (histórico)',
};

export const categoryNames: Record<string, string> = { E: 'E · Externo', I: 'I · Interno', G: 'G · Guardia' };

export const statusLabel = (status: string) => calloutStatusNames[status] || status;
export const categoryLabel = (category: string) => categoryNames[category] || 'Categoría histórica';
export const formatHours = (hours: unknown) => Number(hours).toLocaleString('es-PE');