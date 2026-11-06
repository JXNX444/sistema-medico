-- =====================================================================
-- DATOS DEMO PARA REPORTERIA
--
-- Carga datos de prueba para que los reportes tengan algo que mostrar:
--   * 6 medicos, 15 pacientes y 6 del personal (enfermeria, recepcion,
--     caja, laboratorio, farmacia), con nombres y usuarios realistas
--     (todos inventados). Usan la misma clave que un usuario existente.
--   * 600 citas en los ultimos ~150 dias (80% atendidas, 10% canceladas, 10% no asistio)
--   * consultas finalizadas con diagnostico CIE-10
--   * pagos aprobados (efectivo, Visa, Mastercard, debito)
--   * recetas y despachos de farmacia
--
-- Todo lo demo se identifica por:
--   cita.numero_cita       LIKE 'DEMO-%'
--   pago.numero_transaccion LIKE 'DEMO-%'
--   usuario.correo          LIKE '%@demo.local'
--
-- Se puede correr varias veces: primero borra la carga demo anterior.
-- Para BORRAR los datos demo sin volver a cargarlos: correr solo la
-- PARTE 1 (hasta la linea "FIN PARTE 1").
--
-- En DBeaver: abrir el archivo, conectarse a la BD y ejecutar como
-- script completo con Alt+X.
-- =====================================================================

SET search_path = his, public;

-- =====================================================================
-- PARTE 1: LIMPIEZA DE LA CARGA DEMO ANTERIOR
-- =====================================================================
DO $$
BEGIN
DELETE FROM his.despacho_detalle
WHERE despacho_id IN (SELECT d.despacho_id
                      FROM his.despacho d
                               JOIN his.receta r   ON r.receta_id = d.receta_id
                               JOIN his.consulta c ON c.consulta_id = r.consulta_id
                               JOIN his.cita ci    ON ci.cita_id = c.cita_id
                      WHERE ci.numero_cita LIKE 'DEMO-%');

DELETE FROM his.despacho
WHERE receta_id IN (SELECT r.receta_id
                    FROM his.receta r
                             JOIN his.consulta c ON c.consulta_id = r.consulta_id
                             JOIN his.cita ci    ON ci.cita_id = c.cita_id
                    WHERE ci.numero_cita LIKE 'DEMO-%');

DELETE FROM his.receta_detalle
WHERE receta_id IN (SELECT r.receta_id
                    FROM his.receta r
                             JOIN his.consulta c ON c.consulta_id = r.consulta_id
                             JOIN his.cita ci    ON ci.cita_id = c.cita_id
                    WHERE ci.numero_cita LIKE 'DEMO-%');

DELETE FROM his.receta
WHERE consulta_id IN (SELECT c.consulta_id
                      FROM his.consulta c
                               JOIN his.cita ci ON ci.cita_id = c.cita_id
                      WHERE ci.numero_cita LIKE 'DEMO-%');

DELETE FROM his.pago WHERE numero_transaccion LIKE 'DEMO-%';

DELETE FROM his.consulta
WHERE cita_id IN (SELECT cita_id FROM his.cita WHERE numero_cita LIKE 'DEMO-%');

-- historial que llena el trigger de cita
DELETE FROM his.cita_historial_estado
WHERE cita_id IN (SELECT cita_id FROM his.cita WHERE numero_cita LIKE 'DEMO-%');

DELETE FROM his.cita WHERE numero_cita LIKE 'DEMO-%';

-- Usuarios demo: se intentan borrar; si otra tabla los referencia
-- (por ejemplo la bitacora de auditoria) solo se desactivan.
BEGIN
DELETE FROM his.usuario WHERE correo LIKE '%@demo.local';
EXCEPTION WHEN foreign_key_violation THEN
UPDATE his.usuario
SET state = 0,
    username = 'x' || right(username, 8),
    correo   = 'baja.' || usuario_id || '@demo.local'
