package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.service.DespachoService;

import java.util.List;

/**
 * Datos que envia la pantalla de farmacia al confirmar un despacho. [CU-11 paso 9]
 *
 * @param recetaId receta a despachar (vigente y no despachada).
 * @param items    un item por medicamento recetado: cantidad a entregar
 *                 (0 = no se entrega, FA01) y datos de sustitucion (FA02).
 * @param notas    observaciones opcionales del farmaceutico (max. 500).
 */
public record DespachoForm(
        Integer recetaId,
        List<DespachoService.ItemSolicitud> items,
        String notas
) {}