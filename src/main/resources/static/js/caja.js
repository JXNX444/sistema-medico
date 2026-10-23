/* =====================================================================
   Modulo Caja - CU-06 (Cobro de Consulta en Caja)
   ===================================================================== */

// ---------- Referencias del buscador ----------
const btnModoDpi    = document.getElementById('btnModoDpi');
const btnModoNumero = document.getElementById('btnModoNumero');
const campoValor    = document.getElementById('campoValor');
const btnBuscar     = document.getElementById('btnBuscar');
const errorBusqueda = document.getElementById('errorBusqueda');
const cargando      = document.getElementById('cargando');
const zonaResultado = document.getElementById('zonaResultado');

// ---------- Referencias del modal ----------
const overlayCobro    = document.getElementById('overlayCobro');
const btnCerrarModal  = document.getElementById('btnCerrarModal');
const zonaFormCobro   = document.getElementById('zonaFormCobro');
const zonaComprobante = document.getElementById('zonaComprobante');

const rcPaciente     = document.getElementById('rcPaciente');
const rcNumeroCita   = document.getElementById('rcNumeroCita');
const rcEspecialidad = document.getElementById('rcEspecialidad');
const rcMonto        = document.getElementById('rcMonto');

const metodoPago     = document.getElementById('metodoPago');
const bloqueEfectivo = document.getElementById('bloqueEfectivo');
const bloqueTarjeta  = document.getElementById('bloqueTarjeta');
const montoRecibido  = document.getElementById('montoRecibido');
const cajaCambio     = document.getElementById('cajaCambio');
const txtCambio      = document.getElementById('txtCambio');
const ultimos4       = document.getElementById('ultimos4');
const errorModal     = document.getElementById('errorModal');
const btnRegistrarPago = document.getElementById('btnRegistrarPago');

const btnImprimir   = document.getElementById('btnImprimir');
const btnNuevoCobro = document.getElementById('btnNuevoCobro');

// ---------- Estado ----------
let modo = 'dpi';          // 'dpi' | 'numero'
let citaActual = null;     // cita seleccionada para cobrar
let montoActual = 0;       // monto numerico de la cita actual
let idempotencyKey = null; // llave del intento en curso

// =====================================================================
// Alternancia DPI / No. Cita
// =====================================================================
btnModoDpi.addEventListener('click', () => cambiarModo('dpi'));
btnModoNumero.addEventListener('click', () => cambiarModo('numero'));

function cambiarModo(nuevo) {
    modo = nuevo;
    btnModoDpi.classList.toggle('activa', modo === 'dpi');
    btnModoNumero.classList.toggle('activa', modo === 'numero');
    campoValor.placeholder = modo === 'dpi' ? '1234567890101' : 'CITA-2026-00001';
    campoValor.value = '';
    errorBusqueda.textContent = '';
    zonaResultado.innerHTML = '';
    campoValor.focus();
}

// =====================================================================
// Buscar citas pendientes de pago
// =====================================================================
btnBuscar.addEventListener('click', buscar);
campoValor.addEventListener('keydown', (e) => { if (e.key === 'Enter') buscar(); });

async function buscar() {
    const valor = campoValor.value.trim();
    errorBusqueda.textContent = '';
    zonaResultado.innerHTML = '';

    if (!valor) {
        errorBusqueda.textContent = 'Debe ingresar un numero de cita o DPI para buscar.';
        return;
    }

    cargando.classList.remove('oculto');
    try {
        const url = CTX + 'caja/api/buscar?modo=' + encodeURIComponent(modo) +
            '&valor=' + encodeURIComponent(valor);
        const resp = await fetch(url);
        const data = await resp.json();

        if (data.tipo === 'CITAS_ENCONTRADAS') {
            pintarCitas(data.citas);
        } else {
            // SIN_RESULTADOS / SIN_PARAMETROS -> aviso (FA02)
            zonaResultado.innerHTML =
                '<div class="aviso"><i class="ti ti-alert-triangle"></i> ' +
                (data.mensaje || 'No hay citas pendientes de pago.') + '</div>';
        }
    } catch (e) {
        errorBusqueda.textContent = 'Ocurrio un error al buscar. Intente de nuevo.';
    } finally {
        cargando.classList.add('oculto');
    }
}

