/* =========================================================================
   LOGISTI-X — panel operativo

   Sin framework a proposito: el panel es una pieza de apoyo para operar y
   demostrar la API, y meterle un framework lo volveria un segundo proyecto
   que mantener. Todo lo que hace aqui se puede hacer igual desde Swagger o
   desde cualquier cliente HTTP; esta pantalla solo lo hace comodo.

   El token se guarda en memoria (no en localStorage): si se cierra la
   pestana hay que volver a entrar, que es lo correcto para una herramienta
   interna.
   ========================================================================= */

const API = '/api/v1';

const estado = {
    token: null,
    usuario: null,
    despachos: [],
    transportadores: [],
    pedidos: [],
    filtroEstado: '',
    seleccionado: null,
    flujo: null,
};

const $ = (id) => document.getElementById(id);

// ===================================================== utilidades ========

const ESTADOS = {
    REGISTRADO:     'Registrado',
    EN_PREPARACION: 'En preparación',
    ASIGNADO:       'Asignado',
    EN_RUTA:        'En ruta',
    ENTREGADO:      'Entregado',
    FALLIDO:        'Fallido',
    DEVUELTO:       'Devuelto',
    CANCELADO:      'Cancelado',
};

const pesos = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
const money = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

const kg = (v) => v == null ? '—' : `${pesos.format(v)} kg`;
const cop = (v) => v == null ? '—' : money.format(v);
const texto = (v) => v == null || v === '' ? '—' : String(v);

function fecha(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short' });
}

function fechaHora(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleString('es-CO', {
        day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
    });
}

function escapar(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
}

function avisar(titulo, detalle, esError = false) {
    const nodo = document.createElement('div');
    nodo.className = `aviso${esError ? ' error' : ''}`;
    nodo.innerHTML = `<div class="titulo">${escapar(titulo)}</div>` +
        (detalle ? `<div class="detalle">${escapar(detalle)}</div>` : '');
    $('avisos').appendChild(nodo);

    setTimeout(() => {
        nodo.classList.add('saliendo');
        setTimeout(() => nodo.remove(), 200);
    }, esError ? 6000 : 3500);
}

// ========================================================== red =========

async function llamar(ruta, opciones = {}) {
    const respuesta = await fetch(API + ruta, {
        ...opciones,
        headers: {
            'Content-Type': 'application/json',
            ...(estado.token ? { Authorization: `Bearer ${estado.token}` } : {}),
            ...(opciones.headers || {}),
        },
    });

    if (respuesta.status === 204) return null;

    const cuerpo = await respuesta.json().catch(() => null);

    if (!respuesta.ok) {
        // El backend responde en formato Problem Details (RFC 7807): el campo
        // 'detail' trae el mensaje pensado para leerse, no el codigo interno.
        const mensaje = cuerpo?.detail || cuerpo?.title || `Error ${respuesta.status}`;
        const error = new Error(mensaje);
        error.codigo = cuerpo?.codigo;
        throw error;
    }
    return cuerpo;
}

// ======================================================= acceso =========

$('formAcceso').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const boton = evento.target.querySelector('button');
    boton.disabled = true;
    boton.textContent = 'Entrando…';

    try {
        const datos = await llamar('/auth/login', {
            method: 'POST',
            body: JSON.stringify({
                username: $('usuario').value.trim(),
                password: $('clave').value,
            }),
        });

        estado.token = datos.token;
        estado.usuario = datos.nombre || datos.username;

        $('pantallaAcceso').style.display = 'none';
        $('aplicacion').classList.add('visible');
        $('sesionInfo').textContent = `${estado.usuario} · ${datos.rol || ''}`.trim();

        await Promise.all([cargarTransportadores(), cargarIntegracion()]);
        await cargarDespachos();
        conectarEnVivo();

    } catch (error) {
        avisar('No se pudo entrar', error.message, true);
    } finally {
        boton.disabled = false;
        boton.textContent = 'Entrar';
    }
});

$('btnSalir').addEventListener('click', () => location.reload());

// ======================================================== datos =========

