# LOGISTI-X · Análisis y diseño: casos de uso, clases y mockups

- **Microproyecto:** LOGISTI-X — Gestión de Despachos y Entregas (INTEGRAX, punto 2.5)
- **Asignatura:** Integración de Aplicaciones · Semestre 6 · Unisabaneta
- **Docente:** Ing. Mauricio Páez A.
- **Entregable:** diagrama de casos de uso, diagrama de clases y mockups de interfaz, con su trazabilidad.

## 1. Cómo están armados los tres artefactos

Los tres artefactos salen del mismo modelo y usan los mismos nombres. La cadena es:

| Paso | Artefacto | Qué aporta al siguiente |
|---|---|---|
| 1 | Requisitos RF-01..RF-11 del enunciado | Qué tiene que hacer el sistema |
| 2 | Diagrama de casos de uso (CU-01..CU-20) | Quién lo hace, en qué orden y con qué sistema externo |
| 3 | Diagrama de clases (dominio + contratos) | Con qué datos: cada entidad de los casos de uso, y cada DTO/JSON de integración |
| 4 | Mockups (prototipo navegable) | Cómo se ve: una pantalla por caso de uso, un campo por atributo |
| 5 | Contratos de integración (siguiente fase) | Los DTO del paso 3 ya son el contrato JSON |

**Terminología única.** En los tres artefactos se usa `Despacho`, `DespachoItem`, `Novedad`,
`HistorialEstadoDespacho`, `Transportador`, `Ruta`, `EstadoDespacho`, `TipoNovedad`, y los mismos
nombres de atributo (`numeroGuia`, `pesoTotalKg`, `receptorNombre`…). Son los nombres reales del
código Java, así que el diseño no se desvía de lo que se va a implementar.

**Archivos fuente.** Los diagramas están en PlantUML (`docs/uml/*.puml`), notación UML 2.5
estándar, y se regeneran con un comando. Los mockups son un prototipo HTML navegable
(`docs/mockups/index.html`).

---

## 2. Diagrama de casos de uso

### 2.1 Actores

| Actor | Tipo | Relación con LOGISTI-X |
|---|---|---|
| Usuario LOGISTI-X | Humano, «abstracto» | Generaliza a todos los usuarios autenticados: consultar despachos, trazabilidad e historial |
| Coordinador de despachos | Humano | Registra, corrige, alista, asigna, saca a ruta, devuelve y cancela |
| Transportador | Humano | Confirma entregas, registra novedades y reprograma |
| Administrador | Humano | Hereda todo lo del Coordinador (generalización) + maestros, reportes y diagnóstico |
| Consulta | Humano | Solo lectura |
| SALES-X | Sistema externo | **Proveedor** del pedido; **consumidor** de los estados DESPACHADO/ENTREGADO; **iniciador** de CU-19 |
| CRM-X | Sistema externo | **Proveedor** de la dirección y el teléfono del cliente |
| SERVI-X | Sistema externo | **Consumidor** de la trazabilidad de una guía (atención de PQRS) |
| FINAN-X | Sistema externo | **Consumidor** del evento `despacho.entregado` para cerrar el cobro |

Los sistemas externos se modelan como actores con el estereotipo «sistema externo» porque el
enunciado de INTEGRAX exige que cada microproyecto sea consumidor y/o proveedor de los demás:
sin ellos, el diagrama describiría un programa aislado y la integración quedaría sin especificar.

### 2.2 Límite del sistema y organización

El rectángulo **LOGISTI-X** es el límite del sistema: todo lo que está dentro es responsabilidad
de este microproyecto; los actores quedan afuera. Por legibilidad, el diagrama se presenta en
**tres vistas** del mismo modelo (una por etapa del ciclo del despacho), en lugar de un único
diagrama con cruces de líneas. Los identificadores CU-xx y CI-x son los mismos en las tres.

![Vista 1 · Preparación y distribución](uml/01a-cu-preparacion-distribucion.png)

![Vista 2 · Entrega en calle](uml/01b-cu-entrega.png)

![Vista 3 · Consulta, administración e integración](uml/01c-cu-consulta-admin.png)

### 2.3 Relaciones: cuándo «include» y cuándo «extend»

