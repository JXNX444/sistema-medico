package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de sucursales / sedes del hospital. [RN-CU15-04]
 * Tabla: his.sucursal
 *
 * En CU-00 el portal muestra las sedes disponibles; en CU-03 el paciente
 * elige una sede como primer paso del agendamiento.
 */
@Entity
@Table(name = "sucursal", schema = "his")
public class Sucursal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sucursal_id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** [RN-CU15-04] exactamente 8 digitos si se ingresa. */
    @Column(name = "telefono", length = 8)
    private String telefono;

    @Column(name = "direccion", length = 500)
    private String direccion;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}