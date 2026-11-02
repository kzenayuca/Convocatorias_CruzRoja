import type { Feature } from '../../types';
import { ModulePage } from '../../shared/ui/ModulePage';
const feature: Feature = { id: 'justifications', nav: [{ label: 'Justificaciones', path: '/justifications', icon: '•', roles: ['VOLUNTARIO','RECLUTADOR','JEFATURA'] }], routes: [{ path: '/justifications', element: <ModulePage title='Justificaciones' description='Presenta y resuelve ausencias dentro del plazo de 72 horas.' action='Justificar ausencia' items={[{ title: 'Ausencia · Apoyo en emergencias', detail: 'Quedan 48 horas para presentar el motivo', status: 'Plazo vigente', tone: 'warning' as const },
{ title: 'Campaña médica', detail: 'Motivo de salud revisado por jefatura', status: 'Aprobada', tone: 'ok' as const }]} /> }] };
export default feature;