**Criterio aplicado.** Se usa **«include»** cuando el caso base *no está completo* sin el
comportamiento incluido: siempre ocurre, y se extrae porque varios casos lo repiten. Se usa
**«extend»** cuando el comportamiento es *opcional o condicionado*: el caso base está completo
sin él, y la extensión solo se ejecuta en un **punto de extensión** si se cumple una
**condición**. En «extend» la flecha va del caso que extiende al caso base.

**Relaciones «include» (comportamiento común, CI-x):**

| Caso base | Incluye | Por qué es obligatorio |
|---|---|---|
| CU-01 Registrar despacho | CI-1 Validar el pedido en SALES-X | Sin validar el pedido no se sabe si es despachable (RN-09) ni si ya tiene despacho (RN-08) |
| CU-01 Registrar despacho | CI-2 Obtener la dirección en CRM-X | El despacho necesita un destino; el dueño del dato es el CRM |
| CU-01, CU-03, CU-05 | CI-4 Calcular peso, valor y flete | El peso y el valor nunca se digitan (RN-11); se recalculan en cada cambio |
| CU-05 Asignar transportador | CI-3 Generar número de guía | Asignar sin guía deja el despacho sin identificador de transporte (RN-03) |
| CU-07, CU-08, CU-11, CU-12 | CI-5 Publicar evento de estado | Todo cambio que le importa a SALES-X o FINAN-X se publica por la bandeja de salida |

**Relaciones «extend» (comportamiento condicionado):**

| Caso que extiende | Caso base | Punto de extensión | Condición |
|---|---|---|---|
| CU-06 Consultar e imprimir la guía | CU-05 Asignar transportador | rótulo solicitado | [el coordinador pide el rótulo] |
| CU-09 Registrar novedad | CU-08 Confirmar entrega | entrega no posible | [la entrega no se pudo hacer] |
| CU-10 Reprogramar la entrega | CU-09 Registrar novedad | novedad bloqueante | [bloqueante e intentos < 3] (RN-06) |
| CU-11 Devolver la carga | CU-09 Registrar novedad | novedad bloqueante | [3 intentos agotados] (RN-06) |

**Generalización de actores.** *Consulta*, *Coordinador* y *Transportador* especializan a
*Usuario LOGISTI-X* (todos consultan); *Administrador* especializa a *Coordinador* (hace todo lo
que hace el coordinador, más la administración).

### 2.4 Trazabilidad requisito → caso de uso

| Requisito del enunciado | Caso(s) de uso | Pantalla del mockup |
|---|---|---|
| RF-01 Registrar despachos | CU-01, CU-02, CU-03 | Registrar despacho · Detalle |
| RF-02 Asociar despachos a pedidos | CU-01, CU-13 | Registrar despacho · Tablero |
| RF-03 Consultar información de despachos | CU-13 | Tablero de despachos |
| RF-04 Actualizar estado de entrega | CU-04, CU-07, CU-10, CU-11, CU-12, CU-19 | Detalle · Sacar a ruta · Reprogramar · Devolver · Integración |
| RF-05 Asignar transportadores | CU-05, CU-17, CU-18 | Asignar · Transportadores · Rutas |
| RF-06 Consultar historial de entregas | CU-15 | Trazabilidad e historial |
| RF-07 Generar número de guía | CU-05 (CI-3), CU-06 | Asignar · Guía |
| RF-08 Registrar novedades de transporte | CU-09 | App: registrar novedad |
| RF-09 Confirmar entregas realizadas | CU-08 | App: confirmar entrega |
| RF-10 Consultar trazabilidad | CU-14 | Trazabilidad e historial |
| RF-11 Generar reportes logísticos | CU-16 | Reporte logístico |

Los once requisitos funcionales tienen al menos un caso de uso y una pantalla. CU-20 (diagnóstico)
responde al requisito no funcional RNF-08 de interoperabilidad. La ficha completa de cada caso
de uso (precondiciones, flujo principal, flujos alternos y excepciones) está en
`08-casos-de-uso.md`.

---

## 3. Diagrama de clases

