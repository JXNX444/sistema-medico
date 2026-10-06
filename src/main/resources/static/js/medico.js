/* ============================================================
   CU-08 Consulta Medica
   ============================================================ */

let citasPorId = {};          // ultima carga del panel, para leer nombres sin pasarlos por onclick
let citaConsulta = null;      // cita abierta en el formulario de consulta
let citaEvaluada = null;      // cita de "Evaluados" para orden / receta / seguimiento
let cie10Seleccionado = null; // id del codigo CIE-10 elegido
let sugerenciasCie10 = [];
let timerCie10 = null;
let catalogoExamenes = null;
let catalogoMedicamentos = null;

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('select.sm-select').forEach(embellecerSelect);
    document.addEventListener('click', cerrarSelects);
    cargarPanel();
    setInterval(cargarPanel, 30000); // [paso 1] se actualiza cada 30 segundos
});

/* ---------- Selects con el estilo del sistema ----------
   La lista desplegable nativa la dibuja el sistema operativo y no se
   puede estilizar. Se oculta el <select> real y se muestra un boton +
   lista propios; el <select> sigue siendo el que guarda el valor. */

function embellecerSelect(sel) {
    if (sel.dataset.bonito) return;
    sel.dataset.bonito = '1';

    const wrap = document.createElement('div');
    wrap.className = 'sel';
    sel.parentNode.insertBefore(wrap, sel);
    wrap.appendChild(sel);

    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'sel__boton';
    boton.innerHTML = '<span class="sel__texto"></span><i class="ti ti-chevron-down sel__flecha" aria-hidden="true"></i>';

    const lista = document.createElement('div');
    lista.className = 'sel__lista';

    wrap.append(boton, lista);

    boton.addEventListener('click', e => {
        e.stopPropagation();
        const estabaAbierto = wrap.classList.contains('abierto');
        cerrarSelects();
        if (!estabaAbierto) wrap.classList.add('abierto');
    });

    lista.addEventListener('click', e => {
        e.stopPropagation();
        const op = e.target.closest('.sel__opcion');
        if (!op) return;
        sel.value = op.dataset.value;
        sel.dispatchEvent(new Event('change'));
        wrap.classList.remove('abierto');
    });

    sel.addEventListener('change', () => sincronizarSelect(sel));
    // Si las opciones cambian (ej: horas del seguimiento), se redibuja sola
    new MutationObserver(() => sincronizarSelect(sel)).observe(sel, { childList: true });

    sincronizarSelect(sel);
}

/** Redibuja texto y opciones. Llamarlo si se cambia sel.value desde codigo. */
function sincronizarSelect(sel) {
    const wrap = sel.parentNode;
    if (!wrap || !wrap.classList.contains('sel')) return;

    const actual = sel.options[sel.selectedIndex];
    const texto = wrap.querySelector('.sel__texto');
    texto.textContent = actual ? actual.text : '';
    texto.classList.toggle('placeholder', !actual || actual.value === '');

    wrap.querySelector('.sel__lista').innerHTML = [...sel.options].map(o => `
        <div class="sel__opcion ${o.value === sel.value ? 'activa' : ''} ${o.value === '' ? 'vacia' : ''}"
             data-value="${esc(o.value)}">${esc(o.text)}</div>`).join('');
}

function cerrarSelects() {
    document.querySelectorAll('.sel.abierto').forEach(w => w.classList.remove('abierto'));
}

/* ---------- Mensajes en pantalla (mismo estilo que CU-07) ---------- */

function mostrarMensaje(texto, tipo) {
    const cont = document.getElementById('mensajeGlobal');
    cont.className = 'mensaje-caja ' + (tipo === 'error' ? 'mensaje-error' : 'mensaje-exito');
    cont.textContent = texto;
    cont.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    if (tipo !== 'error') {
        setTimeout(() => { cont.className = 'oculto'; }, 6000);
    }
}

/** Pinta cada error en su <p data-error="campo"> del formulario; si no existe, lo muestra arriba. */
function mostrarErrores(formId, errores) {
    const form = document.getElementById(formId);
    for (const campo in (errores || {})) {
        const el = form.querySelector(`[data-error="${campo}"]`);
        if (el) el.textContent = errores[campo];
        else mostrarMensaje(errores[campo], 'error');
    }
}

function limpiarErrores(formId) {
    document.querySelectorAll(`#${formId} [data-error]`).forEach(e => e.textContent = '');
}

