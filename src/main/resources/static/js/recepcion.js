/* ============================================================
   CU-05 Recepcion y Verificacion de Cita
   ============================================================ */

let modoActual = 'dpi'; // 'dpi' | 'numero'
let ultimoDpiBuscado = '';

/* Listado de citas del paciente (busqueda por DPI): filtro por estado + paginacion */
const CITAS_POR_PAGINA = 5;
let citasActuales = [];   // todas las citas que devolvio la busqueda
let filtroEstado = '';    // '' = todos los estados
let paginaActual = 1;

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btnModoDpi').addEventListener('click', () => cambiarModo('dpi'));
    document.getElementById('btnModoNumero').addEventListener('click', () => cambiarModo('numero'));
    document.getElementById('btnBuscar').addEventListener('click', buscar);
    document.getElementById('campoValor').addEventListener('keydown', e => {
        if (e.key === 'Enter') buscar();
    });
});

// FB1-3 FA02
function cambiarModo(modo) {
    modoActual = modo;
    document.getElementById('btnModoDpi').classList.toggle('activa', modo === 'dpi');
    document.getElementById('btnModoNumero').classList.toggle('activa', modo === 'numero');

    const campo = document.getElementById('campoValor');
    campo.placeholder = modo === 'dpi' ? '1234567890101' : 'CITA-2026-00042';
    campo.value = '';
    ocultarResultado();
}

/* ---------- RN-CU05-01: buscar por DPI o numero de cita ---------- */

// FB1-3
async function buscar() {
    const valor = document.getElementById('campoValor').value.trim();
    const error = document.getElementById('errorBusqueda');
    error.textContent = '';
    ocultarResultado();

    if (modoActual === 'dpi') ultimoDpiBuscado = valor;

    document.getElementById('cargando').classList.remove('oculto');

    try {
        const resp = await fetch(`${CTX}recepcion/api/buscar?modo=${modoActual}&valor=${encodeURIComponent(valor)}`);
        const data = await resp.json();
        document.getElementById('cargando').classList.add('oculto');
        renderResultado(data);
    } catch (e) {
        document.getElementById('cargando').classList.add('oculto');
        error.textContent = 'Error al buscar. Intente nuevamente.';
    }
}

function ocultarResultado() {
    document.getElementById('zonaResultado').innerHTML = '';
}

// FB4-5
function renderResultado(data) {
    const zona = document.getElementById('zonaResultado');
    const error = document.getElementById('errorBusqueda');

    switch (data.tipo) {
        case 'SIN_PARAMETROS':
        case 'SIN_RESULTADOS':
            error.textContent = data.mensaje;
            break;

        // FA03
        case 'PACIENTE_NO_EXISTE':
            zona.innerHTML = `
<div class="tarjeta-resultado">
<p class="resultado-nombre">${data.mensaje}</p>
<p class="resultado-subtitulo" style="margin-bottom:16px;">${data.subMensaje}</p>
<a href="${CTX}registro" class="boton-principal">
<i class="ti ti-user-plus"></i> Registrar Paciente
</a>
</div>`;
            break;

        // FA04
        case 'SIN_CITAS':
            zona.innerHTML = `
<div class="tarjeta-resultado">
<p class="resultado-nombre">${data.mensaje}</p>
<p class="resultado-subtitulo" style="margin-bottom:16px;">${data.subMensaje}</p>
<button class="boton-principal" onclick="mostrarFormularioWalkIn()">
<i class="ti ti-calendar-plus"></i> Nueva Cita (Walk-in)
</button>
            ${botonEmergencia()}
</div>`;
            break;

        case 'CITA_ENCONTRADA':
            zona.innerHTML = tarjetaCita(data.cita);
            break;

        case 'CITAS_ENCONTRADAS':
            citasActuales = data.citas;
            filtroEstado = '';
            paginaActual = 1;
            zona.innerHTML =
                botonEmergencia() +
                barraFiltroEstado() +
                `<div id="listaCitas"></div>`;
            renderListaCitas();
            break;
    }
}

/* ---------- Filtro por estado y paginacion (5 citas por pagina) ---------- */

