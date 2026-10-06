/* =====================================================================
   Modulo Caja - CU-10 (Cobro de Laboratorio en Caja)
   ===================================================================== */

// ---------- Buscador ----------
const btnModoDpi    = document.getElementById('btnModoDpi');
const btnModoNumero = document.getElementById('btnModoNumero');
const campoValor    = document.getElementById('campoValor');
const btnBuscar     = document.getElementById('btnBuscar');
const errorBusqueda = document.getElementById('errorBusqueda');
const cargando      = document.getElementById('cargando');
const zonaResultado = document.getElementById('zonaResultado');

// ---------- Modal ----------
const overlayCobro    = document.getElementById('overlayCobro');
const btnCerrarModal  = document.getElementById('btnCerrarModal');
const zonaFormCobro   = document.getElementById('zonaFormCobro');
const zonaComprobante = document.getElementById('zonaComprobante');

const metodoPago       = document.getElementById('metodoPago');
const bloqueEfectivo   = document.getElementById('bloqueEfectivo');
const bloqueTarjeta    = document.getElementById('bloqueTarjeta');
const montoRecibido    = document.getElementById('montoRecibido');
const cajaCambio       = document.getElementById('cajaCambio');
const txtCambio        = document.getElementById('txtCambio');
const ultimos4         = document.getElementById('ultimos4');
const errorModal       = document.getElementById('errorModal');
const btnConfirmarPago = document.getElementById('btnConfirmarPago');

const btnImprimir   = document.getElementById('btnImprimir');
const btnNuevoCobro = document.getElementById('btnNuevoCobro');

// ---------- Estado ----------
let modo = 'dpi';          // 'dpi' | 'numero'
let ordenActual = null;
let montoActual = 0;
let idempotencyKey = null; // [RNF-016]

const $ = (id) => document.getElementById(id);

// =====================================================================
// Paso 2: alternancia DPI / No. Orden
// =====================================================================
btnModoDpi.addEventListener('click', () => cambiarModo('dpi'));
btnModoNumero.addEventListener('click', () => cambiarModo('numero'));

function cambiarModo(nuevo) {
    modo = nuevo;
    btnModoDpi.classList.toggle('activa', modo === 'dpi');
    btnModoNumero.classList.toggle('activa', modo === 'numero');
    campoValor.placeholder = modo === 'dpi' ? '1234567890101' : 'LAB-2026-00001';
    campoValor.value = '';
    errorBusqueda.textContent = '';
    zonaResultado.innerHTML = '';
    campoValor.focus();
}

// =====================================================================
// Pasos 2-3 + FA01: buscar ordenes pendientes de pago
// =====================================================================
btnBuscar.addEventListener('click', buscar);
campoValor.addEventListener('keydown', (e) => { if (e.key === 'Enter') buscar(); });

async function buscar() {
    const valor = campoValor.value.trim();
    errorBusqueda.textContent = '';
    zonaResultado.innerHTML = '';

    if (!valor) {
        errorBusqueda.textContent = 'Debe ingresar un DPI o numero de orden para buscar.';
        return;
    }
    if (modo === 'dpi' && !/^\d{13}$/.test(valor)) {   // RN-GLOBAL-001
        errorBusqueda.textContent = 'El DPI debe tener 13 digitos.';
        return;
    }

    cargando.classList.remove('oculto');
    try {
        const url = CTX + 'caja/lab/api/buscar?modo=' + encodeURIComponent(modo) +
            '&valor=' + encodeURIComponent(valor);
        const data = await (await fetch(url)).json();

        if (data.ok) {
            pintarOrdenes(data.ordenes);
        } else {
            // FA01
            zonaResultado.innerHTML =
                '<div class="aviso"><i class="ti ti-alert-triangle"></i> ' + data.mensaje + '</div>';
        }
    } catch (e) {
        errorBusqueda.textContent = 'Ocurrio un error al buscar. Intente de nuevo.';
    } finally {
        cargando.classList.add('oculto');
    }
}

function pintarOrdenes(ordenes) {
    zonaResultado.innerHTML = ordenes.map((o, i) => `
        <div class="tarjeta-cita">
            <div class="cita-cabecera">
                <div>
                    <div class="cita-numero">${o.numeroOrden}</div>
                    <div class="cita-paciente">${o.pacienteNombre}
                        <small>· DPI ${o.pacienteDpi}</small></div>
                </div>
                <span class="badge-estado"><i class="ti ti-clock-dollar"></i> Pendiente de pago</span>
            </div>
            <div class="cita-datos">
                <div class="dato"><span>Exámenes</span><span>${o.cantidadExamenes}</span></div>
                <div class="dato"><span>Fecha de creación</span><span>${o.fechaCreacion}</span></div>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
                <span class="cita-monto">${o.montoFmt}</span>
                <button type="button" class="boton-cobrar" data-idx="${i}">
                    <i class="ti ti-cash-register"></i> Cobrar
                </button>
            </div>
        </div>
    `).join('');

    // Paso 4: seleccionar la orden
    zonaResultado.querySelectorAll('.boton-cobrar').forEach(btn => {
        btn.addEventListener('click', () => abrirModal(ordenes[btn.dataset.idx]));
    });
}

