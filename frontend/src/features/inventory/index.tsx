import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'inventory', nav: [{ label: 'Inventario', path: '/inventory', icon: '•', roles: ['RECLUTADOR','JEFATURA'] }], routes: [{ path: '/inventory', element: <ModulePage title='Inventario' description='Controla materiales disponibles, asignaciones y préstamos.' action='Registrar material' items={[{ title: 'Botiquín portátil', detail: '20 total · 6 reservados · 14 disponibles', status: 'Stock disponible', tone: 'ok' as const },
{ title: 'Radios de comunicación', detail: '10 total · 9 reservados · 1 disponible', status: 'Stock bajo', tone: 'warning' as const }]} /> }] };
export default feature;