function primerError(errores) {
    const valores = Object.values(errores || {});
    return valores.length ? valores[0] : 'Ocurrio un error.';
}

function esc(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML.replace(/"/g, '&quot;');
}

async function postJson(url, body) {
    const resp = await fetch(`${CTX}${url}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    return resp.json();
}

/* ---------- Paso 1: panel en tres secciones ---------- */

async function cargarPanel() {
    try {
        const resp = await fetch(`${CTX}medico/api/citas`);
        const data = await resp.json();

        citasPorId = {};
        [...data.enEspera, ...data.enConsulta, ...data.evaluados].forEach(c => citasPorId[c.citaId] = c);

        renderLista('listaEnEspera', 'countEnEspera', data.enEspera,
            'No hay pacientes en espera de consulta.', botonesEnEspera);
        renderLista('listaEnConsulta', 'countEnConsulta', data.enConsulta,
            'Ningun paciente en consulta ahora mismo.', botonesEnConsulta);
        renderLista('listaEvaluados', 'countEvaluados', data.evaluados,
            'No hay pacientes pendientes de cierre.', botonesEvaluados);
    } catch (e) {
        console.error('Error al cargar el panel', e);
    }
}

function renderLista(idLista, idContador, citas, textoVacio, botonesFn) {
    const cont = document.getElementById(idLista);
    const contador = document.getElementById(idContador);
    contador.textContent = citas.length;

    if (citas.length === 0) {
        cont.innerHTML = `<p class="sm-error" style="color:var(--sm-slate-400);">${textoVacio}</p>`;
        return;
    }

    cont.innerHTML = citas.map(c => `
        <div class="sm-panel" style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:10px;padding:14px 16px;">
            <div>
                <div style="font-weight:700;">${esc(c.pacienteNombre)}
                    ${c.esEmergencia ? '<span class="sm-badge sm-badge--danger">EMERGENCIA</span>' : ''}</div>
                <div style="font-size:12px;color:var(--sm-slate-500);">
                    ${esc(c.numeroCita)} &middot; ${esc(c.especialidad)} &middot; ${esc(c.fechaHora)} &middot; ${esc(c.estado)}
                </div>
                ${idLista === 'listaEvaluados' ? resumenEvaluado(c) : ''}
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end;">${botonesFn(c)}</div>
        </div>`).join('');
}

function resumenEvaluado(c) {
    return `<div style="font-size:12px;color:var(--sm-slate-500);margin-top:4px;">
                Ordenes de laboratorio: ${c.ordenes} &middot; Recetas: ${c.recetas} &middot; Seguimientos: ${c.seguimientos}
            </div>`;
}

function botonesEnEspera(c) {
    return `<button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="marcarNoAsistio(${c.citaId})">No Asistió</button>
            <button class="sm-btn sm-btn--primary sm-btn--sm" onclick="iniciarConsulta(${c.citaId})">Iniciar Consulta</button>`;
}

function botonesEnConsulta(c) {
    const noAsistio = c.tieneConsulta ? ''
        : `<button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="marcarNoAsistio(${c.citaId})">No Asistió</button>`;
    return `${noAsistio}
            <button class="sm-btn sm-btn--dark sm-btn--sm" onclick="abrirConsulta(${c.citaId})">Ver / Completar Consulta</button>`;
}

function botonesEvaluados(c) {
    return `<button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="abrirOrden(${c.citaId})">Orden de laboratorio</button>
            <button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="abrirReceta(${c.citaId})">Receta médica</button>
            <button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="abrirSeguimiento(${c.citaId})">Agendar Seguimiento</button>
            <button class="sm-btn sm-btn--primary sm-btn--sm" onclick="finalizarAtencion(${c.citaId})">Finalizar Atención</button>`;
}

function contextoDe(citaId) {
    const c = citasPorId[citaId];
    return c ? `${c.pacienteNombre} — ${c.numeroCita}` : '—';
}

/* ---------- Paso 2: Iniciar Consulta (con TTS) ---------- */

async function iniciarConsulta(citaId) {
    try {
        const data = await postJson('medico/api/iniciar', { citaId });
        if (data.ok) {
            anunciarPorVoz(data.anuncio);
            mostrarMensaje(data.mensaje, 'exito');
            cargarPanel();
        } else {
            mostrarMensaje(primerError(data.errores), 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al iniciar la consulta.', 'error');
    }
}

function anunciarPorVoz(texto) {
    if (!texto || !('speechSynthesis' in window)) return;
    const utterance = new SpeechSynthesisUtterance(texto);
    utterance.lang = 'es-GT';
    window.speechSynthesis.speak(utterance);
}

/* ---------- FA06: No Asistio ---------- */

async function marcarNoAsistio(citaId) {
    try {
        const data = await postJson('medico/api/no-asistio', { citaId });
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cargarPanel();
        } else {
            mostrarMensaje(primerError(data.errores), 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al marcar la cita como No Asistio.', 'error');
    }
}

/* ---------- Paso 3: abrir formulario de consulta ---------- */

async function abrirConsulta(citaId) {
    try {
        const resp = await fetch(`${CTX}medico/api/consulta/${citaId}`);
        const data = await resp.json();
        if (!data.ok) {
            mostrarMensaje(data.mensaje, 'error');
            return;
        }

        cerrarFormularios();
        citaConsulta = citaId;
        document.getElementById('consultaContexto').textContent =
            `${data.cita.pacienteNombre} — ${data.cita.numeroCita} · ${data.cita.especialidad}`;
        renderSignos(data.signos);

        const c = data.consulta;
        document.getElementById('inpMotivo').value = c?.motivoVisita ?? '';
        document.getElementById('inpHallazgos').value = c?.hallazgos ?? '';
        document.getElementById('inpDiagnostico').value = c?.diagnostico ?? '';
        document.getElementById('inpPlan').value = c?.planTratamiento ?? '';
        document.getElementById('inpNotas').value = c?.notas ?? '';

        const estado = document.getElementById('selEstadoConsulta');
        estado.value = '0';
        sincronizarSelect(estado);

        if (c?.cie10) {
            cie10Seleccionado = c.cie10.id;
            document.getElementById('inpCie10').value = `${c.cie10.codigo} - ${c.cie10.descripcion}`;
        }

        mostrarFormulario('formConsulta');
    } catch (e) {
        mostrarMensaje('Error al abrir la consulta.', 'error');
    }
}

/** Signos vitales de enfermeria (CU-07), solo lectura. */
function renderSignos(s) {
    const cont = document.getElementById('signosConsulta');
    if (!s) {
        cont.innerHTML = '<p class="sm-error" style="color:var(--sm-slate-400);">Sin signos vitales registrados.</p>';
        return;
    }
    let alertas = [];
    try { alertas = s.alertas ? JSON.parse(s.alertas) : []; } catch (e) { alertas = []; }

    const dato = (etiqueta, valor) => `
        <div class="sm-stat" style="padding:10px 12px;">
            <div class="sm-stat__label">${etiqueta}</div>
            <div style="font-size:15px;font-weight:700;">${esc(valor)}</div>
        </div>`;

    cont.innerHTML = `
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:10px;">
            ${dato('Presión arterial', s.presionArterial + ' mmHg')}
            ${dato('Temperatura', s.temperatura + ' °C')}
            ${dato('Frec. cardíaca', s.frecuenciaCardiaca + ' lpm')}
            ${dato('Peso', s.pesoKg + ' kg')}
            ${dato('Talla', s.tallaCm + ' cm')}
        </div>
        ${alertas.map(a => `<div class="sm-badge sm-badge--warning" style="display:block;margin-top:6px;">${esc(a)}</div>`).join('')}`;
}

/* ---------- Paso 7: autocompletado CIE-10 ---------- */

function buscarCie10() {
    cie10Seleccionado = null; // si vuelve a escribir, se pierde la seleccion anterior
    clearTimeout(timerCie10);
    timerCie10 = setTimeout(async () => {
        const q = document.getElementById('inpCie10').value.trim();
        const cont = document.getElementById('sugerenciasCie10');
        if (q.length < 1) {
            cont.innerHTML = '';
            return;
        }
        try {
            const resp = await fetch(`${CTX}medico/api/cie10?q=${encodeURIComponent(q)}`);
            sugerenciasCie10 = await resp.json();
            cont.innerHTML = sugerenciasCie10.length === 0
                ? '<div class="cie-item" style="color:var(--sm-slate-400);">Sin coincidencias.</div>'
                : sugerenciasCie10.map((s, i) =>
                    `<div class="cie-item" onclick="seleccionarCie10(${i})"><b>${esc(s.codigo)}</b> — ${esc(s.descripcion)}</div>`).join('');
        } catch (e) { /* silencioso: es solo una ayuda */ }
    }, 250);
}

function seleccionarCie10(indice) {
    const s = sugerenciasCie10[indice];
    cie10Seleccionado = s.id;
    document.getElementById('inpCie10').value = `${s.codigo} - ${s.descripcion}`;
    document.getElementById('sugerenciasCie10').innerHTML = '';
}

/* ---------- Pasos 4-10 + FA05: guardar consulta ---------- */

async function guardarConsulta() {
    limpiarErrores('formConsulta');

    const body = {
        citaId: citaConsulta,
        motivoVisita: document.getElementById('inpMotivo').value,
        hallazgos: document.getElementById('inpHallazgos').value,
        cie10Id: cie10Seleccionado,
        diagnostico: document.getElementById('inpDiagnostico').value,
        planTratamiento: document.getElementById('inpPlan').value,
        notas: document.getElementById('inpNotas').value,
        finalizar: document.getElementById('selEstadoConsulta').value === '1'
    };

    try {
        const data = await postJson('medico/api/consulta', body);
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormularios();
            cargarPanel();
        } else {
            mostrarErrores('formConsulta', data.errores);
        }
    } catch (e) {
        mostrarMensaje('Error al guardar la consulta.', 'error');
    }
}

/* ---------- FA01: orden de laboratorio ---------- */

async function abrirOrden(citaId) {
    try {
        if (!catalogoExamenes) {
            const resp = await fetch(`${CTX}medico/api/examenes`);
            catalogoExamenes = await resp.json();
        }
        cerrarFormularios();
        citaEvaluada = citaId;
        document.getElementById('ordenContexto').textContent = contextoDe(citaId);
        document.getElementById('listaExamenes').innerHTML = catalogoExamenes.map(e => `
            <label class="examen-chip">
                <input type="checkbox" class="chk-examen" value="${e.id}">
                <span style="flex:1;">${esc(e.nombre)}</span>
                <span style="color:var(--sm-slate-400);">Q${Number(e.precio).toFixed(2)}</span>
            </label>`).join('');
        mostrarFormulario('formOrden');
    } catch (e) {
        mostrarMensaje('Error al cargar el catalogo de examenes.', 'error');
    }
}

async function guardarOrden() {
    limpiarErrores('formOrden');
    const examenIds = [...document.querySelectorAll('.chk-examen:checked')].map(c => Number(c.value));
    const body = {
        citaId: citaEvaluada,
        examenIds,
        observaciones: document.getElementById('inpObsOrden').value,
        esExterna: document.getElementById('chkOrdenExterna').checked   // [CU-09 FA01]
    };
    try {
        const data = await postJson('medico/api/orden-laboratorio', body);
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormularios();
            cargarPanel();
        } else {
            mostrarErrores('formOrden', data.errores);
        }
    } catch (e) {
        mostrarMensaje('Error al generar la orden de laboratorio.', 'error');
    }
}

/* ---------- FA04: receta medica ---------- */

async function abrirReceta(citaId) {
    try {
        if (!catalogoMedicamentos) {
            const resp = await fetch(`${CTX}medico/api/medicamentos`);
            catalogoMedicamentos = await resp.json();
        }
        cerrarFormularios();
        citaEvaluada = citaId;
        document.getElementById('recetaContexto').textContent = contextoDe(citaId);
        document.getElementById('itemsReceta').innerHTML = '';
        agregarItemReceta();
        mostrarFormulario('formReceta');
    } catch (e) {
        mostrarMensaje('Error al cargar el catalogo de medicamentos.', 'error');
    }
}

function agregarItemReceta() {
    const opciones = catalogoMedicamentos
        .map(m => `<option value="${m.id}">${esc(m.nombre)} (${esc(m.unidad)})</option>`).join('');

    const div = document.createElement('div');
    div.className = 'receta-item sm-panel';
    div.style.cssText = 'padding:14px;margin-bottom:10px;';
    div.innerHTML = `
        <div style="display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px;">
            <select class="sm-select it-medicamento"><option value="">Seleccione medicamento...</option>${opciones}</select>
            <input class="sm-input it-dosis" placeholder="Dosis (ej: 500 mg)">
            <input class="sm-input it-frecuencia" placeholder="Frecuencia (ej: cada 8 h)">
            <input class="sm-input it-duracion" placeholder="Duración (ej: 7 días)">
            <input class="sm-input it-cantidad" type="number" min="1" placeholder="Cantidad">
            <input class="sm-input it-indicaciones" placeholder="Indicaciones">
        </div>
        <button class="sm-btn sm-btn--ghost sm-btn--sm" style="margin-top:8px;" onclick="this.closest('.receta-item').remove()">Quitar</button>`;
    document.getElementById('itemsReceta').appendChild(div);
    embellecerSelect(div.querySelector('.it-medicamento'));
}

async function guardarReceta() {
    limpiarErrores('formReceta');
    const items = [...document.querySelectorAll('#itemsReceta .receta-item')].map(it => {
        const cant = it.querySelector('.it-cantidad').value;
        const med = it.querySelector('.it-medicamento').value;
        return {
            medicamentoId: med ? Number(med) : null,
            dosis: it.querySelector('.it-dosis').value,
            frecuencia: it.querySelector('.it-frecuencia').value,
            duracion: it.querySelector('.it-duracion').value,
            cantidad: cant === '' ? null : Number(cant),
            indicaciones: it.querySelector('.it-indicaciones').value
        };
    });

    try {
        const data = await postJson('medico/api/receta', {
            citaId: citaEvaluada,
            items,
            notas: document.getElementById('inpNotasReceta').value
        });
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormularios();
            cargarPanel();
        } else {
            mostrarErrores('formReceta', data.errores);
        }
    } catch (e) {
        mostrarMensaje('Error al generar la receta.', 'error');
    }
}

/* ---------- FA02: cita de seguimiento ---------- */

function hoyLocal() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 10);
}

function abrirSeguimiento(citaId) {
    cerrarFormularios();
    citaEvaluada = citaId;
    document.getElementById('seguimientoContexto').textContent = contextoDe(citaId);
    const fecha = document.getElementById('inpFechaSeg');
    fecha.min = hoyLocal();
    fecha.value = '';
    document.getElementById('selHoraSeg').innerHTML = '<option value="">Primero elija una fecha</option>';
    mostrarFormulario('formSeguimiento');
}

async function cargarHorarios() {
    const fecha = document.getElementById('inpFechaSeg').value;
    const sel = document.getElementById('selHoraSeg');
    if (!fecha) return;
    try {
        const resp = await fetch(`${CTX}medico/api/horarios?citaId=${citaEvaluada}&fecha=${fecha}`);
        const horas = await resp.json();
        sel.innerHTML = horas.length === 0
            ? '<option value="">Sin horarios disponibles ese día</option>'
            : '<option value="">Seleccione hora...</option>' + horas.map(h => `<option value="${h}">${h}</option>`).join('');
    } catch (e) {
        sel.innerHTML = '<option value="">Error al cargar horarios</option>';
    }
}

async function guardarSeguimiento() {
    limpiarErrores('formSeguimiento');
    const tipo = document.getElementById('selTipoSeg').value;
    const body = {
        citaId: citaEvaluada,
        fecha: document.getElementById('inpFechaSeg').value,
        hora: document.getElementById('selHoraSeg').value,
        tipo: tipo === '' ? null : Number(tipo)
    };
    try {
        const data = await postJson('medico/api/seguimiento', body);
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormularios();
            cargarPanel();
        } else {
            mostrarErrores('formSeguimiento', data.errores);
        }
    } catch (e) {
        mostrarMensaje('Error al agendar el seguimiento.', 'error');
    }
}

/* ---------- Pasos 11-12: Finalizar Atencion ---------- */

async function finalizarAtencion(citaId) {
    try {
        const data = await postJson('medico/api/finalizar-atencion', { citaId });
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            cerrarFormularios();
            cargarPanel();
        } else {
            mostrarMensaje(primerError(data.errores), 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al finalizar la atencion.', 'error');
    }
}

/* ---------- Mostrar / cerrar formularios ---------- */

function mostrarFormulario(id) {
    const form = document.getElementById(id);
    limpiarErrores(id);
    form.classList.remove('oculto');
    form.scrollIntoView({ behavior: 'smooth' });
}

function cerrarFormularios() {
    ['formConsulta', 'formOrden', 'formReceta', 'formSeguimiento'].forEach(id => {
        const form = document.getElementById(id);
        form.classList.add('oculto');
        form.querySelectorAll('input, textarea').forEach(i => {
            if (i.type === 'checkbox') i.checked = false; else i.value = '';
        });
        form.querySelectorAll('select').forEach(s => {
            s.selectedIndex = 0;
            sincronizarSelect(s);
        });
        limpiarErrores(id);
    });
    cerrarSelects();
    document.getElementById('sugerenciasCie10').innerHTML = '';
    citaConsulta = null;
    citaEvaluada = null;
    cie10Seleccionado = null;
}