// =====================================================================
// Paso 5: formulario de cobro
// =====================================================================
function abrirModal(orden) {
    ordenActual = orden;
    montoActual = parseFloat(orden.montoFmt.replace('Q', '')) || 0;
    idempotencyKey = crypto.randomUUID();

    $('rcPaciente').textContent    = orden.pacienteNombre;
    $('rcDpi').textContent         = orden.pacienteDpi;
    $('rcNumeroOrden').textContent = orden.numeroOrden;
    $('rcExamenes').textContent    = orden.cantidadExamenes;
    $('rcMonto').textContent       = orden.montoFmt;

    metodoPago.value = '0';
    montoRecibido.value = '';
    ultimos4.value = '';
    errorModal.textContent = '';
    aplicarMetodo();
    restaurarBoton();

    zonaFormCobro.classList.remove('oculto');
    zonaComprobante.classList.add('oculto');
    overlayCobro.classList.remove('oculto');
}

function cerrarModal() {
    overlayCobro.classList.add('oculto');
    ordenActual = null;
}
btnCerrarModal.addEventListener('click', cerrarModal);   // FA02: no se procesa el cobro

function restaurarBoton() {
    btnConfirmarPago.disabled = false;
    btnConfirmarPago.textContent = 'Confirmar Pago Q' + montoActual.toFixed(2);   // paso 8
}

// Paso 6 + FA03: efectivo o tarjeta
metodoPago.addEventListener('change', aplicarMetodo);
function aplicarMetodo() {
    const esEfectivo = metodoPago.value === '0';
    bloqueEfectivo.classList.toggle('oculto', !esEfectivo);
    bloqueTarjeta.classList.toggle('oculto', esEfectivo);
    errorModal.textContent = '';
    calcularCambio();
}

// Paso 7: cambio en tiempo real
montoRecibido.addEventListener('input', calcularCambio);
function calcularCambio() {
    if (metodoPago.value !== '0') { cajaCambio.classList.add('oculto'); return; }
    const recibido = parseFloat(montoRecibido.value);
    if (!isNaN(recibido) && recibido >= montoActual) {
        txtCambio.textContent = 'Q' + (recibido - montoActual).toFixed(2);
        cajaCambio.classList.remove('oculto');
    } else {
        cajaCambio.classList.add('oculto');
    }
}

// =====================================================================
// Pasos 8-9 + FA04: confirmar pago
// =====================================================================
btnConfirmarPago.addEventListener('click', confirmarPago);

async function confirmarPago() {
    errorModal.textContent = '';
    const metodo = parseInt(metodoPago.value, 10);

    if (metodo === 0) {
        const recibido = parseFloat(montoRecibido.value);
        if (isNaN(recibido)) {
            errorModal.textContent = 'Ingrese el monto recibido.';
            return;
        }
        if (recibido < montoActual) {
            errorModal.textContent = 'El monto recibido (Q' + recibido.toFixed(2) +
                ') es menor al monto a cobrar (Q' + montoActual.toFixed(2) + ')';
            return;
        }
    } else if (!/^\d{4}$/.test(ultimos4.value.trim())) {
        errorModal.textContent = 'Ingrese los últimos 4 dígitos de la tarjeta.';
        return;
    }

    btnConfirmarPago.disabled = true;
    btnConfirmarPago.textContent = 'Procesando...';

    try {
        const resp = await fetch(CTX + 'caja/lab/api/registrar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                ordenId: ordenActual.ordenId,
                metodoPago: metodo,
                montoRecibido: metodo === 0 ? parseFloat(montoRecibido.value) : null,
                ultimos4: metodo === 0 ? null : ultimos4.value.trim(),
                idempotencyKey: idempotencyKey
            })
        });
        const data = await resp.json();

        if (data.ok && data.comprobante) {
            mostrarComprobante(data.mensaje, data.comprobante);
        } else {
            // VALIDACION / RECHAZADO (FA04) / YA_PAGADA / ERROR -> vuelve al paso 6
            errorModal.textContent = data.mensaje || 'No se pudo registrar el pago.';
            idempotencyKey = crypto.randomUUID();
        }
    } catch (e) {
        errorModal.textContent = 'Ocurrio un error al registrar el pago.';
        idempotencyKey = crypto.randomUUID();
    } finally {
        restaurarBoton();
    }
}

// =====================================================================
// Pasos 10-11: comprobante
// =====================================================================
function mostrarComprobante(mensaje, c) {
    $('cpMensaje').textContent     = mensaje;
    $('cpTrx').textContent         = c.numeroTransaccion;
    $('cpSucursal').textContent    = c.sucursal;
    $('cpPaciente').textContent    = c.pacienteNombre;
    $('cpDpi').textContent         = c.pacienteDpi;
    $('cpNumeroOrden').textContent = c.numeroOrden;
    $('cpFechaTrx').textContent    = c.fechaTransaccion;
    $('cpFormaPago').textContent   = c.formaPago;
    $('cpMonto').textContent       = c.montoFmt;

    $('cpExamenes').innerHTML = c.examenes.map(e =>
        `<div class="comprobante-fila examen"><span>${e.nombre}</span><span>${e.montoFmt}</span></div>`
    ).join('');

    const efectivo = !!c.montoRecibidoFmt;
    $('cpFilaRecibido').classList.toggle('oculto', !efectivo);
    $('cpFilaCambio').classList.toggle('oculto', !efectivo);
    $('cpFilaUltimos4').classList.toggle('oculto', efectivo);
    if (efectivo) {
        $('cpRecibido').textContent = c.montoRecibidoFmt;
        $('cpCambio').textContent   = c.cambioFmt;
    } else {
        $('cpUltimos4').textContent = '**** ' + c.ultimos4;
    }

    zonaFormCobro.classList.add('oculto');
    zonaComprobante.classList.remove('oculto');
}

btnImprimir.addEventListener('click', () => window.print());

btnNuevoCobro.addEventListener('click', () => {
    cerrarModal();
    campoValor.value = '';
    zonaResultado.innerHTML = '';
    campoValor.focus();
});