package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de diagnosticos CIE-10. Tabla: his.cie10  [CU-08 paso 7]
 *
 * No extiende BaseEntity: la tabla solo tiene "state", sin row_version
 * ni created_at/updated_at (mismo patron que EstadoCita).
 */
@Entity
@Table(name = "cie10", schema = "his")
public class Cie10 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cie10_id")
    private Integer id;

    /** Codigo CIE-10, ej: "J06.9". Unico. */
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "capitulo", length = 200)
    private String capitulo;

    /** 1 = activo, 0 = inactivo. */
    @Column(name = "state", nullable = false)
    private Short state = 1;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCapitulo() { return capitulo; }
    public void setCapitulo(String capitulo) { this.capitulo = capitulo; }
    public Short getState() { return state; }
    public void setState(Short state) { this.state = state; }
}