async function cargarIntegracion() {
    try {
        const info = await llamar('/integracion/estado');
        $('resumenIntegracion').textContent =
            `Ventas: ${info.ventas} · Clientes: ${info.crm}`;
    } catch {
        $('resumenIntegracion').textContent = '';
    }
}

async function cargarTransportadores() {
    estado.transportadores = await llamar('/transportadores?soloActivos=true');
    const select = $('filtroTransportador');
    select.length = 1;
    estado.transportadores.forEach((t) => {
        const opcion = document.createElement('option');
        opcion.value = t.id;
        opcion.textContent = `${t.nombre} · ${t.tipoVehiculo.toLowerCase()}`;
        select.appendChild(opcion);
    });
}

async function cargarDespachos() {
    const parametros = new URLSearchParams({ tamano: '100' });
    if (estado.filtroEstado) parametros.set('estado', estado.filtroEstado);
    if ($('filtroTransportador').value) parametros.set('transportadorId', $('filtroTransportador').value);
    const busqueda = $('filtroTexto').value.trim();
    if (busqueda) parametros.set('texto', busqueda);

    $('estadoTabla').innerHTML = '<div class="cargando">Cargando despachos…</div>';

    try {
        const pagina = await llamar(`/despachos?${parametros}`);
        estado.despachos = pagina.contenido;
        pintarTabla();
        pintarMetricas();
    } catch (error) {
        $('estadoTabla').innerHTML =
            `<div class="vacio"><h3>No se pudo cargar</h3><p>${escapar(error.message)}</p></div>`;
    }
}

// ======================================================= pintado ========

function pintarMetricas() {
    const conteo = {};
    estado.despachos.forEach((d) => { conteo[d.estado] = (conteo[d.estado] || 0) + 1; });

    const enCalle = (conteo.EN_RUTA || 0) + (conteo.FALLIDO || 0);
    const tarjetas = [
        { clave: '',           etiqueta: 'Total',       valor: estado.despachos.length },
        { clave: 'EN_RUTA',    etiqueta: 'En ruta',     valor: conteo.EN_RUTA || 0 },
        { clave: 'FALLIDO',    etiqueta: 'Con novedad', valor: conteo.FALLIDO || 0 },
        { clave: 'ENTREGADO',  etiqueta: 'Entregados',  valor: conteo.ENTREGADO || 0 },
        { clave: 'REGISTRADO', etiqueta: 'Por alistar', valor: (conteo.REGISTRADO || 0) + (conteo.EN_PREPARACION || 0) },
    ];

    $('metricas').innerHTML = tarjetas.map((t) => `
        <button class="metrica" data-estado="${t.clave}"
                aria-pressed="${estado.filtroEstado === t.clave && t.clave !== ''}">
            <span class="metrica-etiqueta">
                ${t.clave ? `<span class="punto ${t.clave.toLowerCase()}"></span>` : ''}
                ${t.etiqueta}
            </span>
            <span class="metrica-valor cifra">${t.valor}</span>
        </button>
    `).join('');

    $('metricas').querySelectorAll('.metrica').forEach((boton) => {
        boton.addEventListener('click', () => {
            estado.filtroEstado = boton.dataset.estado;
            $('filtroEstado').value = estado.filtroEstado;
            cargarDespachos();
        });
    });

    document.title = enCalle > 0
        ? `(${enCalle} en calle) LOGISTI-X`
        : 'LOGISTI-X · Despachos y entregas';
}

