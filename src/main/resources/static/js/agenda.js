/* ============================================================
   CU-16 Gestion de Agenda Medica
   - Calendario con FullCalendar: citas (color de su estado) y
     eventos personales (violeta) [paso 2] [FA05]
   - Popover de detalle [FA01], crear / editar / eliminar evento
     [pasos 3-9] [FA07] [FA04]
   - Panel lateral de tareas del dia elegido [paso 10] [FA06]

   Restricciones de fecha (decision del equipo; el servidor las
   vuelve a validar):
   - Evento con hora y tarea: desde la fecha y hora actual.
   - Evento de todo el dia: desde hoy.
   - Evento que ya termino: sin "Editar" (solo "Eliminar").
   - Evento en curso: se puede editar conservando su inicio.
   ============================================================ */

let calendario = null;
let diaSeleccionado = fechaISO(new Date());   // "yyyy-MM-dd": dia de las tareas
let tareas = [];                               // tareas del dia (pendientes y completadas)
let filtroTareas = 'pendientes';
let eventoPopover = null;                      // evento de FullCalendar abierto en el popover
let eventoEditandoId = null;                   // null = nuevo, numero = editando [FA07]
let inicioOriginal = null;                     // inicio del evento en edicion (para eventos en curso)

document.addEventListener('DOMContentLoaded', () => {
    iniciarCalendario();
    actualizarTituloTareas();

    // Clic fuera del popover lo cierra; Escape cierra popover y ventana
    document.addEventListener('click', (e) => {
        const pop = document.getElementById('popover');
        if (!pop.classList.contains('oculto') && !pop.contains(e.target) && !e.target.closest('.fc-event')) {
            cerrarPopover();
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            cerrarPopover();
            cerrarModalEvento();
        }
    });
});

/* ---------- Utilidades ---------- */

function esc(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML;
}

function dos(n) {
    return String(n).padStart(2, '0');
}

