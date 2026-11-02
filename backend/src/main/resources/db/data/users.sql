INSERT INTO users (id,name,email,password_hash,role,graduated,status) VALUES
('00000000-0000-0000-0000-000000000001','Ana Torres','voluntario@demo.pe','$2a$10$XUhiAZZyjO5mCnBvXq1.ieKzneDf5QZSFOZ9IWjghVhXi4asOBEKC','VOLUNTARIO',false,'ACTIVO'),
('00000000-0000-0000-0000-000000000002','Luis Quispe','reclutador@demo.pe','$2a$10$XUhiAZZyjO5mCnBvXq1.ieKzneDf5QZSFOZ9IWjghVhXi4asOBEKC','RECLUTADOR',true,'ACTIVO'),
('00000000-0000-0000-0000-000000000003','Alejandro Ruiz','jefatura@demo.pe','$2a$10$XUhiAZZyjO5mCnBvXq1.ieKzneDf5QZSFOZ9IWjghVhXi4asOBEKC','JEFATURA',false,'ACTIVO')
ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,role=EXCLUDED.role,graduated=EXCLUDED.graduated,status=EXCLUDED.status;