/** Lista desplegable con los estados que traen las citas encontradas (sin repetir). */
function barraFiltroEstado() {
    const estados = [];
    citasActuales.forEach(c => {
        if (!estados.some(e => e.codigo === c.estadoCodigo)) {
            estados.push({ codigo: c.estadoCodigo, nombre: c.estadoNombre });
        }
    });
    const opciones = estados
        .map(e => `<option value="${e.codigo}">${e.nombre}</option>`)
        .join('');
    return `
<div class="filtro-citas">
<p class="resultado-subtitulo" id="conteoCitas"></p>
<div class="filtro-citas__control">
<label class="etiqueta-campo" for="filtroEstadoCita">Estado</label>
<select id="filtroEstadoCita" onchange="cambiarFiltroEstado(this.value)">
<option value="">Todos</option>
            ${opciones}
</select>
</div>
</div>`;
}

function cambiarFiltroEstado(codigo) {
    filtroEstado = codigo;
    paginaActual = 1;
    renderListaCitas();
}

function irAPagina(pagina) {
    paginaActual = pagina;
    renderListaCitas();
    document.getElementById('zonaResultado').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/** Dibuja solo las tarjetas de la pagina actual y los botones de pagina. */
// FB4-5
function renderListaCitas() {
    const lista = document.getElementById('listaCitas');
    if (!lista) return;

    const filtradas = filtroEstado
        ? citasActuales.filter(c => c.estadoCodigo === filtroEstado)
        : citasActuales;

    const totalPaginas = Math.max(1, Math.ceil(filtradas.length / CITAS_POR_PAGINA));
    if (paginaActual > totalPaginas) paginaActual = totalPaginas;

    const inicio = (paginaActual - 1) * CITAS_POR_PAGINA;
    const pagina = filtradas.slice(inicio, inicio + CITAS_POR_PAGINA);

    const conteo = document.getElementById('conteoCitas');
    if (conteo) {
        conteo.textContent = filtroEstado
            ? `${filtradas.length} de ${citasActuales.length} cita(s) con este estado`
            : `${citasActuales.length} cita(s) encontrada(s)`;
    }

    if (pagina.length === 0) {
        lista.innerHTML = `<div class="resultado-mensaje tono-ambar">No hay citas con ese estado.</div>`;
        return;
    }

    lista.innerHTML = pagina.map(tarjetaCita).join('') + paginador(totalPaginas);
}

function paginador(totalPaginas) {
    if (totalPaginas <= 1) return '';
    let botones = '';
    for (let p = 1; p <= totalPaginas; p++) {
        botones += `<button type="button" class="paginador__num${p === paginaActual ? ' is-active' : ''}"
            onclick="irAPagina(${p})">${p}</button>`;
    }
    return `
<div class="paginador">
<button type="button" class="paginador__num" ${paginaActual === 1 ? 'disabled' : ''}
            onclick="irAPagina(${paginaActual - 1})">&lsaquo;</button>
        ${botones}
<button type="button" class="paginador__num" ${paginaActual === totalPaginas ? 'disabled' : ''}
            onclick="irAPagina(${paginaActual + 1})">&rsaquo;</button>
<span class="paginador__texto">Pagina ${paginaActual} de ${totalPaginas}</span>
</div>`;
}

/* ---------- Arma el HTML de una tarjeta de cita, segun su estado y su fecha ---------- */

// FB4-5
function tarjetaCita(c) {
    let mensaje = '';
    let accion = '';

    // FA05
    if (c.estadoCodigo === 'PENDIENTE_PAGO') {
        mensaje = `<div class="resultado-mensaje tono-ambar">
            La cita del paciente tiene estado "Pendiente de pago". Debe realizar el pago en caja antes de ser atendido.
</div>`;
    // FA06
    } else if (c.estadoCodigo === 'CANCELADA') {
        const motivo = c.motivoEstado || 'La cita fue cancelada.';
        mensaje = `<div class="resultado-mensaje tono-rojo">${motivo}</div>`;
        accion = `<button class="boton-secundario" onclick="mostrarFormularioWalkIn()">Nueva Cita</button>`;
    } else if (c.estadoCodigo === 'NO_ASISTIO') {
        const motivo = c.motivoEstado || 'El paciente no se presento a esta cita.';
        mensaje = `<div class="resultado-mensaje tono-rojo">${motivo}</div>`;
        accion = `<button class="boton-secundario" onclick="mostrarFormularioWalkIn()">Nueva Cita</button>`;
    } else if (c.estadoCodigo === 'PACIENTE_PRESENTE') {
        // FA08: si es emergencia ya esta de primero en la fila de enfermeria (prioridad 2)
        mensaje = c.esEmergencia
            ? `<div class="resultado-mensaje tono-rojo">
            Paciente con prioridad de <strong>EMERGENCIA</strong>: debe pasar directamente a toma de signos vitales.
            Ya esta de primero en la fila de enfermeria.
</div>`
            : `<div class="resultado-mensaje tono-teal">
            La llegada de este paciente ya fue registrada. El paciente debe pasar a la sala de espera.
</div>`;
    } else if (c.estadoCodigo === 'CONFIRMADA' && !c.esFechaHoy) {
        mensaje = `<div class="resultado-mensaje tono-ambar">
            Esta cita esta programada para el <strong>${c.fechaHora}</strong>, que no es hoy.
            Si el paciente necesita ser atendido hoy, puede reprogramar la cita para hoy.
</div>`;
        accion = `
<div style="display:flex;gap:10px;flex-wrap:wrap;">
<button class="boton-secundario" style="flex:1;" onclick="reprogramarHoy(${c.citaId}, false)">
<i class="ti ti-calendar-repeat"></i> Reprogramar para hoy
</button>
<button class="boton-principal" style="flex:1;background:var(--sm-danger);" onclick="reprogramarHoy(${c.citaId}, true)">
<i class="ti ti-alert-triangle"></i> Reprogramar como emergencia
</button>
</div>`;
    } else if (c.estadoCodigo === 'CONFIRMADA' && c.esFechaHoy) {
        accion = `<button class="boton-principal" onclick="registrarLlegada(${c.citaId})">
<i class="ti ti-check"></i> Registrar llegada
</button>`;
        // FA08: boton adicional para pasar directo a signos vitales [CU-07].
        // Registra la llegada y, como la cita tiene prioridad 2, enfermeria la ve de primero.
        if (c.esEmergencia) {
            accion = `
<div style="display:flex;gap:10px;flex-wrap:wrap;">
<button class="boton-principal" style="flex:1;" onclick="registrarLlegada(${c.citaId})">
<i class="ti ti-check"></i> Registrar llegada
</button>
<button class="boton-principal" style="flex:1;background:var(--sm-danger);" onclick="registrarLlegada(${c.citaId})">
<i class="ti ti-heartbeat"></i> Signos Vitales (Urgente)
</button>
</div>`;
        }
    }

    // FA07: reasignar medico en citas Confirmadas o con Paciente Presente
    if (c.estadoCodigo === 'CONFIRMADA' || c.estadoCodigo === 'PACIENTE_PRESENTE') {
        accion += `
<button class="boton-secundario" style="margin-top:10px;" onclick="mostrarReasignacion(${c.citaId})">
<i class="ti ti-switch-horizontal"></i> Reasignar Medico
</button>
<div id="reasignar-${c.citaId}"></div>`;
    }

    const emergencia = c.esEmergencia
        // FA08
        ? `<span class="badge-emergencia"><i class="ti ti-alert-triangle"></i> EMERGENCIA</span>`
        : '';

    return `
<div class="tarjeta-resultado" id="tarjeta-cita-${c.citaId}">
<div class="resultado-encabezado">
<div>
<p class="resultado-nombre">${c.pacienteNombre} ${emergencia}</p>
<p class="resultado-subtitulo">${c.numeroCita} &middot; ${c.especialidad}</p>
</div>
<span class="badge-estado" style="background-color:${c.estadoColor || '#0F6E56'}">${c.estadoNombre}</span>
</div>
<div class="resultado-detalle">
<span><i class="ti ti-user"></i> Medico: <strong>${c.medico}</strong></span>
<span><i class="ti ti-building"></i> Sucursal: <strong>${c.sucursal}</strong></span>
<span><i class="ti ti-clock"></i> Hora: <strong>${c.fechaHora}</strong></span>
<span><i class="ti ti-notes"></i> Motivo: <strong>${c.motivo}</strong></span>
</div>
            ${mensaje}
            ${accion}
</div>`;
}

/* ---------- Paso 6-7 flujo normal: registrar llegada ---------- */

// FB6-7 FA09
async function registrarLlegada(citaId) {
    try {
        const resp = await fetch(`${CTX}recepcion/api/registrar-llegada`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ citaId })
        });
        const data = await resp.json();

        if (data.ok) {
            mostrarExito(data.mensaje);
        } else {
            Aviso.mostrar(data.mensaje, { tipo: 'error' });
            if (data.cita) reemplazarTarjeta(data.cita);
        }
    } catch (e) {
        Aviso.mostrar('Error al registrar la llegada.', { tipo: 'error' });
    }
}

