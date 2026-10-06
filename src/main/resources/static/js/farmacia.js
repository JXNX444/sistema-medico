/* =====================================================================
   Modulo Farmacia - CU-11 (Despacho de Medicamentos)
   ===================================================================== */

const $ = (id) => document.getElementById(id);
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const q = (n) => 'Q' + Number(n || 0).toFixed(2);

let detalleActual = null;   // respuesta de /api/receta/{id}
let confirmarNoAdquiere = false;

// =====================================================================
// Paso 1-3: busqueda
// =====================================================================
$('btnBuscar').addEventListener('click', buscar);
['filtroReceta', 'filtroConsulta'].forEach(id =>
    $(id).addEventListener('keydown', e => { if (e.key === 'Enter') buscar(); }));
$('btnNuevoDespacho').addEventListener('click', nuevoDespacho);
$('btnOtroDespacho').addEventListener('click', nuevoDespacho);
$('btnVolver').addEventListener('click', () => mostrar('vistaBusqueda'));

function mostrar(vista) {
    ['vistaBusqueda', 'vistaDetalle', 'vistaResumen'].forEach(v => $(v).classList.toggle('oculto', v !== vista));
    window.scrollTo(0, 0);
}

function nuevoDespacho() {
    $('filtroReceta').value = '';
    $('filtroConsulta').value = '';
    $('errorBusqueda').textContent = '';
    $('zonaTabla').classList.add('oculto');
    mostrar('vistaBusqueda');
    $('filtroReceta').focus();
}

async function buscar() {
    const receta = $('filtroReceta').value.trim();
    const consulta = $('filtroConsulta').value.trim();
    $('errorBusqueda').textContent = '';
    $('zonaTabla').classList.add('oculto');

    if (!receta && !consulta) {
        $('errorBusqueda').textContent = 'Ingrese un ID de receta o un ID de consulta.';
        return;
    }
    if ((receta && !/^\d+$/.test(receta)) || (consulta && !/^\d+$/.test(consulta))) {
        $('errorBusqueda').textContent = 'Los IDs deben ser solo numeros.';
        return;
    }

    $('cargando').classList.remove('oculto');
    try {
        const url = CTX + 'farmacia/api/recetas?recetaId=' + encodeURIComponent(receta) +
            '&consultaId=' + encodeURIComponent(consulta);
        const data = await (await fetch(url)).json();
        if (!data.ok) {
            $('errorBusqueda').textContent = data.mensaje;
            return;
        }
        pintarRecetas(data.recetas);
    } catch (e) {
        $('errorBusqueda').textContent = 'Ocurrio un error al buscar. Intente de nuevo.';
    } finally {
        $('cargando').classList.add('oculto');
    }
}

function pintarRecetas(recetas) {
    $('cuerpoTabla').innerHTML = recetas.map(r => {
        let vigencia;
        if (r.despachada) {
            vigencia = '<span class="etiqueta gris">Despachada</span>';
        } else if (r.vigente) {
            vigencia = `<span class="etiqueta verde">Vigente · ${r.diasTranscurridos} día(s)</span>`;
        } else {
            vigencia = `<span class="etiqueta roja">Vencida · ${r.diasTranscurridos} días</span>`;
        }
        const deshabilitado = (!r.vigente || r.despachada) ? 'disabled' : '';
        return `<tr>
            <td><strong>#${r.recetaId}</strong></td>
            <td>${r.consultaId}</td>
            <td>${esc(r.paciente)}</td>
            <td>${r.fechaEmision}</td>
            <td>${vigencia}</td>
            <td>${esc(r.notas || '—')}</td>
            <td><button type="button" class="boton-despachar" data-id="${r.recetaId}" ${deshabilitado}>Despachar</button></td>
        </tr>`;
    }).join('');

    $('cuerpoTabla').querySelectorAll('.boton-despachar').forEach(b =>
        b.addEventListener('click', () => abrirReceta(b.dataset.id)));
    $('zonaTabla').classList.remove('oculto');
}

// =====================================================================
// Pasos 4-7 + FA01 + FA02: detalle de la receta
// =====================================================================
async function abrirReceta(recetaId) {
    $('errorBusqueda').textContent = '';
    try {
        const data = await (await fetch(CTX + 'farmacia/api/receta/' + recetaId)).json();
        if (!data.ok) {               // paso 4: receta vencida / ya despachada
            $('errorBusqueda').textContent = data.mensaje;
            return;
        }
        detalleActual = data;
        pintarDetalle(data);
        mostrar('vistaDetalle');
    } catch (e) {
        $('errorBusqueda').textContent = 'Ocurrio un error al abrir la receta.';
    }
}

