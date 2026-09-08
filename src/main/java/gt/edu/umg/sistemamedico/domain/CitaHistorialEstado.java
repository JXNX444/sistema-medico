package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Traza de transiciones de estado de una cita. Tabla: his.cita_historial_estado
 *
 * La tabla ya existia en el schema (pensada para [RNF-021], que la
 * pantalla de recepcion se actualice cuando cambia un estado), pero
 * no tenia entidad Java todavia. Se usa por primera vez aqui para
 * guardar el MOTIVO cuando el scheduler cancela una cita por falta
 * de pago, y que ese motivo se pueda mostrar tanto en Recepcion
 * como en "Mis Citas" del paciente.
 *
 * No extiende BaseEntity: la tabla solo tiene created_at, sin
 * row_version ni updated_at/by (es un registro historico, nunca
 * se edita despues de creado).
 */
@Entity
@Table(name = "cita_historial_estado", schema = "his")
public class CitaHistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historial_id")
    private Integer id;

    @Column(name = "cita_id", nullable = false)
    private Integer citaId;

    @Column(name = "estado_anterior_id")
    private Integer estadoAnteriorId;

    @Column(name = "estado_nuevo_id", nullable = false)
    private Integer estadoNuevoId;

    /** Quien hizo el cambio. Null si fue automatico (ej: el scheduler). */
    @Column(name = "usuario_id")
    private Integer usuarioId;

    /** Motivo legible, ej: "Cancelada automaticamente: no se registro el pago...". */
    @Column(name = "observacion", length = 500)
    private String observacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getCitaId() { return citaId; }
    public void setCitaId(Integer citaId) { this.citaId = citaId; }
    public Integer getEstadoAnteriorId() { return estadoAnteriorId; }
    public void setEstadoAnteriorId(Integer estadoAnteriorId) { this.estadoAnteriorId = estadoAnteriorId; }
    public Integer getEstadoNuevoId() { return estadoNuevoId; }
    public void setEstadoNuevoId(Integer estadoNuevoId) { this.estadoNuevoId = estadoNuevoId; }
    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}