function pintarTabla() {
    const cuerpo = $('cuerpoTabla');

    if (estado.despachos.length === 0) {
        cuerpo.innerHTML = '';
        $('estadoTabla').innerHTML = `
            <div class="vacio">
                <h3>Sin despachos que mostrar</h3>
                <p>Registre uno desde un pedido de SALES-X o cambie los filtros.</p>
            </div>`;
        return;
    }

    $('estadoTabla').innerHTML = '';
    cuerpo.innerHTML = estado.despachos.map((d) => `
        <tr data-id="${d.despachoId}">
            <td>
                <div class="mono">${escapar(d.numero)}</div>
                <div class="secundario">${escapar(d.pedidoNumero || '')}</div>
            </td>
            <td class="mono">${escapar(d.numeroGuia || '—')}</td>
            <td>${escapar(d.clienteNombre)}</td>
            <td>${escapar(d.ciudadEntrega)}</td>
            <td><span class="etiqueta ${d.estado.toLowerCase()}">
                <span class="punto ${d.estado.toLowerCase()}"></span>${ESTADOS[d.estado]}
            </span></td>
            <td>${escapar(d.transportadorNombre || '—')}</td>
            <td class="num">${kg(d.pesoTotalKg)}</td>
            <td class="num">${cop(d.costoFlete)}</td>
            <td>${fecha(d.fechaEstimadaEntrega)}</td>
        </tr>
    `).join('');

    cuerpo.querySelectorAll('tr').forEach((fila) => {
        fila.addEventListener('click', () => abrirDetalle(fila.dataset.id));
    });
}

// ======================================================= detalle ========

async function abrirDetalle(despachoId) {
    $('velo').classList.add('visible');
    $('panelDetalle').classList.add('visible');
    $('panelDetalle').setAttribute('aria-hidden', 'false');
    $('detalleCuerpo').innerHTML = '<div class="cargando">Cargando…</div>';
    $('detalleAcciones').innerHTML = '';

    try {
        const [despacho, traza] = await Promise.all([
            llamar(`/despachos/${despachoId}`),
            llamar(`/despachos/${despachoId}/trazabilidad`),
        ]);
        estado.seleccionado = despacho;
        pintarDetalle(despacho, traza);
    } catch (error) {
        $('detalleCuerpo').innerHTML =
            `<div class="vacio"><h3>No se pudo abrir</h3><p>${escapar(error.message)}</p></div>`;
    }
}

function cerrarDetalle() {
    $('velo').classList.remove('visible');
    $('panelDetalle').classList.remove('visible');
    $('panelDetalle').setAttribute('aria-hidden', 'true');
    estado.seleccionado = null;
}

$('btnCerrarPanel').addEventListener('click', cerrarDetalle);
$('velo').addEventListener('click', cerrarDetalle);

document.addEventListener('keydown', (evento) => {
    if (evento.key !== 'Escape') return;
    if ($('modal').classList.contains('visible')) cerrarModal();
    else if ($('panelDetalle').classList.contains('visible')) cerrarDetalle();
});

function pintarDetalle(d, traza) {
    $('detalleTitulo').textContent = `${d.numero} · ${ESTADOS[d.estado]}`;
    $('detalleGuia').textContent = d.numeroGuia || 'sin guía todavía';

    const carga = d.items.map((i) => `
        <tr>
            <td>${escapar(i.productoNombre)}<div class="secundario mono">${escapar(i.productoSku)}</div></td>
            <td class="num">${i.cantidad}</td>
            <td class="num">${kg(i.pesoLineaKg)}</td>
        </tr>`).join('');

    const eventos = traza.map((e) => `
        <li class="${e.clase === 'NOVEDAD' ? 'novedad' : ''}">
            <div class="titulo">${escapar(e.titulo)}</div>
            ${e.detalle ? `<div class="secundario">${escapar(e.detalle)}</div>` : ''}
            <div class="meta">${fechaHora(e.ocurridoEn)} · ${escapar(e.usuario)}${
                e.ubicacion ? ` · ${escapar(e.ubicacion)}` : ''}</div>
        </li>`).join('');

    $('detalleCuerpo').innerHTML = `
        <div class="bloque">
            <h3>Entrega</h3>
            <dl class="datos">
                <dt>Cliente</dt><dd>${escapar(d.clienteNombre)}</dd>
                <dt>Dirección</dt><dd>${escapar(d.direccionEntrega)}<br>
                    <span class="secundario">${escapar(d.ciudadEntrega)}${
                        d.referenciaDireccion ? ' · ' + escapar(d.referenciaDireccion) : ''}</span></dd>
                <dt>Contacto</dt><dd>${texto(d.clienteTelefono)}</dd>
                <dt>Pedido</dt><dd class="mono">${texto(d.pedidoNumero)}</dd>
                <dt>Fecha prometida</dt><dd>${fecha(d.fechaEstimadaEntrega)}</dd>
                <dt>Intentos</dt><dd>${d.intentosEntrega}</dd>
                ${d.receptorNombre ? `<dt>Recibió</dt><dd>${escapar(d.receptorNombre)}
                    ${d.receptorDocumento ? `<span class="secundario">· ${escapar(d.receptorDocumento)}</span>` : ''}</dd>` : ''}
            </dl>
        </div>

        <div class="bloque">
            <h3>Transporte</h3>
            <dl class="datos">
                <dt>Transportador</dt><dd>${texto(d.transportadorNombre)}</dd>
                <dt>Vehículo</dt><dd>${texto(d.vehiculo)}</dd>
                <dt>Ruta</dt><dd class="mono">${texto(d.rutaCodigo)}</dd>
                <dt>Peso</dt><dd>${kg(d.pesoTotalKg)} · ${d.unidadesTotales} unidades</dd>
                <dt>Valor declarado</dt><dd>${cop(d.valorDeclarado)}</dd>
                <dt>Flete</dt><dd>${cop(d.costoFlete)}</dd>
            </dl>
        </div>

        <div class="bloque">
            <h3>Carga</h3>
            <div class="tarjeta tabla-scroll">
                <table>
                    <thead><tr><th>Producto</th><th class="num">Cant.</th><th class="num">Peso</th></tr></thead>
                    <tbody>${carga}</tbody>
                </table>
            </div>
        </div>

        <div class="bloque">
            <h3>Trazabilidad</h3>
            <ul class="linea-tiempo">${eventos}</ul>
        </div>`;

    pintarAcciones(d);
}

