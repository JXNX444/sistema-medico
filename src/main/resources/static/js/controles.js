/* ============================================================
   Controles con el estilo del sistema: listas desplegables, calendario
   y avisos. Se carga en TODAS las paginas desde fragments/layout :: head.

   - Cualquier <select> se muestra como una lista propia.
   - Cualquier <input type="date"> se muestra con un calendario propio.
   - Aviso.mostrar(...) y Aviso.confirmar(...) reemplazan a alert() y
     confirm() del navegador con una ventana del sistema (ver abajo).

   El control nativo sigue en la pagina (invisible): es el que guarda el
   valor, dispara onchange/addEventListener('change') y se envia en los
   formularios, asi que el JS de cada CU y los controladores no cambian.
   Tambien se detectan los controles que el JS agrega despues (modales,
   filas nuevas, etc.) y los cambios hechos por codigo (.value = ...,
   .disabled = ..., innerHTML de las opciones).

   Para dejar un control con su aspecto nativo: agregarle data-nativo.
   ============================================================ */
(function () {
    'use strict';

    const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio',
        'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    const DIAS = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sá', 'Do'];

    const SVG = (d) => '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        + 'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + d + '</svg>';
    const ICONO_FLECHA = SVG('<path d="M6 9l6 6 6-6"/>');
    const ICONO_CALENDARIO = SVG('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>');
    const ICONO_IZQ = SVG('<path d="M15 18l-6-6 6-6"/>');
    const ICONO_DER = SVG('<path d="M9 18l6-6-6-6"/>');

    /** Panel abierto en este momento (solo puede haber uno). */
    let abierto = null;

    // =================================================================
    // Utilidades
    // =================================================================

    function esc(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : String(texto);
        return div.innerHTML;
    }

    /** Hace que asignar .value (u otra propiedad) por codigo tambien redibuje el control. */
    function interceptar(el, propiedades, alCambiar) {
        propiedades.forEach(prop => {
            let proto = Object.getPrototypeOf(el);
            let desc = null;
            while (proto && !desc) {
                desc = Object.getOwnPropertyDescriptor(proto, prop);
                proto = Object.getPrototypeOf(proto);
            }
            if (!desc || !desc.set) return;
            Object.defineProperty(el, prop, {
                configurable: true,
                get() { return desc.get.call(this); },
                set(v) { desc.set.call(this, v); alCambiar(); }
            });
        });
    }

    /** Coloca el panel debajo del boton (o arriba si no cabe). Fijo a la ventana: no lo recorta ninguna tarjeta. */
    function posicionar(panel, ancla, igualarAncho) {
        const r = ancla.getBoundingClientRect();
        if (igualarAncho) panel.style.minWidth = r.width + 'px';
        const alto = panel.offsetHeight;
        const ancho = panel.offsetWidth;
        const cabeAbajo = window.innerHeight - r.bottom >= alto + 12;
        const top = (!cabeAbajo && r.top > alto + 12) ? r.top - alto - 6 : r.bottom + 6;
        const left = Math.max(8, Math.min(r.left, window.innerWidth - ancho - 8));
        panel.style.top = top + 'px';
        panel.style.left = left + 'px';
    }

    function abrir(control, panel, boton, igualarAncho, alCerrar) {
        cerrar();
        panel.classList.add('ctl-panel');
        panel.addEventListener('mousedown', e => e.preventDefault()); // el foco se queda en el boton
        document.body.appendChild(panel);
        posicionar(panel, boton, igualarAncho);
        control.classList.add('ctl--abierto');
        abierto = { control, panel, boton, igualarAncho, alCerrar };
    }

    function cerrar() {
        if (!abierto) return;
        const a = abierto;
        abierto = null;
        a.panel.remove();
        a.control.classList.remove('ctl--abierto');
        if (a.alCerrar) a.alCerrar();
    }

    document.addEventListener('mousedown', e => {
        if (abierto && !abierto.panel.contains(e.target) && !abierto.control.contains(e.target)) cerrar();
    });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') cerrar(); });
    window.addEventListener('resize', () => abierto && posicionar(abierto.panel, abierto.boton, abierto.igualarAncho));
    window.addEventListener('scroll', e => {
        if (abierto && !abierto.panel.contains(e.target)) posicionar(abierto.panel, abierto.boton, abierto.igualarAncho);
    }, true);

    /** Mete el control nativo en un contenedor y le agrega el boton visible. */
    function envolver(nativo, tipo) {
        const estilo = getComputedStyle(nativo);
        const control = document.createElement('div');
        control.className = 'ctl ctl--' + tipo;
        control.style.marginTop = estilo.marginTop;
        control.style.marginBottom = estilo.marginBottom;
        if (nativo.style.maxWidth) control.style.maxWidth = nativo.style.maxWidth;
        if (nativo.style.width === 'auto') {
            control.style.display = 'inline-block';
            control.style.width = 'auto';
        } else if (nativo.style.width) {
            control.style.width = nativo.style.width;
        }

        nativo.parentNode.insertBefore(control, nativo);
        control.appendChild(nativo);
        nativo.classList.add('ctl-nativo');
        nativo.tabIndex = -1;

        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'ctl-boton';
        control.appendChild(boton);

        // Si alguien le da foco al nativo (ej: clic en su <label>), pasarlo al boton.
        nativo.addEventListener('focus', () => boton.focus());
        return { control, boton };
    }

    function reflejarDeshabilitado(nativo, control, boton) {
        boton.disabled = nativo.disabled;
        control.classList.toggle('ctl--deshabilitado', nativo.disabled);
    }

    function disparar(el) {
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    }

    // =================================================================
    // Listas desplegables (<select>)
    // =================================================================

    function mejorarSelect(sel) {
        if (sel.dataset.ctl || sel.hasAttribute('data-nativo') || sel.multiple || sel.size > 1) return;
        sel.dataset.ctl = '1';

        const { control, boton } = envolver(sel, 'select');
        boton.innerHTML = '<span class="ctl-texto"></span><span class="ctl-icono">' + ICONO_FLECHA + '</span>';
        const texto = boton.querySelector('.ctl-texto');

        const pintar = () => {
            const op = sel.options[sel.selectedIndex];
            texto.textContent = op ? op.text : '';
            texto.classList.toggle('ctl-placeholder', !op || op.value === '');
            reflejarDeshabilitado(sel, control, boton);
        };

        boton.addEventListener('click', () => {
            if (abierto && abierto.control === control) cerrar();
            else abrirLista(sel, control, boton);
        });
        boton.addEventListener('keydown', e => {
            const estaAbierto = abierto && abierto.control === control;
            if (!estaAbierto && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
                e.preventDefault();
                abrirLista(sel, control, boton);
            }
        });

        sel.addEventListener('change', pintar);
        interceptar(sel, ['value', 'selectedIndex'], pintar);
        new MutationObserver(pintar).observe(sel, {
            childList: true, subtree: true, characterData: true,
            attributes: true, attributeFilter: ['disabled', 'selected']
        });
        pintar();
    }

    function abrirLista(sel, control, boton) {
        const opciones = [...sel.options];
        const panel = document.createElement('div');
        panel.className = 'ctl-lista';
        panel.innerHTML = opciones.length === 0
            ? '<div class="ctl-opcion ctl-opcion--vacia">Sin opciones</div>'
            : opciones.map((o, i) => {
                const clases = ['ctl-opcion'];
                if (o.value === '') clases.push('ctl-opcion--vacia');
                if (i === sel.selectedIndex) clases.push('ctl-opcion--activa');
                if (o.disabled) clases.push('ctl-opcion--deshabilitada');
                return `<div class="${clases.join(' ')}" data-i="${i}">${esc(o.text)}</div>`;
            }).join('');

        let foco = Math.max(0, sel.selectedIndex);
        const items = () => panel.querySelectorAll('.ctl-opcion[data-i]');
        const marcar = () => items().forEach((d, i) => {
            d.classList.toggle('ctl-opcion--foco', i === foco);
            if (i === foco) d.scrollIntoView({ block: 'nearest' });
        });

        const elegir = i => {
            const o = sel.options[i];
            if (!o || o.disabled) return;
            cerrar();
            if (sel.selectedIndex !== i) {
                sel.selectedIndex = i;
                disparar(sel);
            }
            boton.focus();
        };

        panel.addEventListener('click', e => {
            const d = e.target.closest('.ctl-opcion[data-i]');
            if (d) elegir(Number(d.dataset.i));
        });

        const teclas = e => {
            if (e.key === 'ArrowDown') { e.preventDefault(); foco = Math.min(opciones.length - 1, foco + 1); marcar(); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); foco = Math.max(0, foco - 1); marcar(); }
            else if (e.key === 'Enter') { e.preventDefault(); elegir(foco); }
            else if (e.key === 'Tab') { cerrar(); }
        };
        boton.addEventListener('keydown', teclas);

        abrir(control, panel, boton, true, () => boton.removeEventListener('keydown', teclas));
        const activa = panel.querySelector('.ctl-opcion--activa');
        if (activa) activa.scrollIntoView({ block: 'nearest' });
    }

    // =================================================================
    // Calendario (<input type="date">)
    // =================================================================

    const dosDigitos = n => String(n).padStart(2, '0');
    const aIso = d => d.getFullYear() + '-' + dosDigitos(d.getMonth() + 1) + '-' + dosDigitos(d.getDate());

    function deIso(texto) {
        if (!texto) return null;
        const [a, m, d] = texto.split('-').map(Number);
        return (a && m && d) ? new Date(a, m - 1, d) : null;
    }

    function mejorarFecha(inp) {
        if (inp.dataset.ctl || inp.hasAttribute('data-nativo')) return;
        inp.dataset.ctl = '1';

        const { control, boton } = envolver(inp, 'fecha');
        boton.innerHTML = '<span class="ctl-texto"></span><span class="ctl-icono">' + ICONO_CALENDARIO + '</span>';
        const texto = boton.querySelector('.ctl-texto');

        const pintar = () => {
            const d = deIso(inp.value);
            texto.textContent = d
                ? dosDigitos(d.getDate()) + '/' + dosDigitos(d.getMonth() + 1) + '/' + d.getFullYear()
                : (inp.getAttribute('placeholder') || 'Seleccione una fecha');
            texto.classList.toggle('ctl-placeholder', !d);
            reflejarDeshabilitado(inp, control, boton);
        };

        boton.addEventListener('click', () => {
            if (abierto && abierto.control === control) cerrar();
            else abrirCalendario(inp, control, boton);
        });

        inp.addEventListener('input', pintar);
        inp.addEventListener('change', pintar);
        interceptar(inp, ['value', 'valueAsDate', 'valueAsNumber'], pintar);
        new MutationObserver(pintar).observe(inp, { attributes: true, attributeFilter: ['disabled', 'value'] });
        pintar();
    }

    function abrirCalendario(inp, control, boton) {
        const hoy = new Date();
        hoy.setHours(0, 0, 0, 0);
        const min = deIso(inp.min);
        const max = deIso(inp.max);
        const seleccionada = deIso(inp.value);
        const fueraDeRango = d => (min && d < min) || (max && d > max);

        const base = seleccionada || (min && min > hoy ? min : hoy);
        let vista = new Date(base.getFullYear(), base.getMonth(), 1);

        const panel = document.createElement('div');
        panel.className = 'ctl-calendario';

        const dibujar = () => {
            const anio = vista.getFullYear();
            const mes = vista.getMonth();
            const huecos = (new Date(anio, mes, 1).getDay() + 6) % 7; // semana empieza en lunes
            const diasDelMes = new Date(anio, mes + 1, 0).getDate();

            let celdas = '<span></span>'.repeat(huecos);
            for (let dia = 1; dia <= diasDelMes; dia++) {
                const d = new Date(anio, mes, dia);
                const clases = ['ctl-dia'];
                if (seleccionada && d.getTime() === seleccionada.getTime()) clases.push('ctl-dia--sel');
                if (d.getTime() === hoy.getTime()) clases.push('ctl-dia--hoy');
                celdas += `<button type="button" class="${clases.join(' ')}" data-iso="${aIso(d)}"`
                    + `${fueraDeRango(d) ? ' disabled' : ''}>${dia}</button>`;
            }

            const anteriorBloqueado = min && new Date(anio, mes, 0) < min;
            const siguienteBloqueado = max && new Date(anio, mes + 1, 1) > max;

            panel.innerHTML = `
                <div class="ctl-cal-cab">
                    <button type="button" class="ctl-cal-nav" data-nav="-1" aria-label="Mes anterior"
                            ${anteriorBloqueado ? 'disabled' : ''}>${ICONO_IZQ}</button>
                    <div class="ctl-cal-titulo">${MESES[mes]} ${anio}</div>
                    <button type="button" class="ctl-cal-nav" data-nav="1" aria-label="Mes siguiente"
                            ${siguienteBloqueado ? 'disabled' : ''}>${ICONO_DER}</button>
                </div>
                <div class="ctl-cal-semana">${DIAS.map(d => `<span>${d}</span>`).join('')}</div>
                <div class="ctl-cal-dias">${celdas}</div>
                <div class="ctl-cal-pie">
                    <button type="button" class="ctl-cal-link" data-accion="borrar">Borrar</button>
                    <button type="button" class="ctl-cal-link ctl-cal-link--fuerte" data-accion="hoy"
                            ${fueraDeRango(hoy) ? 'disabled' : ''}>Hoy</button>
                </div>`;
        };

        const elegir = iso => {
            cerrar();
            if (inp.value !== iso) {
                inp.value = iso;
                disparar(inp);
            }
            boton.focus();
        };

        panel.addEventListener('click', e => {
            const b = e.target.closest('button');
            if (!b || b.disabled) return;
            if (b.dataset.nav) {
                vista = new Date(vista.getFullYear(), vista.getMonth() + Number(b.dataset.nav), 1);
                dibujar();
                posicionar(panel, boton, false);
            } else if (b.dataset.iso) {
                elegir(b.dataset.iso);
            } else if (b.dataset.accion === 'hoy') {
                elegir(aIso(hoy));
            } else if (b.dataset.accion === 'borrar') {
                elegir('');
            }
        });

        dibujar();
        abrir(control, panel, boton, false, null);
    }


    // =================================================================
    // Avisos: reemplazo de alert() y confirm() del navegador
    // =================================================================
    //
    //   Aviso.mostrar('Texto', { tipo: 'error' })            -> Promise (se resuelve al cerrar)
    //   Aviso.confirmar('Texto', { aceptar: 'Si, borrar' })  -> Promise<boolean>
    //
    // tipo: 'info' (default en mostrar), 'exito', 'error', 'advertencia' (default en confirmar)
    // A diferencia de alert/confirm NO detienen el codigo: usar await o .then().

    const ICONOS_AVISO = {
        info: '<path d="M12 16v-4M12 8h.01"/><circle cx="12" cy="12" r="10"/>',
        exito: '<circle cx="12" cy="12" r="10"/><path d="M8 12l3 3 5-6"/>',
        error: '<circle cx="12" cy="12" r="10"/><path d="M15 9l-6 6M9 9l6 6"/>',
        advertencia: '<path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/>'
    };
    const TITULOS_AVISO = {
        info: 'Aviso', exito: 'Listo', error: 'Ocurrió un problema', advertencia: '¿Está seguro?'
    };

    function ventanaAviso(mensaje, op, conCancelar) {
        return new Promise(resolver => {
            cerrar();
            const tipo = ICONOS_AVISO[op.tipo] ? op.tipo : (conCancelar ? 'advertencia' : 'info');
            const anterior = document.activeElement;

            const velo = document.createElement('div');
            velo.className = 'aviso-velo';
            velo.innerHTML = `
                <div class="aviso" role="${conCancelar ? 'alertdialog' : 'dialog'}" aria-modal="true">
                    <div class="aviso-icono aviso-icono--${tipo}">
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONOS_AVISO[tipo]}</svg>
                    </div>
                    <div class="aviso-titulo">${esc(op.titulo || TITULOS_AVISO[tipo])}</div>
                    <div class="aviso-texto">${esc(mensaje)}</div>
                    <div class="aviso-botones">
                        ${conCancelar ? `<button type="button" class="sm-btn sm-btn--ghost" data-r="0">${esc(op.cancelar || 'Cancelar')}</button>` : ''}
                        <button type="button" class="sm-btn sm-btn--primary" data-r="1">${esc(op.aceptar || op.boton || (conCancelar ? 'Sí, continuar' : 'Entendido'))}</button>
                    </div>
                </div>`;

            const terminar = valor => {
                document.removeEventListener('keydown', teclas, true);
                velo.classList.add('aviso-velo--saliendo');
                setTimeout(() => velo.remove(), 120);
                if (anterior && anterior.focus) anterior.focus();
                resolver(valor);
            };
            const teclas = e => {
                if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); terminar(false); }
                else if (e.key === 'Tab') {   // el foco no se sale de la ventana
                    const botones = [...velo.querySelectorAll('button')];
                    const i = botones.indexOf(document.activeElement);
                    e.preventDefault();
                    botones[(i + (e.shiftKey ? -1 : 1) + botones.length) % botones.length].focus();
                }
            };

            velo.addEventListener('click', e => {
                const b = e.target.closest('button[data-r]');
                if (b) terminar(b.dataset.r === '1');
                else if (e.target === velo) terminar(false);
            });
            document.addEventListener('keydown', teclas, true);
            document.body.appendChild(velo);
            velo.querySelector('button[data-r="1"]').focus();
        });
    }

    window.Aviso = {
        mostrar: (mensaje, opciones) => ventanaAviso(mensaje, opciones || {}, false).then(() => undefined),
        confirmar: (mensaje, opciones) => ventanaAviso(mensaje, opciones || {}, true)
    };

    // =================================================================
    // Arranque: lo que ya esta en la pagina + lo que el JS agregue despues
    // =================================================================

    function mejorar(raiz) {
        if (!raiz || raiz.nodeType !== 1) return;
        if (raiz.matches('select')) mejorarSelect(raiz);
        if (raiz.matches('input[type="date"]')) mejorarFecha(raiz);
        raiz.querySelectorAll('select').forEach(mejorarSelect);
        raiz.querySelectorAll('input[type="date"]').forEach(mejorarFecha);
    }

    function iniciar() {
        mejorar(document.body);
        new MutationObserver(cambios => {
            if (abierto && !document.body.contains(abierto.control)) cerrar();
            cambios.forEach(c => c.addedNodes.forEach(mejorar));
        }).observe(document.body, { childList: true, subtree: true });
    }

    window.Controles = { mejorar, cerrar };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
