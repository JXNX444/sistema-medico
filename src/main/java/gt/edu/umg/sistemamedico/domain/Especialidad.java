package gt.edu.umg.sistemamedico.domain;

import jakarta.persistence.*;

/**
 * Catalogo de especialidades medicas.
 * Tabla: his.especialidad
 *
 * En CU-00 el portal las lista como servicios; en CU-03 el paciente
 * elige especialidad; en CU-01 un usuario Medico se asocia a una.
 */
@Entity
@Table(name = "especialidad", schema = "his")
public class Especialidad extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "especialidad_id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** [RN-CU15-01] la descripcion es obligatoria en especialidades. */
    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    // ---- Getters / setters ----

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}