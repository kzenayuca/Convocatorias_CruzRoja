import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'users', nav: [{ label: 'Usuarios', path: '/users', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/users', element: <ModulePage title='Usuarios y perfiles' description='Consulta perfiles, disponibilidad y nivel de formación.' action='Editar mi perfil' items={[{ title: 'Ana Torres', detail: 'Voluntaria · Nivel básico', status: 'Activo', tone: 'ok' as const },
{ title: 'Luis Quispe', detail: 'Reclutador graduado · Nivel avanzado', status: 'Puede gestionar', tone: 'ok' as const }]} /> }] };
export default feature;
