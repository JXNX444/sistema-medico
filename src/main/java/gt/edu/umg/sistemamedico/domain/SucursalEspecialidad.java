package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Tabla intermedia: que especialidades ofrece cada sucursal.
 * Tabla: his.sucursal_especialidad
 *
 * [CU-03 paso 2] El wizard usa esta tabla para filtrar, una vez elegida
 * la sucursal, solo las especialidades que esa sede realmente atiende.
 */
@Entity
@Table(name = "sucursal_especialidad", schema = "his")
public class SucursalEspecialidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sucursal_especialidad_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    /** 1 = activo, 0 = inactivo. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public Especialidad getEspecialidad() { return especialidad; }
    public void setEspecialidad(Especialidad especialidad) { this.especialidad = especialidad; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}