function pintarAcciones(d) {
    const acciones = [];
    const puede = (destino) => d.transicionesPosibles.includes(destino);

    if (puede('EN_PREPARACION')) {
        acciones.push({ texto: 'Alistar', principal: true, fn: () => accion(`/despachos/${d.despachoId}/preparar`) });
    }
    if (puede('ASIGNADO')) {
        acciones.push({ texto: 'Asignar transportador', principal: true, fn: () => modalAsignar(d) });
    }
    if (d.estado === 'ASIGNADO') {
        acciones.push({ texto: 'Sacar a ruta', principal: true, fn: () => accion(`/despachos/${d.despachoId}/despachar`, {}) });
    }
    if (d.estado === 'EN_RUTA') {
        acciones.push({ texto: 'Confirmar entrega', principal: true, fn: () => modalEntrega(d) });
        acciones.push({ texto: 'Registrar novedad', fn: () => modalNovedad(d) });
    }
    if (d.estado === 'FALLIDO') {
        acciones.push({ texto: 'Reprogramar', principal: true, fn: () => accion(`/despachos/${d.despachoId}/reprogramar`, {}) });
        acciones.push({ texto: 'Devolver', peligro: true, fn: () => modalMotivo(d, 'devolver', 'Devolver la carga') });
    }
    if (puede('CANCELADO')) {
        acciones.push({ texto: 'Cancelar', peligro: true, fn: () => modalMotivo(d, 'cancelar', 'Cancelar el despacho') });
    }
    if (d.numeroGuia) {
        acciones.push({ texto: 'Ver rótulo', fn: () => verRotulo(d) });
    }

    const pie = $('detalleAcciones');
    pie.innerHTML = '';

    if (acciones.length === 0) {
        pie.innerHTML = '<span class="secundario">Despacho cerrado: no admite más movimientos.</span>';
        return;
    }

    acciones.forEach((a) => {
        const boton = document.createElement('button');
        boton.className = `boton${a.principal ? ' principal' : ''}${a.peligro ? ' peligro' : ''}`;
        boton.textContent = a.texto;
        boton.addEventListener('click', a.fn);
        pie.appendChild(boton);
    });
}

async function accion(ruta, cuerpo) {
    try {
        await llamar(ruta, {
            method: 'POST',
            body: JSON.stringify(cuerpo ?? {}),
        });
        avisar('Listo', 'El despacho se actualizó.');
        const id = estado.seleccionado.despachoId;
        await cargarDespachos();
        await abrirDetalle(id);
    } catch (error) {
        avisar('No se pudo completar', error.message, true);
    }
}

