# 04 · Contratos de integración

**Semana 3** — cierre 4 de septiembre de 2026
Lo que LOGISTI-X **necesita** de otros equipos y lo que **ofrece** a cambio

> Este documento es el que hay que llevarle a los otros cinco equipos. Todo lo
> que aparece aquí ya está implementado en LOGISTI-X contra dobles de prueba;
> pasar a real es cambiar una propiedad de configuración.

---

## 1. Identificadores comunes

El enunciado obliga a compartir estos identificadores. LOGISTI-X los respeta
tal cual y aporta el suyo:

| Entidad | Identificador | Dueño |
|---|---|---|
| Cliente | `clienteId` | CRM-X |
| Producto | `productoId` | INVENTA-X |
| Pedido | `pedidoId` | SALES-X |
| Factura | `facturaId` | FINAN-X |
| **Despacho** | **`despachoId`** | **LOGISTI-X** |
| Caso de atención | `casoId` | SERVI-X |

Además, LOGISTI-X publica un identificador **legible para humanos**: el
`numeroGuia` (`LGX-2026-000001-4`). Es el que el cliente dice por teléfono y el
que SERVI-X debería pedirle al abrir una PQRS de entrega.

---

## 2. Lo que LOGISTI-X NECESITA

### 2.1 De SALES-X — consultar un pedido

```http
GET {sales-x}/api/v1/pedidos/{pedidoId}
Authorization: Bearer {token}   (o X-API-Key)
```

Respuesta esperada (200):

```json
{
  "pedidoId": "55555555-0001-4000-8000-000000000001",
  "numero": "PED-2026-000001",
  "clienteId": "11111111-1111-4111-8111-111111111111",
  "clienteNombre": "Almacenes La 14 S.A.S.",
  "clienteDocumento": "900123456-7",
  "estado": "FACTURADO",
  "total": 1174510.00,
  "items": [
    {
      "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
      "productoSku": "CEM-50",
      "productoNombre": "Cemento gris 50 kg",
      "cantidad": 20,
      "precioUnitario": 32900.00,
      "pesoUnitarioKg": 50.000
    }
  ]
}
```

**Campos que LOGISTI-X usa y por qué:**

| Campo | Para qué | ¿Obligatorio? |
|---|---|---|
| `pedidoId`, `numero` | Identificar y mostrar el pedido | Sí |
| `clienteId` | Pedirle a CRM-X la dirección | Sí |
| `clienteNombre`, `clienteDocumento` | Snapshot para la guía | Sí |
| `estado` | Solo se despacha CONFIRMADO, FACTURADO o DESPACHADO (RN-09). **Si ustedes usan otras palabras, se configuran de este lado** | Sí |
| `items[]` | Armar la carga y validar cantidades (RN-13) | Sí |
| `pesoUnitarioKg` | Calcular peso, flete y capacidad | **No**: si no viene, LOGISTI-X lo pide al armar la carga |

> **Nota para el equipo de SALES-X:** el peso no es responsabilidad suya.
> Ustedes venden unidades. Si su modelo no lo tiene, envíen el campo en `null`
> o no lo envíen: LOGISTI-X lo resuelve. Lo que sí es indispensable es `estado`
> e `items[]`, porque de ahí salen dos reglas que impiden despachar de más.

Códigos que LOGISTI-X entiende: `200` (procede), `404` (el pedido no existe →
502 hacia el usuario), `5xx` (→ 502).

### 2.2 De CRM-X — consultar un cliente

```http
GET {crm-x}/api/v1/clientes/{clienteId}
```

```json
{
  "clienteId": "11111111-1111-4111-8111-111111111111",
  "tipo": "JURIDICA",
  "nombre": "Almacenes La 14 S.A.S.",
  "documento": "900123456-7",
  "telefono": "6045551414",
  "direccion": "Carrera 48 # 32-45, Bodega 3",
  "ciudad": "Medellin",
  "departamento": "Antioquia",
  "estado": "ACTIVO"
}
```

**Lo único que LOGISTI-X necesita de ustedes es la dirección de entrega.**
`direccion` y `ciudad` son los campos críticos: sin ellos el despacho no se
puede registrar (se responde 409 `DIRECCION_REQUERIDA`). `telefono` no bloquea,
pero sin él el transportador no tiene a quién llamar cuando nadie abre, y esa
es la causa número uno de entregas fallidas.

