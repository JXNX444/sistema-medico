-- =====================================================================
-- Correcciones de la base de datos del HIS (esquema his)
--
-- Se puede correr sobre la BD que ya existe (Azure) sin perder datos.
-- Cada bloque se puede volver a correr sin problema (IF NOT EXISTS /
-- DROP ... IF EXISTS).
--
-- Antes de correr el punto 6, revisar que no haya duplicados (ver consultas
-- comentadas en ese punto); si los hay, el indice no se puede crear.
-- =====================================================================

SET search_path = his, public;

-- ---------------------------------------------------------------------
-- 1. Extensiones que usa el esquema (citext en usuario.correo y pg_trgm
--    en las busquedas por nombre). En una BD nueva el dump fallaba sin esto.
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ---------------------------------------------------------------------
-- 2. Horario ocupado: una cita CANCELADA o NO_ASISTIO ya no debe bloquear
--    el horario del medico. Antes el indice solo miraba state = 1, y como
--    la cita cancelada sigue con state = 1, el horario quedaba bloqueado
--    para siempre (el calendario lo mostraba libre pero al guardar daba error).
--    Los ids de los estados se buscan por codigo para no escribirlos a mano.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_ids text;
BEGIN
    SELECT string_agg(estado_cita_id::text, ',')
      INTO v_ids
      FROM his.estado_cita
     WHERE codigo IN ('CANCELADA', 'NO_ASISTIO');

    IF v_ids IS NULL THEN
        RAISE EXCEPTION 'No existen los estados CANCELADA / NO_ASISTIO. Corra primero datos_iniciales.sql';
    END IF;

    EXECUTE 'DROP INDEX IF EXISTS his.ux_cita_medico_slot';
    EXECUTE 'CREATE UNIQUE INDEX ux_cita_medico_slot ON his.cita (medico_id, fecha_hora) '
         || 'WHERE state = 1 AND estado_cita_id NOT IN (' || v_ids || ')';
END $$;


-- ---------------------------------------------------------------------
-- 3. NIT alfanumerico de 8 a 9 caracteres (RN-GLOBAL-002). En Guatemala
--    hay NIT que terminan en K; antes la BD solo aceptaba numeros.
-- ---------------------------------------------------------------------
ALTER TABLE his.usuario DROP CONSTRAINT IF EXISTS ck_usuario_nit;
ALTER TABLE his.usuario ADD CONSTRAINT ck_usuario_nit
    CHECK (nit IS NULL OR nit ~ '^[0-9A-Za-z]{8,9}$');


-- ---------------------------------------------------------------------
-- 4. Las funciones de los triggers usan nombres de tabla sin "his.".
--    Con esto funcionan aunque alguien se conecte sin currentSchema=his.
-- ---------------------------------------------------------------------
ALTER FUNCTION his.fn_cita_historial()             SET search_path = his;
ALTER FUNCTION his.fn_consulta_version()           SET search_path = his;
ALTER FUNCTION his.fn_valida_especialidad_medico() SET search_path = his;
ALTER FUNCTION his.fn_valida_sede_especialidad()   SET search_path = his;


-- ---------------------------------------------------------------------
-- 5. La validacion sede-especialidad tambien al ACTUALIZAR la cita
--    (antes solo al crearla; un cambio de sede o especialidad no se revisaba).
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS tr_cita_sede_especialidad ON his.cita;
CREATE TRIGGER tr_cita_sede_especialidad
    BEFORE INSERT OR UPDATE OF sucursal_id, especialidad_id ON his.cita
    FOR EACH ROW EXECUTE FUNCTION his.fn_valida_sede_especialidad();


-- ---------------------------------------------------------------------
-- 6. Evitar duplicados que el codigo espera que no existan.
--
--    Revisar antes:
--      SELECT receta_id, count(*) FROM his.despacho
--       WHERE estado_despacho = 0 AND state = 1 GROUP BY receta_id HAVING count(*) > 1;
--      SELECT cita_id, count(*) FROM his.signos_vitales
--       WHERE state = 1 GROUP BY cita_id HAVING count(*) > 1;
-- ---------------------------------------------------------------------

-- Una receta solo se despacha una vez (estado_despacho 0 = DESPACHADO).
CREATE UNIQUE INDEX IF NOT EXISTS ux_despacho_receta
    ON his.despacho (receta_id) WHERE estado_despacho = 0 AND state = 1;

-- Una sola toma de signos vitales activa por cita (CU-08 la busca como unica).
CREATE UNIQUE INDEX IF NOT EXISTS ux_signos_cita
    ON his.signos_vitales (cita_id) WHERE state = 1;