WHERE correo LIKE '%@demo.local' AND state = 1;
END;

    RAISE NOTICE 'Datos demo anteriores eliminados';
END $$;
-- ============================ FIN PARTE 1 ============================


-- =====================================================================
-- PARTE 2: CARGA DE DATOS DEMO
-- =====================================================================
DO $$
DECLARE
v_rol_medico    int;
    v_rol_paciente  int;
    v_hash          varchar;
    v_cajero        int;
    v_farmaceutico  int;
    v_estado_fin    int;
    v_estado_can    int;
    v_estado_noa    int;

    v_medicos       int[];
    v_pacientes     int[];
    v_cie10         int[];
    v_medicamentos  int[];

    v_par           record;
    v_u             record;
    v_sede          int;
    v_med_nombre    text[] := ARRAY['Dr. Ricardo Alvarado','Dra. Patricia Soto','Dr. Ernesto Villatoro',
                                    'Dra. Claudia Barrios','Dr. Hugo Samayoa','Dra. Natalia Recinos'];
    v_med_usuario   text[] := ARRAY['ralvarado','psoto2026','evillator','cbarrios1','hsamayoa1','nrecinos1'];
    v_med_correo    text[] := ARRAY['ricardo.alvarado','patricia.soto','ernesto.villatoro',
                                    'claudia.barrios','hugo.samayoa','natalia.recinos'];
    v_medico        record;
    v_i             int;
    v_j             int;
    v_r             double precision;
    v_estado        int;
    v_fecha         timestamptz;
    v_paciente      int;
    v_monto         numeric(10,2);
    v_metodo        smallint;
    v_canal         smallint;
    v_cita_id       int;
    v_consulta_id   int;
    v_receta_id     int;
    v_despacho_id   int;
    v_cie           int;
    v_med           int;
    v_cant          int;
    v_precio        numeric(10,2);
    v_total         numeric(10,2);
BEGIN
    PERFORM setseed(0.42);   -- mismos datos cada vez que se corre

    -- -------------------------------------------------------------
    -- Catalogos que se necesitan
    -- -------------------------------------------------------------
SELECT rol_id INTO v_rol_medico   FROM his.rol WHERE lower(nombre) = 'medico'   AND state = 1 LIMIT 1;
SELECT rol_id INTO v_rol_paciente FROM his.rol WHERE lower(nombre) = 'paciente' AND state = 1 LIMIT 1;

SELECT estado_cita_id INTO v_estado_fin FROM his.estado_cita WHERE codigo = 'ATENCION_FINALIZADA';
SELECT estado_cita_id INTO v_estado_can FROM his.estado_cita WHERE codigo = 'CANCELADA';
SELECT estado_cita_id INTO v_estado_noa FROM his.estado_cita WHERE codigo = 'NO_ASISTIO';

IF v_rol_medico IS NULL OR v_rol_paciente IS NULL OR v_estado_fin IS NULL THEN
        RAISE EXCEPTION 'Faltan roles o estados de cita. Corra primero datos_iniciales.sql';
END IF;

    -- clave: se copia la de cualquier usuario activo (los demo no son para iniciar sesion)
SELECT password_hash INTO v_hash FROM his.usuario WHERE state = 1 ORDER BY usuario_id LIMIT 1;
IF v_hash IS NULL THEN
        RAISE EXCEPTION 'No hay ningun usuario en la BD para copiar la clave';
END IF;

SELECT array_agg(cie10_id ORDER BY cie10_id) INTO v_cie10
FROM (SELECT cie10_id FROM his.cie10 WHERE state = 1 ORDER BY cie10_id LIMIT 15) x;

SELECT array_agg(medicamento_id ORDER BY medicamento_id) INTO v_medicamentos
FROM (SELECT medicamento_id FROM his.medicamento WHERE state = 1 ORDER BY medicamento_id LIMIT 12) x;

