-- Cuentas exclusivas para desarrollo local. Contraseña común: convocatorias.
INSERT INTO users (id, name, email, password_hash, role, graduated, status)
SELECT id::uuid, name, email, '$2b$12$CcAB2QEU.0aMjHJ5IkC2leOiABAaCXB0OuAefOcchvTSPY6LTZpvG', role, graduated, status
FROM (VALUES
    ('00000000-0000-0000-0000-000000000001', 'Ana Torres', 'voluntario@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000002', 'Luis Quispe', 'reclutador@demo.pe', 'RECLUTADOR', true, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000003', 'Alejandro Ruiz', 'jefatura@demo.pe', 'JEFATURA', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000004', 'María Flores', 'maria@demo.pe', 'VOLUNTARIO', true, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000005', 'Diego Rojas', 'diego@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000006', 'Lucía Vega', 'lucia@demo.pe', 'VOLUNTARIO', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000007', 'Carlos Medina', 'reclutador.formacion@demo.pe', 'RECLUTADOR', false, 'ACTIVO'),
    ('00000000-0000-0000-0000-000000000008', 'Sofía Aguilar', 'reclutador2@demo.pe', 'RECLUTADOR', true, 'ACTIVO'),
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