Se presenta en tres vistas del mismo modelo: el **dominio** (las entidades de los casos de uso) y
los **contratos de integración**, separados en lo que LOGISTI-X **consume** y lo que **expone**.
Convenciones: visibilidad `-` privado, `+` público, `#` protegido, `~` de paquete; los getters se
omiten; `{static}` va subrayado.

### 3.1 Modelo de dominio

![Diagrama de clases · dominio](uml/02a-clases-dominio.png)

**Relaciones y su semántica.**

| Relación | Tipo | Multiplicidad | Justificación |
|---|---|---|---|
| Despacho — DespachoItem | **Composición** | 1 a 1..* | Una línea de carga no existe sin su despacho: se crea, guarda y borra con él |
| Despacho — HistorialEstadoDespacho | **Composición** | 1 a 1..* | Todo despacho nace con su primer registro de historial y la historia muere con él |
| Despacho — Novedad | **Composición** | 1 a 0..* | La novedad solo tiene sentido dentro del despacho donde ocurrió |
| Transportador — Despacho | **Agregación** | 0..1 a 0..* | El transportador *agrupa* los despachos que lleva, pero no es su dueño: si se inactiva, los despachos y su historia siguen (RN-14: no se borra, se inactiva) |
| Despacho → Ruta | **Asociación** dirigida | 0..* a 0..1 | El despacho conoce la ruta por la que viaja; la ruta no necesita conocer sus despachos |
| Despacho, Historial → EstadoDespacho; Novedad → TipoNovedad; Transportador → TipoVehiculo | **Dependencia** de tipo | — | Enumeraciones con comportamiento propio (`permitePasarA()`, `bloqueaPorDefecto()`) |

`Despacho` es la **raíz del agregado**: el resto del sistema solo modifica sus partes a través de
sus métodos (`agregarItem()`, `entregar()`, `registrarNovedad()`…). Por eso las reglas de negocio
viven en esos métodos y no en la pantalla.

**Consistencia con los casos de uso.**

| Entidad del caso de uso | Clase | Casos de uso donde aparece |
|---|---|---|
| Despacho | `Despacho` | CU-01 a CU-16, CU-19 |
| Carga / líneas del pedido | `DespachoItem` | CU-01, CU-03, CU-04 |
| Novedad de transporte | `Novedad` + `TipoNovedad` | CU-09, CU-10, CU-11 |
| Trazabilidad / historial | `HistorialEstadoDespacho` | CU-14, CU-15, todas las transiciones |
| Transportador | `Transportador` + `TipoVehiculo` | CU-05, CU-17 |
| Ruta | `Ruta` | CU-05, CU-18 |
| Estado del despacho | `EstadoDespacho` | CU-04, CU-07, CU-08, CU-10, CU-11, CU-12, CU-19 |
| Número de guía | `Despacho.numeroGuia` + `GuiaServicio` | CU-05, CU-06, CU-14 |

**Métodos que sirven a la integración** (no son CRUD genérico):

| Método | Para qué sirve en la integración |
|---|---|
| `PedidoExterno.sePuedeDespachar(estados)` | Valida el pedido de SALES-X contra la lista configurable de estados despachables |
| `EstadoDespacho.permitePasarA(destino)` | Rechaza cambios de estado inválidos que lleguen de SALES-X (CU-19, RN-07) |
| `Despacho.moverA(destino, …, origen)` | Registra en el historial *qué sistema* hizo el cambio |
| `GuiaServicio.esValida(guia)` | Valida el dígito de verificación de una guía que consulta SERVI-X |
| `DespachoResumen.de()`, `EventoTrazabilidad.de()` | Serializan la entidad al contrato JSON de salida |
| `RegistradorEventos.aJson()` | Serializa el evento para FINAN-X y SALES-X |
| `OutboxEvento.registrarFallo(error, maxIntentos)` | Reintentos de entrega a otro sistema |

### 3.2 Contratos de entrada (lo que LOGISTI-X consume)

![Diagrama de clases · contratos de entrada](uml/02b-clases-entrada.png)

La integración con SALES-X y CRM-X se hace por **puertos** (interfaces). Cada puerto tiene dos
realizaciones: el adaptador REST real y un doble de prueba. Pasar de simulado a real es cambiar
una propiedad, sin tocar el dominio. El dominio **nunca** depende del JSON ajeno: el adaptador
lo traduce en la frontera.

