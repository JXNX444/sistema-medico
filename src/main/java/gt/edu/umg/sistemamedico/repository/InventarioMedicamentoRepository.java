package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.InventarioMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventarioMedicamentoRepository extends JpaRepository<InventarioMedicamento, Integer> {

    /**
     * [CU-11 paso 5] Stock de varios medicamentos en una sucursal, de una sola vez.
     * Si un medicamento no aparece en la lista -> FA01 "Sin inventario registrado".
     */
    List<InventarioMedicamento> findBySucursalIdAndMedicamentoIdInAndState(
            Integer sucursalId, Collection<Integer> medicamentoIds, Short state);

    /** [CU-11 paso 10] Stock de UN medicamento en la sucursal (ux_inv_med_sucursal garantiza 0 o 1). */
    Optional<InventarioMedicamento> findBySucursalIdAndMedicamentoIdAndState(
            Integer sucursalId, Integer medicamentoId, Short state);
}