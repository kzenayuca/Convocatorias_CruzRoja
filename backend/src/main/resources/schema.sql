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
