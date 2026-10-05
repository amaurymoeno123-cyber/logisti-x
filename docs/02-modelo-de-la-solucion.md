# 02 · Modelo de la solución

**Semana 2** — cierre 28 de agosto de 2026
Casos de uso · modelo de dominio · modelo de datos · máquina de estados · secuencias

> Los diagramas de este documento están también como imagen en
> `docs/diagramas/` (PNG a 2x), listos para pegar en la presentación.

---

## 1. Contexto: dónde encaja LOGISTI-X en INTEGRAX

Antes de los casos de uso hay que ver la frontera. LOGISTI-X es el eslabón
entre la venta y el cliente: recibe el pedido de SALES-X y le devuelve el
resultado de la entrega.

```mermaid
flowchart LR
    CRM["CRM-X<br/><small>clientes</small>"]
    INV["INVENTA-X<br/><small>productos e inventario</small>"]
    SALES["SALES-X<br/><small>ventas y pedidos</small>"]
    FINAN["FINAN-X<br/><small>facturación y pagos</small>"]
    LOG["LOGISTI-X<br/><b>despachos y entregas</b>"]
    SERVI["SERVI-X<br/><small>PQRS</small>"]

    SALES -->|"pedido a despachar"| LOG
    CRM -->|"dirección de entrega"| LOG
    LOG -->|"DESPACHADO / ENTREGADO"| SALES
    LOG -->|"despacho.entregado"| FINAN
    LOG -->|"trazabilidad de la guía"| SERVI
    INV -.->|"peso y datos del producto"| SALES

    classDef propio fill:#1d4ed8,stroke:#1d4ed8,color:#fff,font-weight:bold
    classDef otros fill:#f5f5f4,stroke:#d6d3d1,color:#1c1917
    class LOG propio
    class CRM,INV,SALES,FINAN,SERVI otros
```

**Lo que se lee en este diagrama:** LOGISTI-X no le pide nada a INVENTA-X.
El peso y el nombre del producto llegan dentro del pedido de SALES-X, que es
quien ya los consultó. Un sistema menos al que llamar es un sistema menos que
puede estar caído.

---

## 2. Casos de uso

### 2.1 Diagrama general

El sistema tiene veinte casos de uso de negocio y cuatro de soporte técnico.
Aquí se presentan agrupados en dos diagramas para que se lean: primero **quién
hace qué** (actores humanos) y después **qué le pide LOGISTI-X a los otros
microproyectos y qué le piden a él**. Los cuatro casos de uso centrales están
detallados más abajo; **la especificación completa de los veinte, con flujos
alternos, excepciones y trazabilidad, está en
[`08-casos-de-uso.md`](08-casos-de-uso.md)**.

#### Actores humanos

