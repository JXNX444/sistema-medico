package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Catalogo de examenes de laboratorio. Tabla: his.examen_laboratorio
 * [CU-08 FA01] el medico los selecciona al generar una orden.
 *
 * Extiende BaseEntity: la tabla tiene state, row_version,
 * created_at/by y updated_at/by.
 */
@Entity
@Table(name = "examen_laboratorio", schema = "his")
public class ExamenLaboratorio extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "examen_id")
    private Integer id;

    /** Laboratorio que realiza el examen (his.laboratorio). Solo el id, no se navega. */
    @Column(name = "laboratorio_id", nullable = false)
    private Integer laboratorioId;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    /** Precio que se copia al detalle de la orden. Mayor que 0. */
    @Column(name = "precio_base", nullable = false)
    private BigDecimal precioBase;

    @Column(name = "rango_referencia", length = 100)
    private String rangoReferencia;

    @Column(name = "unidad", length = 50)
    private String unidad;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getLaboratorioId() { return laboratorioId; }
    public void setLaboratorioId(Integer laboratorioId) { this.laboratorioId = laboratorioId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecioBase() { return precioBase; }
    public void setPrecioBase(BigDecimal precioBase) { this.precioBase = precioBase; }
    public String getRangoReferencia() { return rangoReferencia; }
    public void setRangoReferencia(String rangoReferencia) { this.rangoReferencia = rangoReferencia; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
}