### 3.3 Contratos de salida (lo que LOGISTI-X expone y publica)

![Diagrama de clases · contratos de salida](uml/02c-clases-salida.png)

### 3.4 Del modelo al contrato JSON

El modelo permite derivar los contratos directamente: cada DTO es un `record` de Java cuyos
campos son atributos de las clases de dominio. Ejemplos reales:

**Entrada — `PedidoExterno` (respuesta de SALES-X a `GET /pedidos/{id}`):**

```
{
  "pedidoId": "a41f7c1e-…",
  "numero": "PED-2026-0418",
  "clienteId": "5b2c90aa-…",
  "clienteNombre": "Ferretería El Tornillo",
  "clienteDocumento": "900456123",
  "estado": "APROBADO",
  "total": 948000,
  "items": [
    { "productoId": "…", "sku": "TOR-3/8", "nombre": "Tornillo 3/8\" x 100 u.",
      "cantidad": 12, "precioUnitario": 45000, "pesoUnitarioKg": 1.2 }
  ]
}
```

**Salida — `SobreEvento` publicado a FINAN-X y SALES-X al confirmar una entrega:**

```
{
  "eventoId": "e3b1…",
  "tipo": "despacho.entregado",
  "version": 1,
  "origen": "LOGISTI-X",
  "ocurridoEn": "2026-09-25T15:42:10-05:00",
  "datos": {
    "despachoId": "…", "numero": "DES-2026-000131",
    "numeroGuia": "LGX-2026-000131-1", "pedidoId": "…",
    "estado": "ENTREGADO", "receptorNombre": "Jorge Álvarez",
    "entregadoEn": "2026-09-25T15:42:10-05:00", "costoFlete": 18500,
    "items": [ { "sku": "TOR-3/8", "cantidad": 12, "pesoLineaKg": 14.4 } ]
  }
}
```

| Contrato | Sentido | Sistema | Clase de origen | Casos de uso |
|---|---|---|---|---|
| `PedidoExterno` + `LineaPedidoExterna` | Entra | SALES-X | → `Despacho`, `DespachoItem` | CU-01 |
| `ClienteExterno` | Entra | CRM-X | → `Despacho` (destino) | CU-01 |
| `CambiarEstado` | Entra | SALES-X | → `Despacho.moverA()` | CU-19 |
| `DespachoResumen`, `DespachoDetalle` | Sale | Panel, app | `Despacho` | CU-13 |
| `EventoTrazabilidad[]` | Sale | SERVI-X | `HistorialEstadoDespacho`, `Novedad` | CU-14 |
| `SobreEvento` (`despacho.en-ruta`, `.entregado`, `.devuelto`, `.cancelado`) | Sale (asíncrono) | SALES-X, FINAN-X | `Despacho` | CU-07, CU-08, CU-11, CU-12 |

---

## 4. Mockups de interfaz

### 4.1 Qué se entrega

Un **prototipo navegable** de fidelidad media (`mockups/index.html`, se abre en cualquier
navegador sin instalar nada) con 17 pantallas: el panel web del coordinador y el administrador,
y la app móvil del transportador. Tiene tres controles:

- **Menú lateral:** una entrada por caso de uso, en el orden del ciclo del despacho.
- **Estados:** cada pantalla se puede ver en estado *normal*, *cargando*, *vacío* y *error*, con
  el mensaje, el código HTTP y la acción que ofrece.
- **Anotaciones:** prenden y apagan, sobre cada campo, el atributo del diagrama de clases que
  representa y, con un contorno de color, el origen del dato cuando viene de otro sistema.

Al lado de cada pantalla, una ficha muestra su caso de uso, requisito, clases, servicio REST y
contrato, y un desplegable muestra el JSON real que la pantalla recibe o envía.

**Convención de colores de integración** (la misma en todas las pantallas):

| Color del contorno | Significa |
|---|---|
| Azul · ← SALES-X | Dato que llega de SALES-X |
| Morado · ← CRM-X | Dato que llega de CRM-X |
| Gris · calculado en servidor | No se digita; lo calcula el dominio (RN-11) |
| Naranja · evento → | La acción publica un evento a otro sistema |
| Rosa · → SERVI-X | Dato que se expone por REST a otro sistema |

