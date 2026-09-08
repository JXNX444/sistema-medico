package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de estados de una cita (Pendiente de pago, Confirmada,
 * Cancelada, Atendida, etc). Tabla: his.estado_cita
 *
 * No extiende BaseEntity: esta tabla no tiene row_version ni
 * created_at/updated_at, solo "state" para activo/inactivo del catalogo.
 */
@Entity
@Table(name = "estado_cita", schema = "his")
public class EstadoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "estado_cita_id")
    private Integer id;

    /** Codigo corto interno, ej: "PENDIENTE_PAGO", "CONFIRMADA". */
    @Column(name = "codigo", nullable = false)
    private String codigo;

    /** Nombre visible al usuario, ej: "Pendiente de pago". */
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    /** Color hexadecimal para mostrar el estado en la interfaz (ej: badges). */
    @Column(name = "color_hex")
    private String colorHex;

    /** Orden de despliegue en listas/dropdowns. */
    @Column(name = "orden", nullable = false)
    private Short orden;

    /** 1 = activo, 0 = inactivo. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public Short getOrden() { return orden; }
    public void setOrden(Short orden) { this.orden = orden; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
}