function pintarDetalle(d) {
    $('dtTitulo').textContent = 'Receta #' + d.recetaId;
    $('dtSubtitulo').textContent = d.paciente + ' · Dr(a). ' + d.medico + ' · Emitida ' + d.fechaEmision;
    $('notasDespacho').value = '';
    $('errorDespacho').textContent = '';
    confirmarNoAdquiere = false;
    $('btnNoAdquiere').textContent = 'Paciente no adquiere';

    // FA01: alertas de inventario
    $('zonaAlertas').innerHTML = d.alertas.map(a =>
        `<div class="alerta ${a.includes('Sin inventario') ? 'roja' : 'ambar'}">${esc(a)}</div>`).join('');

    $('cuerpoItems').innerHTML = d.items.map((it, i) => {
        let disp;
        if (it.stock === null) {
            disp = '<span class="etiqueta roja">Sin inventario registrado</span>';
        } else if (it.stockBajo) {
            disp = `<span class="etiqueta ambar">Stock bajo: ${it.stock} (mín. ${it.stockMinimo})</span>`;
        } else {
            disp = `<span class="etiqueta verde">Disponible: ${it.stock}</span>`;
        }
        const inicial = it.stock === null ? 0 : Math.min(it.stock, it.cantidad);

        const opciones = d.catalogo
            .filter(m => m.medicamentoId !== it.medicamentoId)
            .map(m => `<option value="${m.medicamentoId}" data-precio="${m.precio}">
                ${esc(m.nombre)} — ${q(m.precio)} — ${m.stock === null ? 'sin inventario' : 'stock ' + m.stock}</option>`)
            .join('');

        return `<tr>
            <td><strong>${esc(it.nombre)}</strong><br><span class="ayuda">${esc(it.unidad)}</span></td>
            <td>${esc(it.dosis)}<br><span class="ayuda">${esc(it.frecuencia)} · ${esc(it.duracion)}</span></td>
            <td>${it.cantidad}</td>
            <td>${q(it.precioUnitario)}</td>
            <td>${disp}</td>
            <td style="width:110px;"><input type="number" class="inp-cantidad" data-i="${i}"
                   min="0" max="${it.cantidad}" step="1" value="${inicial}"></td>
            <td style="text-align:center;"><input type="checkbox" class="chk-sustituir" data-i="${i}"></td>
        </tr>
        <tr class="fila-sustitucion oculto" id="sust-${i}">
            <td colspan="7">
                <div class="contenido">
                    <div class="grupo">
                        <label>Medicamento alternativo</label>
                        <select class="sel-alternativo" data-i="${i}">
                            <option value="">Seleccione...</option>${opciones}
                        </select>
                    </div>
                    <div class="grupo">
                        <label>Razón de sustitución (obligatoria)</label>
                        <input type="text" class="inp-razon" data-i="${i}" maxlength="500">
                    </div>
                </div>
                <div class="alerta azul oculto" id="msgSust-${i}"></div>
            </td>
        </tr>`;
    }).join('');

    // Eventos de la tabla
    $('cuerpoItems').querySelectorAll('.inp-cantidad').forEach(inp => inp.addEventListener('input', recalcular));
    $('cuerpoItems').querySelectorAll('.chk-sustituir').forEach(chk => chk.addEventListener('change', () => {
        $('sust-' + chk.dataset.i).classList.toggle('oculto', !chk.checked);   // FA02 paso 2
        actualizarMensajeSustitucion(chk.dataset.i);
        recalcular();
    }));
    $('cuerpoItems').querySelectorAll('.sel-alternativo').forEach(sel => sel.addEventListener('change', () => {
        actualizarMensajeSustitucion(sel.dataset.i);
        recalcular();
    }));

    recalcular();
}

// FA02 paso 4: aviso de sustitucion
function actualizarMensajeSustitucion(i) {
    const chk = document.querySelector(`.chk-sustituir[data-i="${i}"]`);
    const sel = document.querySelector(`.sel-alternativo[data-i="${i}"]`);
    const msg = $('msgSust-' + i);
    if (chk.checked && sel.value) {
        const alt = sel.options[sel.selectedIndex].text.split('—')[0].trim();
        msg.textContent = `Medicamento ${detalleActual.items[i].nombre} sustituido por ${alt}. ` +
            'El médico tratante será notificado de la sustitución.';
        msg.classList.remove('oculto');
    } else {
        msg.classList.add('oculto');
    }
}

// Paso 7: total del despacho
function recalcular() {
    let total = 0;
    detalleActual.items.forEach((it, i) => {
        const cant = parseInt(document.querySelector(`.inp-cantidad[data-i="${i}"]`).value, 10) || 0;
        const chk = document.querySelector(`.chk-sustituir[data-i="${i}"]`);
        const sel = document.querySelector(`.sel-alternativo[data-i="${i}"]`);
        let precio = Number(it.precioUnitario);
        if (chk.checked && sel.value) {
            precio = Number(sel.options[sel.selectedIndex].dataset.precio);
        }
        total += precio * cant;
    });
    $('txtTotal').textContent = q(total);
}