function pintarCitas(citas) {
    zonaResultado.innerHTML = citas.map((c, i) => `
        <div class="tarjeta-cita">
            <div class="cita-cabecera">
                <div>
                    <div class="cita-numero">${c.numeroCita}</div>
                    <div class="cita-paciente">${c.pacienteNombre}
                        <small>· DPI ${c.pacienteDpi}</small></div>
                </div>
                <span class="badge-estado"><i class="ti ti-clock-dollar"></i> Pendiente de pago</span>
            </div>
            <div class="cita-datos">
                <div class="dato"><span>Especialidad</span><span>${c.especialidad}</span></div>
                <div class="dato"><span>Medico</span><span>${c.medico}</span></div>
                <div class="dato"><span>Sucursal</span><span>${c.sucursal}</span></div>
                <div class="dato"><span>Fecha y hora</span><span>${c.fechaHora}</span></div>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
                <span class="cita-monto">${c.montoFmt}</span>
                <button type="button" class="boton-cobrar" data-idx="${i}">
                    <i class="ti ti-cash-register"></i> Cobrar
                </button>
            </div>
        </div>
    `).join('');

    // Enlazar botones "Cobrar"
    zonaResultado.querySelectorAll('.boton-cobrar').forEach(btn => {
        btn.addEventListener('click', () => abrirModal(citas[btn.dataset.idx]));
    });
}

// =====================================================================
// Modal de cobro
// =====================================================================
function abrirModal(cita) {
    citaActual = cita;
    montoActual = parseFloat((cita.montoFmt || 'Q0').replace('Q', '')) || 0;
    idempotencyKey = crypto.randomUUID();

    rcPaciente.textContent     = cita.pacienteNombre;
    rcNumeroCita.textContent   = cita.numeroCita;
    rcEspecialidad.textContent = cita.especialidad;
    rcMonto.textContent        = cita.montoFmt;

    // Reset del formulario
    metodoPago.value = '0';
    montoRecibido.value = '';
    ultimos4.value = '';
    errorModal.textContent = '';
    cajaCambio.classList.add('oculto');
    aplicarMetodo();

    zonaFormCobro.classList.remove('oculto');
    zonaComprobante.classList.add('oculto');
    overlayCobro.classList.remove('oculto');
}

function cerrarModal() {
    overlayCobro.classList.add('oculto');
    citaActual = null;
}
btnCerrarModal.addEventListener('click', cerrarModal);

// Mostrar bloque segun metodo (efectivo vs tarjeta)
metodoPago.addEventListener('change', aplicarMetodo);
function aplicarMetodo() {
    const esEfectivo = metodoPago.value === '0';
    bloqueEfectivo.classList.toggle('oculto', !esEfectivo);
    bloqueTarjeta.classList.toggle('oculto', esEfectivo);
    errorModal.textContent = '';
    calcularCambio();
}

// Calcular cambio en vivo
montoRecibido.addEventListener('input', calcularCambio);
function calcularCambio() {
    if (metodoPago.value !== '0') { cajaCambio.classList.add('oculto'); return; }
    const recibido = parseFloat(montoRecibido.value);
    if (!isNaN(recibido) && recibido >= montoActual) {
        const cambio = (recibido - montoActual).toFixed(2);
        txtCambio.textContent = 'Q' + cambio;
        cajaCambio.classList.remove('oculto');
    } else {
        cajaCambio.classList.add('oculto');
    }
}

// =====================================================================
// Registrar pago
// =====================================================================
btnRegistrarPago.addEventListener('click', registrarPago);