/** Fecha local del navegador como "yyyy-MM-dd". */
function fechaISO(d) {
    return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`;
}

/** Fecha y hora local como "yyyy-MM-ddTHH:mm" (formato de datetime-local). */
function fechaHoraISO(d) {
    return `${fechaISO(d)}T${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

/** La hora actual al minuto, como texto de datetime-local. */
function ahoraISO() {
    return fechaHoraISO(new Date());
}

/** La proxima media hora (10:07 -> 10:30), para sugerir horas que no hayan pasado. */
function proximaMediaHora() {
    const d = new Date();
    d.setSeconds(0, 0);
    d.setMinutes(d.getMinutes() <= 30 ? 30 : 60);
    return d;
}

/** dd/mm/yyyy, como pide el CU: toLocaleDateString("es-GT"). */
function fechaGT(d) {
    return d.toLocaleDateString('es-GT', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function horaGT(d) {
    return d.toLocaleTimeString('es-GT', { hour: '2-digit', minute: '2-digit', hour12: false });
}

async function postJson(url, body) {
    const resp = await fetch(`${CTX}${url}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body == null ? null : JSON.stringify(body)
    });
    return resp.json();
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

/** Campo de pantalla que se resalta en rojo para cada clave de error [FA03]. */
function camposDeError(clave) {
    const todoElDia = document.getElementById('chkTodoElDia').checked;
    const mapa = {
        titulo: ['inpTitulo', 'inpTareaTitulo'],
        descripcion: ['inpDescripcion', 'inpTareaDescripcion'],
        fechaInicio: [todoElDia ? 'inpInicioDia' : 'inpInicioHora'],
        fechaFin: [todoElDia ? 'inpFinDia' : 'inpFinHora'],
        fechaLimite: ['inpTareaFecha']
    };
    return mapa[clave] || [];
}

/** Pinta cada error en su <p data-error="campo"> y resalta el campo [FA03]. */
function mostrarErrores(contenedorId, errores) {
    const cont = document.getElementById(contenedorId);
    for (const campo in (errores || {})) {
        const p = cont.querySelector(`[data-error="${campo}"]`);
        if (p) p.textContent = errores[campo];
        else mostrarMensaje(errores[campo], 'error');

        camposDeError(campo).forEach(id => {
            const el = document.getElementById(id);
            if (el && cont.contains(el)) el.style.borderColor = 'var(--sm-danger)';
        });
    }
}

function limpiarErrores(contenedorId) {
    const cont = document.getElementById(contenedorId);
    cont.querySelectorAll('[data-error]').forEach(p => p.textContent = '');
    cont.querySelectorAll('input, textarea').forEach(el => el.style.borderColor = '');
}

/* ============================================================
   Paso 2 + FA05: calendario
   ============================================================ */

// CU-16 FB1-2
function iniciarCalendario() {
    calendario = new FullCalendar.Calendar(document.getElementById('calendario'), {
        locale: 'es',
        // CU-16 FA05
        initialView: 'dayGridMonth',
        firstDay: 1,                                   // la semana empieza el lunes
        height: 'auto',
        nowIndicator: true,
        dayMaxEvents: 3,                               // "+N mas" si un dia tiene muchos
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek,timeGridDay'
        },
        buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', day: 'Día' },
        eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
        slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false },

        // FullCalendar pide los eventos cada vez que cambia el rango visible
        events: async (info, exito, fallo) => {
            try {
                const url = `${CTX}medico/agenda/api/calendario`
                    + `?start=${encodeURIComponent(info.startStr)}&end=${encodeURIComponent(info.endStr)}`;
                const resp = await fetch(url);
                exito(await resp.json());
            } catch (e) {
                fallo(e);
                mostrarMensaje('No se pudo cargar la agenda.', 'error');
            }
        },

        // FA01: clic en un evento o cita
        eventClick: (info) => {
            info.jsEvent.preventDefault();
            info.jsEvent.stopPropagation();
            mostrarPopover(info.event, info.jsEvent);
        },

        // Clic en un dia: ese dia pasa a ser el de las tareas
        dateClick: (info) => seleccionarDia(info.dateStr.substring(0, 10)),

        // Al cambiar de mes/semana/dia se vuelve a marcar el dia elegido
        datesSet: () => marcarDiaElegido()
    });
    calendario.render();
}

function marcarDiaElegido() {
    document.querySelectorAll('#calendario .ag-dia-elegido')
        .forEach(el => el.classList.remove('ag-dia-elegido'));
    document.querySelectorAll(`#calendario td[data-date="${diaSeleccionado}"]`)
        .forEach(el => el.classList.add('ag-dia-elegido'));
}

function seleccionarDia(dia) {
    diaSeleccionado = dia;
    marcarDiaElegido();
    actualizarTituloTareas();
    if (!document.getElementById('panelTareas').classList.contains('oculto')) {
        cargarTareas();
    }
}

/* ============================================================
   FA01: popover de detalle
   ============================================================ */

function mostrarPopover(evento, clic) {
    eventoPopover = evento;
    const p = evento.extendedProps;
    const esEvento = p.clase === 'evento';
    let html;

    if (esEvento) {
        let fechas;
        if (evento.allDay) {
            // El fin se guardo como el dia siguiente: se muestra el anterior
            const fin = new Date(evento.end.getTime() - 24 * 60 * 60 * 1000);
            fechas = fechaGT(evento.start) === fechaGT(fin)
                ? `${fechaGT(evento.start)} (todo el día)`
                : `${fechaGT(evento.start)} a ${fechaGT(fin)} (todo el día)`;
        } else {
            fechas = `${fechaGT(evento.start)} ${horaGT(evento.start)} – ${fechaGT(evento.end)} ${horaGT(evento.end)}`;
        }
        html = `
            <div style="font-size:15px;font-weight:800;margin-bottom:4px;">${esc(evento.title)}</div>
            <div style="color:var(--sm-slate-500);">Tipo: ${esc(p.nombreTipo)}</div>
            <div style="color:var(--sm-slate-500);">${esc(fechas)}</div>
            ${p.descripcion ? `<div style="margin-top:8px;">${esc(p.descripcion)}</div>` : ''}`;
    } else {
        html = `
            <div style="font-size:15px;font-weight:800;margin-bottom:4px;">${esc(p.paciente)}</div>
            <div style="color:var(--sm-slate-500);">Cita ${esc(p.numeroCita)} · ${esc(p.estado)}</div>
            <div style="color:var(--sm-slate-500);">${esc(fechaGT(evento.start))} ${esc(horaGT(evento.start))}
                – ${esc(horaGT(evento.end))}</div>
            <div style="margin-top:8px;">${esc(p.especialidad)} · ${esc(p.sucursal)}</div>`;
    }

    const pop = document.getElementById('popover');
    document.getElementById('popoverContenido').innerHTML = html;

    // Las citas no se editan ni eliminan desde la agenda.
    // Un evento que ya termino no se edita, pero si se elimina.
    const yaPaso = esEvento && evento.end && evento.end.getTime() <= Date.now();
    document.getElementById('popoverAcciones').classList.toggle('oculto', !esEvento);
    document.getElementById('btnPopoverEditar').classList.toggle('oculto', yaPaso);

    pop.style.borderLeftColor = evento.backgroundColor || '#8b5cf6';
    pop.classList.remove('oculto');

    // Cerca del clic, sin salirse de la ventana
    const ancho = pop.offsetWidth, alto = pop.offsetHeight;
    pop.style.left = Math.max(10, Math.min(clic.clientX + 10, window.innerWidth - ancho - 10)) + 'px';
    pop.style.top = Math.max(10, Math.min(clic.clientY + 10, window.innerHeight - alto - 10)) + 'px';
}

function cerrarPopover() {
    document.getElementById('popover').classList.add('oculto');
}

/* ============================================================
   Pasos 3-9 + FA07: formulario de evento
   ============================================================ */

// CU-16 FB3-4
function abrirNuevoEvento() {
    cerrarPopover();
    eventoEditandoId = null;
    inicioOriginal = null;
    document.getElementById('modalTitulo').textContent = 'Nuevo Evento';

    // Sugerencia: el dia seleccionado de 08:00 a 09:00; si eso ya paso,
    // la proxima media hora de hoy y una hora de duracion.
    let inicio = new Date(`${diaSeleccionado}T08:00`);
    if (inicio.getTime() < Date.now()) inicio = proximaMediaHora();
    const fin = new Date(inicio.getTime() + 60 * 60 * 1000);

    llenarFormularioEvento({
        titulo: '', descripcion: '', tipo: '', todoElDia: false,
        inicio: fechaHoraISO(inicio), fin: fechaHoraISO(fin)
    });
}

/** FA07: formulario prellenado con el evento del popover. */
function editarDesdePopover() {
    if (!eventoPopover) return;
    const p = eventoPopover.extendedProps;
    eventoEditandoId = p.eventoId;
    inicioOriginal = { todoElDia: p.todoElDia, valor: p.inicioForm };   // evento en curso
    cerrarPopover();
    document.getElementById('modalTitulo').textContent = 'Editar Evento';
    llenarFormularioEvento({
        titulo: eventoPopover.title,
        descripcion: p.descripcion || '',
        tipo: String(p.tipoEvento),
        todoElDia: p.todoElDia,
        inicio: p.inicioForm,
        fin: p.finForm
    });
}

function llenarFormularioEvento(d) {
    limpiarErrores('modalEvento');
    aplicarMinimos();
    document.getElementById('inpTitulo').value = d.titulo;
    document.getElementById('inpDescripcion').value = d.descripcion;
    document.getElementById('selTipo').value = d.tipo;
    document.getElementById('chkTodoElDia').checked = d.todoElDia;

    if (d.todoElDia) {
        document.getElementById('inpInicioDia').value = d.inicio.substring(0, 10);
        document.getElementById('inpFinDia').value = d.fin.substring(0, 10);
        document.getElementById('inpInicioHora').value = horaSugerida(d.inicio.substring(0, 10), '08:00');
        document.getElementById('inpFinHora').value = horaSugerida(d.fin.substring(0, 10), '09:00');
    } else {
        document.getElementById('inpInicioHora').value = d.inicio;
        document.getElementById('inpFinHora').value = d.fin;
        document.getElementById('inpInicioDia').value = d.inicio.substring(0, 10);
        document.getElementById('inpFinDia').value = d.fin.substring(0, 10);
    }
    aplicarTodoElDia();
    document.getElementById('modalEvento').classList.remove('oculto');
    document.getElementById('inpTitulo').focus();
}

/**
 * Minimos de los campos de fecha (decision del equipo):
 * - con hora: desde la hora actual;
 * - solo fecha (todo el dia): desde hoy.
 * Si se edita un evento en curso, su inicio original sigue permitido.
 */
function aplicarMinimos() {
    const ahora = ahoraISO();
    const hoy = fechaISO(new Date());

    let minInicioHora = ahora;
    let minInicioDia = hoy;
    if (inicioOriginal) {
        if (!inicioOriginal.todoElDia && inicioOriginal.valor < ahora) minInicioHora = inicioOriginal.valor;
        if (inicioOriginal.todoElDia && inicioOriginal.valor < hoy) minInicioDia = inicioOriginal.valor;
    }

    document.getElementById('inpInicioHora').min = minInicioHora;
    document.getElementById('inpFinHora').min = ahora;
    document.getElementById('inpInicioDia').min = minInicioDia;
    document.getElementById('inpFinDia').min = hoy;
}

/** "yyyy-MM-ddTHH:mm" con la hora sugerida, o la proxima media hora si esa ya paso. */
function horaSugerida(dia, hora) {
    const sugerida = new Date(`${dia}T${hora}`);
    return sugerida.getTime() < Date.now() ? fechaHoraISO(proximaMediaHora()) : `${dia}T${hora}`;
}

/** RN-CU14-01: "Todo el dia" cambia los campos de fecha-hora a solo fecha. */
function cambiarTodoElDia() {
    const todoElDia = document.getElementById('chkTodoElDia').checked;
    // Se pasan las fechas de un par de campos al otro para no perderlas
    if (todoElDia) {
        const ini = document.getElementById('inpInicioHora').value;
        const fin = document.getElementById('inpFinHora').value;
        if (ini) document.getElementById('inpInicioDia').value = ini.substring(0, 10);
        if (fin) document.getElementById('inpFinDia').value = fin.substring(0, 10);
    } else {
        const ini = document.getElementById('inpInicioDia').value;
        const fin = document.getElementById('inpFinDia').value;
        if (ini) document.getElementById('inpInicioHora').value = horaSugerida(ini, '08:00');
        if (fin) document.getElementById('inpFinHora').value = horaSugerida(fin, '09:00');
    }
    limpiarErrores('modalEvento');
    aplicarTodoElDia();
}

function aplicarTodoElDia() {
    const todoElDia = document.getElementById('chkTodoElDia').checked;
    document.getElementById('fechasConHora').classList.toggle('oculto', todoElDia);
    document.getElementById('fechasSoloDia').classList.toggle('oculto', !todoElDia);
}

function cerrarModalEvento() {
    document.getElementById('modalEvento').classList.add('oculto');
    if (window.Controles) Controles.cerrar();
}

// CU-16 FB5-9
async function guardarEvento() {
    limpiarErrores('modalEvento');
    const todoElDia = document.getElementById('chkTodoElDia').checked;
    const tipo = document.getElementById('selTipo').value;

    const body = {
        titulo: document.getElementById('inpTitulo').value,
        descripcion: document.getElementById('inpDescripcion').value,
        inicio: document.getElementById(todoElDia ? 'inpInicioDia' : 'inpInicioHora').value,
        fin: document.getElementById(todoElDia ? 'inpFinDia' : 'inpFinHora').value,
        tipo: tipo === '' ? null : Number(tipo),
        todoElDia: todoElDia
    };

    const url = eventoEditandoId == null
        ? 'medico/agenda/api/eventos'
        : `medico/agenda/api/eventos/${eventoEditandoId}`;

    try {
        const data = await postJson(url, body);
        if (data.ok) {
            cerrarModalEvento();
            mostrarMensaje(data.mensaje, 'exito');
            calendario.refetchEvents();          // paso 9: el calendario se actualiza
        } else {
            mostrarErrores('modalEvento', data.errores);
        }
    } catch (e) {
        mostrarErrores('modalEvento', { general: 'Error al guardar el evento.' });
    }
}

/* ============================================================
   FA04: eliminar evento
   ============================================================ */

async function eliminarDesdePopover() {
    if (!eventoPopover) return;
    const id = eventoPopover.extendedProps.eventoId;
    cerrarPopover();

    const confirmado = await Aviso.confirmar('¿Está seguro de eliminar este evento?', { aceptar: 'Eliminar' });
    if (!confirmado) return;

    try {
        const data = await postJson(`medico/agenda/api/eventos/${id}/eliminar`);
        if (data.ok) {
            mostrarMensaje(data.mensaje, 'exito');
            calendario.refetchEvents();
        } else {
            mostrarMensaje(Object.values(data.errores || {})[0] || 'No se pudo eliminar el evento.', 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al eliminar el evento.', 'error');
    }
}

/* ============================================================
   Paso 10 + FA06: panel de tareas
   ============================================================ */

// CU-16 FB10
function alternarTareas() {
    const panel = document.getElementById('panelTareas');
    const abrir = panel.classList.contains('oculto');
    panel.classList.toggle('oculto', !abrir);
    document.getElementById('agendaCuerpo').classList.toggle('con-tareas', abrir);
    document.getElementById('btnTareas').classList.toggle('sm-btn--primary', abrir);
    document.getElementById('btnTareas').classList.toggle('sm-btn--ghost', !abrir);
    if (abrir) cargarTareas();
    // El calendario cambia de ancho: que se redibuje
    setTimeout(() => calendario.updateSize(), 50);
}

function actualizarTituloTareas() {
    const [a, m, d] = diaSeleccionado.split('-');
    document.getElementById('tareasFecha').textContent = `${d}/${m}/${a}`;
}

// CU-16 FB10 FA06
async function cargarTareas() {
    try {
        const resp = await fetch(`${CTX}medico/agenda/api/tareas?dia=${diaSeleccionado}`);
        tareas = await resp.json();
    } catch (e) {
        tareas = [];
        mostrarMensaje('No se pudieron cargar las tareas.', 'error');
    }
    renderTareas();
}

// CU-16 FB10 FA06
function filtrarTareas(filtro) {
    filtroTareas = filtro;
    document.querySelectorAll('.ag-filtro')
        .forEach(b => b.classList.toggle('activo', b.dataset.filtro === filtro));
    renderTareas();
}

function renderTareas() {
    const visibles = tareas.filter(t =>
        filtroTareas === 'todas' ? true
            : filtroTareas === 'completadas' ? t.completada
                : !t.completada);

    document.getElementById('tareasConteo').textContent = visibles.length;
    const cont = document.getElementById('listaTareas');

    if (visibles.length === 0) {
        const textos = {
            pendientes: 'No hay tareas pendientes para este día.',
            completadas: 'No hay tareas completadas para este día.',
            todas: 'No hay tareas para este día.'
        };
        cont.innerHTML = `<p style="font-size:13px;color:var(--sm-slate-400);margin:4px 0 8px;">${textos[filtroTareas]}</p>`;
        return;
    }

    cont.innerHTML = visibles.map(t => `
        <div class="ag-tarea ${t.completada ? 'completada' : ''}">
            <div class="ag-tarea__cab">
                <input type="checkbox" ${t.completada ? 'checked' : ''}
                       onchange="marcarTarea(${t.id}, this.checked)" aria-label="Completada">
                <span class="ag-tarea__titulo">${esc(t.titulo)}</span>
                <button type="button" class="ag-tarea__borrar" onclick="eliminarTarea(${t.id})" aria-label="Eliminar tarea">
                    <i class="ti ti-trash" aria-hidden="true"></i>
                </button>
            </div>
            <div class="ag-tarea__meta">
                <i class="ti ti-flag-filled ag-prio-${t.prioridad}" aria-hidden="true"></i>
                ${esc(t.nombrePrioridad)} · ${esc(t.horaLimite)}
                · ${t.completada ? 'Completada' : 'Pendiente'}
            </div>
            ${t.descripcion ? `<div class="ag-tarea__desc">${esc(t.descripcion)}</div>` : ''}
        </div>`).join('');
}

// CU-16 FB10 FA06
function abrirNuevaTarea() {
    limpiarErrores('formTarea');
    document.getElementById('inpTareaTitulo').value = '';
    document.getElementById('inpTareaDescripcion').value = '';
    document.getElementById('selTareaPrioridad').value = '1';            // Normal
    // Decision del equipo: la fecha limite no puede ser anterior a la hora actual
    const fecha = document.getElementById('inpTareaFecha');
    fecha.min = ahoraISO();
    fecha.value = horaSugerida(diaSeleccionado, '09:00');
    document.getElementById('formTarea').classList.remove('oculto');
    document.getElementById('btnNuevaTarea').classList.add('oculto');
    document.getElementById('inpTareaTitulo').focus();
}

function cerrarFormTarea() {
    document.getElementById('formTarea').classList.add('oculto');
    document.getElementById('btnNuevaTarea').classList.remove('oculto');
    if (window.Controles) Controles.cerrar();
}

async function guardarTarea() {
    limpiarErrores('formTarea');
    const prioridad = document.getElementById('selTareaPrioridad').value;
    const body = {
        titulo: document.getElementById('inpTareaTitulo').value,
        descripcion: document.getElementById('inpTareaDescripcion').value,
        prioridad: prioridad === '' ? null : Number(prioridad),
        fechaLimite: document.getElementById('inpTareaFecha').value
    };
    try {
        const data = await postJson('medico/agenda/api/tareas', body);
        if (data.ok) {
            cerrarFormTarea();
            mostrarMensaje(data.mensaje, 'exito');
            // Si la fecha limite es otro dia, se muestra ese dia
            const dia = body.fechaLimite.substring(0, 10);
            if (dia !== diaSeleccionado) seleccionarDia(dia);
            else cargarTareas();
        } else {
            mostrarErrores('formTarea', data.errores);
        }
    } catch (e) {
        mostrarErrores('formTarea', { general: 'Error al guardar la tarea.' });
    }
}

async function marcarTarea(id, completada) {
    try {
        const data = await postJson(`medico/agenda/api/tareas/${id}/completar`, { completada });
        if (!data.ok) {
            mostrarMensaje(Object.values(data.errores || {})[0] || 'No se pudo actualizar la tarea.', 'error');
        }
    } catch (e) {
        mostrarMensaje('Error al actualizar la tarea.', 'error');
    }
    cargarTareas();
}

async function eliminarTarea(id) {
    const tarea = tareas.find(t => t.id === id);
    const confirmado = await Aviso.confirmar(
        `¿Está seguro de eliminar la tarea "${tarea ? tarea.titulo : ''}"?`, { aceptar: 'Eliminar' });
    if (!confirmado) return;

    try {
        const data = await postJson(`medico/agenda/api/tareas/${id}/eliminar`);
        if (data.ok) mostrarMensaje(data.mensaje, 'exito');
        else mostrarMensaje(Object.values(data.errores || {})[0] || 'No se pudo eliminar la tarea.', 'error');
    } catch (e) {
        mostrarMensaje('Error al eliminar la tarea.', 'error');
    }
    cargarTareas();
}