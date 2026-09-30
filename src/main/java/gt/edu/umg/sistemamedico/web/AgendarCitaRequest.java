package gt.edu.umg.sistemamedico.web;

/**
 * Datos que manda el JavaScript del wizard al confirmar la cita (paso 6).
 * Java record: es una clase de solo datos, inmutable, sin necesitar
 * escribir getters/setters/constructor a mano.
 */
public record AgendarCitaRequest(
        Integer sucursalId,
        Integer especialidadId,
        Integer medicoId,
        String fecha,   // formato yyyy-MM-dd
        String hora,    // formato HH:mm
        String motivo,
        String metodoPago  // "TARJETA" (pago en linea) o "CAJA" (paga presencial)
) {
}