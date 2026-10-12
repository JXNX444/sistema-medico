package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Tarea o recordatorio del medico (panel lateral de tareas). Tabla: his.tarea  [CU-16 FA06]
 *
 * NO extiende BaseEntity: esta tabla no tiene created_by ni updated_by.
 * rowVersion arranca en null por la misma razon que en EventoAgenda.
 *
 * Reglas de la BD:
 *   ck_tarea_titulo    -> titulo de 5 a 200 caracteres
 *   ck_tarea_prioridad -> prioridad de 0 a 2
 *   ck_tarea_state     -> state 0 o 1
 */
@Entity
@Table(name = "tarea", schema = "his")
public class Tarea {

    // Prioridades [RN-CU14-02]
    public static final short BAJA = 0;
    public static final short NORMAL = 1;
    public static final short ALTA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tarea_id")
    private Integer id;

    @Column(name = "medico_id", nullable = false)
    private Integer medicoId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Column(name = "prioridad", nullable = false)
    private Short prioridad = NORMAL;

    @Column(name = "fecha_limite", nullable = false)
    private OffsetDateTime fechaLimite;

    /** isCompleted del documento: false = Pendiente (por defecto), true = Completada. */
    @Column(name = "is_completed", nullable = false)
    private boolean completada;

    /** Cuando se marco como completada (null si esta pendiente). */
    @Column(name = "completed_at")
    private OffsetDateTime completadaEn;

    /** Borrado logico: 1 = activa, 0 = eliminada. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Lo llena tambien el trigger tr_tarea_updated_at. */
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

    /** Nombre de la prioridad para mostrar en pantalla. */
    public String getNombrePrioridad() {
        if (prioridad == null) return "";
        return switch (prioridad) {
            case BAJA -> "Baja";
            case NORMAL -> "Normal";
            case ALTA -> "Alta";
            default -> "";
        };
    }

    public boolean estaActiva() {
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

    public Short getPrioridad() { return prioridad; }
    public void setPrioridad(Short prioridad) { this.prioridad = prioridad; }

    public OffsetDateTime getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(OffsetDateTime fechaLimite) { this.fechaLimite = fechaLimite; }

    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }

    public OffsetDateTime getCompletadaEn() { return completadaEn; }
    public void setCompletadaEn(OffsetDateTime completadaEn) { this.completadaEn = completadaEn; }

    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }

    public Integer getRowVersion() { return rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}