```mermaid
flowchart LR
    subgraph ACTORES[" "]
        direction TB
        COOR["Coordinador<br/>de despachos"]
        TRANS["Transportador"]
        ADMIN["Administrador"]
        CONS["Consulta"]
    end

    subgraph LOGISTIX["LOGISTI-X · Gestión de Despachos y Entregas"]
        direction TB

        subgraph PREP["1 · Preparación"]
            direction TB
            U1(["RF-01 · Registrar despacho"])
            U2(["Corregir destino y carga"])
            U3(["Alistar la carga"])
        end

        subgraph DIST["2 · Distribución"]
            direction TB
            U4(["RF-05 · Asignar transportador"])
            U5(["RF-07 · Generar número de guía"])
            U6(["Sacar a ruta"])
        end

        subgraph ENTREGA["3 · Entrega"]
            direction TB
            U7(["RF-09 · Confirmar entrega"])
            U8(["RF-08 · Registrar novedad"])
            U9(["Reprogramar entrega"])
            U10(["Devolver la carga"])
        end

        subgraph CONTROL["4 · Consulta y control"]
            direction TB
            U11(["RF-03 · Consultar despachos"])
            U12(["RF-10 · Consultar trazabilidad"])
            U13(["RF-06 · Historial de entregas"])
            U14(["RF-11 · Reportes logísticos"])
            U15(["Administrar transportadores y rutas"])
        end
    end

    COOR --- U1
    COOR --- U2
    COOR --- U3
    COOR --- U4
    COOR --- U6
    COOR --- U10
    TRANS --- U7
    TRANS --- U8
    TRANS --- U9
    ADMIN --- U14
    ADMIN --- U15
    CONS --- U11
    CONS --- U13

    U4 -.->|"«include»"| U5
    U8 -.->|"«extend» si bloquea"| U9

    classDef actor fill:#ffffff,stroke:#1c1917,stroke-width:2px,color:#1c1917,font-weight:bold
    classDef caso fill:#eff4ff,stroke:#7aa0f5,color:#1e40af
    class COOR,TRANS,ADMIN,CONS actor
    class U1,U2,U3,U4,U5,U6,U7,U8,U9,U10,U11,U12,U13,U14,U15 caso
    style ACTORES fill:#ffffff,stroke:#ffffff
    style LOGISTIX fill:#fbfbfa,stroke:#d6d3d1
```

#### Sistemas que participan

```mermaid
flowchart LR
    subgraph IZQ[" "]
        direction TB
        SALESI["SALES-X"]
        CRMI["CRM-X"]
    end

    subgraph LOGISTIX2["LOGISTI-X"]
        direction TB
        V1(["RF-01 · Registrar despacho"])
        V2(["Sacar a ruta"])
        V3(["RF-09 · Confirmar entrega"])
        V4(["RF-04 · Actualizar estado"])
        V5(["RF-10 · Consultar trazabilidad"])
    end

    subgraph DER[" "]
        direction TB
        SALESD["SALES-X"]
        FINAND["FINAN-X"]
        SERVID["SERVI-X"]
    end

    SALESI -->|"pedido a despachar"| V1
    CRMI -->|"dirección de entrega"| V1
    SALESI -->|"reporta un cambio"| V4

    V2 -->|"pedido → DESPACHADO"| SALESD
    V3 -->|"pedido → ENTREGADO"| SALESD
    V3 -->|"evento despacho.entregado"| FINAND
    V5 -->|"estado de la guía"| SERVID

    classDef externo fill:#f5f5f4,stroke:#57534e,stroke-width:2px,color:#1c1917,font-weight:bold
    classDef caso fill:#eff4ff,stroke:#7aa0f5,color:#1e40af
    class SALESI,CRMI,SALESD,FINAND,SERVID externo
    class V1,V2,V3,V4,V5 caso
    style IZQ fill:#ffffff,stroke:#ffffff
    style DER fill:#ffffff,stroke:#ffffff
    style LOGISTIX2 fill:#fbfbfa,stroke:#d6d3d1
```

### 2.2 Caso de uso central: **Registrar despacho** (RF-01, RF-02)

| | |
|---|---|
| **Identificador** | CU-01 |
| **Actor principal** | Coordinador de despachos |
| **Actores de apoyo** | SALES-X, CRM-X |
| **Precondición** | El pedido existe en SALES-X y está CONFIRMADO o FACTURADO |
| **Postcondición** | Despacho en REGISTRADO, con la carga y la dirección copiadas, y evento `despacho.creado` en la bandeja de salida |

**Flujo principal**

1. El coordinador solicita `POST /api/v1/despachos` con el `pedidoId`.
2. LOGISTI-X pide el pedido a **SALES-X**.
3. Verifica que el estado del pedido admita despacho (RN-09).
4. Verifica que ese pedido no tenga ya un despacho vivo (RN-08).
5. Pide a **CRM-X** los datos de entrega del cliente del pedido.
6. Copia dirección, ciudad y teléfono como *snapshot*.
7. Copia la carga del pedido línea por línea y calcula peso, unidades y valor
   declarado en el servidor (RN-11).
