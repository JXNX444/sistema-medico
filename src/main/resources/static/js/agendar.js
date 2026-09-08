/* CU-03 Agendar Citas: wizard de 5 pasos, todo en una sola pagina. */

const seleccion = {
    sucursalId: null, sucursalNombre: null,
    especialidadId: null, especialidadNombre: null,
    medicoId: null, medicoNombre: null,
    fecha: null, hora: null
};

let pasoActual = 1;

document.addEventListener('DOMContentLoaded', () => {
    // [RN-CU03-05] el calendario nunca deja elegir fechas pasadas.
    const inputFecha = document.getElementById('fechaCita');
    const hoy = new Date().toISOString().split('T')[0];
    inputFecha.min = hoy;
    inputFecha.addEventListener('change', onCambioFecha);

    document.querySelectorAll('#listaSucursales input[name="sucursal"]').forEach(radio => {
        radio.addEventListener('change', () => {
            seleccion.sucursalId = radio.value;
            seleccion.sucursalNombre = radio.dataset.nombre;
        });
    });
});

function mostrarError(paso, mensaje) {
    document.getElementById('errorPaso' + paso).textContent = mensaje || '';
}

/** Valida el paso actual antes de dejar avanzar. Devuelve true si esta OK. */
function validarPasoActual() {
    if (pasoActual === 1) {
        if (!seleccion.sucursalId) {
            mostrarError(1, 'Debe seleccionar una sucursal para continuar.');
            return false;
        }
    }
    if (pasoActual === 2) {
        if (!seleccion.especialidadId) {
            mostrarError(2, 'Debe seleccionar una especialidad medica para continuar.');
            return false;
        }
    }
    if (pasoActual === 3) {
        if (!seleccion.medicoId) {
            mostrarError(3, 'Debe seleccionar un medico para continuar.');
            return false;
        }
    }
    if (pasoActual === 4) {
        if (!seleccion.fecha || !seleccion.hora) {
            mostrarError(4, 'Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.');
            return false;
        }
    }
    return true;
}

function irAPaso(destino) {
    // Solo se valida cuando se avanza (destino > actual), no al regresar.
    if (destino > pasoActual && !validarPasoActual()) {
        return;
    }

    document.getElementById('paso' + pasoActual).classList.add('oculto');
    document.getElementById('paso' + destino).classList.remove('oculto');

    actualizarIndicador(destino);
    pasoActual = destino;

    if (destino === 2) cargarEspecialidades();
    if (destino === 3) cargarMedicos();
    if (destino === 5) renderResumen();
}

function actualizarIndicador(pasoDestino) {
    document.querySelectorAll('#indicadorPasos li').forEach(li => {
        const n = parseInt(li.dataset.paso, 10);
        li.classList.remove('paso-activo', 'paso-completo');
        if (n === pasoDestino) li.classList.add('paso-activo');
        else if (n < pasoDestino) li.classList.add('paso-completo');
    });
}

/* ---------- PASO 2: especialidades ---------- */

function cargarEspecialidades() {
    const contenedor = document.getElementById('listaEspecialidades');
    contenedor.innerHTML = '<p class="cargando">Cargando especialidades...</p>';

    fetch(CTX + 'citas/api/especialidades?sucursalId=' + seleccion.sucursalId)
        .then(r => r.json())
        .then(lista => {
            if (lista.length === 0) {
                contenedor.innerHTML = '<p class="error">Esta sucursal no tiene especialidades disponibles.</p>';
                return;
            }
            contenedor.innerHTML = '';
            lista.forEach(esp => {
                const label = document.createElement('label');
                label.className = 'opcion';
                label.innerHTML = `
<input type="radio" name="especialidad" value="${esp.id}" data-nombre="${esp.nombre}">
<span class="opcion-texto">
<i class="ti ti-stethoscope"></i>
<span>${esp.nombre}</span>
</span>`;
                label.querySelector('input').addEventListener('change', e => {
                    seleccion.especialidadId = e.target.value;
                    seleccion.especialidadNombre = e.target.dataset.nombre;
                    mostrarError(2, '');
                });
                contenedor.appendChild(label);
            });
        });
}

/* ---------- PASO 3: medicos ---------- */

function cargarMedicos() {
    const contenedor = document.getElementById('listaMedicos');
    contenedor.innerHTML = '<p class="cargando">Cargando medicos...</p>';

    const url = CTX + 'citas/api/medicos?sucursalId=' + seleccion.sucursalId
        + '&especialidadId=' + seleccion.especialidadId;

    fetch(url)
        .then(r => r.json())
        .then(lista => {
            if (lista.length === 0) {
                contenedor.innerHTML = '<p class="error">No hay medicos disponibles para esta especialidad en esta sucursal.</p>';
                return;
            }
            contenedor.innerHTML = '';
            lista.forEach(med => {
                const label = document.createElement('label');
                label.className = 'opcion';
                label.innerHTML = `
<input type="radio" name="medico" value="${med.id}" data-nombre="${med.nombre}">
<span class="opcion-texto">
<i class="ti ti-user"></i>
<span>${med.nombre}</span>
</span>`;
                label.querySelector('input').addEventListener('change', e => {
                    seleccion.medicoId = e.target.value;
                    seleccion.medicoNombre = e.target.dataset.nombre;
                    mostrarError(3, '');
                });
                contenedor.appendChild(label);
            });
        });
}