// =====================================================================
// Pasos 8-12 + FA04: confirmar despacho
// =====================================================================
$('btnConfirmar').addEventListener('click', confirmar);

async function confirmar() {
    $('errorDespacho').textContent = '';
    const items = [];

    for (let i = 0; i < detalleActual.items.length; i++) {
        const it = detalleActual.items[i];
        const cant = parseInt(document.querySelector(`.inp-cantidad[data-i="${i}"]`).value, 10) || 0;
        const sustituir = document.querySelector(`.chk-sustituir[data-i="${i}"]`).checked;
        const alt = document.querySelector(`.sel-alternativo[data-i="${i}"]`).value;
        const razon = document.querySelector(`.inp-razon[data-i="${i}"]`).value.trim();

        if (cant < 0 || cant > it.cantidad) {
            $('errorDespacho').textContent = `La cantidad de ${it.nombre} debe estar entre 0 y ${it.cantidad}.`;
            return;
        }
        if (cant > 0 && sustituir && (!alt || !razon)) {
            $('errorDespacho').textContent = `Complete el medicamento alternativo y la razón de sustitución de ${it.nombre}.`;
            return;
        }
        items.push({
            medicamentoId: it.medicamentoId,
            cantidad: cant,
            sustituir: sustituir,
            alternativoId: sustituir && alt ? parseInt(alt, 10) : null,
            razon: sustituir ? razon : null
        });
    }

    if (!items.some(x => x.cantidad > 0)) {
        $('errorDespacho').textContent = 'Debe despachar al menos un medicamento (cantidad mayor a 0).';
        return;
    }

    $('btnConfirmar').disabled = true;
    $('btnConfirmar').textContent = 'Procesando...';
    try {
        const resp = await fetch(CTX + 'farmacia/api/despachar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                recetaId: detalleActual.recetaId,
                items: items,
                notas: $('notasDespacho').value.trim() || null
            })
        });
        const data = await resp.json();
        if (data.ok) {
            pintarResumen(data);
        } else {
            $('errorDespacho').textContent = data.mensaje;
        }
    } catch (e) {
        $('errorDespacho').textContent = 'Ocurrio un error al registrar el despacho.';
    } finally {
        $('btnConfirmar').disabled = false;
        $('btnConfirmar').textContent = 'Confirmar Despacho';
    }
}

function pintarResumen(r) {
    $('rsMensaje').textContent = r.mensaje;
    $('rsSustituciones').innerHTML = r.sustituciones.map(s => `<div class="alerta azul">${esc(s)}</div>`).join('');
    $('rsAlertas').innerHTML = r.alertas.map(a => `<div class="alerta roja">${esc(a)}</div>`).join('');   // FA04
    $('rsCuerpo').innerHTML = r.lineas.map(l => `<tr>
        <td>${esc(l.medicamento)}${l.sustituyeA ? `<br><span class="ayuda">Sustituye a ${esc(l.sustituyeA)}</span>` : ''}</td>
        <td>${l.cantidad}</td>
        <td>${l.precioFmt}</td>
        <td>${l.subtotalFmt}</td>
    </tr>`).join('');
    $('rsTotal').textContent = r.totalFmt;
    mostrar('vistaResumen');
}

// =====================================================================
// FA03: el paciente no desea adquirir los medicamentos
// =====================================================================
$('btnNoAdquiere').addEventListener('click', async () => {
    // Doble clic de confirmacion (sin alert/confirm del navegador)
    if (!confirmarNoAdquiere) {
        confirmarNoAdquiere = true;
        $('btnNoAdquiere').textContent = 'Clic de nuevo para confirmar';
        return;
    }
    $('errorDespacho').textContent = '';
    try {
        const resp = await fetch(CTX + 'farmacia/api/no-adquirido/' + detalleActual.recetaId, { method: 'POST' });
        const data = await resp.json();
        if (data.ok) {
            $('rsMensaje').textContent = data.mensaje;
            $('rsSustituciones').innerHTML = '';
            $('rsAlertas').innerHTML = '';
            $('rsCuerpo').innerHTML = '';
            $('rsTotal').textContent = 'Q0.00';
            mostrar('vistaResumen');
        } else {
            $('errorDespacho').textContent = data.mensaje;
        }
    } catch (e) {
        $('errorDespacho').textContent = 'Ocurrio un error al registrar.';
    } finally {
        confirmarNoAdquiere = false;
        $('btnNoAdquiere').textContent = 'Paciente no adquiere';
    }
});