-- -------------------------------------------------------------
-- 6 medicos demo, cada uno en una sede + especialidad valida
-- (sucursal_especialidad activa, asi pasan los triggers de cita)
-- -------------------------------------------------------------
v_i := 0;
FOR v_par IN
SELECT se.sucursal_id, se.especialidad_id, e.nombre AS especialidad
FROM his.sucursal_especialidad se
         JOIN his.especialidad e ON e.especialidad_id = se.especialidad_id AND e.state = 1
         JOIN his.sucursal s     ON s.sucursal_id = se.sucursal_id       AND s.state = 1
WHERE se.state = 1
-- primero una sede por especialidad, asi cada medico demo queda en una especialidad distinta
ORDER BY row_number() OVER (PARTITION BY se.especialidad_id ORDER BY se.sucursal_id),
                  se.especialidad_id
    LOOP
        EXIT WHEN v_i >= 6;
v_i := v_i + 1;
INSERT INTO his.usuario (nombre_completo, username, password_hash, correo, dpi, telefono,
                         rol_id, sucursal_id, especialidad_id, intentos_fallidos,
                         state, row_version, created_at)
VALUES (v_med_nombre[v_i],
        v_med_usuario[v_i],
        v_hash,
        v_med_correo[v_i] || '@demo.local',
        '99000000001' || lpad(v_i::text, 2, '0'),
        '5500' || lpad(v_i::text, 4, '0'),
        v_rol_medico, v_par.sucursal_id, v_par.especialidad_id, 0,
        1, 0, now())
    ON CONFLICT DO NOTHING;   -- si el username/correo/DPI ya existe en la BD, lo salta
END LOOP;

    IF v_i = 0 THEN
        RAISE EXCEPTION 'No hay sedes con especialidades activas (tabla sucursal_especialidad)';
END IF;

    -- -------------------------------------------------------------
    -- 15 pacientes
    -- -------------------------------------------------------------
FOR v_u IN
SELECT * FROM (VALUES
                   ( 1, 'Pedro Ramirez',    'pramirez1', 'pedro.ramirez'),
                   ( 2, 'Lucia Gonzalez',   'lgonzalez', 'lucia.gonzalez'),
                   ( 3, 'Jose Perez',       'joseperez', 'jose.perez'),
                   ( 4, 'Carmen Ruiz',      'cruiz2001', 'carmen.ruiz'),
                   ( 5, 'Miguel Torres',    'mtorres01', 'miguel.torres'),
                   ( 6, 'Andrea Flores',    'aflores01', 'andrea.flores'),
                   ( 7, 'Diego Ortiz',      'diegortiz', 'diego.ortiz'),
                   ( 8, 'Valeria Cruz',     'valecruz1', 'valeria.cruz'),
                   ( 9, 'Ricardo Vasquez',  'rvasquez1', 'ricardo.vasquez'),
                   (10, 'Gabriela Reyes',   'gabyreyes', 'gabriela.reyes'),
                   (11, 'Fernando Aguilar', 'faguilar1', 'fernando.aguilar'),
                   (12, 'Daniela Juarez',   'djuarez01', 'daniela.juarez'),
                   (13, 'Oscar Chavez',     'ochavez01', 'oscar.chavez'),
                   (14, 'Paola Estrada',    'pestrada1', 'paola.estrada'),
                   (15, 'Mario Sandoval',   'msandoval', 'mario.sandoval')
              ) AS t(n, nombre, usuario, correo)
    LOOP
INSERT INTO his.usuario (nombre_completo, username, password_hash, correo, dpi, telefono,
                         rol_id, intentos_fallidos, state, row_version, created_at)
VALUES (v_u.nombre, v_u.usuario, v_hash, v_u.correo || '@demo.local',
    '99000000002' || lpad(v_u.n::text, 2, '0'),
    '5600' || lpad(v_u.n::text, 4, '0'),
    v_rol_paciente, 0, 1, 0, now())
