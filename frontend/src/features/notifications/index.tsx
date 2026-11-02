import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'notifications', nav: [{ label: 'Avisos', path: '/notifications', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/notifications', element: <ModulePage title='Avisos y comunicados' description='Consulta novedades oficiales y accede directamente a cada tarea.' action='Nuevo comunicado' items={[{ title: 'Inscripción actualizada', detail: 'Tu inscripción fue confirmada', status: 'Sin leer', tone: 'warning' as const },
{ title: 'Comunicado de jefatura', detail: 'Reunión general el viernes a las 18:00', status: 'Leído', tone: 'ok' as const }]} /> }] };
export default feature;
