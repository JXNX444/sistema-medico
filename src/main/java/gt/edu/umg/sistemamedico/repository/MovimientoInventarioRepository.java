package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;

/** Kardex de inventario. [CU-11 paso 10] Por ahora solo se usa save(). */
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
}