async function verRotulo(d) {
    try {
        const guia = await llamar(`/despachos/${d.despachoId}/guia`);
        abrirModal({
            titulo: 'Rótulo de la guía',
            ayuda: 'Esto es lo que se imprime y se pega en la caja.',
            campos: [{ tipo: 'textarea', id: 'rotulo', etiqueta: '', valor: guia.rotulo, filas: 6 }],
            aceptar: 'Cerrar',
            sinCancelar: true,
            alAceptar: () => cerrarModal(),
        });
    } catch (error) {
        avisar('No se pudo leer la guía', error.message, true);
    }
}

// ========================================================= modal ========

function abrirModal({ titulo, ayuda, campos, aceptar = 'Confirmar', sinCancelar = false, alAceptar }) {
    $('modalTitulo').textContent = titulo;
    $('modalAyuda').textContent = ayuda || '';
    $('modalAceptar').textContent = aceptar;
    $('modalCancelar').style.display = sinCancelar ? 'none' : '';

    $('modalForm').innerHTML = campos.map((c) => {
        if (c.tipo === 'select') {
            return `<label class="campo"><span>${escapar(c.etiqueta)}</span>
                <select id="${c.id}">${c.opciones.map((o) =>
                    `<option value="${escapar(o.valor)}">${escapar(o.texto)}</option>`).join('')}</select></label>`;
        }
        if (c.tipo === 'textarea') {
            return `<label class="campo"><span>${escapar(c.etiqueta)}</span>
                <textarea id="${c.id}" rows="${c.filas || 3}" class="mono">${escapar(c.valor || '')}</textarea></label>`;
        }
        return `<label class="campo"><span>${escapar(c.etiqueta)}</span>
            <input type="${c.tipo || 'text'}" id="${c.id}" value="${escapar(c.valor || '')}"
                   placeholder="${escapar(c.ejemplo || '')}"></label>`;
    }).join('');

    estado.flujo = alAceptar;
    $('modal').classList.add('visible');

    const primero = $('modalForm').querySelector('input, select, textarea');
    if (primero) primero.focus();
}

function cerrarModal() {
    $('modal').classList.remove('visible');
    estado.flujo = null;
}

$('modalCancelar').addEventListener('click', cerrarModal);

$('modalAceptar').addEventListener('click', async () => {
    if (!estado.flujo) return cerrarModal();
    const boton = $('modalAceptar');
    boton.disabled = true;
    try {
        await estado.flujo();
    } finally {
        boton.disabled = false;
    }
});

// ============================================ flujos de operacion =======

async function modalNuevo() {
    try {
        estado.pedidos = await llamar('/pedidos');
    } catch {
        estado.pedidos = [];
    }

    if (estado.pedidos.length === 0) {
        avisar('Sin pedidos disponibles',
            'Con SALES-X real, indique el pedidoId desde la API.', true);
        return;
    }

    abrirModal({
        titulo: 'Registrar despacho',
        ayuda: 'Se toma la carga y la dirección del pedido; puede corregirlas después.',
        campos: [
            {
                tipo: 'select', id: 'pedido', etiqueta: 'Pedido de SALES-X',
                opciones: estado.pedidos.map((p) => ({
                    valor: p.pedidoId,
                    texto: `${p.numero} · ${p.clienteNombre} · ${p.estado}`,
                })),
            },
            { id: 'referencia', etiqueta: 'Punto de referencia (opcional)',
              ejemplo: 'Portón azul, al lado de la panadería' },
            { id: 'observaciones', etiqueta: 'Observaciones (opcional)',
              ejemplo: 'Entregar antes de las 5 p. m.' },
        ],
        aceptar: 'Registrar',
        alAceptar: async () => {
            try {
                const creado = await llamar('/despachos', {
                    method: 'POST',
                    body: JSON.stringify({
                        pedidoId: $('pedido').value,
                        referenciaDireccion: $('referencia').value || null,
                        observaciones: $('observaciones').value || null,
                    }),
                });
                cerrarModal();
                avisar('Despacho registrado', creado.numero);
                await cargarDespachos();
                abrirDetalle(creado.despachoId);
            } catch (error) {
                avisar('No se pudo registrar', error.message, true);
            }
        },
    });
}

