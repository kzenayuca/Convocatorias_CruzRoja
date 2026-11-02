import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'callouts', nav: [{ label: 'Convocatorias', path: '/callouts', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/callouts', element: <ModulePage title='Convocatorias' description='Encuentra servicios compatibles y revisa horarios y cupos.' action='Nueva convocatoria' items={[{ title: 'Campaña de donación de sangre', detail: '14 nov · 08:00–12:00 · Cercado · 32/40 cupos', status: 'Publicada', tone: 'ok' as const },
{ title: 'Apoyo en emergencias', detail: '20 nov · 07:30–17:00 · Camaná · faltan 2 para el mínimo', status: 'Mínimo pendiente', tone: 'warning' as const }]} /> }] };
export default feature;