### 4.2 Cobertura: una pantalla por caso de uso

| Caso de uso | Pantalla | Actor |
|---|---|---|
| CU-01 Registrar despacho (+CI-1, CI-2, CI-4) | Registrar despacho | Coordinador |
| CU-02, CU-03, CU-04, CU-12 | Detalle: corregir, ajustar, alistar, cancelar | Coordinador |
| CU-05 (+CI-3) | Asignar transportador y generar guía | Coordinador |
| CU-06 | Consultar e imprimir la guía | Coordinador, Consulta |
| CU-07 (+CI-5) | Sacar la carga a ruta | Coordinador |
| CU-08 (+CI-5) | App: confirmar entrega | Transportador |
| CU-09 | App: registrar novedad | Transportador |
| CU-10 | App: reprogramar entrega | Transportador |
| CU-11 (+CI-5) | Devolver la carga | Coordinador |
| CU-13 | Tablero de despachos | Todos |
| CU-14, CU-15 | Trazabilidad e historial | Todos · SERVI-X |
| CU-16 | Reporte logístico | Administrador |
| CU-17 | Administrar transportadores | Administrador |
| CU-18 | Administrar rutas | Administrador |
| CU-19, CU-20 | Integración: bandeja y diagnóstico | Administrador · SALES-X |
| Soporte | Acceso (JWT por rol) | Todos |

Los 20 casos de uso tienen pantalla. Cuatro de ellos comparten la pantalla de detalle porque
actúan sobre el mismo objeto editable; sus botones se habilitan según `EstadoDespacho.esEditable()`.

### 4.3 Pantallas

**Tablero de despachos (CU-13).** Cada columna es un atributo de `DespachoResumen`.

![Tablero de despachos](mockups/capturas/02-tablero.png)

**Registrar despacho (CU-01).** Se ven los tres orígenes del dato: pedido de SALES-X (azul),
dirección de CRM-X (morado) y peso y valor calculados (gris).

![Registrar despacho](mockups/capturas/03-registrar.png)

**Detalle del despacho (CU-02, CU-03, CU-04, CU-12).**

![Detalle del despacho](mockups/capturas/04-detalle.png)

**Asignar transportador y generar guía (CU-05).** Se muestra RN-12: un transportador sin
capacidad para la carga no se puede elegir.

![Asignar transportador](mockups/capturas/05-asignar.png)

**Consultar e imprimir la guía (CU-06).**

![Rótulo de la guía](mockups/capturas/06-guia.png)

**Sacar a ruta (CU-07).** La confirmación publica el evento a SALES-X por la bandeja de salida.

![Sacar a ruta](mockups/capturas/07-ruta.png)

**App del transportador: confirmar entrega (CU-08).**

![Confirmar entrega](mockups/capturas/08-entregar.png)

**App del transportador: registrar novedad (CU-09).** Los tipos son la enumeración `TipoNovedad`.

![Registrar novedad](mockups/capturas/09-novedad.png)

**App del transportador: reprogramar (CU-10).**

![Reprogramar](mockups/capturas/10-reprogramar.png)

**Devolver la carga (CU-11).**

![Devolver la carga](mockups/capturas/11-devolver.png)

**Trazabilidad e historial (CU-14, CU-15).** Es el recurso que consume SERVI-X.

![Trazabilidad](mockups/capturas/12-trazabilidad.png)

**Reporte logístico (CU-16).**

![Reporte logístico](mockups/capturas/13-reportes.png)

**Administrar transportadores (CU-17) y rutas (CU-18).**

![Transportadores](mockups/capturas/14-transportadores.png)

![Rutas](mockups/capturas/15-rutas.png)

**Integración: bandeja de salida y diagnóstico (CU-19, CU-20).**

![Integración](mockups/capturas/16-integracion.png)

### 4.4 Estados de carga, vacío y error

Cada pantalla define sus tres estados. Algunos ejemplos:

