import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'attendance', nav: [{ label: 'Asistencia', path: '/attendance', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/attendance', element: <ModulePage title='Asistencia y horas' description='Registra entrada y salida; las horas se calculan automáticamente.' action='Pasar lista' items={[{ title: 'Campaña de donación de sangre', detail: 'Check-in 08:00 · Check-out 12:30 · 4.5 horas', status: 'Presente', tone: 'ok' as const },
{ title: 'Apoyo en emergencias', detail: 'Sin check-in ni check-out · 0 horas', status: 'Ausente', tone: 'warning' as const }]} /> }] };
export default feature;