async function registrarPago() {
    errorModal.textContent = '';
    const metodo = parseInt(metodoPago.value, 10);

    // Validacion en el navegador (el backend vuelve a validar)
    if (metodo === 0) {
        const recibido = parseFloat(montoRecibido.value);
        if (isNaN(recibido)) {
            errorModal.textContent = 'Ingrese el monto recibido.';
            return;
        }
        if (recibido < montoActual) {
            errorModal.textContent = 'El monto recibido (Q' + recibido.toFixed(2) +
                ') es menor al monto a cobrar (Q' + montoActual.toFixed(2) + ').';
            return;
        }
    } else {
        if (!/^\d{4}$/.test(ultimos4.value.trim())) {
            errorModal.textContent = 'Ingrese los ultimos 4 digitos de la tarjeta.';
            return;
        }
    }

    btnRegistrarPago.disabled = true;
    btnRegistrarPago.innerHTML = '<i class="ti ti-loader"></i> Procesando...';

    try {
        const resp = await fetch(CTX + 'caja/api/registrar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                citaId: citaActual.citaId,
                metodoPago: metodo,
                montoRecibido: metodo === 0 ? parseFloat(montoRecibido.value) : null,
                ultimos4: metodo === 0 ? null : ultimos4.value.trim(),
                idempotencyKey: idempotencyKey
            })
        });
        const data = await resp.json();

        if (data.ok && data.comprobante) {
            mostrarComprobante(data.comprobante);
        } else {
            // VALIDACION / RECHAZADO / YA_PAGADA / ERROR
            errorModal.textContent = data.mensaje || 'No se pudo registrar el pago.';
            // Nuevo intento -> nueva llave, para que un reintento no choque con idempotencia.
            idempotencyKey = crypto.randomUUID();
        }
    } catch (e) {
        errorModal.textContent = 'Ocurrio un error al registrar el pago.';
        idempotencyKey = crypto.randomUUID();
    } finally {
        btnRegistrarPago.disabled = false;
        btnRegistrarPago.innerHTML = '<i class="ti ti-check"></i> Registrar pago';
    }
}

// =====================================================================
// Comprobante
// =====================================================================
function mostrarComprobante(c) {
    document.getElementById('cpTrx').textContent          = c.numeroTransaccion;
    document.getElementById('cpPaciente').textContent     = c.pacienteNombre;
    document.getElementById('cpNumeroCita').textContent   = c.numeroCita;
    document.getElementById('cpEspecialidad').textContent = c.especialidad;
    document.getElementById('cpMedico').textContent       = c.medico;
    document.getElementById('cpSucursal').textContent     = c.sucursal;
    document.getElementById('cpFechaCita').textContent    = c.fechaCita;
    document.getElementById('cpFechaTrx').textContent     = c.fechaTransaccion;
    document.getElementById('cpFormaPago').textContent    = c.formaPago;
    document.getElementById('cpMonto').textContent        = c.montoFmt;

    const filaRecibido = document.getElementById('cpFilaRecibido');
    const filaCambio   = document.getElementById('cpFilaCambio');
    if (c.montoRecibidoFmt) {
        document.getElementById('cpRecibido').textContent = c.montoRecibidoFmt;
        document.getElementById('cpCambio').textContent   = c.cambioFmt;
        filaRecibido.classList.remove('oculto');
        filaCambio.classList.remove('oculto');
    } else {
        filaRecibido.classList.add('oculto');
        filaCambio.classList.add('oculto');
    }

    zonaFormCobro.classList.add('oculto');
    zonaComprobante.classList.remove('oculto');
}

btnImprimir.addEventListener('click', () => window.print());

btnNuevoCobro.addEventListener('click', () => {
    cerrarModal();
    // Quitar la cita ya cobrada del listado (ya no esta pendiente)
    if (citaActual) { /* citaActual ya se limpio en cerrarModal */ }
    campoValor.value = '';
    zonaResultado.innerHTML = '';
    campoValor.focus();
});