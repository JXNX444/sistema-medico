/* ============================================================
   CU-09 Gestion de Laboratorio
   Una sola pagina con dos vistas:
     - vistaLista:   tabla de ordenes con filtros       [paso 1]
     - vistaDetalle: examenes de una orden               [pasos 2-14, FA01, FA02]

   SOLO_LECTURA (lo define panel.html): true si entro un medico.
   En ese modo no se dibujan formularios ni botones, y los examenes
   sin publicar se muestran como "Pendiente de publicacion".
   ============================================================ */

const PENDIENTE = 0;
const EN_PROCESO = 1;
const COMPLETADA = 2;

let ordenActual = null;         // id de la orden abierta en el detalle
let editando = new Set();       // examenes guardados que el usuario quiere editar
let timerFiltro = null;

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('filtroEstado').addEventListener('change', cargarOrdenes);
    document.getElementById('filtroPaciente').addEventListener('input', filtrarConPausa);
    document.getElementById('filtroMedico').addEventListener('input', filtrarConPausa);

    cargarOrdenes();

    // La tabla se actualiza sola cada 30 s (por ejemplo, cuando caja cobra una orden)
    setInterval(() => {
        if (ordenActual === null) cargarOrdenes();
    }, 30000);
});

/* ---------- Helpers ---------- */

