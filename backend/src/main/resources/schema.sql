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
