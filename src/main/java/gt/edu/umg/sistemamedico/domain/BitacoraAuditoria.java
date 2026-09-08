package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Registro de auditoria: quien hizo que accion, sobre que entidad,
 * y el estado antes/despues del cambio. [Postcondiciones CU-01]
 *
 * No extiende BaseEntity porque esta tabla no tiene borrado logico
 * ni control de concurrencia (un registro de auditoria nunca se edita
 * ni se elimina, solo se crea).
 */
@Entity
@Table(name = "bitacora_auditoria", schema = "his")
public class BitacoraAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bitacora_id")
    private Long id;

    /** Quien ejecuto la accion. Puede ser null si fue el propio sistema. */
    @Column(name = "usuario_id")
    private Integer usuarioId;

    /** Sobre que tipo de entidad fue la accion, ej: "usuario". */
    @Column(name = "entidad", nullable = false)
    private String entidad;

    /** El ID de esa entidad especifica afectada. */
    @Column(name = "entidad_id")
    private String entidadId;

    /** CREAR, EDITAR, ELIMINAR. */
    @Column(name = "accion", nullable = false)
    private String accion;

    /** Snapshot en JSON del estado ANTES del cambio (null si es creacion). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_antes", columnDefinition = "jsonb")
    private String datosAntes;

    /** Snapshot en JSON del estado DESPUES del cambio. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_despues", columnDefinition = "jsonb")
    private String datosDespues;

    @Column(name = "ip_origen")
    private String ipOrigen;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void alCrear() {
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }
    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }
    public String getEntidadId() { return entidadId; }
    public void setEntidadId(String entidadId) { this.entidadId = entidadId; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getDatosAntes() { return datosAntes; }
    public void setDatosAntes(String datosAntes) { this.datosAntes = datosAntes; }
    public String getDatosDespues() { return datosDespues; }
    public void setDatosDespues(String datosDespues) { this.datosDespues = datosDespues; }
    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}