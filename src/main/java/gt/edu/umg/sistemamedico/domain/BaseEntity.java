package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * Columnas comunes a todas las tablas del esquema his:
 * borrado logico (state), control de concurrencia (row_version) y
 * auditoria (created_at/by, updated_at/by).
 *
 * @MappedSuperclass: NO crea tabla propia. Sus campos se heredan como
 * columnas en cada entidad que la extienda.
 */
@MappedSuperclass
public abstract class BaseEntity {

    /** Borrado logico. 1 = activo, 0 = inactivo. RN-CU01-10. */
    @Column(name = "state", nullable = false)
    protected Short state = 1;

    /**
     * Control de concurrencia optimista. RNF-025.
     * Hibernate incrementa este numero en cada UPDATE y verifica que
     * nadie mas haya modificado la fila mientras tanto.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    protected Integer rowVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    protected Integer createdBy;

    @Column(name = "updated_at")
    protected OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    protected Integer updatedBy;

    /**
     * Se ejecuta automaticamente justo antes del primer INSERT.
     * Sin esto, created_at llegaba en null y la base lo rechazaba
     * (la columna es NOT NULL).
     */
    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Se ejecuta automaticamente justo antes de cada UPDATE. */
    @PreUpdate
    protected void alActualizar() {
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean estaActivo() {
        return state != null && state == 1;
    }

    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
}