/* ---------- Reprogramar una cita futura para hoy ---------- */

async function reprogramarHoy(citaId, emergencia) {
    const confirmacion = emergencia
        ? '¿Confirma reprogramar esta cita para HOY con prioridad de EMERGENCIA?'
        : '¿Confirma reprogramar esta cita para HOY?';
    const confirmado = await Aviso.confirmar(confirmacion, {
        titulo: emergencia ? 'Reprogramar con emergencia' : 'Reprogramar cita',
        aceptar: 'Sí, reprogramar'
    });
    if (!confirmado) return;

    try {
        const resp = await fetch(`${CTX}recepcion/api/reprogramar-hoy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ citaId, emergencia })
        });
        const data = await resp.json();

        if (data.ok) {
            Aviso.mostrar(data.mensaje, { tipo: 'exito' });
            if (data.cita) reemplazarTarjeta(data.cita);
        } else {
            Aviso.mostrar(data.mensaje, { tipo: 'error' });
        }
    } catch (e) {
        Aviso.mostrar('Error al reprogramar la cita.', { tipo: 'error' });
    }
}

/* ---------- FA07: reasignacion de medico ---------- */

async function mostrarReasignacion(citaId) {
    const zona = document.getElementById(`reasignar-${citaId}`);
    if (zona.innerHTML.trim() !== '') {   // segundo clic: cerrar
        zona.innerHTML = '';
        return;
    }
    let medicos = [];
    try {
        medicos = await (await fetch(`${CTX}recepcion/api/medicos-reasignacion?citaId=${citaId}`)).json();
    } catch (e) {
        Aviso.mostrar('No se pudieron cargar los medicos disponibles.', { tipo: 'error' });
        return;
    }
    if (medicos.length === 0) {
        zona.innerHTML = `<div class="resultado-mensaje tono-ambar" style="margin-top:10px;">
            No hay otros medicos de la misma sede y especialidad libres a la hora de esta cita.
</div>`;
        return;
    }
    zona.innerHTML = `
<div style="margin-top:12px;">
<label class="etiqueta-campo">Nuevo medico</label>
<select id="reaMedico-${citaId}" class="campo-formulario">
<option value="">Seleccione...</option>
            ${medicos.map(m => `<option value="${m.id}">${m.nombre}</option>`).join('')}
</select>
<label class="etiqueta-campo">Motivo de la reasignacion (opcional)</label>
<textarea id="reaMotivo-${citaId}" class="campo-formulario" maxlength="500"
          placeholder="Ej: el medico asignado no se presento hoy"></textarea>
<div style="display:flex;gap:10px;">
<button class="boton-secundario" style="flex:1;" onclick="mostrarReasignacion(${citaId})">Cancelar</button>
<button class="boton-principal" style="flex:1;" onclick="confirmarReasignacion(${citaId})">
<i class="ti ti-check"></i> Confirmar Reasignacion
</button>
</div>
</div>`;
}

async function confirmarReasignacion(citaId) {
    const medicoId = document.getElementById(`reaMedico-${citaId}`).value;
    const motivo = document.getElementById(`reaMotivo-${citaId}`).value;
    if (!medicoId) {
        Aviso.mostrar('Seleccione el nuevo medico.', { tipo: 'error' });
        return;
    }
    try {
        const resp = await fetch(`${CTX}recepcion/api/reasignar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ citaId, medicoId: Number(medicoId), motivo })
        });
        const data = await resp.json();
        Aviso.mostrar(data.mensaje, { tipo: data.ok ? 'exito' : 'error' });
        if (data.cita) reemplazarTarjeta(data.cita);
    } catch (e) {
        Aviso.mostrar('Error al reasignar el medico.', { tipo: 'error' });
    }
}

