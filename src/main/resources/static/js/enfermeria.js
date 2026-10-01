/* ============================================================
   CU-07 Toma de Signos Vitales
   ============================================================ */

let citaSeleccionada = null;

document.addEventListener('DOMContentLoaded', () => {
    cargarPanel();
    setInterval(cargarPanel, 15000); // refresca la fila cada 15s
});

/* ---------- Mensajes en pantalla (en vez de alert() del navegador) ---------- */

function mostrarMensaje(texto, tipo) {
    const cont = document.getElementById('mensajeGlobal');
    cont.className = 'mensaje-caja ' + (tipo === 'error' ? 'mensaje-error' : 'mensaje-exito');
    cont.textContent = texto;
    cont.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    if (tipo !== 'error') {
        setTimeout(() => { cont.className = 'oculto'; }, 6000);
    }
}

/* ---------- Paso 1 flujo normal: lista de pacientes ---------- */

async function cargarPanel() {
    try {
        const resp = await fetch(`${CTX}enfermeria/api/pacientes`);
        const data = await resp.json();
        renderListaPresentes(data.presentes);
        renderListaEnSignos(data.enSignos);
    } catch (e) {
        console.error('Error al cargar el panel', e);
    }
}

function renderListaPresentes(citas) {
    const cont = document.getElementById('listaPresentes');
    const contador = document.getElementById('countPresentes');
    contador.textContent = citas.length;

    if (citas.length === 0) {
        cont.innerHTML = '<p class="sm-error" style="color:var(--sm-slate-400);">No hay pacientes esperando.</p>';
        return;
    }
    cont.innerHTML = citas.map(c => `
        <div class="sm-panel" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;padding:14px 16px;">
            <div>
                <div style="font-weight:700;">${c.pacienteNombre} ${c.esEmergencia ? '<span class="sm-badge sm-badge--danger">EMERGENCIA</span>' : ''}</div>
                <div style="font-size:12px;color:var(--sm-slate-500);">${c.numeroCita} &middot; ${c.especialidad} &middot; ${c.fechaHora}</div>
            </div>
            <button class="sm-btn sm-btn--primary sm-btn--sm" onclick="llamarPaciente(${c.citaId})">Llamar y Tomar Signos</button>
        </div>`).join('');
}

function renderListaEnSignos(citas) {
    const cont = document.getElementById('listaEnSignos');
    const contador = document.getElementById('countEnSignos');
    contador.textContent = citas.length;
    contador.className = 'sv-contador ' + (citas.length > 0 ? 'sv-contador--alerta' : 'sv-contador--gris');

    if (citas.length === 0) {
        cont.innerHTML = '<p class="sm-error" style="color:var(--sm-slate-400);">Nadie en toma de signos ahora mismo.</p>';
        return;
    }
    cont.innerHTML = citas.map(c => `
        <div class="sm-panel" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;padding:14px 16px;">
            <div>
                <div style="font-weight:700;">${c.pacienteNombre}</div>
                <div style="font-size:12px;color:var(--sm-slate-500);">${c.numeroCita} &middot; ${c.especialidad}</div>
            </div>
            <button class="sm-btn sm-btn--dark sm-btn--sm" onclick="abrirFormulario(${c.citaId}, '${c.pacienteNombre}', '${c.numeroCita}')">Registrar Signos Vitales</button>
        </div>`).join('');
}

/* ---------- Paso 2 flujo normal: llamar paciente (con TTS) ---------- */

async function llamarPaciente(citaId) {
    try {
        const resp = await fetch(`${CTX}enfermeria/api/llamar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ citaId })
        });
        const data = await resp.json();

        if (data.ok) {
            anunciarPorVoz(data.anuncio);
            cargarPanel();
        } else {
            mostrarMensaje(data.mensaje, 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al llamar al paciente.', 'error');
    }
}

function anunciarPorVoz(texto) {
    if (!('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance(texto);
    utterance.lang = 'es-GT';
    window.speechSynthesis.speak(utterance);
}

/* ---------- Pasos 3-11: formulario de registro ---------- */

function abrirFormulario(citaId, nombre, numeroCita) {
    citaSeleccionada = citaId;
    document.getElementById('formContexto').textContent = `${nombre} — ${numeroCita}`;
    document.getElementById('formSignos').classList.remove('oculto');
    document.getElementById('alertasEnVivo').innerHTML = '';
    limpiarErrores();
    document.getElementById('formSignos').scrollIntoView({ behavior: 'smooth' });
}

function cerrarFormulario() {
    citaSeleccionada = null;
    document.getElementById('formSignos').classList.add('oculto');
    document.querySelectorAll('#formSignos input').forEach(i => {
        if (i.type === 'checkbox') i.checked = false; else i.value = '';
    });
}

function limpiarErrores() {
    document.querySelectorAll('#formSignos .sm-error').forEach(e => e.textContent = '');
}

/* ---------- FA03: alertas clinicas en tiempo real ---------- */

async function chequearAlertas() {
    const body = {
        presionSistolica: numOrNull('inpSistolica'),
        presionDiastolica: numOrNull('inpDiastolica'),
        temperatura: numOrNull('inpTemperatura'),
        frecuenciaCardiaca: numOrNull('inpFrecuencia')
    };
    try {
        const resp = await fetch(`${CTX}enfermeria/api/chequear-alertas`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const alertas = await resp.json();
        const cont = document.getElementById('alertasEnVivo');
        cont.innerHTML = alertas.map(a => `<div class="sm-badge sm-badge--warning" style="display:block;margin-top:6px;">${a}</div>`).join('');
    } catch (e) { /* silencioso: es solo una ayuda visual */ }
}

function numOrNull(id) {
    const v = document.getElementById(id).value;
    return v === '' ? null : Number(v);
}

/* ---------- Guardar el registro ---------- */

async function registrarSignos() {
    limpiarErrores();

    const body = {
        citaId: citaSeleccionada,
        presionSistolica: numOrNull('inpSistolica'),
        presionDiastolica: numOrNull('inpDiastolica'),
        temperatura: numOrNull('inpTemperatura'),
        pesoKg: numOrNull('inpPeso'),
        tallaCm: numOrNull('inpTalla'),
        frecuenciaCardiaca: numOrNull('inpFrecuencia'),
        esEmergencia: document.getElementById('inpEmergencia').checked
    };

    try {
        const resp = await fetch(`${CTX}enfermeria/api/registrar-signos`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();

        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormulario();
            cargarPanel();
        } else {
            for (const campo in data.errores) {
                const el = document.getElementById('err_' + campo);
                if (el) el.textContent = data.errores[campo];
            }
        }
    } catch (e) {
        mostrarMensaje('Error al registrar los signos vitales.', 'error');
    }
}