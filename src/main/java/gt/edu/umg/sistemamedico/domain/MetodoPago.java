package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de metodos de pago.
 * Tabla: his.metodo_pago
 *
 * Los ids 0-3 son los mismos codigos que usa Pago.java:
 * 0 Efectivo, 1 Tarjeta Visa, 2 Tarjeta Mastercard, 3 Tarjeta de Debito.
 * La columna his.pago.metodo_pago apunta a esta tabla (fk_pago_metodo_pago).
 */
@Entity
@Table(name = "metodo_pago", schema = "his")
public class MetodoPago extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metodo_pago_id")
    private Short id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    // ---- Getters / setters ----

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}