/* ============================================================
   CU-05 Recepcion y Verificacion de Cita
   ============================================================ */

let modoActual = 'dpi'; // 'dpi' | 'numero'
let ultimoDpiBuscado = '';

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btnModoDpi').addEventListener('click', () => cambiarModo('dpi'));
    document.getElementById('btnModoNumero').addEventListener('click', () => cambiarModo('numero'));
    document.getElementById('btnBuscar').addEventListener('click', buscar);
    document.getElementById('campoValor').addEventListener('keydown', e => {
        if (e.key === 'Enter') buscar();
    });
});

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

function renderResultado(data) {
    const zona = document.getElementById('zonaResultado');
    const error = document.getElementById('errorBusqueda');

    switch (data.tipo) {
        case 'SIN_PARAMETROS':
        case 'SIN_RESULTADOS':
            error.textContent = data.mensaje;
            break;

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

        case 'SIN_CITAS':
            zona.innerHTML = `
<div class="tarjeta-resultado">
<p class="resultado-nombre">${data.mensaje}</p>
<p class="resultado-subtitulo" style="margin-bottom:16px;">${data.subMensaje}</p>
<button class="boton-principal" onclick="mostrarFormularioWalkIn()">
<i class="ti ti-calendar-plus"></i> Nueva Cita (Walk-in)
</button>
</div>`;
            break;

        case 'CITA_ENCONTRADA':
            zona.innerHTML = tarjetaCita(data.cita);
            break;

        case 'CITAS_ENCONTRADAS':
            zona.innerHTML =
                `<p class="resultado-subtitulo" style="margin-bottom:12px;">${data.citas.length} cita(s) encontrada(s)</p>` +
                data.citas.map(tarjetaCita).join('');
            break;
    }
}

/* ---------- Arma el HTML de una tarjeta de cita, segun su estado y su fecha ---------- */

function tarjetaCita(c) {
    let mensaje = '';
    let accion = '';

    if (c.estadoCodigo === 'PENDIENTE_PAGO') {
        mensaje = `<div class="resultado-mensaje tono-ambar">
            La cita del paciente tiene estado "Pendiente de pago". Debe realizar el pago en caja antes de ser atendido.
</div>`;
    } else if (c.estadoCodigo === 'CANCELADA') {
        const motivo = c.motivoEstado || 'La cita fue cancelada.';
        mensaje = `<div class="resultado-mensaje tono-rojo">${motivo}</div>`;
        accion = `<button class="boton-secundario" onclick="mostrarFormularioWalkIn()">Nueva Cita</button>`;
    } else if (c.estadoCodigo === 'NO_ASISTIO') {
        const motivo = c.motivoEstado || 'El paciente no se presento a esta cita.';
        mensaje = `<div class="resultado-mensaje tono-rojo">${motivo}</div>`;
        accion = `<button class="boton-secundario" onclick="mostrarFormularioWalkIn()">Nueva Cita</button>`;
    } else if (c.estadoCodigo === 'PACIENTE_PRESENTE') {
        mensaje = `<div class="resultado-mensaje tono-teal">
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
<button class="boton-principal" style="flex:1;background:var(--rojo);" onclick="reprogramarHoy(${c.citaId}, true)">
<i class="ti ti-alert-triangle"></i> Reprogramar como emergencia
</button>
</div>`;
    } else if (c.estadoCodigo === 'CONFIRMADA' && c.esFechaHoy) {
        accion = `<button class="boton-principal" onclick="registrarLlegada(${c.citaId})">
<i class="ti ti-check"></i> Registrar llegada
</button>`;
    }

    const emergencia = c.esEmergencia
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
            alert(data.mensaje);
            if (data.cita) reemplazarTarjeta(data.cita);
        }
    } catch (e) {
        alert('Error al registrar la llegada.');
    }
}

/* ---------- Reprogramar una cita futura para hoy ---------- */

async function reprogramarHoy(citaId, emergencia) {
    const confirmacion = emergencia
        ? '¿Confirma reprogramar esta cita para HOY con prioridad de EMERGENCIA?'
        : '¿Confirma reprogramar esta cita para HOY?';
    if (!confirm(confirmacion)) return;

    try {
        const resp = await fetch(`${CTX}recepcion/api/reprogramar-hoy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ citaId, emergencia })
        });
        const data = await resp.json();

        if (data.ok) {
            alert(data.mensaje);
            if (data.cita) reemplazarTarjeta(data.cita);
        } else {
            alert(data.mensaje);
        }
    } catch (e) {
        alert('Error al reprogramar la cita.');
    }
}

function reemplazarTarjeta(cita) {
    const actual = document.getElementById(`tarjeta-cita-${cita.citaId}`);
    if (actual) {
        actual.outerHTML = tarjetaCita(cita);
    } else {
        document.getElementById('zonaResultado').innerHTML = tarjetaCita(cita);
    }
}

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
            if (data.errores.dpi) alert(data.errores.dpi);
        }
    } catch (e) {
        alert('Error al crear la cita.');
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