function modalAsignar(d) {
    const aptos = estado.transportadores.filter((t) => Number(t.capacidadKg) >= Number(d.pesoTotalKg));
    const lista = aptos.length > 0 ? aptos : estado.transportadores;

    abrirModal({
        titulo: 'Asignar transportador',
        ayuda: aptos.length > 0
            ? `Se muestran los que pueden con ${kg(d.pesoTotalKg)}. La guía se genera al asignar.`
            : `Ningún vehículo activo admite ${kg(d.pesoTotalKg)}: divida la carga.`,
        campos: [
            {
                tipo: 'select', id: 'transportador', etiqueta: 'Transportador',
                opciones: lista.map((t) => ({
                    valor: t.id,
                    texto: `${t.nombre} · ${t.tipoVehiculo.toLowerCase()} · hasta ${kg(t.capacidadKg)}`,
                })),
            },
            { tipo: 'date', id: 'fechaEntrega', etiqueta: 'Fecha prometida (opcional)' },
        ],
        aceptar: 'Asignar y generar guía',
        alAceptar: async () => {
            try {
                await llamar(`/despachos/${d.despachoId}/asignar`, {
                    method: 'POST',
                    body: JSON.stringify({
                        transportadorId: $('transportador').value,
                        fechaEstimadaEntrega: $('fechaEntrega').value || null,
                    }),
                });
                cerrarModal();
                avisar('Asignado', 'Se generó el número de guía.');
                await cargarDespachos();
                abrirDetalle(d.despachoId);
            } catch (error) {
                avisar('No se pudo asignar', error.message, true);
            }
        },
    });
}

function modalEntrega(d) {
    abrirModal({
        titulo: 'Confirmar entrega',
        ayuda: 'Sin el nombre de quien recibe no hay entrega: es la evidencia ante un reclamo.',
        campos: [
            { id: 'receptor', etiqueta: 'Quién recibió', ejemplo: 'Ana María Gil' },
            { id: 'documento', etiqueta: 'Documento (opcional)', ejemplo: '43112233' },
            { id: 'observacion', etiqueta: 'Observación (opcional)', ejemplo: 'Recibido en portería' },
        ],
        aceptar: 'Confirmar entrega',
        alAceptar: async () => {
            if (!$('receptor').value.trim()) {
                avisar('Falta un dato', 'Escriba quién recibió la mercancía.', true);
                return;
            }
            try {
                await llamar(`/despachos/${d.despachoId}/entregar`, {
                    method: 'POST',
                    body: JSON.stringify({
                        receptorNombre: $('receptor').value.trim(),
                        receptorDocumento: $('documento').value || null,
                        observacion: $('observacion').value || null,
                        ubicacion: d.ciudadEntrega,
                    }),
                });
                cerrarModal();
                avisar('Entrega confirmada', d.numero);
                await cargarDespachos();
                abrirDetalle(d.despachoId);
            } catch (error) {
                avisar('No se pudo confirmar', error.message, true);
            }
        },
    });
}

