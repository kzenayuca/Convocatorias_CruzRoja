-- Cuentas exclusivas para desarrollo local. Contraseña común: convocatorias.
INSERT INTO users (id, name, email, password_hash, role, graduated, status) VALUES
('00000000-0000-0000-0000-000000000001', 'Ana Torres', 'voluntario@demo.pe', '$2b$12$CcAB2QEU.0aMjHJ5IkC2leOiABAaCXB0OuAefOcchvTSPY6LTZpvG', 'VOLUNTARIO', false, 'ACTIVO'),
('00000000-0000-0000-0000-000000000002', 'Luis Quispe', 'reclutador@demo.pe', '$2b$12$CcAB2QEU.0aMjHJ5IkC2leOiABAaCXB0OuAefOcchvTSPY6LTZpvG', 'RECLUTADOR', true, 'ACTIVO'),
('00000000-0000-0000-0000-000000000003', 'Alejandro Ruiz', 'jefatura@demo.pe', '$2b$12$CcAB2QEU.0aMjHJ5IkC2leOiABAaCXB0OuAefOcchvTSPY6LTZpvG', 'JEFATURA', false, 'ACTIVO')
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash;

INSERT INTO callouts (id, title, description, start_at, end_at, location, requirements, minimum_level, capacity, minimum_participants, status)
VALUES ('10000000-0000-0000-0000-000000000001', 'Campaña de donación de sangre', 'Apoyo a donantes', CURRENT_TIMESTAMP + INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '7 days 6 hours', 'Cercado', 'Nivel básico', 'BASICO', 40, 20, 'PUBLICADA')
ON CONFLICT DO NOTHING;

INSERT INTO materials (id, name, category, total_stock, reserved_stock)
VALUES ('20000000-0000-0000-0000-000000000001', 'Botiquín portátil', 'Primeros auxilios', 20, 6)
ON CONFLICT DO NOTHING;
