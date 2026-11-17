-- Initial adoption preserves the existing schema and all historical records.
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    graduated BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS volunteer_profiles (
    user_id UUID PRIMARY KEY,
    document VARCHAR(20) NOT NULL UNIQUE,
    phone VARCHAR(30),
    address VARCHAR(200),
    availability VARCHAR(200),
    training_level VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS callouts (
    id UUID PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    location VARCHAR(200),
    requirements TEXT,
    minimum_level VARCHAR(30) NOT NULL,
    capacity INT NOT NULL,
    minimum_participants INT NOT NULL,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS enrollments (
    id UUID PRIMARY KEY,
    volunteer_id UUID NOT NULL,
    callout_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (volunteer_id, callout_id)
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL,
    volunteer_id UUID NOT NULL,
    callout_id UUID NOT NULL,
    check_in TIMESTAMP,
    check_out TIMESTAMP,
    responsible_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    signed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS justifications (
    id UUID PRIMARY KEY,
    absence_id UUID NOT NULL UNIQUE,
    volunteer_id UUID NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    resolution_comment TEXT
);

CREATE TABLE IF NOT EXISTS materials (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(80),
    total_stock INT NOT NULL,
    reserved_stock INT NOT NULL
);

CREATE TABLE IF NOT EXISTS material_assignments (
    id UUID PRIMARY KEY,
    material_id UUID NOT NULL,
    callout_id UUID NOT NULL,
    quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS loans (
    id UUID PRIMARY KEY,
    material_id UUID NOT NULL,
    volunteer_id UUID NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    loaned_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(300),
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    audience VARCHAR(30) NOT NULL,
    role VARCHAR(30),
    published_by UUID NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Evolución aditiva: no se borran tablas ni datos locales.
ALTER TABLE users ADD COLUMN IF NOT EXISTS surnames VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS occupation VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS review_comment TEXT;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS kind VARCHAR(30) NOT NULL DEFAULT 'SERVICIO';
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS area VARCHAR(120) NOT NULL DEFAULT 'Voluntariado';
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS contact VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS responsible_id UUID REFERENCES users(id);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS opened_at TIMESTAMP;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS observations TEXT NOT NULL DEFAULT '';
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS approved_hours NUMERIC(10,4);
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS observations TEXT NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS attendance_enrollment_unique ON attendance_records(enrollment_id);
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS comment TEXT;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS replacement_for UUID REFERENCES enrollments(id);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS late BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS profile_changes (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    name VARCHAR(120) NOT NULL, surnames VARCHAR(160) NOT NULL,
    email VARCHAR(180) NOT NULL, document VARCHAR(20) NOT NULL,
    occupation VARCHAR(160) NOT NULL, submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS material_requests (
    id UUID PRIMARY KEY, callout_id UUID NOT NULL REFERENCES callouts(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    name VARCHAR(160) NOT NULL, quantity INT NOT NULL CHECK (quantity > 0),
    details TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS enrollment_callout_status ON enrollments(callout_id, status);
CREATE INDEX IF NOT EXISTS notification_recipient ON notifications(recipient_id);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS team_photo BYTEA;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS photo_type VARCHAR(30);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_case_insensitive ON users(lower(email));

-- Versión 2.2: puestos, coordinación separada del encargado y trazabilidad de materiales.
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS service_code VARCHAR(40);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS service_type VARCHAR(100) NOT NULL DEFAULT 'Servicio comunitario';
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS socorrista_slots INT NOT NULL DEFAULT 1 CHECK (socorrista_slots > 0);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS radio_slots INT NOT NULL DEFAULT 0 CHECK (radio_slots BETWEEN 0 AND 1);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS pilot_slots INT NOT NULL DEFAULT 0 CHECK (pilot_slots BETWEEN 0 AND 1);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS planned_hours NUMERIC GENERATED ALWAYS AS (EXTRACT(EPOCH FROM (end_at-start_at))/3600) STORED;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS position VARCHAR(30) NOT NULL DEFAULT 'SOCORRISTA';
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
ALTER TABLE volunteer_profiles ADD COLUMN IF NOT EXISTS radio_qualified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE volunteer_profiles ADD COLUMN IF NOT EXISTS pilot_qualified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS material_id UUID REFERENCES materials(id);
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS received_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS returned_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS received_by UUID REFERENCES users(id);
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS returned_by UUID REFERENCES users(id);
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS received_at TIMESTAMP;
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS returned_at TIMESTAMP;
ALTER TABLE material_requests ADD COLUMN IF NOT EXISTS condition_note TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'AUSENCIA';
-- Los registros existentes siguen con su cálculo anterior; los nuevos usan la versión 2.
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS policy_version INT NOT NULL DEFAULT 1;
ALTER TABLE attendance_records ALTER COLUMN policy_version SET DEFAULT 2;
-- Solo registros anteriores: el código marca las convocatorias que ya fueron migradas.
UPDATE callouts SET created_by=responsible_id, service_code='LEG-'||id::text,
    socorrista_slots=capacity, deadline=COALESCE(deadline,start_at),
    responsible_id=CASE WHEN start_at>CURRENT_TIMESTAMP AND opened_at IS NULL THEN NULL ELSE responsible_id END,
    status=CASE WHEN status IN ('PUBLICADA','CONFIRMADA') AND start_at>CURRENT_TIMESTAMP THEN 'ABIERTA' ELSE status END
WHERE service_code IS NULL AND created_by IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS callout_service_code_normalized ON callouts(lower(service_code));
CREATE INDEX IF NOT EXISTS enrollment_position ON enrollments(callout_id,position,status);
CREATE UNIQUE INDEX IF NOT EXISTS one_responsible_per_callout ON enrollments(callout_id)
WHERE position='RESPONSABLE' AND status='CONFIRMADA';

-- Versión 2.3: migraciones aditivas y ejecutadas una sola vez cuando corresponde.
ALTER TABLE callouts DROP CONSTRAINT IF EXISTS callouts_socorrista_slots_check;
ALTER TABLE callouts ADD CONSTRAINT callouts_socorrista_slots_check CHECK (socorrista_slots >= 0);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS graduate_slots INT NOT NULL DEFAULT 0 CHECK (graduate_slots >= 0);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS trainee_slots INT NOT NULL DEFAULT 0 CHECK (trainee_slots >= 0);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS photo_uploaded_at TIMESTAMP;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS photo_uploaded_by UUID REFERENCES users(id);
CREATE TABLE IF NOT EXISTS callout_photo_archive (
    id UUID PRIMARY KEY, callout_id UUID NOT NULL REFERENCES callouts(id),
    photo BYTEA NOT NULL, photo_type VARCHAR(30) NOT NULL,
    uploaded_by UUID REFERENCES users(id), uploaded_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS service_code_history (
    id UUID PRIMARY KEY, callout_id UUID NOT NULL REFERENCES callouts(id),
    service_code VARCHAR(40) NOT NULL, service_type VARCHAR(100) NOT NULL,
    assigned_by UUID REFERENCES users(id), assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS code_history_type ON service_code_history(service_type,assigned_at DESC);
CREATE TABLE IF NOT EXISTS institution_settings (
    id INT PRIMARY KEY CHECK(id=1), graduation_hours NUMERIC(10,2) CHECK(graduation_hours>0),
    updated_by UUID REFERENCES users(id), updated_at TIMESTAMP
);
INSERT INTO institution_settings(id) VALUES(1) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS monthly_reports (
    month DATE PRIMARY KEY, status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    approved_by UUID REFERENCES users(id), approved_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS monthly_report_rows (
    month DATE NOT NULL REFERENCES monthly_reports(month), user_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL, earned NUMERIC(12,4) NOT NULL, deductions NUMERIC(12,4) NOT NULL,
    hours NUMERIC(12,4) NOT NULL, pending INT NOT NULL, PRIMARY KEY(month,user_id)
);
INSERT INTO service_code_history(id,callout_id,service_code,service_type)
SELECT id,id,service_code,service_type FROM callouts c WHERE service_code IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM service_code_history h WHERE lower(h.service_code)=lower(c.service_code))
ON CONFLICT DO NOTHING;
CREATE UNIQUE INDEX IF NOT EXISTS code_history_reserved ON service_code_history(lower(service_code));

-- Total inclusivo. Solo se calcula una vez; no altera inscripciones ni responsables existentes.
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS total_volunteers INT CHECK(total_volunteers>0);
UPDATE callouts c SET total_volunteers=GREATEST(1,
    c.socorrista_slots+c.graduate_slots+c.trainee_slots+c.radio_slots+c.pilot_slots+
    (SELECT count(*)::int FROM enrollments e WHERE e.callout_id=c.id AND e.position='RESPONSABLE' AND e.status='CONFIRMADA'),
    (SELECT count(*)::int FROM enrollments e WHERE e.callout_id=c.id AND e.status='CONFIRMADA'))
WHERE total_volunteers IS NULL;

-- Metas generales de saldo acumulado según condición; la meta antigua se conserva para formación.
CREATE TABLE IF NOT EXISTS volunteer_hour_targets (
    condition VARCHAR(20) PRIMARY KEY CHECK(condition IN ('FORMACION','GRADUADO')),
    hours NUMERIC(10,2) CHECK(hours>0), updated_by UUID REFERENCES users(id), updated_at TIMESTAMP
);
INSERT INTO volunteer_hour_targets(condition,hours,updated_by,updated_at)
SELECT 'FORMACION',graduation_hours,updated_by,updated_at FROM institution_settings WHERE id=1
ON CONFLICT DO NOTHING;
INSERT INTO volunteer_hour_targets(condition) VALUES('GRADUADO') ON CONFLICT DO NOTHING;

-- El antiguo rol no otorga permisos globales. Se conserva la procedencia sin tocar asignaciones.
CREATE TABLE IF NOT EXISTS legacy_account_roles (
    user_id UUID PRIMARY KEY REFERENCES users(id), previous_role VARCHAR(30) NOT NULL,
    migrated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO legacy_account_roles(user_id,previous_role)
SELECT id,role FROM users WHERE role='RECLUTADOR' ON CONFLICT DO NOTHING;
UPDATE users SET role='VOLUNTARIO' WHERE role='RECLUTADOR';
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS assigned_by UUID REFERENCES users(id);
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS requirements_accepted BOOLEAN NOT NULL DEFAULT FALSE;

-- Niveles antiguos se conservan solo como datos históricos; no se usan en permisos o elegibilidad.
ALTER TABLE volunteer_profiles ALTER COLUMN training_level SET DEFAULT 'BASICO';
ALTER TABLE callouts ALTER COLUMN minimum_level SET DEFAULT 'BASICO';
CREATE TABLE IF NOT EXISTS responsible_history (
    id UUID PRIMARY KEY, callout_id UUID NOT NULL REFERENCES callouts(id),
    previous_user_id UUID REFERENCES users(id), next_user_id UUID REFERENCES users(id),
    changed_by UUID NOT NULL REFERENCES users(id), changed_at TIMESTAMP NOT NULL, reason TEXT NOT NULL
);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS resolved_by UUID REFERENCES users(id);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
CREATE TABLE IF NOT EXISTS justification_resolutions (
    id UUID PRIMARY KEY, justification_id UUID NOT NULL REFERENCES justifications(id),
    previous_status VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL,
    resolved_by UUID NOT NULL REFERENCES users(id), resolved_at TIMESTAMP NOT NULL,
    comment TEXT NOT NULL, deduction_before NUMERIC(12,4) NOT NULL, deduction_after NUMERIC(12,4) NOT NULL
);
-- Ajustes anexos: las filas originales del cierre nunca se reescriben.
CREATE TABLE IF NOT EXISTS monthly_report_adjustments (
    id UUID PRIMARY KEY, month DATE NOT NULL REFERENCES monthly_reports(month),
    user_id UUID NOT NULL REFERENCES users(id), resolution_id UUID NOT NULL UNIQUE REFERENCES justification_resolutions(id),
    deduction_delta NUMERIC(12,4) NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS monthly_adjustment_user ON monthly_report_adjustments(month,user_id);

-- Solicitudes preventivas de reemplazo y evidencia privada; se conserva todo el historial previo.
ALTER TABLE justifications ALTER COLUMN absence_id DROP NOT NULL;
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS enrollment_id UUID REFERENCES enrollments(id);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS proposed_replacement_name VARCHAR(281);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS assigned_replacement_id UUID REFERENCES users(id);
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS evidence BYTEA;
ALTER TABLE justifications ADD COLUMN IF NOT EXISTS evidence_type VARCHAR(30);
CREATE UNIQUE INDEX IF NOT EXISTS one_pending_replacement_request ON justifications(enrollment_id)
WHERE kind='REEMPLAZO' AND status='PENDIENTE';

-- Ciclos administrativos y códigos automáticos; los datos previos no se renumeran.
CREATE TABLE IF NOT EXISTS annual_enrollments (
    user_id UUID NOT NULL REFERENCES users(id), year INT NOT NULL CHECK(year BETWEEN 2000 AND 9999),
    registered_by UUID REFERENCES users(id), registered_at TIMESTAMP NOT NULL,
    source VARCHAR(30) NOT NULL, PRIMARY KEY(user_id,year)
);
CREATE TABLE IF NOT EXISTS graduation_history (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), approved_by UUID NOT NULL REFERENCES users(id),
    approved_at TIMESTAMP NOT NULL, net_hours NUMERIC(12,4) NOT NULL, required_hours NUMERIC(12,4) NOT NULL, reason TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS service_code_counters (
    category VARCHAR(1) NOT NULL CHECK(category IN ('E','I','G')), year INT NOT NULL,
    last_value INT NOT NULL CHECK(last_value>0), PRIMARY KEY(category,year)
);
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS service_category VARCHAR(1) CHECK(service_category IN ('E','I','G'));
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS code_year INT;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS code_sequence INT;
ALTER TABLE callouts ADD COLUMN IF NOT EXISTS code_created_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS callout_category_sequence ON callouts(service_category,code_year,code_sequence);
CREATE TABLE IF NOT EXISTS application_migrations (id VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- Una sola transición: mantiene compromisos existentes, sin renovar automáticamente cada enero.
INSERT INTO annual_enrollments(user_id,year,registered_at,source)
SELECT DISTINCT u.id,EXTRACT(YEAR FROM c.start_at)::int,CURRENT_TIMESTAMP,'MIGRACION'
FROM users u JOIN enrollments e ON e.volunteer_id=u.id JOIN callouts c ON c.id=e.callout_id
WHERE u.role='VOLUNTARIO' AND u.graduated=true AND EXTRACT(YEAR FROM c.start_at)>=2000
AND e.status='CONFIRMADA' AND NOT EXISTS(SELECT 1 FROM application_migrations WHERE id='annual-enrollments-v1')
ON CONFLICT DO NOTHING;
INSERT INTO application_migrations(id) VALUES('annual-enrollments-v1') ON CONFLICT DO NOTHING;
-- Meta ilustrativa inicial; nunca reemplaza una meta ya configurada por Jefatura.
UPDATE volunteer_hour_targets SET hours=200 WHERE condition='FORMACION' AND hours IS NULL;

-- Solicitudes navegables e historial de decisiones: los enlaces no desaparecen al resolver.
ALTER TABLE profile_changes ADD COLUMN IF NOT EXISTS request_id UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS profile_change_request_id ON profile_changes(request_id);
CREATE TABLE IF NOT EXISTS profile_change_requests (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL, surnames VARCHAR(160) NOT NULL, email VARCHAR(180) NOT NULL,
    document VARCHAR(20) NOT NULL, occupation VARCHAR(160) NOT NULL, submitted_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', resolution_comment TEXT,
    resolved_by UUID REFERENCES users(id), resolved_at TIMESTAMP
);
INSERT INTO profile_change_requests(id,user_id,name,surnames,email,document,occupation,submitted_at)
SELECT request_id,user_id,name,surnames,email,document,occupation,submitted_at FROM profile_changes ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS attendance_review_requests (
    id UUID PRIMARY KEY, attendance_id UUID NOT NULL REFERENCES attendance_records(id),
    volunteer_id UUID NOT NULL REFERENCES users(id), reason TEXT NOT NULL, submitted_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', resolution_comment TEXT,
    resolved_by UUID REFERENCES users(id), resolved_at TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS one_pending_attendance_review ON attendance_review_requests(attendance_id) WHERE status='PENDIENTE';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(300);
CREATE UNIQUE INDEX IF NOT EXISTS notification_event_once ON notifications(recipient_id,event_key) WHERE event_key IS NOT NULL;
