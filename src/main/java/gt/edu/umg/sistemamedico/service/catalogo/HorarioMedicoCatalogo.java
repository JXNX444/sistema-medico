package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.HorarioMedico;
import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.HorarioMedicoRepository;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo de Horarios de Medicos (his.horario_medico).
 *
 * Antes no habia pantalla para esto: los horarios se insertaban a mano en SQL,
 * y un medico nuevo nunca aparecia con horarios en CU-03. Es la base de
 * DisponibilidadService (CU-03, CU-05 y CU-12).
 *
 * Campos: Medico, Sede, Dia de la semana, Hora inicio, Hora fin, Duracion de
 * cada cita, Vigente desde, Vigente hasta (opcional) y Estado.
 *
 * Reglas:
 * - El medico debe estar activo, tener rol Medico y la sede debe ser la suya
 *   (CU-03 busca a los medicos por su sede).
 * - Hora fin despues de hora inicio, y que quepa al menos una cita.
 * - No se permiten dos horarios activos del mismo medico que se crucen el
 *   mismo dia en fechas de vigencia que tambien se crucen.
 */
@Component
@Order(9)
public class HorarioMedicoCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "horario_medico";
    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final List<Short> DURACIONES = List.of((short) 15, (short) 20, (short) 30, (short) 45, (short) 60);

    private final HorarioMedicoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalService sucursalService;

    public HorarioMedicoCatalogo(AuditoriaService auditoria,
                                 HorarioMedicoRepository repository,
                                 UsuarioRepository usuarioRepository,
                                 SucursalService sucursalService) {
        super(auditoria);
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.sucursalService = sucursalService;
    }

    @Override public String clave() { return "horarios-medicos"; }
    @Override public String titulo() { return "Horarios de Médicos"; }
    @Override public String descripcion() { return "Días y horas en que cada médico atiende citas en su sede."; }
    @Override public List<String> columnas() { return List.of("Médico", "Sede", "Día", "Horario", "Cada", "Vigencia"); }

    @Override
    public List<Campo> campos(Integer id) {
        List<Opcion> medicos = medicosActivos().stream()
                .map(m -> new Opcion(m.getId().toString(), m.getNombreCompleto())).toList();
        List<Opcion> sedes = sucursalService.listarActivas().stream()
                .map(s -> new Opcion(s.getId().toString(), s.getNombre())).toList();
        List<Opcion> dias = new ArrayList<>();
        for (int i = 0; i < DIAS.length; i++) dias.add(new Opcion(String.valueOf(i + 1), DIAS[i]));
        List<Opcion> horas = new ArrayList<>();
        for (LocalTime t = LocalTime.of(6, 0); !t.isAfter(LocalTime.of(22, 0)); t = t.plusMinutes(30)) {
            horas.add(new Opcion(t.toString(), t.toString()));
        }
        List<Opcion> duraciones = DURACIONES.stream()
                .map(d -> new Opcion(d.toString(), d + " minutos")).toList();

        return List.of(
                lista("medicoId", "Médico", medicos),
                lista("sucursalId", "Sede", sedes),
                lista("diaSemana", "Día de la semana", dias),
                lista("horaInicio", "Hora de inicio", horas),
                lista("horaFin", "Hora de fin", horas),
                lista("duracionSlot", "Duración de cada cita", duraciones),
                texto("vigenteDesde", "Vigente desde (AAAA-MM-DD, vacío = hoy)", 10, false),
                texto("vigenteHasta", "Vigente hasta (AAAA-MM-DD, opcional)", 10, false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByMedicoNombreCompletoContainingIgnoreCaseAndStateIn(
                        filtro(texto), estados(estado), pagina(pagina, "medico.nombreCompleto"))
                .map(h -> new Fila(h.getId(), nombre(h),
                        List.of(h.getMedico().getNombreCompleto(), h.getSucursal().getNombre(),
                                DIAS[h.getDiaSemana() - 1], h.getHoraInicio() + " - " + h.getHoraFin(),
                                h.getDuracionSlot() + " min",
                                h.getVigenteDesde() + (h.getVigenteHasta() == null ? " en adelante" : " a " + h.getVigenteHasta())),
                        h.getState() != null && h.getState() == ACTIVO, List.of(), true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(h -> mapa("medicoId", h.getMedico().getId(), "sucursalId", h.getSucursal().getId(),
                        "diaSemana", h.getDiaSemana(), "horaInicio", h.getHoraInicio(), "horaFin", h.getHoraFin(),
                        "duracionSlot", h.getDuracionSlot(), "vigenteDesde", h.getVigenteDesde(),
                        "vigenteHasta", h.getVigenteHasta() == null ? "" : h.getVigenteHasta(), "state", h.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        HorarioMedico h = id == null ? new HorarioMedico() : repository.findById(id).orElse(null);
        if (h == null) return Resultado.error("general", NO_EXISTE);

        Map<String, String> errores = new LinkedHashMap<>();

        Integer medicoId = leerId(datos.get("medicoId"));
        Usuario medico = medicosActivos().stream().filter(m -> m.getId().equals(medicoId)).findFirst().orElse(null);
        if (medico == null) errores.put("medicoId", "Debe seleccionar un médico activo.");

        Integer sucursalId = leerId(datos.get("sucursalId"));
        Sucursal sede = sucursalService.listarActivas().stream()
                .filter(s -> s.getId().equals(sucursalId)).findFirst().orElse(null);
        if (sede == null) {
            errores.put("sucursalId", "Debe seleccionar una sede.");
        } else if (medico != null && (medico.getSucursal() == null || !medico.getSucursal().getId().equals(sede.getId()))) {
            errores.put("sucursalId", "El médico pertenece a la sede "
                    + (medico.getSucursal() == null ? "(sin sede)" : medico.getSucursal().getNombre())
                    + ". El horario debe ser en esa sede.");
        }

        Integer dia = leerId(datos.get("diaSemana"));
        if (dia == null || dia < 1 || dia > 7) errores.put("diaSemana", "Debe seleccionar el día de la semana.");

        LocalTime inicio = leerHora(datos.get("horaInicio"));
        LocalTime fin = leerHora(datos.get("horaFin"));
        Integer duracion = leerId(datos.get("duracionSlot"));
        if (inicio == null) errores.put("horaInicio", "Debe seleccionar la hora de inicio.");
        if (fin == null) errores.put("horaFin", "Debe seleccionar la hora de fin.");
        if (duracion == null || !DURACIONES.contains(duracion.shortValue())) {
            errores.put("duracionSlot", "Debe seleccionar la duración de cada cita.");
        }
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            errores.put("horaFin", "La hora de fin debe ser posterior a la hora de inicio.");
        } else if (inicio != null && fin != null && duracion != null && inicio.plusMinutes(duracion).isAfter(fin)) {
            errores.put("horaFin", "En ese rango no cabe ni una cita de " + duracion + " minutos.");
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Guatemala"));
        String desdeTxt = limpiar(datos.get("vigenteDesde"));
        LocalDate desde = desdeTxt == null ? (id == null ? hoy : h.getVigenteDesde()) : leerFecha(desdeTxt);
        if (desde == null) errores.put("vigenteDesde", "Use el formato AAAA-MM-DD (ej: 2026-10-13).");
        String hastaTxt = limpiar(datos.get("vigenteHasta"));
        LocalDate hasta = hastaTxt == null ? null : leerFecha(hastaTxt);
        if (hastaTxt != null && hasta == null) {
            errores.put("vigenteHasta", "Use el formato AAAA-MM-DD (ej: 2026-12-31).");
        } else if (hasta != null && desde != null && hasta.isBefore(desde)) {
            errores.put("vigenteHasta", "La fecha final no puede ser anterior a la fecha inicial.");
        }

        Short state = leerEstado(errores, datos.get("state"));
        if (!errores.isEmpty()) return Resultado.errores(errores);

        // No se cruza con otro horario activo del mismo medico, mismo dia y vigencias que se cruzan.
        if (state == ACTIVO) {
            final LocalDate d = desde;
            final LocalDate hst = hasta;
            boolean cruza = repository.findByMedicoIdAndSucursalIdAndState(medico.getId(), sede.getId(), ACTIVO).stream()
                    .filter(o -> id == null || !o.getId().equals(id))
                    .filter(o -> o.getDiaSemana().intValue() == dia)
                    .filter(o -> o.getHoraInicio().isBefore(fin) && o.getHoraFin().isAfter(inicio))
                    .anyMatch(o -> (o.getVigenteHasta() == null || !o.getVigenteHasta().isBefore(d))
                            && (hst == null || !hst.isBefore(o.getVigenteDesde())));
            if (cruza) {
                return Resultado.error("horaInicio",
                        "Ese horario se cruza con otro horario activo del mismo médico ese día.");
            }
        }

        Map<String, Object> antes = id == null ? null : snapshot(h);
        h.setMedico(medico);
        h.setSucursal(sede);
        h.setDiaSemana(dia.shortValue());
        h.setHoraInicio(inicio);
        h.setHoraFin(fin);
        h.setDuracionSlot(duracion.shortValue());
        h.setVigenteDesde(desde);
        h.setVigenteHasta(hasta);
        h.setState(state);
        h = repository.save(h);

        auditoria.registrar(ENTIDAD, accion(id), h.getId(), antes, snapshot(h));
        return guardado(id, nombre(h));
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        HorarioMedico h = repository.findById(id).orElse(null);
        if (h == null || h.getState() == null || h.getState() != ACTIVO) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(h);
        h.setState(INACTIVO);
        repository.save(h);

        auditoria.registrar(ENTIDAD, "ELIMINAR", h.getId(), antes, snapshot(h));
        return eliminado(nombre(h));
    }

    // ---------- Helpers ----------

    private List<Usuario> medicosActivos() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getState() != null && u.getState() == ACTIVO)
                .filter(u -> "Medico".equalsIgnoreCase(u.getNombreRol()))
                .sorted((a, b) -> a.getNombreCompleto().compareToIgnoreCase(b.getNombreCompleto()))
                .toList();
    }

    /** "Dr. Perez - Lunes 08:00-12:00": el "nombre" del registro para los mensajes. */
    private String nombre(HorarioMedico h) {
        return h.getMedico().getNombreCompleto() + " - " + DIAS[h.getDiaSemana() - 1] + " "
                + h.getHoraInicio() + "-" + h.getHoraFin();
    }

    private LocalTime leerHora(String valor) {
        try {
            return LocalTime.parse(valor.trim());
        } catch (NullPointerException | DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate leerFecha(String valor) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Map<String, Object> snapshot(HorarioMedico h) {
        return snapshot("id", h.getId(), "medicoId", h.getMedico() == null ? null : h.getMedico().getId(),
                "sucursalId", h.getSucursal() == null ? null : h.getSucursal().getId(),
                "diaSemana", h.getDiaSemana(), "horaInicio", h.getHoraInicio(), "horaFin", h.getHoraFin(),
                "duracionSlot", h.getDuracionSlot(), "vigenteDesde", h.getVigenteDesde(),
                "vigenteHasta", h.getVigenteHasta(), "state", h.getState());
    }
}
