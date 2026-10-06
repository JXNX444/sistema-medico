package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorioDetalle;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.OrdenLaboratorioDetalleRepository;
import gt.edu.umg.sistemamedico.repository.OrdenLaboratorioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-09 Gestion de Laboratorio. Rol: Laboratorista.
 *
 * Flujo de estados de la orden (order_status):
 *   0 Pendiente   -> la creo el medico [CU-08], falta pagar en caja
 *   1 En proceso  -> ya se pago [CU-10], se pueden tomar muestras
 *   2 Completada  -> todos los examenes tienen su resultado publicado
 *
 * Los resultados se guardan y publican EXAMEN POR EXAMEN (no masivo).
 * Una vez publicado, el resultado no se puede cambiar [RNF-024]:
 * la BD lo bloquea con el trigger tr_resultado_inmutable, y aqui lo
 * validamos antes para dar un mensaje claro en vez de un error de BD.
 */
@Service
public class LaboratorioService {

    public static final short PENDIENTE = 0;
    public static final short EN_PROCESO = 1;
    public static final short COMPLETADA = 2;

    private static final short ACTIVO = 1;
    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    private final OrdenLaboratorioRepository ordenRepository;
    private final OrdenLaboratorioDetalleRepository detalleRepository;

    public LaboratorioService(OrdenLaboratorioRepository ordenRepository,
                              OrdenLaboratorioDetalleRepository detalleRepository) {
        this.ordenRepository = ordenRepository;
        this.detalleRepository = detalleRepository;
    }