function modalNovedad(d) {
    const tipos = [
        ['CLIENTE_AUSENTE', 'Cliente ausente'],
        ['DIRECCION_ERRADA', 'Dirección errada'],
        ['CLIENTE_RECHAZA', 'El cliente rechaza'],
        ['DEMORA_EN_VIA', 'Demora en vía'],
        ['AVERIA_VEHICULO', 'Avería del vehículo'],
        ['CARGA_AVERIADA', 'Carga averiada'],
        ['ZONA_DE_RIESGO', 'Zona de riesgo'],
        ['DATOS_INCOMPLETOS', 'Datos incompletos'],
        ['OTRA', 'Otra'],
    ];

    abrirModal({
        titulo: 'Registrar novedad',
        ayuda: 'Las novedades que impiden entregar dejan el despacho en fallido y suman un intento.',
        campos: [
            { tipo: 'select', id: 'tipoNovedad', etiqueta: 'Tipo',
              opciones: tipos.map(([valor, texto]) => ({ valor, texto })) },
            { id: 'descripcionNovedad', etiqueta: 'Qué pasó',
              ejemplo: 'Timbré tres veces y nadie abrió' },
            { id: 'ubicacionNovedad', etiqueta: 'Dónde', valor: d.ciudadEntrega },
        ],
        aceptar: 'Registrar',
        alAceptar: async () => {
            if (!$('descripcionNovedad').value.trim()) {
                avisar('Falta un dato', 'Describa la novedad.', true);
                return;
            }
            try {
                await llamar(`/despachos/${d.despachoId}/novedades`, {
                    method: 'POST',
                    body: JSON.stringify({
                        tipo: $('tipoNovedad').value,
                        descripcion: $('descripcionNovedad').value.trim(),
                        ubicacion: $('ubicacionNovedad').value || null,
                    }),
                });
                cerrarModal();
                avisar('Novedad registrada', '');
                await cargarDespachos();
                abrirDetalle(d.despachoId);
            } catch (error) {
                avisar('No se pudo registrar', error.message, true);
            }
        },
    });
}

function modalMotivo(d, ruta, titulo) {
    abrirModal({
        titulo,
        ayuda: 'El motivo queda en la trazabilidad del despacho.',
        campos: [{ id: 'motivo', etiqueta: 'Motivo', ejemplo: 'Tres intentos de entrega fallidos' }],
        aceptar: titulo,
        alAceptar: async () => {
            if (!$('motivo').value.trim()) {
                avisar('Falta un dato', 'Escriba el motivo.', true);
                return;
            }
            try {
                await llamar(`/despachos/${d.despachoId}/${ruta}`, {
                    method: 'POST',
                    body: JSON.stringify({ motivo: $('motivo').value.trim() }),
                });
                cerrarModal();
                avisar('Listo', `${d.numero} actualizado.`);
                await cargarDespachos();
                abrirDetalle(d.despachoId);
            } catch (error) {
                avisar('No se pudo completar', error.message, true);
            }
        },
    });
}

// ====================================================== en vivo =========

function conectarEnVivo() {
    // EventSource no admite cabeceras: por eso el token va por parametro en
    // esta unica ruta (ver JwtFiltro).
    const fuente = new EventSource(`${API}/despachos/eventos?access_token=${encodeURIComponent(estado.token)}`);

    fuente.addEventListener('conectado', () => {
        $('indicadorVivo').classList.add('conectado');
        $('textoVivo').textContent = 'en vivo';
    });

    fuente.addEventListener('despacho', (evento) => {
        const cambiado = JSON.parse(evento.data);
        const indice = estado.despachos.findIndex((d) => d.despachoId === cambiado.despachoId);

        if (indice >= 0) estado.despachos[indice] = cambiado;
        else estado.despachos.unshift(cambiado);

        pintarTabla();
        pintarMetricas();

        const fila = $('cuerpoTabla').querySelector(`tr[data-id="${cambiado.despachoId}"]`);
        if (fila) fila.classList.add('destacada');
    });

    fuente.onerror = () => {
        $('indicadorVivo').classList.remove('conectado');
        $('textoVivo').textContent = 'reconectando…';
    };
}

// ======================================================= eventos ========

$('btnNuevo').addEventListener('click', modalNuevo);

$('filtroEstado').addEventListener('change', (e) => {
    estado.filtroEstado = e.target.value;
    cargarDespachos();
});

$('filtroTransportador').addEventListener('change', cargarDespachos);

let temporizador;
$('filtroTexto').addEventListener('input', () => {
    clearTimeout(temporizador);
    temporizador = setTimeout(cargarDespachos, 300);
});

$('btnLimpiar').addEventListener('click', () => {
    estado.filtroEstado = '';
    $('filtroEstado').value = '';
    $('filtroTransportador').value = '';
    $('filtroTexto').value = '';
    cargarDespachos();
});
