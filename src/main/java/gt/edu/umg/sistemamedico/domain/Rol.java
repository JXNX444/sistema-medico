package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de roles del sistema. [RN-CU01-03]
 * Tabla: his.rol
 *
 * Los 8 roles se cargaron en la migracion V2:
 * Administrador, Medico, Enfermero, Recepcionista, Cajero,
 * Laboratorista, Farmaceutico, Paciente.
 */
@Entity
@Table(name = "rol", schema = "his")
public class Rol extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}