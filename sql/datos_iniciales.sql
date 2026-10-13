-- =====================================================================
-- Datos iniciales que el sistema NECESITA para funcionar.
--
-- El codigo busca los estados de cita por su CODIGO y los roles por su
-- NOMBRE; si no existen, las pantallas fallan. Este script solo inserta
-- lo que falta: en la BD de Azure, que ya los tiene, no cambia nada.
--
-- Orden para una BD nueva:
--   1. his_schema.sql
--   2. datos_iniciales.sql   (este archivo)
--   3. correcciones_bd.sql
-- =====================================================================

SET search_path = his, public;

-- ---------------------------------------------------------------------
-- Estados de la cita, en el orden del flujo de atencion.
-- ---------------------------------------------------------------------
INSERT INTO his.estado_cita (codigo, nombre, descripcion, color_hex, orden)
SELECT v.codigo, v.nombre, v.descripcion, v.color_hex, v.orden
  FROM (VALUES
    ('PENDIENTE_PAGO',      'Pendiente de pago',   'Cita agendada, falta el pago',                 '#D97706', 1),
    ('CONFIRMADA',          'Confirmada',          'Cita pagada, esperando la llegada',            '#16A34A', 2),
    ('PACIENTE_PRESENTE',   'Paciente Presente',   'Recepcion registro la llegada',                '#0D9488', 3),
    ('SIGNOS_VITALES',      'Signos Vitales',      'Enfermeria esta tomando los signos vitales',   '#0891B2', 4),
    ('EN_ESPERA',           'En Espera',           'Esperando ser llamado a consulta',             '#2563EB', 5),
    ('CONSULTA_MEDICA',     'Consulta Medica',     'El medico esta atendiendo al paciente',        '#7C3AED', 6),
    ('EVALUADO',            'Evaluado',            'Consulta finalizada, pendiente de cierre',     '#9333EA', 7),
    ('ATENCION_FINALIZADA', 'Atencion Finalizada', 'Atencion cerrada por el medico',               '#475569', 8),
    ('CANCELADA',           'Cancelada',           'Cita cancelada',                               '#DC2626', 9),
    ('NO_ASISTIO',          'No Asistio',          'El paciente no se presento a la cita',         '#B91C1C', 10)
  ) AS v(codigo, nombre, descripcion, color_hex, orden)
 WHERE NOT EXISTS (SELECT 1 FROM his.estado_cita e WHERE e.codigo = v.codigo);


-- ---------------------------------------------------------------------
-- Roles (RN-CU01-03 + Paciente, que se asigna en el registro del portal).
-- El codigo compara los nombres sin tilde: "Medico", "Farmaceutico".
-- ---------------------------------------------------------------------
INSERT INTO his.rol (nombre, descripcion)
SELECT v.nombre, v.descripcion
  FROM (VALUES
    ('Administrador', 'Administra usuarios, catalogos y configuracion'),
    ('Medico',        'Atiende consultas y gestiona su agenda'),
    ('Enfermero',     'Toma los signos vitales'),
    ('Recepcionista', 'Recibe a los pacientes y verifica sus citas'),
    ('Cajero',        'Cobra consultas y ordenes de laboratorio'),
    ('Laboratorista', 'Procesa y publica resultados de laboratorio'),
    ('Farmaceutico',  'Despacha medicamentos y maneja el inventario'),
    ('Paciente',      'Usuario externo del portal de citas')
  ) AS v(nombre, descripcion)
 WHERE NOT EXISTS (SELECT 1 FROM his.rol r WHERE lower(r.nombre) = lower(v.nombre) AND r.state = 1);