Se ignoran los campos que no estén en esta lista, así que pueden agregar los
suyos sin romper nada.

> **Y si sus campos se llaman distinto, tampoco pasa nada.** LOGISTI-X acepta
> varios nombres por campo (`direccion`, `direccionPrincipal`, `domicilio`…) y,
> si aparece uno que no está previsto, se agrega **en una sola clase de este
> lado**. No hay que pedirles que renombren su API ni que cambien su modelo:
> quien consume paga la traducción. Ver
> [07 · Plan de integración](07-plan-de-integracion.md).

---

## 3. Lo que LOGISTI-X OFRECE

### 3.1 Consultar el estado de una entrega

Tres formas de preguntar lo mismo, según lo que tenga a la mano quien pregunta:

```http
GET /api/v1/despachos/{despachoId}          por identificador común
GET /api/v1/despachos/guia/{numeroGuia}     por la guía (lo que dice el cliente)
GET /api/v1/despachos/pedido/{pedidoId}     todos los despachos de un pedido
```

Respuesta resumida:

```json
{
  "despachoId": "…",
  "numero": "DES-2026-000001",
  "numeroGuia": "LGX-2026-000001-4",
  "pedidoId": "55555555-0001-4000-8000-000000000001",
  "estado": "ENTREGADO",
  "transportadorNombre": "Jhon Alexander Ruiz",
  "ciudadEntrega": "Medellin",
  "fechaEstimadaEntrega": "2026-09-05",
  "entregadoEn": "2026-09-04T20:48:39-05:00",
  "receptorNombre": "Ana Maria Gil",
  "pesoTotalKg": 1010.000,
  "costoFlete": 878500.00
}
```

### 3.2 Trazabilidad — pensado para SERVI-X

```http
GET /api/v1/despachos/{despachoId}/trazabilidad
```

```json
[
  { "ocurridoEn": "2026-09-04T20:48:38-05:00", "clase": "ESTADO",
    "titulo": "Registrado como REGISTRADO",
    "detalle": "Registro del despacho para el pedido PED-2026-000001",
    "ubicacion": "Bodega", "usuario": "coordinador", "origen": "LOGISTI-X" },
  { "ocurridoEn": "2026-09-04T20:52:10-05:00", "clase": "NOVEDAD",
    "titulo": "Novedad: CLIENTE_AUSENTE",
    "detalle": "Timbré tres veces y nadie abrió",
    "ubicacion": "Bello", "usuario": "transportador", "origen": "LOGISTI-X" }
]
```

**Para el equipo de SERVI-X:** esta es la respuesta a "¿dónde va mi pedido?".
Mezcla movimientos de estado y novedades en una sola línea de tiempo ordenada,
así que se puede mostrar tal cual al cliente sin pegar dos consultas.

### 3.3 Reportar un cambio de estado (para otro sistema)

```http
POST /api/v1/despachos/{despachoId}/estado
{ "estado": "EN_RUTA", "motivo": "…", "ubicacion": "…", "origen": "SALES-X" }
```

Rol necesario: `SISTEMA` o `ADMIN`. Rechaza cualquier transición que la máquina
de estados no permita. **Entregar, cancelar y devolver no se aceptan por aquí**:
exigen evidencia o motivo y tienen endpoint propio.

### 3.4 Eventos publicados

LOGISTI-X entrega estos eventos por `POST` a los suscriptores configurados:

| Evento | Cuándo | A quién le importa |
|---|---|---|
| `despacho.creado` | Se registró el despacho | SERVI-X |
| `despacho.en-ruta` | La carga salió | **SALES-X** (pedido → DESPACHADO), SERVI-X |
| `despacho.entregado` | Entrega confirmada | **SALES-X** (pedido → ENTREGADO), FINAN-X, SERVI-X |
| `despacho.fallido` | No se pudo entregar | SERVI-X |
| `despacho.devuelto` | La carga regresó | SALES-X, FINAN-X |
| `despacho.cancelado` | Anulado antes de salir | SALES-X |

Formato del mensaje (contrato público, versionado):