/* ---------- PASO 4: fecha y horarios ---------- */

function onCambioFecha(e) {
    seleccion.fecha = e.target.value;
    seleccion.hora = null;
    mostrarError(4, '');

    const bloque = document.getElementById('bloqueHorarios');
    const lista = document.getElementById('listaHorarios');

    if (!seleccion.fecha) {
        bloque.classList.add('oculto');
        return;
    }

    bloque.classList.remove('oculto');
    lista.innerHTML = '<p class="cargando">Buscando horarios disponibles...</p>';

    const url = CTX + 'citas/api/horarios?medicoId=' + seleccion.medicoId
        + '&sucursalId=' + seleccion.sucursalId + '&fecha=' + seleccion.fecha;

    fetch(url)
        .then(r => r.json())
        .then(horas => {
            if (horas.length === 0) {
                lista.innerHTML = '<p class="error">No hay horarios disponibles para esa fecha. Intente con otra.</p>';
                return;
            }
            lista.innerHTML = '';
            horas.forEach(h => {
                const div = document.createElement('div');
                div.className = 'hora-slot';
                div.textContent = h.substring(0, 5); // HH:mm
                div.addEventListener('click', () => {
                    document.querySelectorAll('.hora-slot').forEach(el => el.classList.remove('seleccionada'));
                    div.classList.add('seleccionada');
                    seleccion.hora = h;
                    mostrarError(4, '');
                });
                lista.appendChild(div);
            });
        });
}

/* ---------- PASO 5: resumen y confirmacion ---------- */

function renderResumen() {
    const cont = document.getElementById('resumenCita');
    cont.innerHTML = `
<p><strong>Sucursal:</strong> ${seleccion.sucursalNombre}</p>
<p><strong>Especialidad:</strong> ${seleccion.especialidadNombre}</p>
<p><strong>Medico:</strong> ${seleccion.medicoNombre}</p>
<p><strong>Fecha:</strong> ${seleccion.fecha}</p>
<p><strong>Hora:</strong> ${seleccion.hora ? seleccion.hora.substring(0, 5) : ''}</p>
    `;
}

function confirmarCita() {
    const motivo = document.getElementById('motivoCita').value.trim();

    if (motivo.length === 0) {
        mostrarError(5, 'El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso 0 caracteres.');
        return;
    }
    if (motivo.length < 10 || motivo.length > 2000) {
        mostrarError(5, 'El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso ' + motivo.length + ' caracteres.');
        return;
    }

    const btn = document.getElementById('btnConfirmar');
    btn.disabled = true;
    btn.textContent = 'Guardando...';

    fetch(CTX + 'citas/api/agendar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sucursalId: seleccion.sucursalId,
            especialidadId: seleccion.especialidadId,
            medicoId: seleccion.medicoId,
            fecha: seleccion.fecha,
            hora: seleccion.hora,
            motivo: motivo
        })
    })
        .then(r => r.json())
        .then(resp => {
            if (!resp.ok) {
                const primerError = Object.values(resp.errores)[0];
                mostrarError(5, primerError);
                btn.disabled = false;
                btn.innerHTML = '<i class="ti ti-calendar-check"></i> Confirmar cita';
                return;
            }
            // CU-03 -> CU-04: la cita quedo PENDIENTE_PAGO, vamos al pago.
            window.location.href = CTX + 'citas/pago/' + resp.citaId;
        })
        .catch(() => {
            mostrarError(5, 'Ocurrio un error de conexion. Intente de nuevo.');
            btn.disabled = false;
            btn.innerHTML = '<i class="ti ti-calendar-check"></i> Confirmar cita';
        });
}

/* ---------- Pantalla final e Y [FA03] temporizador de reserva ---------- */

function mostrarExito(resp) {
    document.getElementById('paso5').classList.add('oculto');
    document.getElementById('pasoExito').classList.remove('oculto');
    document.getElementById('numeroCitaTexto').textContent = resp.numeroCita;

    const expiraEn = new Date(resp.expiraEn).getTime();
    const contador = document.getElementById('contadorReserva');

    const intervalo = setInterval(() => {
        const restanteMs = expiraEn - Date.now();
        if (restanteMs <= 0) {
            clearInterval(intervalo);
            contador.textContent = '0:00';
            return;
        }
        const minutos = Math.floor(restanteMs / 60000);
        const segundos = Math.floor((restanteMs % 60000) / 1000);
        contador.textContent = minutos + ':' + String(segundos).padStart(2, '0');
    }, 1000);
}