function reemplazarTarjeta(cita) {
    // Mantener al dia la lista guardada para que el filtro y las paginas usen el estado nuevo
    const i = citasActuales.findIndex(c => c.citaId === cita.citaId);
    if (i >= 0) citasActuales[i] = cita;

    const actual = document.getElementById(`tarjeta-cita-${cita.citaId}`);
    if (actual) {
        actual.outerHTML = tarjetaCita(cita);
    } else {
        document.getElementById('zonaResultado').innerHTML = tarjetaCita(cita);
    }
}

// FB6-7
function mostrarExito(mensaje) {
    document.getElementById('zonaResultado').innerHTML = `
<div class="exito-llegada">
<i class="ti ti-circle-check"></i>
<p class="resultado-nombre">Llegada registrada</p>
<p>${mensaje}</p>
</div>`;
    document.getElementById('campoValor').value = '';
}

/* ============================================================
   FA01: Paciente que llega por emergencia sin cita
   ============================================================ */

function botonEmergencia() {
    // Solo tiene sentido cuando se busco por DPI (se sabe quien es el paciente).
    if (modoActual !== 'dpi' || !ultimoDpiBuscado) return '';
    return `
<button class="boton-principal" style="background:var(--sm-danger);margin:10px 0;" onclick="mostrarFormularioEmergencia()">
<i class="ti ti-alert-triangle"></i> Emergencia (sin cita)
</button>`;
}