ON CONFLICT DO NOTHING;
END LOOP;

    -- -------------------------------------------------------------
    -- Personal: enfermeria, recepcion, caja, laboratorio y farmacia
    -- (todos en la primera sede activa)
    -- -------------------------------------------------------------
SELECT sucursal_id INTO v_sede FROM his.sucursal WHERE state = 1 ORDER BY sucursal_id LIMIT 1;

FOR v_u IN
SELECT * FROM (VALUES
                   (1, 'Rosa Cifuentes',    'rcifuen01', 'rosa.cifuentes',    'enfermero'),
                   (2, 'Kevin Barrios',     'kbarrios1', 'kevin.barrios',     'enfermero'),
                   (3, 'Julia Monterroso',  'jmonterro', 'julia.monterroso',  'recepcionista'),
                   (4, 'Hector Galindo',    'hgalindo1', 'hector.galindo',    'cajero'),
                   (5, 'Silvia Orellana',   'sorellana', 'silvia.orellana',   'laboratorista'),
                   (6, 'Byron Solares',     'bsolares1', 'byron.solares',     'farmaceutico')
              ) AS t(n, nombre, usuario, correo, rol)
    LOOP
INSERT INTO his.usuario (nombre_completo, username, password_hash, correo, dpi, telefono,
                         rol_id, sucursal_id, intentos_fallidos, state, row_version, created_at)
SELECT v_u.nombre, v_u.usuario, v_hash, v_u.correo || '@demo.local',
       '99000000003' || lpad(v_u.n::text, 2, '0'),
       '5700' || lpad(v_u.n::text, 4, '0'),
       r.rol_id, v_sede, 0, 1, 0, now()
FROM his.rol r
WHERE lower(r.nombre) = v_u.rol AND r.state = 1
    LIMIT 1
ON CONFLICT DO NOTHING;
END LOOP;

    -- cajero y farmaceutico demo; si no se pudieron crear, se usa uno real
SELECT u.usuario_id INTO v_cajero
FROM his.usuario u JOIN his.rol r ON r.rol_id = u.rol_id
WHERE lower(r.nombre) = 'cajero' AND u.state = 1
ORDER BY (u.correo LIKE '%@demo.local') DESC, u.usuario_id LIMIT 1;

SELECT u.usuario_id INTO v_farmaceutico
FROM his.usuario u JOIN his.rol r ON r.rol_id = u.rol_id
WHERE lower(r.nombre) = 'farmaceutico' AND u.state = 1
ORDER BY (u.correo LIKE '%@demo.local') DESC, u.usuario_id LIMIT 1;

-- Medicos que pueden recibir citas: los demo + los reales que tengan
-- sede y especialidad validas. Orden aleatorio fijo (por setseed).
SELECT array_agg(usuario_id ORDER BY orden) INTO v_medicos
FROM (SELECT u.usuario_id, random() AS orden
      FROM his.usuario u
      WHERE u.rol_id = v_rol_medico AND u.state = 1
        AND EXISTS (SELECT 1 FROM his.sucursal_especialidad se
                    WHERE se.sucursal_id = u.sucursal_id
                      AND se.especialidad_id = u.especialidad_id
                      AND se.state = 1)
      ORDER BY u.usuario_id) x;

SELECT array_agg(usuario_id ORDER BY usuario_id) INTO v_pacientes
FROM his.usuario u
WHERE u.correo LIKE '%@demo.local' AND u.state = 1 AND u.rol_id = v_rol_paciente;

-- -------------------------------------------------------------
-- 600 citas: 4 por dia (08:00, 10:00, 12:00, 14:00 hora GT)
-- hacia atras desde ayer. Cada horario es unico, asi no choca
-- con el indice ux_cita_medico_slot.
-- -------------------------------------------------------------
FOR v_i IN 1..600 LOOP
        v_fecha := ((current_date - 1 - ((v_i - 1) / 4))::timestamp
                    + make_interval(hours => 8 + ((v_i - 1) % 4) * 2))
                   AT TIME ZONE 'America/Guatemala';

        -- reparto desigual para que haya un ranking claro de medicos
        v_j := 1 + floor(power(random(), 1.7) * array_length(v_medicos, 1))::int;
