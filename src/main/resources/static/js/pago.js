/* ============================================================
   CU-04 Pago en Linea
   ============================================================ */

// Llave de idempotencia para este intento. [RNF-016]
// Se regenera despues de un rechazo, para que el reintento sea un intento nuevo.
let idempotencyKey = crearUUID();

function crearUUID() {
    if (window.crypto && crypto.randomUUID) {
        return crypto.randomUUID();
    }
    // Respaldo por si el navegador es viejo.
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

/* ---------- Temporizador de reserva (FA02) ---------- */

let intervaloReserva = null;

function iniciarTemporizador() {
    if (!EXPIRA_EN) return;

    const expira = new Date(EXPIRA_EN).getTime();
    const contador = document.getElementById('contadorReserva');

    intervaloReserva = setInterval(() => {
        const restante = expira - Date.now();

        if (restante <= 0) {
            clearInterval(intervaloReserva);
            contador.textContent = '0:00';
            expirarReserva();
            return;
        }
        const min = Math.floor(restante / 60000);
        const seg = Math.floor((restante % 60000) / 1000);
        contador.textContent = min + ':' + String(seg).padStart(2, '0');
    }, 1000);
}

function expirarReserva() {
    bloquearFormulario();
    mostrarBanner('El tiempo para confirmar su cita ha expirado. El horario seleccionado ha sido ' +
        'liberado. Por favor, seleccione un nuevo horario. Sera redirigido en unos segundos...');
    setTimeout(() => { window.location.href = CTX + 'citas/nueva'; }, 4000);
}

/* ---------- Formateo de campos ---------- */

const inTarjeta = document.getElementById('numeroTarjeta');
const inTitular = document.getElementById('titular');
const inVenc = document.getElementById('vencimiento');

// Numero de tarjeta: agrupar de 4 en 4 mientras escribe.
inTarjeta.addEventListener('input', () => {
    let v = inTarjeta.value.replace(/\D/g, '').slice(0, 19);
    inTarjeta.value = v.replace(/(.{4})/g, '$1 ').trim();
});

// [RN-CU04-01] Al perder el foco, enmascarar dejando solo los ultimos 4.
inTarjeta.addEventListener('blur', () => {
    const soloDig = inTarjeta.value.replace(/\D/g, '');
    inTarjeta.dataset.real = soloDig; // guardamos el numero real para enviarlo
    if (soloDig.length >= 4) {
        inTarjeta.value = '•••• •••• •••• ' + soloDig.slice(-4);
    }
});

// Al volver a enfocar, restauramos el numero real para poder editarlo.
inTarjeta.addEventListener('focus', () => {
    if (inTarjeta.dataset.real) {
        const v = inTarjeta.dataset.real;
        inTarjeta.value = v.replace(/(.{4})/g, '$1 ').trim();
    }
});

// [RN-CU04-02] Titular en mayusculas.
inTitular.addEventListener('input', () => {
    inTitular.value = inTitular.value.toUpperCase();
});

// [RN-CU04-03] Vencimiento auto-formateado MM/AA.
inVenc.addEventListener('input', () => {
    let v = inVenc.value.replace(/\D/g, '').slice(0, 4);
    if (v.length >= 3) v = v.slice(0, 2) + '/' + v.slice(2);
    inVenc.value = v;
});

/* ---------- Envio del pago ---------- */

document.getElementById('btnPagar').addEventListener('click', pagar);

function pagar() {
    limpiarErrores();

    const numeroReal = inTarjeta.dataset.real || inTarjeta.value.replace(/\D/g, '');

    const btn = document.getElementById('btnPagar');
    btn.disabled = true;
    btn.innerHTML = '<i class="ti ti-loader-2"></i> Procesando pago...';

    fetch(CTX + 'citas/pago/' + CITA_ID + '/procesar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            numeroTarjeta: numeroReal,
            titular: inTitular.value.trim(),
            vencimiento: inVenc.value.trim(),
            cvv: document.getElementById('cvv').value.trim(),
            idempotencyKey: idempotencyKey
        })
    })
        .then(r => r.json())
        .then(resp => manejarRespuesta(resp))
        .catch(() => {
            mostrarBanner('Ocurrio un error de conexion. Intente de nuevo.');
            reactivarBoton();
        });
}

function manejarRespuesta(resp) {
    // FA01: errores de validacion campo por campo.
    if (resp.estado === 'VALIDACION') {
        pintarErrores(resp.errores);
        reactivarBoton();
        return;
    }

    // FA02: reserva expirada.
    if (resp.estado === 'EXPIRADO') {
        clearInterval(intervaloReserva);
        expirarReserva();
        return;
    }

    // Pago aprobado (o la cita ya estaba pagada).
    if (resp.ok) {
        mostrarExito(resp.numeroTransaccion);
        return;
    }

    // FA03: rechazo de la pasarela. La reserva sigue corriendo, se puede reintentar.
    mostrarBanner(resp.mensaje);
    idempotencyKey = crearUUID(); // nuevo intento = nueva llave
    reactivarBoton();
}

/* ---------- Pantalla de exito (ConfirmationPage) ---------- */

function mostrarExito(numeroTransaccion) {
    clearInterval(intervaloReserva);
    document.querySelector('.grid-pago').classList.add('oculto');
    document.getElementById('reservaTimer').classList.add('oculto');
    document.getElementById('bannerError').classList.add('oculto');

    document.getElementById('trxNumero').textContent = numeroTransaccion;
    document.getElementById('pagoExito').classList.remove('oculto');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ---------- Helpers de UI ---------- */

function reactivarBoton() {
    const btn = document.getElementById('btnPagar');
    btn.disabled = false;
    btn.innerHTML = '<i class="ti ti-credit-card"></i> Reintentar pago';
}

function bloquearFormulario() {
    document.getElementById('btnPagar').disabled = true;
    [inTarjeta, inTitular, inVenc, document.getElementById('cvv')]
        .forEach(el => el.disabled = true);
}

function mostrarBanner(texto) {
    const b = document.getElementById('bannerError');
    document.getElementById('bannerErrorTexto').textContent = texto;
    b.classList.remove('oculto');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function pintarErrores(errores) {
    const mapa = {
        numeroTarjeta: 'errNumeroTarjeta',
        titular: 'errTitular',
        vencimiento: 'errVencimiento',
        cvv: 'errCvv'
    };
    Object.keys(errores).forEach(campo => {
        const el = document.getElementById(mapa[campo]);
        if (el) el.textContent = errores[campo];
    });
}

function limpiarErrores() {
    document.getElementById('bannerError').classList.add('oculto');
    ['errNumeroTarjeta', 'errTitular', 'errVencimiento', 'errCvv']
        .forEach(id => document.getElementById(id).textContent = '');
}

/* ---------- Arranque ---------- */
iniciarTemporizador();