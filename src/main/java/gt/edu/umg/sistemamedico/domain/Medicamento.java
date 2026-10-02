package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Catalogo de medicamentos. Tabla: his.medicamento
 * [CU-08 FA04] el medico los selecciona al generar una receta.
 * Farmacia (CU-10) los despacha.
 *
 * Extiende BaseEntity: la tabla tiene state, row_version,
 * created_at/by y updated_at/by.
 */
@Entity
@Table(name = "medicamento", schema = "his")
public class Medicamento extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicamento_id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "precio_default", nullable = false)
    private BigDecimal precioDefault;

    /** Unidad de presentacion, ej: "tableta", "ml". */
    @Column(name = "unidad", nullable = false, length = 50)
    private String unidad;

    /** true si es medicamento controlado. */
    @Column(name = "is_controlled", nullable = false)
    private boolean controlado = false;

    @Column(name = "minimum_stock")
    private Integer stockMinimo;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecioDefault() { return precioDefault; }
    public void setPrecioDefault(BigDecimal precioDefault) { this.precioDefault = precioDefault; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public boolean isControlado() { return controlado; }
    public void setControlado(boolean controlado) { this.controlado = controlado; }
    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }
}