function mostrarFormularioEmergencia() {
    document.getElementById('zonaResultado').innerHTML = `
<div class="tarjeta-resultado">
<p class="resultado-nombre" style="margin-bottom:6px;">Registrar emergencia</p>
<p class="resultado-subtitulo" style="margin-bottom:16px;">El paciente pasa directo a toma de signos vitales con prioridad de EMERGENCIA.</p>

<label class="etiqueta-campo">Sucursal</label>
<select id="wiSucursal" class="campo-formulario" onchange="cargarEspecialidadesWalkIn()">
<option value="">Seleccione...</option>
</select>

<label class="etiqueta-campo">Especialidad</label>
<select id="wiEspecialidad" class="campo-formulario" disabled onchange="cargarMedicosWalkIn()">
<option value="">Seleccione una sucursal primero...</option>
</select>

<label class="etiqueta-campo">Medico</label>
<select id="wiMedico" class="campo-formulario" disabled>
<option value="">Seleccione una especialidad primero...</option>
</select>
<input type="hidden" id="wiFecha" value="">
<select id="wiHora" style="display:none;"></select>
<p class="error-campo" id="emErrorMedico"></p>

<label class="etiqueta-campo">Motivo / sintomas</label>
<textarea id="emMotivo" class="campo-formulario" placeholder="Describa la emergencia (minimo 10 caracteres)"></textarea>
<p class="error-campo" id="emErrorMotivo"></p>

<div style="display:flex;gap:10px;">
<button class="boton-secundario" style="flex:1;" onclick="buscar()">Cancelar</button>
<button class="boton-principal" style="flex:1;background:var(--sm-danger);" onclick="enviarEmergencia()">
<i class="ti ti-alert-triangle"></i> Registrar emergencia
</button>
</div>
</div>`;

    cargarSucursalesWalkIn();
}

async function enviarEmergencia() {
    document.getElementById('emErrorMedico').textContent = '';
    document.getElementById('emErrorMotivo').textContent = '';

    const medicoId = document.getElementById('wiMedico').value;
    const motivo = document.getElementById('emMotivo').value;
    if (!medicoId) {
        document.getElementById('emErrorMedico').textContent = 'Seleccione sucursal, especialidad y medico.';
        return;
    }

    try {
        const resp = await fetch(`${CTX}recepcion/api/emergencia`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ dpi: ultimoDpiBuscado, medicoId: Number(medicoId), motivo })
        });
        const data = await resp.json();

        if (data.ok) {
            mostrarExito(data.mensaje);
        } else {
            const e = data.errores || {};
            if (e.medicoId) document.getElementById('emErrorMedico').textContent = e.medicoId;
            if (e.motivo) document.getElementById('emErrorMotivo').textContent = e.motivo;
            if (e.dpi || e.general) Aviso.mostrar(e.dpi || e.general, { tipo: 'error' });
        }
    } catch (err) {
        Aviso.mostrar('Error al registrar la emergencia.', { tipo: 'error' });
    }
}

