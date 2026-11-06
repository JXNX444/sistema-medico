/*
 * reportes.js - Graficas de la pantalla de Reportes (Chart.js 4).
 *
 * Lee window.REPORTE, que la plantilla reportes/reporte.html llena con:
 *   grafica, graficaSecundaria  -> 'BARRAS' | 'BARRAS_HORIZONTALES' | 'LINEA' | 'DONA'
 *   moneda                      -> true si los valores son quetzales
 *   columnaValor                -> nombre del valor (Consultas, Ingresos (Q), ...)
 *   etiquetas, valores          -> datos de la tabla, en el mismo orden
 *
 * Dibuja la grafica principal en #graficaPrincipal y la secundaria en #graficaSecundaria.
 */
(function () {
    'use strict';

    const R = window.REPORTE;
    if (!R || typeof Chart === 'undefined' || !R.etiquetas || R.etiquetas.length === 0) {
        return;
    }

    // Colores del sistema de diseno
    const CIAN = '#0891B2';                 // --sm-cyan-dark (una sola serie)
    const TINTA = '#0F172A';                // --sm-ink
    const GRIS = '#64748B';                 // --sm-slate-500
    const LINEA_GRID = '#F1F5F9';           // --sm-line

    // Colores por categoria (dona), siempre en este orden; maximo 8
    const CATEGORIAS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100',
        '#e87ba4', '#008300', '#6250d6', '#e34948'];
    const OTROS = '#94A3B8';                // --sm-slate-400 para "Otros"

    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.font.size = 12;
    Chart.defaults.color = GRIS;

    const valores = R.valores.map(Number);
    const total = valores.reduce((a, b) => a + b, 0);

    function formatear(n) {
        if (R.moneda) {
            return 'Q ' + Number(n).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
        }
        return Number(n).toLocaleString('en-US', {maximumFractionDigits: 2});
    }

    function formatearCorto(n) {
        // ejes: sin decimales
        return (R.moneda ? 'Q ' : '') + Number(n).toLocaleString('en-US', {maximumFractionDigits: 0});
    }

    function porcentaje(n) {
        return total === 0 ? '0.0 %' : (n * 100 / total).toFixed(1) + ' %';
    }

    /** Recorta etiquetas largas en los ejes (el tooltip muestra la completa). */
    function recortar(texto, max) {
        return texto.length > max ? texto.slice(0, max - 1) + '…' : texto;
    }

    const tooltipBase = {
        backgroundColor: TINTA,
        padding: 10,
        cornerRadius: 8,
        displayColors: false,
        titleFont: {weight: '700'},
    };

    // ---------------------------------------------------------------------
    // Barras (verticales u horizontales) y linea: una sola serie en cian
    // ---------------------------------------------------------------------
    function configCategorias(tipo) {
        const horizontal = tipo === 'BARRAS_HORIZONTALES';
        const esLinea = tipo === 'LINEA';
        const ejeValor = horizontal ? 'x' : 'y';
        const ejeCategoria = horizontal ? 'y' : 'x';

        const dataset = esLinea
            ? {
                data: valores,
                borderColor: CIAN,
                backgroundColor: CIAN,
                borderWidth: 2,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: CIAN,
                pointBorderColor: '#fff',
                pointBorderWidth: 2,
                tension: 0,
                fill: false
            }
            : {
                data: valores,
                backgroundColor: CIAN,
                hoverBackgroundColor: TINTA,
                borderRadius: 4,
                borderSkipped: 'start',   // esquinas redondas solo en la punta de la barra
                maxBarThickness: 32
            };

        const escalas = {};
        escalas[ejeValor] = {
            beginAtZero: true,
            grid: {color: LINEA_GRID},
            border: {display: false},
            ticks: {callback: (v) => formatearCorto(v), precision: R.moneda ? undefined : 0},
            title: {display: true, text: R.columnaValor, color: GRIS, font: {weight: '600'}}
        };
        escalas[ejeCategoria] = {
            grid: {display: false},
            border: {display: false},
            ticks: {
                color: TINTA,
                autoSkip: false,
                callback: function (valor) {
                    return recortar(this.getLabelForValue(valor), horizontal ? 26 : 14);
                }
            }
        };

        return {
            type: esLinea ? 'line' : 'bar',
            data: {labels: R.etiquetas, datasets: [dataset]},
            options: {
                indexAxis: horizontal ? 'y' : 'x',
                maintainAspectRatio: false,
                interaction: {mode: esLinea ? 'index' : 'nearest', intersect: false},
                plugins: {
                    legend: {display: false},
                    tooltip: Object.assign({}, tooltipBase, {
                        callbacks: {
                            label: (ctx) => R.columnaValor + ': ' + formatear(ctx.raw)
                                + '  (' + porcentaje(ctx.raw) + ')'
                        }
                    })
                },
                scales: escalas
            }
        };
    }

    // ---------------------------------------------------------------------
    // Dona: participacion de cada categoria. Mas de 8 -> las ultimas se juntan en "Otros"
    // ---------------------------------------------------------------------
    function configDona() {
        let etiquetas = R.etiquetas.slice();
        let datos = valores.slice();
        let colores;

        if (etiquetas.length > CATEGORIAS.length) {
            const visibles = CATEGORIAS.length - 1;   // 7 + "Otros"
            const resto = datos.slice(visibles).reduce((a, b) => a + b, 0);
            etiquetas = etiquetas.slice(0, visibles).concat(['Otros']);
            datos = datos.slice(0, visibles).concat([resto]);
            colores = CATEGORIAS.slice(0, visibles).concat([OTROS]);
        } else {
            colores = CATEGORIAS.slice(0, etiquetas.length);
        }

        return {
            type: 'doughnut',
            data: {
                labels: etiquetas,
                datasets: [{
                    data: datos,
                    backgroundColor: colores,
                    borderColor: '#fff',
                    borderWidth: 2,
                    hoverOffset: 6
                }]
            },
            options: {
                maintainAspectRatio: false,
                cutout: '62%',
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: TINTA,
                            boxWidth: 10,
                            boxHeight: 10,
                            usePointStyle: true,
                            pointStyle: 'circle',
                            padding: 12,
                            // la leyenda muestra el % para no depender solo del color
                            generateLabels: function (chart) {
                                const base = Chart.overrides.doughnut.plugins.legend.labels.generateLabels(chart);
                                return base.map((item, i) => Object.assign(item, {
                                    text: recortar(etiquetas[i], 22) + '  ' + porcentaje(datos[i])
                                }));
                            }
                        }
                    },
                    tooltip: Object.assign({}, tooltipBase, {
                        callbacks: {
                            label: (ctx) => formatear(ctx.raw) + '  (' + porcentaje(ctx.raw) + ')'
                        }
                    })
                }
            }
        };
    }

    function crear(idCanvas, tipo) {
        const canvas = document.getElementById(idCanvas);
        if (!canvas) {
            return;
        }
        const config = (tipo === 'DONA') ? configDona() : configCategorias(tipo);
        new Chart(canvas, config);
    }

    crear('graficaPrincipal', R.grafica);
    crear('graficaSecundaria', R.graficaSecundaria);
})();