SELECT usuario_id, sucursal_id, especialidad_id INTO v_medico
FROM his.usuario WHERE usuario_id = v_medicos[v_j];

v_paciente := v_pacientes[1 + floor(random() * array_length(v_pacientes, 1))::int];
        v_monto    := 150 + (v_medico.especialidad_id % 5) * 50;

        v_r := random();
        v_estado := CASE WHEN v_r < 0.80 THEN v_estado_fin
                         WHEN v_r < 0.90 THEN v_estado_can
                         ELSE v_estado_noa END;

INSERT INTO his.cita (numero_cita, paciente_id, medico_id, especialidad_id, sucursal_id,
                      estado_cita_id, fecha_hora, duracion_min, motivo, monto,
                      es_emergencia, prioridad, hora_llegada, origen, es_seguimiento,
                      state, row_version, created_at)
VALUES ('DEMO-' || lpad(v_i::text, 5, '0'), v_paciente, v_medico.usuario_id,
        v_medico.especialidad_id, v_medico.sucursal_id, v_estado, v_fecha, 30,
        'Cita de prueba para reportes', v_monto, false, 0,
        CASE WHEN v_estado = v_estado_fin THEN v_fecha - interval '15 minutes' END,
        0, false, 1, 0, v_fecha - interval '3 days')
    RETURNING cita_id INTO v_cita_id;

-- ---------- pago (atendidas y no asistio ya habian pagado) ----------
IF v_estado IN (v_estado_fin, v_estado_noa) THEN
            v_metodo := floor(random() * 4)::smallint;              -- 0 efectivo .. 3 debito
            v_canal  := CASE WHEN v_metodo = 0 THEN 1                -- efectivo solo en caja
                             ELSE floor(random() * 2)::smallint END;

INSERT INTO his.pago (numero_transaccion, tipo_pago, cita_id, paciente_id, sucursal_id,
                      metodo_pago, canal, monto, monto_recibido, cambio, ultimos4,
                      estado_pago, pasarela_ref, idempotency_key, cajero_id,
                      state, row_version, created_at)
VALUES ('DEMO-TRX-' || lpad(v_i::text, 5, '0'), 0, v_cita_id, v_paciente,
        v_medico.sucursal_id, v_metodo, v_canal, v_monto,
        CASE WHEN v_metodo = 0 THEN v_monto END,
        CASE WHEN v_metodo = 0 THEN 0 END,
        CASE WHEN v_metodo <> 0 THEN '4242' END,
        1,
        CASE WHEN v_canal = 0 THEN 'DEMO-REF-' || v_i END,
        gen_random_uuid(),
        CASE WHEN v_canal = 1 THEN v_cajero END,
        1, 0, v_fecha - interval '2 days');
END IF;

        -- ---------- consulta finalizada ----------
        IF v_estado = v_estado_fin THEN
            v_cie := CASE WHEN v_cie10 IS NULL THEN NULL
                          ELSE v_cie10[1 + floor(power(random(), 1.5) * array_length(v_cie10, 1))::int] END;

INSERT INTO his.consulta (cita_id, medico_id, motivo_visita, hallazgos, cie10_id,
                          diagnostico, plan_tratamiento, estado_consulta, finalizada_en,
                          state, row_version, created_at)
VALUES (v_cita_id, v_medico.usuario_id, 'Consulta de prueba para reportes',
        'Sin hallazgos relevantes',
        v_cie,
           -- la BD exige 10-5000 caracteres en el diagnostico de una consulta finalizada
        COALESCE((SELECT codigo || ' - ' || descripcion || '. Diagnostico de prueba'
                  FROM his.cie10 WHERE cie10_id = v_cie),
                 'Diagnostico de prueba para reportes'),
        'Tratamiento de prueba', 1, v_fecha + interval '30 minutes',
        1, 0, v_fecha)
    RETURNING consulta_id INTO v_consulta_id;