/* ============================================================
   FA04: Nueva Cita (Walk-in)
   ============================================================ */

function mostrarFormularioWalkIn() {
    const hoy = new Date().toISOString().split('T')[0];

    document.getElementById('zonaResultado').innerHTML = `
<div class="tarjeta-resultado">
<p class="resultado-nombre" style="margin-bottom:16px;">Nueva cita (walk-in)</p>
 
            <label class="etiqueta-campo">Sucursal</label>
<select id="wiSucursal" class="campo-formulario" onchange="cargarEspecialidadesWalkIn()">
<option value="">Seleccione...</option>
</select>
 
            <label class="etiqueta-campo">Especialidad</label>
<select id="wiEspecialidad" class="campo-formulario" disabled onchange="cargarMedicosWalkIn()">
<option value="">Seleccione una sucursal primero...</option>
</select>
 
            <label class="etiqueta-campo">Medico</label>
<select id="wiMedico" class="campo-formulario" disabled onchange="cargarHorariosWalkIn()">
<option value="">Seleccione una especialidad primero...</option>
</select>
 
            <div class="fila-dos-columnas">
<div>
<label class="etiqueta-campo">Fecha</label>
<input type="date" id="wiFecha" class="campo-formulario" min="${hoy}" value="${hoy}" onchange="cargarHorariosWalkIn()">
</div>
<div>
<label class="etiqueta-campo">Hora</label>
<select id="wiHora" class="campo-formulario" disabled>
<option value="">Seleccione un medico primero...</option>
</select>
</div>
</div>
<p class="error-campo" id="wiErrorFechaHora"></p>
 
            <label class="etiqueta-campo">Motivo de la consulta</label>
<textarea id="wiMotivo" class="campo-formulario" placeholder="Describa brevemente el motivo de la consulta (minimo 10 caracteres)"></textarea>
<p class="error-campo" id="wiErrorMotivo"></p>
 
            <div style="display:flex;gap:10px;">
<button class="boton-secundario" style="flex:1;" onclick="buscar()">Cancelar</button>
<button class="boton-principal" style="flex:1;" onclick="enviarWalkIn()">
<i class="ti ti-calendar-plus"></i> Crear cita
</button>
</div>
</div>`;

    cargarSucursalesWalkIn();
}

async function cargarSucursalesWalkIn() {
    const select = document.getElementById('wiSucursal');
    const sucursales = await (await fetch(`${CTX}recepcion/api/sucursales`)).json();
    select.innerHTML = '<option value="">Seleccione...</option>' +
        sucursales.map(s => `<option value="${s.id}">${s.nombre}</option>`).join('');
}

async function cargarEspecialidadesWalkIn() {
    const sucursalId = document.getElementById('wiSucursal').value;
    const especialidadSelect = document.getElementById('wiEspecialidad');
    const medicoSelect = document.getElementById('wiMedico');
    const horaSelect = document.getElementById('wiHora');

    medicoSelect.innerHTML = '<option value="">Seleccione una especialidad primero...</option>';
    medicoSelect.disabled = true;
    horaSelect.innerHTML = '<option value="">Seleccione un medico primero...</option>';
    horaSelect.disabled = true;

    if (!sucursalId) {
        especialidadSelect.innerHTML = '<option value="">Seleccione una sucursal primero...</option>';
        especialidadSelect.disabled = true;
        return;
    }

    const especialidades = await (await fetch(`${CTX}recepcion/api/especialidades?sucursalId=${sucursalId}`)).json();
    especialidadSelect.innerHTML = '<option value="">Seleccione...</option>' +
        especialidades.map(e => `<option value="${e.id}">${e.nombre}</option>`).join('');
    especialidadSelect.disabled = false;
}