8. Guarda el despacho con consecutivo `DES-2026-000001` y registra el primer
   movimiento del historial.
9. Inserta el evento `despacho.creado` en la bandeja de salida, **en la misma
   transacción**.
10. Responde 201 con la cabecera `Location` y el despacho creado.

**Flujos alternos**

| # | Situación | Respuesta |
|---|---|---|
| A1 | El pedido no existe en SALES-X | 502 `PEDIDO_NO_ENCONTRADO` |
| A2 | El pedido está CANCELADO o en BORRADOR | 409 `PEDIDO_NO_DESPACHABLE` |
| A3 | El pedido ya tiene un despacho vivo | 409 `DESPACHO_DUPLICADO` |
| A4 | El cliente no tiene dirección en CRM-X y tampoco viene en la solicitud | 409 `DIRECCION_REQUERIDA` |
| A5 | SALES-X o CRM-X no responden | 502, y **no se guarda nada** |
| A6 | Se pide despachar un producto ajeno al pedido | 409 `PRODUCTO_AJENO_AL_PEDIDO` |
| A7 | Se piden más unidades de las del pedido | 409 `CANTIDAD_SUPERA_PEDIDO` |

### 2.3 Caso de uso: **Asignar transportador y generar guía** (RF-05 + RF-07)

| | |
|---|---|
| **Identificador** | CU-02 |
| **Actor principal** | Coordinador de despachos |
| **Precondición** | El despacho está en EN_PREPARACION y tiene carga |
| **Postcondición** | Despacho ASIGNADO, con guía, ruta, fecha prometida y flete calculado |

**Flujo principal**

1. El coordinador solicita `POST /despachos/{id}/asignar` con el transportador.
2. LOGISTI-X verifica que el transportador esté activo (RN-14).
3. Verifica que la carga quepa en el vehículo (RN-12).
4. Si no se indicó ruta, elige la ruta activa más rápida hacia la ciudad de
   destino.
5. **Genera el número de guía** con dígito de verificación, si no tenía (RN-03).
6. Calcula la fecha prometida con los días estimados de la ruta.
7. Calcula el flete: tarifa base + tarifa de la ruta + costo por kilogramo.
8. Mueve el despacho a ASIGNADO y registra el movimiento.

**Flujos alternos**

| # | Situación | Respuesta |
|---|---|---|
| B1 | El transportador está inactivo | 409 `TRANSPORTADOR_INACTIVO` |
| B2 | La carga supera la capacidad del vehículo | 409 `CAPACIDAD_INSUFICIENTE` |
| B3 | El despacho ya está ASIGNADO | Se reasigna, **conservando la guía**, y queda en la trazabilidad |
| B4 | No hay ruta activa hacia esa ciudad | Se asigna sin ruta y la fecha queda a cargo del coordinador |

### 2.4 Caso de uso: **Confirmar entrega** (RF-09)

| | |
|---|---|
| **Identificador** | CU-03 |
| **Actor principal** | Transportador |
| **Precondición** | El despacho está EN_RUTA |
| **Postcondición** | Despacho ENTREGADO con evidencia; evento `despacho.entregado` en la bandeja de salida; SALES-X marca el pedido ENTREGADO |

**Flujo principal**

1. El transportador solicita `POST /despachos/{id}/entregar` con el nombre de
   quien recibió.
2. LOGISTI-X exige esa evidencia; sin ella rechaza (RN-05).
3. Mueve el despacho a ENTREGADO y guarda receptor, documento y hora.
4. Inserta el evento `despacho.entregado` en la bandeja de salida.
5. El publicador, **fuera de la transacción**, le reporta a SALES-X que el
   pedido quedó ENTREGADO y reintenta si SALES-X está caído.

**Flujos alternos**