    /** Texto que se muestra en pantalla para cada estado. */
    public static String nombreEstado(Short estado) {
        if (estado == null) return "Desconocido";
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case EN_PROCESO -> "En proceso";
            case COMPLETADA -> "Completada";
            default -> "Desconocido";
        };
    }

    // ---------- Paso 1: tabla de ordenes con filtros ----------

    /**
     * @param estado   null = todos los estados; 0, 1 o 2 = solo ese estado
     * @param paciente texto a buscar en nombre o DPI (puede venir vacio)
     * @param medico   texto a buscar en el nombre del medico (puede venir vacio)
     */
    @Transactional(readOnly = true)
    public List<OrdenLaboratorio> listarOrdenes(Short estado, String paciente, String medico) {
        boolean todos = (estado == null);
        return ordenRepository.buscarConFiltros(
                todos,
                todos ? PENDIENTE : estado,   // si es "todos", este valor se ignora
                normalizar(paciente),
                normalizar(medico));
    }

    // ---------- Pasos 2-3: detalle de una orden ----------

    /** La orden y sus examenes ordenados (en el orden en que el medico los pidio). */
    public record DetalleOrden(OrdenLaboratorio orden, List<OrdenLaboratorioDetalle> examenes) {}

    @Transactional(readOnly = true)
    public DetalleOrden obtenerDetalle(Integer ordenId) {
        OrdenLaboratorio orden = ordenRepository.buscarConDetalles(ordenId).orElse(null);
        if (orden == null) {
            return null;
        }
        List<OrdenLaboratorioDetalle> examenes = orden.getDetalles().stream()
                .filter(d -> d.getState() != null && d.getState() == ACTIVO)
                .sorted(Comparator.comparing(OrdenLaboratorioDetalle::getId))
                .toList();
        return new DetalleOrden(orden, examenes);
    }

    // ---------- Pasos 9-10 + FA02: guardar resultado de un examen ----------

    public record ResultadoOperacion(boolean ok, Map<String, String> errores,
                                     String mensaje, OrdenLaboratorioDetalle detalle) {}

    @Transactional
    public ResultadoOperacion guardarResultado(Integer detalleId, Usuario laboratorista,
                                               String valor, String unidad, LocalDate fecha,
                                               boolean fueraRango, String notas) {

        OrdenLaboratorioDetalle detalle = detalleRepository.buscarConOrden(detalleId).orElse(null);
        String errorEstado = validarQueSePuedeOperar(detalle);
        if (errorEstado != null) {
            return error("general", errorEstado, detalle);
        }

        // ---- Validacion de campos [RN-CU09-02] ----
        Map<String, String> errores = new LinkedHashMap<>();
        String valorLimpio = limpiar(valor);
        String unidadLimpia = limpiar(unidad);
        String notasLimpias = limpiar(notas);

        if (valorLimpio == null) {
            errores.put("valor", "El valor del resultado es obligatorio.");
        } else if (valorLimpio.length() > 200) {
            errores.put("valor", "El valor del resultado no puede exceder 200 caracteres.");
        }

        if (unidadLimpia == null) {
            errores.put("unidad", "La unidad de medida es obligatoria.");
        } else if (unidadLimpia.length() > 50) {
            errores.put("unidad", "La unidad no puede exceder 50 caracteres.");
        }

        LocalDate hoy = LocalDate.now(ZONA_GT);
        if (fecha == null) {
            errores.put("fecha", "La fecha del resultado es obligatoria.");
        } else if (fecha.isAfter(hoy)) {
            errores.put("fecha", "La fecha del resultado no puede ser futura.");
        }

        if (notasLimpias != null && notasLimpias.length() > 1000) {
            errores.put("notas", "Las notas no pueden exceder 1000 caracteres.");
        }

        if (!errores.isEmpty()) {
            return new ResultadoOperacion(false, errores, null, detalle);
        }

        // ---- Guardar ----
        detalle.setValorResultado(valorLimpio);
        detalle.setUnidad(unidadLimpia);
        detalle.setFechaResultado(fecha);
        detalle.setFueraRango(fueraRango);          // FA02: marcado manual
        detalle.setNotasResultado(notasLimpias);
        detalle.setRegistradoPor(laboratorista.getId());
        detalleRepository.save(detalle);

        return new ResultadoOperacion(true, null, "Resultado guardado exitosamente.", detalle);
    }

    // ---------- Pasos 11-14: publicar resultado de un examen ----------

    @Transactional
    public ResultadoOperacion publicarResultado(Integer detalleId, Usuario laboratorista) {

        OrdenLaboratorioDetalle detalle = detalleRepository.buscarConOrden(detalleId).orElse(null);
        String errorEstado = validarQueSePuedeOperar(detalle);
        if (errorEstado != null) {
            return error("general", errorEstado, detalle);
        }

        // La BD tambien lo exige (ck_ordendet_publicar)
        if (detalle.getValorResultado() == null || detalle.getFechaResultado() == null) {
            return error("general", "Debe guardar el resultado antes de publicarlo.", detalle);
        }

        // ---- Paso 12: marcar como publicado ----
        detalle.setPublicado(true);
        detalle.setPublicadoEn(OffsetDateTime.now());
        detalle.setPublicadoPor(laboratorista.getId());
        detalleRepository.saveAndFlush(detalle);

        // ---- Paso 14: si ya no queda ninguno sin publicar, la orden se completa ----
        OrdenLaboratorio orden = detalle.getOrden();
        long pendientes = detalleRepository.countByOrdenIdAndStateAndPublicadoFalse(orden.getId(), ACTIVO);

        String mensaje = "Resultado publicado exitosamente.";
        if (pendientes == 0) {
            orden.setEstadoOrden(COMPLETADA);
            ordenRepository.save(orden);
            mensaje += " Todos los resultados fueron publicados: la orden "
                    + orden.getNumeroOrden() + " quedo Completada.";
        }

        return new ResultadoOperacion(true, null, mensaje, detalle);
    }

    // ---------- Helpers ----------

    /**
     * Reglas comunes para guardar y publicar. Devuelve el mensaje de error,
     * o null si se puede continuar.
     */
    private String validarQueSePuedeOperar(OrdenLaboratorioDetalle detalle) {
        if (detalle == null) {
            return "El examen no existe.";
        }
        OrdenLaboratorio orden = detalle.getOrden();

        // FA01: los examenes de una orden externa se hacen fuera del hospital
        if (orden.isEsExterna()) {
            return "Esta orden es externa: los examenes se realizan en un laboratorio externo.";
        }
        // Paso 7 + RN-CU09-01: el cobro va antes de la toma de muestras
        if (orden.getEstadoOrden() == PENDIENTE) {
            return "La orden aun no ha sido pagada en caja. El pago es requerido antes de la toma de muestras.";
        }
        if (orden.getEstadoOrden() == COMPLETADA) {
            return "La orden ya esta completada.";
        }
        // RNF-024: un resultado publicado no se modifica
        if (detalle.isPublicado()) {
            return "El resultado ya fue publicado y no puede modificarse.";
        }
        return null;
    }

    private ResultadoOperacion error(String campo, String mensaje, OrdenLaboratorioDetalle detalle) {
        return new ResultadoOperacion(false, Map.of(campo, mensaje), null, detalle);
    }

    /** Quita espacios; si queda vacio devuelve null. */
    private String limpiar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    /** Para los filtros: nunca null, sin espacios y en minusculas. */
    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }
}