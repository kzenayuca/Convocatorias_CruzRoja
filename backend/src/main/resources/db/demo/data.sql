-- Cuentas exclusivas para desarrollo local. Contraseña común: convocatorias.
INSERT INTO users (id, name, email, password_hash, role, graduated, status)
SELECT id::uuid, name, email, '$2b$12$CcAB2QEU.0aMjHJ5IkC2leOiABAaCXB0OuAefOcchvTSPY6LTZpvG', role, graduated, status
FROM (VALUES
    ('00000000-0000-0000-0000-000000000001', 'Ana Torres', 'voluntario@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000002', 'Luis Quispe', 'reclutador@demo.pe', 'VOLUNTARIO', true, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000003', 'Alejandro Ruiz', 'jefatura@demo.pe', 'JEFATURA', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000004', 'María Flores', 'maria@demo.pe', 'VOLUNTARIO', true, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000005', 'Diego Rojas', 'diego@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000006', 'Lucía Vega', 'lucia@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000007', 'Carlos Medina', 'reclutador.formacion@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000008', 'Sofía Aguilar', 'reclutador2@demo.pe', 'VOLUNTARIO', true, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000009', 'Elena Pinto', 'jefatura2@demo.pe', 'JEFATURA', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000010', 'Pedro Salas', 'inactivo@demo.pe', 'VOLUNTARIO', false, 'INACTIVO')
) AS demo(id, name, email, role, graduated, status)
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash;

-- Datos ficticios de perfil para consultar en Adminer; no representan personas reales.
INSERT INTO volunteer_profiles (user_id, document, phone, address, availability, training_level) VALUES
('00000000-0000-0000-0000-000000000001', '70000001', '900000001', 'Cercado', 'Sábados por la mañana', 'BASICO'),
('00000000-0000-0000-0000-000000000002', '70000002', '900000002', 'Yanahuara', 'Fines de semana', 'AVANZADO'),
('00000000-0000-0000-0000-000000000003', '70000003', '900000003', 'Cercado', 'Lunes a viernes', 'AVANZADO'),
('00000000-0000-0000-0000-000000000004', '70000004', '900000004', 'Cerro Colorado', 'Mañanas y emergencias', 'AVANZADO'),
('00000000-0000-0000-0000-000000000005', '70000005', '900000005', 'José Luis Bustamante', 'Domingos', 'INTERMEDIO'),
('00000000-0000-0000-0000-000000000006', '70000006', '900000006', 'Miraflores', 'Tardes', 'BASICO'),
('00000000-0000-0000-0000-000000000007', '70000007', '900000007', 'Paucarpata', 'Sábados', 'INTERMEDIO'),
('00000000-0000-0000-0000-000000000008', '70000008', '900000008', 'Sachaca', 'Fines de semana', 'AVANZADO'),
('00000000-0000-0000-0000-000000000009', '70000009', '900000009', 'Yanahuara', 'Lunes a viernes', 'AVANZADO'),
('00000000-0000-0000-0000-000000000010', '70000010', '900000010', 'Socabaya', 'Sin disponibilidad', 'BASICO')
ON CONFLICT (user_id) DO NOTHING;

-- Casos: cupos libres, mínimo pendiente, llena, confirmada, casi llena,
-- urgente, cancelada y finalizada. Fechas relativas a la primera carga.
INSERT INTO callouts (id, title, description, start_at, end_at, location, requirements, minimum_level, capacity, minimum_participants, status)
VALUES
('10000000-0000-0000-0000-000000000001', 'Campaña de donación de sangre', 'Orientación y acompañamiento a donantes.', CURRENT_DATE + INTERVAL '7 days 8 hours', CURRENT_DATE + INTERVAL '7 days 14 hours', 'Cercado', 'Uniforme e identificación.', 'BASICO', 40, 20, 'PUBLICADA'),
('10000000-0000-0000-0000-000000000002', 'Apoyo en maratón de Arequipa', 'Servicio de seis horas; aún faltan participantes para el mínimo.', CURRENT_DATE + INTERVAL '3 days 7 hours', CURRENT_DATE + INTERVAL '3 days 13 hours', 'José Luis Bustamante', 'Disponibilidad de 07:00 a 13:00.', 'BASICO', 8, 4, 'PUBLICADA'),
('10000000-0000-0000-0000-000000000003', 'Campaña de salud en Miraflores', 'Equipo completo: ejemplo de convocatoria sin cupos.', CURRENT_DATE + INTERVAL '10 days 9 hours', CURRENT_DATE + INTERVAL '10 days 13 hours', 'Miraflores', 'Uniforme e identificación.', 'BASICO', 3, 2, 'CONFIRMADA'),
('10000000-0000-0000-0000-000000000004', 'Simulacro de evacuación', 'Se alcanzó el mínimo y aún quedan cupos disponibles.', CURRENT_DATE + INTERVAL '12 days 8 hours', CURRENT_DATE + INTERVAL '12 days 12 hours', 'Yanahuara', 'Nivel básico y puntualidad.', 'BASICO', 6, 3, 'CONFIRMADA'),
('10000000-0000-0000-0000-000000000005', 'Primeros auxilios en feria vecinal', 'Último cupo disponible para un servicio de tres horas.', CURRENT_DATE + INTERVAL '5 days 15 hours', CURRENT_DATE + INTERVAL '5 days 18 hours', 'Paucarpata', 'Uniforme y disponibilidad por la tarde.', 'BASICO', 4, 4, 'PUBLICADA'),
('10000000-0000-0000-0000-000000000006', 'Apoyo urgente en Cerro Colorado', 'Atención de primeros auxilios para una actividad de mañana.', CURRENT_DATE + INTERVAL '1 day 14 hours', CURRENT_DATE + INTERVAL '1 day 18 hours', 'Cerro Colorado', 'Nivel avanzado y equipo personal.', 'AVANZADO', 5, 3, 'PUBLICADA'),
('10000000-0000-0000-0000-000000000007', 'Jornada comunitaria en Sachaca', 'Cancelada por el organizador; las inscripciones fueron anuladas.', CURRENT_DATE + INTERVAL '8 days 8 hours', CURRENT_DATE + INTERVAL '8 days 12 hours', 'Sachaca', 'No requiere nuevas inscripciones.', 'BASICO', 10, 4, 'CANCELADA'),
('10000000-0000-0000-0000-000000000008', 'Servicio comunitario en Socabaya', 'Servicio finalizado de seis horas para consultar el historial.', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '8 hours', CURRENT_DATE - INTERVAL '5 days' + INTERVAL '14 hours', 'Socabaya', 'Actividad finalizada.', 'BASICO', 10, 2, 'FINALIZADA')
ON CONFLICT DO NOTHING;


-- Inscripciones demo para que los cupos mostrados provengan de datos reales.
INSERT INTO enrollments (id, volunteer_id, callout_id, status) VALUES
('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000003', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000004', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000005', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000005', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000005', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000006', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'CANCELADA'),
('30000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000007', 'CANCELADA'),
('30000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000008', 'CONFIRMADA'),
('30000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000008', 'CONFIRMADA')
ON CONFLICT DO NOTHING;

INSERT INTO materials (id, name, category, total_stock, reserved_stock)
VALUES ('20000000-0000-0000-0000-000000000001', 'Botiquín portátil', 'Primeros auxilios', 20, 6)
ON CONFLICT DO NOTHING;

-- Contacto histórico de los ejemplos; no se reasignan servicios ya configurados.
UPDATE callouts SET responsible_id='00000000-0000-0000-0000-000000000002'
WHERE id::text LIKE '10000000-0000-0000-0000-%' AND responsible_id IS NULL AND service_code IS NULL;

-- Asistencia del ejemplo finalizado: no crear ausencias ficticias al activar los cálculos.
INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,check_in,check_out,responsible_id,status,signed,approved_hours)
SELECT CASE WHEN e.volunteer_id='00000000-0000-0000-0000-000000000001'::uuid
       THEN '40000000-0000-0000-0000-000000000001'::uuid ELSE '40000000-0000-0000-0000-000000000002'::uuid END,
       e.id,e.volunteer_id,c.id,c.start_at,c.end_at,c.responsible_id,'CONFIRMADA',true,
       EXTRACT(EPOCH FROM (c.end_at-c.start_at))/3600
FROM enrollments e JOIN callouts c ON c.id=e.callout_id
WHERE e.id IN ('30000000-0000-0000-0000-000000000016'::uuid,'30000000-0000-0000-0000-000000000017'::uuid)
ON CONFLICT DO NOTHING;

-- Configuración por puestos solo en ejemplos recién creados. Los datos existentes no se reclasifican.
UPDATE enrollments e SET position='RESPONSABLE'
FROM callouts c WHERE e.callout_id=c.id AND c.service_code IS NULL
AND c.id::text LIKE '10000000-0000-0000-0000-%'
AND e.volunteer_id='00000000-0000-0000-0000-000000000004' AND e.status='CONFIRMADA';
UPDATE callouts c SET service_code='DEMO-'||right(c.id::text,12),
    service_type='Apoyo comunitario',socorrista_slots=GREATEST(c.capacity-1,1),
    created_by='00000000-0000-0000-0000-000000000002',deadline=c.start_at,
    responsible_id=(SELECT e.volunteer_id FROM enrollments e WHERE e.callout_id=c.id AND e.position='RESPONSABLE' AND e.status='CONFIRMADA'),
    status=CASE WHEN c.status IN ('PUBLICADA','CONFIRMADA') THEN 'ABIERTA' ELSE c.status END
WHERE c.service_code IS NULL AND c.id::text LIKE '10000000-0000-0000-0000-%';

-- Catálogo orientativo para solicitudes al almacén externo, no un inventario gestionado aquí.
INSERT INTO materials(id,name,category,total_stock,reserved_stock) VALUES
('20000000-0000-0000-0000-000000000002','Radio portátil','Comunicaciones',0,0),
('20000000-0000-0000-0000-000000000003','Carpa','Logística',0,0),
('20000000-0000-0000-0000-000000000004','Camilla','Primeros auxilios',0,0)
ON CONFLICT DO NOTHING;

-- Incluye códigos demo nuevos sin duplicar el historial ni cambiar los códigos guardados.
INSERT INTO service_code_history(id,callout_id,service_code,service_type)
SELECT md5(c.id::text || c.service_code)::uuid,c.id,c.service_code,c.service_type FROM callouts c
WHERE c.service_code IS NOT NULL AND NOT EXISTS(
    SELECT 1 FROM service_code_history h WHERE lower(h.service_code)=lower(c.service_code))
ON CONFLICT DO NOTHING;

-- Los ejemplos recién insertados también reciben su total inclusivo, sin recalcular los existentes.
UPDATE callouts c SET total_volunteers=GREATEST(1,
    c.socorrista_slots+c.graduate_slots+c.trainee_slots+c.radio_slots+c.pilot_slots+
    (SELECT count(*)::int FROM enrollments e WHERE e.callout_id=c.id AND e.position='RESPONSABLE' AND e.status='CONFIRMADA'),
    (SELECT count(*)::int FROM enrollments e WHERE e.callout_id=c.id AND e.status='CONFIRMADA'))
WHERE total_volunteers IS NULL;

-- Retira únicamente los textos predeterminados de nivel; no cambia requisitos personalizados.
UPDATE callouts SET requirements='Conocimientos de evacuación y puntualidad.'
WHERE id='10000000-0000-0000-0000-000000000004' AND requirements='Nivel básico y puntualidad.';
UPDATE callouts SET requirements='Conocimientos de primeros auxilios y equipo personal.'
WHERE id='10000000-0000-0000-0000-000000000006' AND requirements='Nivel avanzado y equipo personal.';
