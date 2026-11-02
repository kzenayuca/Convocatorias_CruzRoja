import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'enrollments', nav: [{ label: 'Inscripciones', path: '/enrollments', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/enrollments', element: <ModulePage title='Inscripciones y participantes' description='Sigue solicitudes y organiza el equipo antes del servicio.' action='Inscribirme' items={[{ title: 'Campaña de donación de sangre', detail: 'Solicitud enviada el 2 de noviembre', status: 'Pendiente', tone: 'warning' as const },
{ title: 'Capacitación en primeros auxilios', detail: 'Participación aceptada', status: 'Confirmada', tone: 'ok' as const }]} /> }] };
export default feature;