| # | Situación | Respuesta |
|---|---|---|
| C1 | No se indica quién recibió | 400 `RECEPTOR_REQUERIDO` |
| C2 | El despacho no está EN_RUTA | 409 `TRANSICION_INVALIDA` |
| C3 | SALES-X está caído al reportar | La entrega **queda firme**; el aviso se reintenta solo |

### 2.5 Caso de uso: **Registrar novedad** (RF-08)

| | |
|---|---|
| **Identificador** | CU-04 |
| **Actor principal** | Transportador |
| **Precondición** | El despacho no está en un estado final |
| **Postcondición** | Novedad registrada; si es bloqueante y la carga iba en ruta, el despacho queda FALLIDO con un intento más |

```mermaid
flowchart TD
    A["Transportador reporta<br/>una novedad"] --> B{"¿El tipo de novedad<br/>bloquea la entrega?"}
    B -->|"No<br/>(demora, zona de riesgo)"| C["Se registra.<br/>El despacho sigue EN_RUTA"]
    B -->|"Sí<br/>(ausente, dirección errada)"| D{"¿La carga va<br/>en la calle?"}
    D -->|No| C
    D -->|Sí| E["Despacho a FALLIDO<br/>+1 intento de entrega"]
    E --> F{"¿Quedan intentos?"}
    F -->|"Sí (menos de 3)"| G["Se puede reprogramar<br/>y vuelve a EN_RUTA"]
    F -->|"No"| H["Solo queda devolver<br/>la carga al remitente"]

    classDef decision fill:#fdf3e3,stroke:#b45309,color:#b45309
    classDef accion fill:#eff4ff,stroke:#c7d7fe,color:#1d4ed8
    classDef final fill:#fdecec,stroke:#f3c6c6,color:#b91c1c
    class B,D,F decision
    class A,C,E,G accion
    class H final
```

---

## 3. Modelo de dominio (diagrama de clases)

```mermaid
classDiagram
    direction LR

    class Despacho {
        <<raíz del agregado>>
        +UUID despachoId
        +String numero
        +String numeroGuia
        +UUID pedidoId
        +UUID clienteId
        +String direccionEntrega
        +EstadoDespacho estado
        +BigDecimal pesoTotalKg
        +BigDecimal valorDeclarado
        +BigDecimal costoFlete
        +LocalDate fechaEstimadaEntrega
        +String receptorNombre
        +int intentosEntrega
        +Long version
        +agregarItem()
        +quitarItem()
        +preparar()
        +asignar()
        +despachar()
        +entregar()
        +marcarFallida()
        +reprogramar()
        +devolver()
        +cancelar()
        +registrarNovedad()
        +moverA()
        +recalcularCarga()
        +recalcularFlete()
    }

    class DespachoItem {
        +UUID id
        +int linea
        +UUID productoId
        +String productoSku
        +int cantidad
        +BigDecimal pesoUnitarioKg
        +BigDecimal pesoLineaKg
        +BigDecimal valorLinea
        ~recalcular()
    }

    class HistorialEstadoDespacho {
        +EstadoDespacho estadoAnterior
        +EstadoDespacho estadoNuevo
        +String motivo
        +String ubicacion
        +String usuario
        +String origen
        +OffsetDateTime ocurridoEn
    }

    class Novedad {
        +UUID id
        +TipoNovedad tipo
        +String descripcion
        +String ubicacion
        +boolean bloqueante
        +boolean resuelta
        +OffsetDateTime ocurridaEn
        +resolver()
    }

    class Transportador {
        +UUID id
        +String documento
        +String nombre
        +TipoVehiculo tipoVehiculo
        +String placa
        +BigDecimal capacidadKg
        +boolean activo
        +puedeCargar()
        +inactivar()
    }

    class Ruta {
        +UUID id
        +String codigo
        +String ciudadOrigen
        +String ciudadDestino
        +int diasEstimados
        +BigDecimal tarifaBase
        +boolean activa
        +fechaEstimadaDesde()
    }

    class EstadoDespacho {
        <<enumeration>>
        REGISTRADO
        EN_PREPARACION
        ASIGNADO
        EN_RUTA
        ENTREGADO
        FALLIDO
        DEVUELTO
        CANCELADO
        +permitePasarA()
        +esEditable()
        +esFinal()
        +bloqueaElPedido()
    }

    class TipoNovedad {
        <<enumeration>>
        DIRECCION_ERRADA
        CLIENTE_AUSENTE
        CLIENTE_RECHAZA
        DEMORA_EN_VIA
        AVERIA_VEHICULO
        CARGA_AVERIADA
        ZONA_DE_RIESGO
        DATOS_INCOMPLETOS
        OTRA
        +bloqueaPorDefecto()
    }

    Despacho "1" *-- "1..*" DespachoItem : carga
    Despacho "1" *-- "1..*" HistorialEstadoDespacho : trazabilidad
    Despacho "1" *-- "0..*" Novedad : novedades
    Despacho "0..*" --> "0..1" Transportador : lo lleva
    Despacho "0..*" --> "0..1" Ruta : viaja por
    Despacho --> EstadoDespacho
    Novedad --> TipoNovedad
```

