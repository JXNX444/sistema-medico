package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Registro de cada cambio de medico de una cita. [CU-05 FA07]
 * Tabla his.cita_reasignacion: queda quien reasigno, de que medico a cual y por que.
 */
@Entity
@Table(name = "cita_reasignacion", schema = "his")
public class CitaReasignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reasignacion_id")
    private Integer id;

    @Column(name = "cita_id", nullable = false)
    private Integer citaId;

    @Column(name = "medico_anterior_id", nullable = false)
    private Integer medicoAnteriorId;

    @Column(name = "medico_nuevo_id", nullable = false)
    private Integer medicoNuevoId;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "reasignado_por", nullable = false)
    private Integer reasignadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public Integer getCitaId() { return citaId; }
    public void setCitaId(Integer citaId) { this.citaId = citaId; }
    public Integer getMedicoAnteriorId() { return medicoAnteriorId; }
    public void setMedicoAnteriorId(Integer medicoAnteriorId) { this.medicoAnteriorId = medicoAnteriorId; }
    public Integer getMedicoNuevoId() { return medicoNuevoId; }
    public void setMedicoNuevoId(Integer medicoNuevoId) { this.medicoNuevoId = medicoNuevoId; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Integer getReasignadoPor() { return reasignadoPor; }
    public void setReasignadoPor(Integer reasignadoPor) { this.reasignadoPor = reasignadoPor; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