function esc(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML.replace(/"/g, '&quot;');
}

function quetzales(monto) {
    return 'Q' + Number(monto || 0).toFixed(2);
}

/** Fecha de hoy en formato yyyy-MM-dd (lo que usa <input type="date">). */
function hoy() {
    const d = new Date();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
}

/** yyyy-MM-dd -> dd/MM/yyyy */
function fechaBonita(iso) {
    if (!iso) return '';
    const [a, m, d] = iso.split('-');
    return `${d}/${m}/${a}`;
}

function badgeEstado(estado, nombre) {
    const clase = estado === PENDIENTE ? 'sm-badge--warning'
        : estado === EN_PROCESO ? 'sm-badge--info'
            : 'sm-badge--success';
    return `<span class="sm-badge ${clase}">${esc(nombre)}</span>`;
}

function mostrarMensaje(texto, tipo) {
    const cont = document.getElementById('mensajeGlobal');
    cont.className = 'mensaje-caja ' + (tipo === 'error' ? 'mensaje-error' : 'mensaje-exito');
    cont.textContent = texto;
    cont.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    if (tipo !== 'error') {
        setTimeout(() => { cont.className = 'oculto'; }, 6000);
    }
}

function ocultarMensaje() {
    document.getElementById('mensajeGlobal').className = 'oculto';
}

async function postJson(url, body) {
    const resp = await fetch(`${CTX}${url}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    return resp.json();
}

/* ---------- Paso 1: tabla de ordenes con filtros ---------- */

/** Espera a que el usuario deje de escribir para no consultar en cada tecla. */
function filtrarConPausa() {
    clearTimeout(timerFiltro);
    timerFiltro = setTimeout(cargarOrdenes, 300);
}

async function cargarOrdenes() {
    const estado = document.getElementById('filtroEstado').value;
    const paciente = document.getElementById('filtroPaciente').value;
    const medico = document.getElementById('filtroMedico').value;

    const params = new URLSearchParams();
    if (estado !== '') params.append('estado', estado);
    if (paciente.trim() !== '') params.append('paciente', paciente.trim());
    // El medico solo ve sus ordenes: no se manda el filtro por medico
    if (!SOLO_LECTURA && medico.trim() !== '') params.append('medico', medico.trim());

    try {
        const resp = await fetch(`${CTX}laboratorio/api/ordenes?${params}`);
        const ordenes = await resp.json();
        renderTabla(ordenes);
    } catch (e) {
        mostrarMensaje('No se pudo cargar la lista de órdenes.', 'error');
    }
}

function renderTabla(ordenes) {
    document.getElementById('countOrdenes').textContent = ordenes.length;
    const cuerpo = document.getElementById('cuerpoOrdenes');

    if (ordenes.length === 0) {
        cuerpo.innerHTML = `<tr><td colspan="6" class="lab-vacio">
            No hay órdenes que coincidan con los filtros.</td></tr>`;
        return;
    }

    cuerpo.innerHTML = ordenes.map(o => `
        <tr class="lab-fila" onclick="abrirOrden(${o.ordenId})">
            <td><b>${esc(o.numeroOrden)}</b>
                ${o.esExterna ? '<span class="lab-tag">Externa</span>' : ''}</td>
            <td>${esc(o.pacienteNombre)}<div class="lab-sub">DPI ${esc(o.pacienteDpi)}</div></td>
            <td>${esc(o.medicoNombre)}</td>
            <td>${esc(o.fecha)}</td>
            <td>${badgeEstado(o.estado, o.estadoNombre)}</td>
            <td style="text-align:right;">${quetzales(o.montoTotal)}</td>
        </tr>`).join('');
}

/* ---------- Pasos 2-3: detalle de la orden ---------- */

async function abrirOrden(ordenId) {
    try {
        const resp = await fetch(`${CTX}laboratorio/api/orden/${ordenId}`);
        const data = await resp.json();
        if (!data.ok) {
            mostrarMensaje(data.mensaje, 'error');
            return;
        }

        if (ordenActual !== ordenId) {
            editando = new Set();   // al cambiar de orden se olvidan las ediciones abiertas
            ocultarMensaje();
        }
        ordenActual = ordenId;

        renderDetalle(data.orden, data.examenes);
        document.getElementById('vistaLista').classList.add('oculto');
        document.getElementById('vistaDetalle').classList.remove('oculto');
    } catch (e) {
        mostrarMensaje('No se pudo cargar la orden.', 'error');
    }
}

function volverALista() {
    ordenActual = null;
    editando = new Set();
    ocultarMensaje();
    document.getElementById('vistaDetalle').classList.add('oculto');
    document.getElementById('vistaLista').classList.remove('oculto');
    cargarOrdenes();
}

function renderDetalle(orden, examenes) {
    // Encabezado
    document.getElementById('detalleTitulo').innerHTML =
        `${esc(orden.numeroOrden)} ${badgeEstado(orden.estado, orden.estadoNombre)}
         ${orden.esExterna ? '<span class="lab-tag">Externa</span>' : ''}`;

    document.getElementById('detalleContexto').textContent =
        `${orden.pacienteNombre} · DPI ${orden.pacienteDpi} · ${orden.medicoNombre} · ${orden.fecha}`;

    const notas = document.getElementById('detalleNotas');
    notas.textContent = orden.notas ? `Notas del médico: ${orden.notas}` : '';

    const publicados = examenes.filter(e => e.publicado).length;
    document.getElementById('detalleResumen').innerHTML =
        `<span>Total: <b>${quetzales(orden.montoTotal)}</b></span>
         <span class="lab-sub">Publicados: ${publicados} de ${examenes.length}</span>`;

    // Aviso segun el estado de la orden (el texto cambia si es el medico quien consulta)
    const aviso = document.getElementById('detalleAviso');
    if (orden.esExterna) {
        // FA01
        aviso.className = 'lab-aviso lab-aviso--gris';
        aviso.textContent = 'Orden externa: el paciente realiza estos exámenes en un laboratorio externo '
            + 'y presenta los resultados al médico en su cita de seguimiento.';
    } else if (orden.estado === PENDIENTE) {
        // Pasos 4-5 + RN-CU09-01
        aviso.className = 'lab-aviso lab-aviso--amarillo';
        aviso.textContent = SOLO_LECTURA
            ? 'La orden aún no ha sido pagada en caja: los exámenes todavía no se han procesado.'
            : `La orden aún no ha sido pagada en caja. Informe al paciente el monto total `
            + `(${quetzales(orden.montoTotal)}): el pago es requerido antes de la toma de muestras.`;
    } else if (orden.estado === COMPLETADA) {
        // Paso 14
        aviso.className = 'lab-aviso lab-aviso--verde';
        aviso.textContent = 'Orden completada: todos los resultados fueron publicados.';
    } else {
        aviso.className = 'oculto';
    }

    // Solo se registran resultados si NO es el medico, la orden esta En proceso y no es externa
    const sePuedeOperar = !SOLO_LECTURA && orden.estado === EN_PROCESO && !orden.esExterna;

    document.getElementById('listaExamenes').innerHTML =
        examenes.map(ex => renderExamen(ex, sePuedeOperar)).join('');
}

/* ---------- Cada examen: 3 estados posibles ---------- */

function renderExamen(ex, sePuedeOperar) {
    const encabezado = `
        <div class="lab-ex__titulo">${esc(ex.examenNombre)}
            <span class="lab-sub">· ${quetzales(ex.monto)}</span></div>`;

    // FA02: alerta roja con el rango de referencia si esta configurado
    const badgeRango = ex.fueraRango
        ? `<span class="sm-badge sm-badge--danger">Fuera de rango${ex.rangoReferencia ? ' (' + esc(ex.rangoReferencia) + ')' : ''}</span>`
        : '';

    // Estado 3: publicado (solo lectura, RNF-024). Igual para laboratorio y medico.
    if (ex.publicado) {
        return `
        <div class="sm-card lab-ex">
            <div class="lab-ex__fila">${encabezado}
                <div class="lab-ex__badges">${badgeRango}
                    <span class="sm-badge sm-badge--success">Publicado</span></div>
            </div>
            ${resumenResultado(ex)}
            <div class="lab-sub">Publicado el ${esc(ex.publicadoEn)}</div>
        </div>`;
    }

    // Modo medico: un resultado sin publicar es un borrador del laboratorio,
    // no se muestra (el servidor tampoco lo envia)
    if (SOLO_LECTURA) {
        const rangoMedico = ex.rangoReferencia
            ? `<div class="lab-sub">Rango de referencia: ${esc(ex.rangoReferencia)}</div>` : '';
        return `
        <div class="sm-card lab-ex">
            <div class="lab-ex__fila">${encabezado}
                <span class="sm-badge lab-badge-gris">Pendiente de publicación</span></div>
            <div class="lab-sub" style="margin-top:4px;">El resultado aún no ha sido publicado por laboratorio.</div>
            ${rangoMedico}
        </div>`;
    }

    // Estado 2: guardado y sin publicar
    if (ex.guardado && !editando.has(ex.detalleId)) {
        const botones = sePuedeOperar ? `
            <div class="lab-ex__botones">
                <button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="editarResultado(${ex.detalleId})">Editar resultado</button>
                <button class="sm-btn sm-btn--primary sm-btn--sm" onclick="publicarResultado(${ex.detalleId})">Publicar resultado</button>
            </div>` : '';
        return `
        <div class="sm-card lab-ex">
            <div class="lab-ex__fila">${encabezado}
                <div class="lab-ex__badges">${badgeRango}
                    <span class="sm-badge sm-badge--warning">Guardado, sin publicar</span></div>
            </div>
            ${resumenResultado(ex)}
            ${botones}
        </div>`;
    }

    // Estado 1: sin resultado (o editando uno guardado)
    const rango = ex.rangoReferencia
        ? `<div class="lab-sub" style="margin-bottom:10px;">Rango de referencia: ${esc(ex.rangoReferencia)}</div>` : '';

    if (!sePuedeOperar) {
        return `
        <div class="sm-card lab-ex">
            <div class="lab-ex__fila">${encabezado}
                <span class="sm-badge lab-badge-gris">Sin resultado</span></div>
            ${rango}
        </div>`;
    }

    // La unidad solo es obligatoria si el examen la tiene en el catalogo
    // (ej: una radiografia no tiene unidad). Debe coincidir con el service.
    const unidadObligatoria = !!(ex.unidadSugerida && ex.unidadSugerida.trim() !== '');
    const etiquetaUnidad = unidadObligatoria ? 'Unidad *' : 'Unidad';
    const placeholderUnidad = unidadObligatoria ? '' : 'Opcional';

    const id = ex.detalleId;
    const esEdicion = editando.has(id);
    return `
    <div class="sm-card lab-ex" id="examen-${id}">
        <div class="lab-ex__fila">${encabezado}
            <span class="sm-badge lab-badge-gris">${esEdicion ? 'Editando' : 'Sin resultado'}</span></div>
        ${rango}
        <div class="lab-grid">
            <div class="sm-field">
                <label>Valor del resultado *</label>
                <input id="valor-${id}" class="sm-input" maxlength="200" value="${esc(ex.valor)}">
                <p class="sm-error" data-error="valor-${id}"></p>
            </div>
            <div class="sm-field">
                <label>${etiquetaUnidad}</label>
                <input id="unidad-${id}" class="sm-input" maxlength="50" placeholder="${placeholderUnidad}"
                       value="${esc(ex.unidad || ex.unidadSugerida)}">
                <p class="sm-error" data-error="unidad-${id}"></p>
            </div>
            <div class="sm-field">
                <label>Fecha del resultado *</label>
                <input id="fecha-${id}" type="date" class="sm-input" max="${hoy()}" value="${esc(ex.fecha || hoy())}">
                <p class="sm-error" data-error="fecha-${id}"></p>
            </div>
        </div>
        <div class="sm-field" style="margin-top:10px;">
            <label>Notas del resultado</label>
            <textarea id="notas-${id}" class="sm-textarea" rows="2" maxlength="1000">${esc(ex.notas)}</textarea>
            <p class="sm-error" data-error="notas-${id}"></p>
        </div>
        <label class="lab-check">
            <input id="fuera-${id}" type="checkbox" ${ex.fueraRango ? 'checked' : ''}> Fuera de rango
        </label>
        <div class="lab-ex__botones">
            ${esEdicion ? `<button class="sm-btn sm-btn--ghost sm-btn--sm" onclick="cancelarEdicion(${id})">Cancelar</button>` : ''}
            <button class="sm-btn sm-btn--primary sm-btn--sm" onclick="guardarResultado(${id})">Guardar resultado</button>
        </div>
    </div>`;
}

function resumenResultado(ex) {
    const unidad = ex.unidad ? ' ' + esc(ex.unidad) : '';
    return `<div class="lab-resultado">
        <b>${esc(ex.valor)}${unidad}</b> · ${fechaBonita(ex.fecha)}
        ${ex.notas ? `<div class="lab-sub">${esc(ex.notas)}</div>` : ''}
    </div>`;
}

function editarResultado(detalleId) {
    editando.add(detalleId);
    abrirOrden(ordenActual);
}

function cancelarEdicion(detalleId) {
    editando.delete(detalleId);
    abrirOrden(ordenActual);
}

/* ---------- Pasos 9-10 + FA02: guardar resultado ---------- */

async function guardarResultado(detalleId) {
    // Limpiar errores anteriores de este examen
    document.querySelectorAll(`#examen-${detalleId} .sm-error`).forEach(p => p.textContent = '');

    const body = {
        detalleId: detalleId,
        valor: document.getElementById(`valor-${detalleId}`).value,
        unidad: document.getElementById(`unidad-${detalleId}`).value,
        fecha: document.getElementById(`fecha-${detalleId}`).value || null,
        fueraRango: document.getElementById(`fuera-${detalleId}`).checked,
        notas: document.getElementById(`notas-${detalleId}`).value
    };

    try {
        const data = await postJson('laboratorio/api/resultado', body);
        if (!data.ok) {
            mostrarErroresExamen(detalleId, data.errores);
            return;
        }
        editando.delete(detalleId);
        await abrirOrden(ordenActual);
        mostrarMensaje(data.mensaje, 'exito');
    } catch (e) {
        mostrarMensaje('No se pudo guardar el resultado.', 'error');
    }
}

/** Cada error va debajo de su campo; "general" va arriba en el mensaje. */
function mostrarErroresExamen(detalleId, errores) {
    Object.entries(errores || {}).forEach(([campo, texto]) => {
        const p = document.querySelector(`[data-error="${campo}-${detalleId}"]`);
        if (p) {
            p.textContent = texto;
        } else {
            mostrarMensaje(texto, 'error');
        }
    });
}

/* ---------- Pasos 11-14: publicar resultado ---------- */

async function publicarResultado(detalleId) {
    try {
        const data = await postJson('laboratorio/api/publicar', { detalleId: detalleId });
        if (!data.ok) {
            mostrarMensaje(Object.values(data.errores)[0], 'error');
            return;
        }
        await abrirOrden(ordenActual);
        mostrarMensaje(data.mensaje, 'exito');
    } catch (e) {
        mostrarMensaje('No se pudo publicar el resultado.', 'error');
    }
}