async function cargarMedicosWalkIn() {
    const sucursalId = document.getElementById('wiSucursal').value;
    const especialidadId = document.getElementById('wiEspecialidad').value;
    const medicoSelect = document.getElementById('wiMedico');
    const horaSelect = document.getElementById('wiHora');

    horaSelect.innerHTML = '<option value="">Seleccione un medico primero...</option>';
    horaSelect.disabled = true;

    if (!especialidadId) {
        medicoSelect.innerHTML = '<option value="">Seleccione una especialidad primero...</option>';
        medicoSelect.disabled = true;
        return;
    }

    const medicos = await (await fetch(`${CTX}recepcion/api/medicos?sucursalId=${sucursalId}&especialidadId=${especialidadId}`)).json();
    medicoSelect.innerHTML = '<option value="">Seleccione...</option>' +
        medicos.map(m => `<option value="${m.id}">${m.nombre}</option>`).join('');
    medicoSelect.disabled = false;
}

async function cargarHorariosWalkIn() {
    const sucursalId = document.getElementById('wiSucursal').value;
    const medicoId = document.getElementById('wiMedico').value;
    const fecha = document.getElementById('wiFecha').value;
    const horaSelect = document.getElementById('wiHora');

    if (!medicoId || !fecha) {
        horaSelect.innerHTML = '<option value="">Seleccione un medico primero...</option>';
        horaSelect.disabled = true;
        return;
    }

    const horarios = await (await fetch(`${CTX}recepcion/api/horarios?medicoId=${medicoId}&sucursalId=${sucursalId}&fecha=${fecha}`)).json();

    if (horarios.length === 0) {
        horaSelect.innerHTML = '<option value="">Sin horarios disponibles ese dia</option>';
        horaSelect.disabled = true;
        return;
    }

    horaSelect.innerHTML = '<option value="">Seleccione...</option>' +
        horarios.map(h => `<option value="${h}">${h}</option>`).join('');
    horaSelect.disabled = false;
}

async function enviarWalkIn() {
    document.getElementById('wiErrorFechaHora').textContent = '';
    document.getElementById('wiErrorMotivo').textContent = '';

    const medicoId = document.getElementById('wiMedico').value;
    const fecha = document.getElementById('wiFecha').value;
    const hora = document.getElementById('wiHora').value;
    const motivo = document.getElementById('wiMotivo').value;

    if (!medicoId || !fecha || !hora) {
        document.getElementById('wiErrorFechaHora').textContent = 'Complete sucursal, especialidad, medico, fecha y hora.';
        return;
    }

    try {
        const resp = await fetch(`${CTX}recepcion/api/walkin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ dpi: ultimoDpiBuscado, medicoId: Number(medicoId), fecha, hora, motivo })
        });
        const data = await resp.json();

        if (data.ok) {
            mostrarExitoWalkIn(data.numeroCita);
        } else {
            if (data.errores.fechaHora) document.getElementById('wiErrorFechaHora').textContent = data.errores.fechaHora;
            if (data.errores.motivo) document.getElementById('wiErrorMotivo').textContent = data.errores.motivo;
            if (data.errores.medicoId) document.getElementById('wiErrorFechaHora').textContent = data.errores.medicoId;
            if (data.errores.dpi) Aviso.mostrar(data.errores.dpi, { tipo: 'error' });
        }
    } catch (e) {
        Aviso.mostrar('Error al crear la cita.', { tipo: 'error' });
    }
}

function mostrarExitoWalkIn(numeroCita) {
    document.getElementById('zonaResultado').innerHTML = `
<div class="exito-llegada">
<i class="ti ti-circle-check"></i>
<p class="resultado-nombre">Cita creada: ${numeroCita}</p>
<p>La cita quedo pendiente de pago. Indique al paciente que pase a caja.</p>
<div style="margin-top:16px;">
<button class="boton-principal" onclick="buscarNumeroCita('${numeroCita}')">
<i class="ti ti-search"></i> Buscar esta cita
</button>
</div>
</div>`;
}

function buscarNumeroCita(numeroCita) {
    cambiarModo('numero');
    document.getElementById('campoValor').value = numeroCita;
    buscar();
}