-- ---------- receta + despacho (6 de cada 10 consultas) ----------
IF v_medicamentos IS NOT NULL AND random() < 0.60 THEN
                INSERT INTO his.receta (consulta_id, medico_id, paciente_id, fecha_emision, notas,
                                        state, row_version, created_at)
                VALUES (v_consulta_id, v_medico.usuario_id, v_paciente,
                        (v_fecha AT TIME ZONE 'America/Guatemala')::date, 'Receta de prueba',
                        1, 0, v_fecha + interval '30 minutes')
                RETURNING receta_id INTO v_receta_id;

                IF v_farmaceutico IS NOT NULL THEN
                    INSERT INTO his.despacho (receta_id, sucursal_id, farmaceutico_id, monto_total,
                                              estado_despacho, notas, state, row_version, created_at)
                    VALUES (v_receta_id, v_medico.sucursal_id, v_farmaceutico, 0,
                            0, 'Despacho de prueba', 1, 0, v_fecha + interval '1 hour')
                    RETURNING despacho_id INTO v_despacho_id;
END IF;

                v_total := 0;
FOR v_j IN 1..(1 + floor(random() * 3)::int) LOOP
                    v_med  := v_medicamentos[1 + floor(power(random(), 1.4) * array_length(v_medicamentos, 1))::int];
                    v_cant := 5 + floor(random() * 26)::int;
SELECT precio_default INTO v_precio FROM his.medicamento WHERE medicamento_id = v_med;

INSERT INTO his.receta_detalle (receta_id, medicamento_id, dosis, frecuencia,
                                duracion, cantidad, indicaciones, state)
VALUES (v_receta_id, v_med, '1 unidad', 'Cada 8 horas', '7 dias', v_cant,
        'Tomar con alimentos', 1);

IF v_farmaceutico IS NOT NULL THEN
                        INSERT INTO his.despacho_detalle (despacho_id, medicamento_id, cantidad,
                                                          precio_unitario, es_sustitucion)
                        VALUES (v_despacho_id, v_med, v_cant, v_precio, false);
                        v_total := v_total + v_cant * v_precio;
END IF;
END LOOP;

                IF v_farmaceutico IS NOT NULL THEN
UPDATE his.despacho SET monto_total = v_total WHERE despacho_id = v_despacho_id;
END IF;
END IF;
END IF;
END LOOP;

    RAISE NOTICE 'Carga demo lista: % medicos en el reparto, 600 citas', array_length(v_medicos, 1);
    IF v_farmaceutico IS NULL THEN
        RAISE NOTICE 'OJO: no hay usuario Farmaceutico activo, no se cargaron despachos';
END IF;
    IF v_cie10 IS NULL THEN
        RAISE NOTICE 'OJO: la tabla cie10 esta vacia, las consultas quedaron sin diagnostico CIE-10';
END IF;
END $$;


-- =====================================================================
-- PARTE 3: VERIFICACION (debe devolver filas con numeros)
-- =====================================================================
SELECT 'citas demo'      AS que, count(*) AS total FROM his.cita     WHERE numero_cita LIKE 'DEMO-%'
UNION ALL
SELECT 'consultas demo', count(*) FROM his.consulta c JOIN his.cita ci ON ci.cita_id = c.cita_id WHERE ci.numero_cita LIKE 'DEMO-%'
UNION ALL
SELECT 'pagos demo',     count(*) FROM his.pago     WHERE numero_transaccion LIKE 'DEMO-%'
UNION ALL
SELECT 'despachos demo', count(*) FROM his.despacho WHERE notas = 'Despacho de prueba';