| Pantalla | Estado | Qué ve el usuario |
|---|---|---|
| Tablero | Cargando | Esqueleto de la tabla mientras responde `GET /despachos` |
| Tablero | Vacío | «Todavía no hay despachos» + botón para registrar el primero |
| Registrar | Error | SALES-X no respondió (502): no se registra con datos sin validar; ofrece ver el diagnóstico |
| Asignar | Error | La carga no cabe en el vehículo (422, RN-12) |
| App entregas | Vacío | «No tiene entregas pendientes hoy» |
| Reprogramar | Error | Se agotaron los intentos (409, RN-06): deriva a devolver |
| Integración | Error | SALES-X lleva 5 intentos sin responder: los despachos siguen operando, los avisos esperan en la bandeja |

![Estado cargando](mockups/capturas/17-tablero-carga.png)

![Estado vacío](mockups/capturas/18-tablero-vacio.png)

![Estado de error de integración](mockups/capturas/19-registrar-error.png)

### 4.5 Trazabilidad campo → atributo (pantalla Registrar despacho)

| Campo en pantalla | Atributo | Origen |
|---|---|---|
| Número de pedido | `Despacho.pedidoNumero` | SALES-X (`PedidoExterno.numero`) |
| Estado en SALES-X | `PedidoExterno.estado` | SALES-X |
| Cliente / Documento | `Despacho.clienteNombre` / `clienteDocumento` | SALES-X |
| Dirección, ciudad, departamento, teléfono | `Despacho.direccionEntrega`, `ciudadEntrega`, `departamentoEntrega`, `clienteTelefono` | CRM-X (`ClienteExterno`) |
| Referencia, observaciones | `Despacho.referenciaDireccion`, `observaciones` | Digitado |
| SKU, producto, cantidad | `DespachoItem.productoSku`, `productoNombre`, `cantidad` | SALES-X (líneas del pedido) |
| Peso unitario | `DespachoItem.pesoUnitarioKg` | SALES-X o digitado |
| Peso línea, valor línea | `DespachoItem.pesoLineaKg`, `valorLinea` | Calculado |
| Unidades, peso, valor declarado | `Despacho.unidadesTotales`, `pesoTotalKg`, `valorDeclarado` | Calculado (RN-11) |

En el prototipo, esta misma correspondencia aparece sobre cada campo de las 17 pantallas al
prender las anotaciones.

---

## 5. Checklist de la rúbrica

| Criterio de la rúbrica | Dónde se cumple |
|---|---|
| Actores humanos y sistemas externos identificados | §2.1: 5 humanos (uno abstracto) y 4 sistemas «sistema externo» |
| Límite del sistema claro | Rectángulo LOGISTI-X en las tres vistas (§2.2) |
| include/extend correctos y justificados | §2.3: criterio, 10 «include» con motivo y 4 «extend» con punto y condición |
| Notación UML estándar | PlantUML UML 2.5: actores, elipses, «include»/«extend» punteados, generalización con flecha hueca |
| Cada caso de uso trazable a un requisito | §2.4 y la etiqueta RF-xx dentro de cada elipse |
| Todas las entidades de los casos de uso representadas | §3.1, tabla «Consistencia con los casos de uso» |
| Atributos con tipo y visibilidad | Diagramas §3.1–3.3: `- atributo : Tipo`, valores por defecto |
| Multiplicidad correcta | Todas las relaciones del dominio llevan multiplicidad en ambos extremos |
| Composición vs agregación con criterio | §3.1: tabla de relaciones con la justificación de cada una |
| Métodos para la integración | §3.1, tabla de métodos (validación, serialización, reintentos) |
| Contrato de datos derivable | §3.2–3.4: DTO de entrada y salida y JSON reales |
| Pantalla por caso de uso | §4.2: 20 de 20 |
| Campos coinciden con el diagrama de clases | §4.5 y las anotaciones del prototipo |
| Puntos de integración señalados | Contornos de color por sistema (§4.1) |
| Estados de carga, error y vacío | §4.4: los tres estados en todas las pantallas |
| Misma terminología en los tres artefactos | Nombres del código Java en los tres (§1) |
| Cómo cada artefacto alimenta al siguiente | §1: requisito → caso de uso → clase → pantalla → contrato |