**Por qué `Despacho` es la raíz del agregado.** Nadie de afuera toca una línea
de carga ni una novedad directamente: se agregan, cambian o quitan *a través
del despacho*, y cada una de esas operaciones termina en `recalcularCarga()`.
Es imposible dejar el despacho con carga nueva y peso viejo, que es el bug
clásico de este dominio. Y una novedad bloqueante **tiene** que cambiar el
estado del despacho en el mismo acto: si se pudiera registrar suelta, el
sistema diría "en ruta" de algo que lleva dos días parado.

**Por qué `Transportador` y `Ruta` no son parte del agregado.** Viven más que
el despacho y se comparten entre miles: son entidades propias, con su propio
ciclo de vida, y por eso se referencian, no se contienen.

---

## 4. Modelo de datos

```mermaid
erDiagram
    DESPACHO ||--o{ DESPACHO_ITEM : "contiene"
    DESPACHO ||--o{ DESPACHO_ESTADO_HISTORIAL : "registra"
    DESPACHO ||--o{ NOVEDAD : "reporta"
    TRANSPORTADOR ||--o{ DESPACHO : "transporta"
    RUTA ||--o{ DESPACHO : "define trayecto"

    DESPACHO {
        uuid despacho_id PK
        varchar numero UK
        varchar numero_guia UK "RF-07, nulo hasta asignar"
        uuid pedido_id "referencia externa a SALES-X, sin FK"
        varchar pedido_numero "snapshot"
        uuid cliente_id "referencia externa a CRM-X, sin FK"
        varchar cliente_nombre "snapshot"
        varchar direccion_entrega
        varchar ciudad_entrega
        varchar estado "CHECK"
        uuid transportador_id FK
        uuid ruta_id FK
        numeric peso_total_kg
        numeric valor_declarado
        numeric costo_flete
        date fecha_estimada_entrega
        varchar receptor_nombre "evidencia RF-09"
        int intentos_entrega
        bigint version "bloqueo optimista"
    }

    DESPACHO_ITEM {
        uuid id PK
        uuid despacho_id FK
        int linea
        uuid producto_id "referencia externa a INVENTA-X, sin FK"
        varchar producto_sku "snapshot"
        int cantidad
        numeric peso_unitario_kg "congelado"
        numeric peso_linea_kg
    }

    DESPACHO_ESTADO_HISTORIAL {
        uuid id PK
        uuid despacho_id FK
        varchar estado_anterior
        varchar estado_nuevo
        varchar motivo
        varchar ubicacion "donde ocurrio"
        varchar usuario
        varchar origen "que sistema lo movio"
        timestamptz ocurrido_en
    }

    NOVEDAD {
        uuid id PK
        uuid despacho_id FK
        varchar tipo "CHECK"
        varchar descripcion
        boolean bloqueante
        boolean resuelta
        timestamptz ocurrida_en
    }

    TRANSPORTADOR {
        uuid id PK
        varchar documento UK
        varchar nombre
        varchar tipo_vehiculo "CHECK"
        varchar placa
        numeric capacidad_kg
        boolean activo
    }

    RUTA {
        uuid id PK
        varchar codigo UK
        varchar ciudad_origen
        varchar ciudad_destino
        int dias_estimados
        numeric tarifa_base
        boolean activa
    }

    USUARIO {
        uuid id PK
        varchar username UK
        varchar password_hash "BCrypt"
        varchar rol "CHECK"
    }

    AUDITORIA {
        bigserial id PK
        varchar entidad
        varchar entidad_id
        varchar accion
        varchar usuario
        varchar ip_origen
        jsonb datos_antes
        jsonb datos_despues
    }

    OUTBOX_EVENTO {
        uuid id PK
        varchar tipo
        uuid agregado_id
        jsonb payload
        varchar estado
        int intentos
    }
```

