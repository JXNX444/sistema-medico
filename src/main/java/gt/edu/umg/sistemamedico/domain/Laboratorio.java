package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de laboratorios del hospital. Tabla: his.laboratorio  [CU-14]
 * Ej: "Laboratorio Clinico", "Imagenologia".
 *
 * Cada examen (his.examen_laboratorio) pertenece a un laboratorio [RN-CU15-03].
 * Extiende BaseEntity: la tabla tiene state, row_version,
 * created_at/by y updated_at/by.
 */
@Entity
@Table(name = "laboratorio", schema = "his")
public class Laboratorio extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "laboratorio_id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** Opcional en laboratorios [RN-CU15-01]. */
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