```json
{
  "eventoId": "8f3c…",
  "tipo": "despacho.entregado",
  "version": 1,
  "origen": "LOGISTI-X",
  "ocurridoEn": "2026-09-04T20:48:39-05:00",
  "datos": {
    "despachoId": "…",
    "numero": "DES-2026-000001",
    "numeroGuia": "LGX-2026-000001-4",
    "pedidoId": "…",
    "clienteId": "…",
    "estado": "ENTREGADO",
    "ciudadEntrega": "Medellin",
    "transportador": "Jhon Alexander Ruiz",
    "entregadoEn": "2026-09-04T20:48:39-05:00",
    "receptorNombre": "Ana Maria Gil",
    "pesoTotalKg": 1010.000,
    "costoFlete": 878500.00,
    "items": [ … ]
  }
}
```

Cabeceras: `X-Evento-Id`, `X-Evento-Tipo`, `X-Sistema-Origen: LOGISTI-X`.

> **Advertencia que hay que decir en voz alta:** la entrega es *al menos una
> vez*, no *exactamente una vez*. Si un suscriptor responde tarde puede recibir
> el mismo evento dos veces. Por eso va el `eventoId`: **quien lo recibe debe
> descartar los repetidos**. Un consumidor que no lo haga terminará con
> facturas o casos duplicados, y no será culpa del que publica.

Configuración de suscriptores:

```yaml
integrax:
  eventos:
    habilitado: true
    max-intentos: 5
    suscriptores:
      - nombre: SERVI-X
        url: https://servi-x.integrax.co/api/v1/eventos/logistica
        api-key: ${SERVIX_API_KEY}
        tipos: [despacho.en-ruta, despacho.entregado, despacho.fallido]
```

### 3.5 Reporte para el otro extremo: SALES-X

Además del evento, LOGISTI-X **llama directamente** a SALES-X para cerrar el
ciclo del pedido:

```http
POST {sales-x}/api/v1/pedidos/{pedidoId}/estado
{ "estado": "DESPACHADO", "motivo": "…", "origen": "LOGISTI-X" }
```

Esa llamada sale del publicador de eventos, **nunca desde dentro de la
transacción del despacho**: la salida del camión ya ocurrió en el mundo real y
no se puede deshacer porque SALES-X esté caído (ver [ADR-008](adr/ADR-008-aviso-a-sales-x.md)).
Si falla, se reintenta hasta cinco veces.

> **Para el equipo de SALES-X:** este endpoint ya existe de su lado y por eso
> se eligió. Lo único que se les pide es que **acepte reintentos**: si el mismo
> pedido ya está en DESPACHADO y llega otra vez la misma orden, responder 200 o
> 409 con un código estable, no 500.

---

## 4. Petición concreta a cada equipo

| Equipo | Lo que LOGISTI-X necesita | Estado hoy |
|---|---|---|
| **SALES-X** | `GET /pedidos/{id}` con `estado` e `items[]`; que `POST /pedidos/{id}/estado` tolere reintentos | Doble de prueba funcionando |
| **CRM-X** | `GET /clientes/{id}` con `direccion`, `ciudad` y `telefono` | Doble de prueba funcionando |
| **INVENTA-X** | Nada. El peso llega por SALES-X | — |
| **FINAN-X** | Un endpoint que reciba `despacho.entregado` si quieren cerrar el cobro contra la entrega | Opcional |
| **SERVI-X** | Un endpoint que reciba los eventos; y usar `GET /despachos/guia/{guia}` al atender una PQRS | Listo del lado de LOGISTI-X |

## 4.1 Cómo comprobar si su API ya sirve (sin discutir)

Con el sistema de ustedes arriba, LOGISTI-X responde esto:

```http
GET /api/v1/integracion/diagnostico?pedidoId={un pedido real}
```

Devuelve, campo por campo, qué llegó y qué falta, con la razón de negocio de
cada cosa. Un **AVISO** no bloquea; una **FALLA** sí. Ese informe es lo que se
manda por el grupo en vez de "tu API no me sirve".

## 5. Qué pasa si un equipo no llega a tiempo

Nada se detiene. LOGISTI-X arranca con
`integrax.ventas.simulado: true` e `integrax.crm.simulado: true` y opera
completo contra dobles de prueba con datos coherentes entre sí. El día que el
otro equipo publique su API:

1. Se cambia la propiedad a `false` y se pone la URL.
2. Se reinicia.
3. No se toca una sola línea de código de negocio.

---

Anterior: [03 · Arquitectura](03-arquitectura.md) ·
Siguiente: [05 · Pruebas](05-pruebas.md)