### Lo que NO está en este modelo, y es a propósito

No hay tabla `cliente`, ni `producto`, ni `pedido`. `cliente_id`, `producto_id`
y `pedido_id` son **referencias externas sin llave foránea**, porque esas
entidades viven en la base de datos de CRM-X, INVENTA-X y SALES-X. Ver
[ADR-002](adr/ADR-002-una-base-por-microproyecto.md) y
[ADR-003](adr/ADR-003-referencias-externas-sin-fk.md).

A cambio de perder la llave foránea se gana independencia, y se paga con dos
cosas que sí están implementadas: **validación por API** al registrar el
despacho y **copia local (snapshot)** de los datos que se usaron. Una guía ya
impresa no puede cambiar porque mañana corrijan un maestro ajeno.

`transportador` y `ruta` **sí** llevan llave foránea: son entidades de esta
casa.

### Decisiones de tipos

| Decisión | Motivo |
|---|---|
| `numeric(14,2)` para el dinero | `float`/`double` acumulan error; un flete descuadrado cuesta horas de conciliación |
| `numeric(10,3)` para el peso | La unidad comercial de la paquetería es el gramo: 0,250 kg × 800 unidades = 200 kg exactos |
| `timestamptz` y no `timestamp` | Guarda el instante real; evita el lío del horario cuando hay más de una zona |
| `varchar` + `CHECK` en vez de `ENUM` nativo | Agregar un estado nuevo no exige `ALTER TYPE`, y mapea directo al `enum` de Java |
| `jsonb` para auditoría y eventos | La forma del documento cambia con el tiempo; `jsonb` además es consultable |
| Identificador asignado por la aplicación | Un `Despacho` recién construido ya tiene identidad; se puede probar sin base de datos |
| Índice único **parcial** sobre `pedido_id` | Un pedido no puede tener dos despachos vivos, pero sí varios cerrados ([ADR-009](adr/ADR-009-un-despacho-vivo-por-pedido.md)) |

---

## 5. Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> REGISTRADO : registrar (RF-01)

    REGISTRADO --> EN_PREPARACION : alistar
    REGISTRADO --> CANCELADO : cancelar

    EN_PREPARACION --> ASIGNADO : asignar transportador<br/>+ generar guía (RF-05, RF-07)
    EN_PREPARACION --> CANCELADO : cancelar

    ASIGNADO --> EN_RUTA : despachar
    ASIGNADO --> CANCELADO : cancelar

    EN_RUTA --> ENTREGADO : confirmar entrega<br/>con evidencia (RF-09)
    EN_RUTA --> FALLIDO : novedad bloqueante (RF-08)

    FALLIDO --> EN_RUTA : reprogramar<br/>(máx. 3 intentos)
    FALLIDO --> DEVUELTO : devolver

    ENTREGADO --> [*]
    DEVUELTO --> [*]
    CANCELADO --> [*]
