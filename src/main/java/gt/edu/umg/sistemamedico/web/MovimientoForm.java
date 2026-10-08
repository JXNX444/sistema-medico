package gt.edu.umg.sistemamedico.web;

/**
 * Datos del formulario "Nuevo Movimiento" de la bitacora. [CU-15 pasos 4-6]
 *
 * Todo llega como texto (como lo escribio el usuario) para que el service
 * valide y devuelva los mensajes del documento [RN-CU13-01] en vez de que
 * Spring falle al convertir, por ejemplo, "abc" a numero.
 *
 * @param medicamentoId    id del medicamento (dropdown)
 * @param sucursalId       id de la sucursal (dropdown)
 * @param tipo             0 a 5 (el 6 Despacho no se ofrece)
 * @param cantidad         entero positivo
 * @param costoUnitario    obligatorio solo para Compra
 * @param numeroReferencia Factura / Devolucion / Venta / Reclamo (opcional)
 * @param motivo           obligatorio en Devolucion, Reclamo, Ajuste+ y Ajuste-
 */
public record MovimientoForm(
        String medicamentoId,
        String sucursalId,
        String tipo,
        String cantidad,
        String costoUnitario,
        String numeroReferencia,
        String motivo
) {}