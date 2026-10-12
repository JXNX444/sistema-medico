package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Evento personal de la agenda del medico. Tabla: his.evento_agenda  [CU-16]
 * Ej: "Capacitacion en congreso medico".
 *
 * NO extiende BaseEntity: esta tabla no tiene created_by ni updated_by.
 *
 * rowVersion arranca en null (no en 0): asi Spring Data sabe que el objeto
 * es NUEVO y hace persist(), en vez de merge() que devuelve una copia y deja
 * el original con id = null (el bug que se corrigio en CU-12 y CU-11).
 *
 * Reglas de la BD:
 *   ck_evento_titulo  -> titulo de 5 a 200 caracteres
 *   ck_evento_fechas  -> fecha_fin > fecha_inicio
 *   ck_evento_tipo    -> tipo_evento de 0 a 4
 *   ck_evento_state   -> state 0 o 1
 */
@Entity
@Table(name = "evento_agenda", schema = "his")
public class EventoAgenda {

    // Tipos de evento [RN-CU14-01]
    public static final short REUNION = 0;
    public static final short DESCANSO = 1;
    public static final short CAPACITACION = 2;
    public static final short PERSONAL = 3;
    public static final short OTRO = 4;

    /** Color fijo de todos los eventos, no configurable [RN-CU14-01]. */
    public static final String COLOR = "#8b5cf6";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evento_id")
    private Integer id;

    /** Duenio del evento (usuario con rol Medico). */
    @Column(name = "medico_id", nullable = false)
    private Integer medicoId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private OffsetDateTime fechaFin;

    @Column(name = "tipo_evento", nullable = false)
    private Short tipoEvento;

    @Column(name = "todo_el_dia", nullable = false)
    private boolean todoElDia;

    /** Borrado logico: 1 = activo, 0 = eliminado. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Lo llena tambien el trigger tr_evento_agenda_updated_at. */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void alActualizar() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Nombre del tipo para mostrar en pantalla. */
    public String getNombreTipo() {
        if (tipoEvento == null) return "";
        return switch (tipoEvento) {
            case REUNION -> "Reunión";
            case DESCANSO -> "Descanso";
            case CAPACITACION -> "Capacitación";
            case PERSONAL -> "Personal";
            case OTRO -> "Otro";
            default -> "";
        };
    }

    public boolean estaActivo() {
        return state != null && state == 1;
    }

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getMedicoId() { return medicoId; }
    public void setMedicoId(Integer medicoId) { this.medicoId = medicoId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public OffsetDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(OffsetDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public OffsetDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(OffsetDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Short getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(Short tipoEvento) { this.tipoEvento = tipoEvento; }

    public boolean isTodoElDia() { return todoElDia; }
    public void setTodoElDia(boolean todoElDia) { this.todoElDia = todoElDia; }

    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }

    public Integer getRowVersion() { return rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}