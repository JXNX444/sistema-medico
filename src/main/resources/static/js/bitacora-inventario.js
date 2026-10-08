/* ============================================================
   CU-15 Bitacora de Movimientos de Inventario - formulario
   - Etiquetas y campos dinamicos segun el tipo [RN-CU13-01]
   - Panel de inventario en tiempo real [RN-CU13-03]
   - Alerta preventiva de stock minimo [FA05]
   La validacion real la hace el servidor; esto solo ayuda al usuario.
   ============================================================ */
(function () {
    'use strict';

    const selMedicamento = document.getElementById('medicamentoId');
    const selSucursal = document.getElementById('sucursalId');
    const selTipo = document.getElementById('tipo');
    const inpCantidad = document.getElementById('cantidad');
    const txtMotivo = document.getElementById('motivo');

    const etiquetaCosto = document.getElementById('etiquetaCosto');
    const etiquetaReferencia = document.getElementById('etiquetaReferencia');
    const etiquetaMotivo = document.getElementById('etiquetaMotivo');
    const bloqueMotivo = document.getElementById('bloqueMotivo');
    const contadorMotivo = document.getElementById('contadorMotivo');

    const panelVacio = document.getElementById('panelVacio');
    const panelSinInventario = document.getElementById('panelSinInventario');
    const panelDatos = document.getElementById('panelDatos');
    const pStockActual = document.getElementById('pStockActual');
    const pStockMinimo = document.getElementById('pStockMinimo');
    const pStockProyectado = document.getElementById('pStockProyectado');
    const pOperacion = document.getElementById('pOperacion');
    const alertaStock = document.getElementById('alertaStock');

    /** Lo ultimo que devolvio el servidor para medicamento + sucursal. */
    let info = null;

    // Tipos: 0 Compra, 1 Devolucion, 2 Venta, 3 Reclamo, 4 Ajuste+, 5 Ajuste-
    const REFERENCIA = { '0': 'Factura', '1': 'Devolución', '2': 'Venta', '3': 'Reclamo' };
    const MOTIVO = {
        '0': 'Notas (opcional)',
        '1': 'Motivo de Devolución *',
        '3': 'Motivo del Reclamo *',
        '4': 'Notas/Justificación *',
        '5': 'Notas/Justificación *'
    };

    function mostrar(el, visible) {
        el.classList.toggle('oculto', !visible);
    }

    /** true = entrada (suma), false = salida (resta), null = sin tipo. */
    function esEntrada() {
        const opcion = selTipo.options[selTipo.selectedIndex];
        if (!opcion || opcion.value === '') return null;
        return opcion.dataset.entrada === 'true';
    }

    // ---------- RN-CU13-01: campos dinamicos ----------

    function actualizarCampos() {
        const tipo = selTipo.value;

        etiquetaCosto.textContent = tipo === '0' ? 'Costo Unitario (Q) *' : 'Costo Unitario (Q) (opcional)';

        etiquetaReferencia.textContent = REFERENCIA[tipo]
            ? 'No. de ' + REFERENCIA[tipo] + ' (opcional)'
            : 'Número de Referencia (opcional)';

        // Venta: el campo de motivo no se muestra
        mostrar(bloqueMotivo, tipo !== '2');
        etiquetaMotivo.textContent = MOTIVO[tipo] || 'Motivo / Notas';
    }

    function contarMotivo() {
        contadorMotivo.textContent = txtMotivo.value.length;
    }

    // ---------- RN-CU13-03: panel de inventario en tiempo real ----------

    async function cargarStock() {
        info = null;
        if (!selMedicamento.value || !selSucursal.value) {
            pintarPanel();
            return;
        }
        try {
            const url = CTX + 'bitacora-inventario/stock?medicamentoId=' + encodeURIComponent(selMedicamento.value)
                + '&sucursalId=' + encodeURIComponent(selSucursal.value);
            const resp = await fetch(url);
            info = resp.ok ? await resp.json() : null;
        } catch (e) {
            info = null;
        }
        pintarPanel();
    }

    function pintarPanel() {
        const hayCombinacion = selMedicamento.value && selSucursal.value;
        mostrar(panelVacio, !hayCombinacion);
        mostrar(panelSinInventario, !!hayCombinacion && info !== null && !info.existe);
        mostrar(panelDatos, !!info && info.existe);
        mostrar(alertaStock, false);

        if (!info || !info.existe) return;

        pStockActual.textContent = info.stockActual;
        pStockMinimo.textContent = info.stockMinimo ?? 'No definido';

        const entrada = esEntrada();
        const cantidad = parseInt(inpCantidad.value, 10);
        pOperacion.textContent = entrada === null ? '—' : (entrada ? 'Entrada (suma)' : 'Salida (resta)');

        if (entrada === null || isNaN(cantidad) || cantidad <= 0) {
            pStockProyectado.textContent = '—';
            return;
        }

        const proyectado = entrada ? info.stockActual + cantidad : info.stockActual - cantidad;
        pStockProyectado.textContent = proyectado;

        // FA02 (aviso previo): la salida es mayor que el stock
        if (proyectado < 0) {
            alertaStock.textContent = 'Stock insuficiente. Stock actual: ' + info.stockActual
                + '. No se puede registrar una salida de ' + cantidad + ' unidades.';
            mostrar(alertaStock, true);
            return;
        }

        // FA05: alerta preventiva de stock minimo [RN-CU10-03]
        if (info.stockMinimo !== null && proyectado <= info.stockMinimo) {
            alertaStock.textContent = info.medicamento + ': Stock bajo — disponible: ' + proyectado
                + ' (mínimo: ' + info.stockMinimo + ')';
            mostrar(alertaStock, true);
        }
    }

    // ---------- Eventos ----------

    selMedicamento.addEventListener('change', cargarStock);
    selSucursal.addEventListener('change', cargarStock);
    selTipo.addEventListener('change', () => { actualizarCampos(); pintarPanel(); });
    inpCantidad.addEventListener('input', pintarPanel);
    txtMotivo.addEventListener('input', contarMotivo);

    // Al abrir (o al volver con errores) se deja todo como corresponde
    actualizarCampos();
    contarMotivo();
    cargarStock();
})();