```

| Estado | Editable | Carga en poder del transportador | Quién lo produce |
|---|---|---|---|
| REGISTRADO | ✅ | — | Coordinador |
| EN_PREPARACION | ✅ | — | Coordinador (bodega) |
| ASIGNADO | ❌ | — | Coordinador |
| EN_RUTA | ❌ | ✅ | Coordinador (salida) |
| ENTREGADO | ❌ | — | Transportador |
| FALLIDO | ❌ | ✅ | Novedad bloqueante |
| DEVUELTO | ❌ | — | Coordinador |
| CANCELADO | ❌ | — | Coordinador |

**FALLIDO no es un estado final**, y ahí está la diferencia con un Excel de
despachos: "no se pudo entregar hoy" no es lo mismo que "se perdió la venta".
De FALLIDO se sale reprogramando —hasta tres intentos— o devolviendo.

La tabla vive en `EstadoDespacho.TRANSICIONES` y está probada movimiento por
movimiento en `MaquinaEstadosTest`, incluidos los que **no** se permiten
(cancelar algo que ya salió a ruta, entregar sin haber despachado, resucitar
un despacho cerrado).

---

## 6. Secuencia: registrar un despacho

```mermaid
sequenceDiagram
    autonumber
    participant C as Coordinador
    participant L as LOGISTI-X
    participant BD as PostgreSQL (logistix)
    participant S as SALES-X
    participant R as CRM-X

    C->>L: POST /despachos {pedidoId}
    L->>S: GET /pedidos/{pedidoId}
    alt el pedido no se puede despachar
        S-->>L: estado CANCELADO
        L-->>C: 409 PEDIDO_NO_DESPACHABLE
    else pedido válido
        S-->>L: pedido + líneas
        L->>BD: ¿hay despacho vivo para ese pedido?
        alt ya existe
            BD-->>L: sí
            L-->>C: 409 DESPACHO_DUPLICADO
        else no existe
            L->>R: GET /clientes/{clienteId}
            R-->>L: dirección, ciudad, teléfono
            L->>L: copiar carga y calcular peso,<br/>unidades y valor declarado
            L->>BD: guardar despacho + historial + evento<br/>(misma transacción)
            L-->>C: 201 Created + Location
        end
    end
```

## 7. Secuencia: entrega y aviso a SALES-X

Esta es la secuencia que explica por qué el aviso va por bandeja de salida y
no por llamada directa.

```mermaid
sequenceDiagram
    autonumber
    participant T as Transportador
    participant L as LOGISTI-X
    participant BD as PostgreSQL (logistix)
    participant P as Publicador
    participant S as SALES-X
    participant V as Tableros (SSE)

    T->>L: POST /despachos/{id}/entregar<br/>{receptorNombre}
    alt sin evidencia de quién recibió
        L-->>T: 400 RECEPTOR_REQUERIDO
    else con evidencia
        L->>BD: estado=ENTREGADO + historial<br/>+ evento despacho.entregado<br/>(misma transacción)
        L->>V: empuja el cambio al tablero
        L-->>T: 200 despacho entregado
        Note over L,S: La entrega ya ocurrió en el mundo real:<br/>no se deshace porque SALES-X esté caído.
        P->>BD: leer eventos PENDIENTE
        P->>S: POST /pedidos/{id}/estado {ENTREGADO}
        alt SALES-X responde
            S-->>P: 200
            P->>BD: marcar PUBLICADO
        else SALES-X caído
            P->>BD: intentos+1, sigue PENDIENTE
            Note over P: reintenta cada 5 s,<br/>hasta 5 veces
        end
    end
```

---

Anterior: [01 · Análisis y requisitos](01-analisis-y-requisitos.md) ·
Siguiente: [03 · Arquitectura](03-arquitectura.md)
