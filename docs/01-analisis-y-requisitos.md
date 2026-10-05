# 01 · Análisis y requisitos

**Semana 1** — cierre 21 de agosto de 2026
Microproyecto 2.5 **LOGISTI-X** · Gestión de Despachos y Entregas

---

## 1. El problema

La empresa vende, factura y despacha, pero el despacho es el único de esos
tres procesos que no tiene sistema. Lo que se repite:

| Síntoma | Costo real |
|---|---|
| Nadie sabe dónde va un pedido | El cliente llama, el vendedor llama a bodega, bodega llama al transportador y la respuesta llega media hora después |
| El estado del envío vive en un chat de WhatsApp | Cuando el transportador cambia de teléfono, la historia se pierde |
| No hay número de guía propio | No hay cómo referirse a un envío sin describirlo entero |
| Las novedades se cuentan de palabra | "No lo pude entregar" no queda escrito, y al mes nadie sabe por qué se cae el 20 % de las entregas |
| No hay evidencia de entrega | Ante un reclamo no se puede probar quién recibió |
| Se despacha dos veces el mismo pedido | Y se descubre cuando el segundo camión llega |
| Ventas, facturación y logística no se hablan | El pedido queda "confirmado" para siempre en SALES-X aunque ya se entregó |

## 2. Alcance

### Dentro del alcance

- Registrar despachos contra un pedido de **SALES-X**.
- Administrar el maestro logístico propio: **transportadores** y **rutas**.
- Asignar transportador y ruta, y **generar el número de guía**.
- Administrar el ciclo de estados del despacho y su historial completo.
- Registrar **novedades de transporte** y su efecto sobre la entrega.
- Confirmar entregas **con evidencia** de quién recibió.
- Consultar la **trazabilidad** completa de un despacho.
- Reportes logísticos: por estado, por transportador, causas de no entrega y
  cumplimiento de la promesa de entrega.
- Exponer una API REST documentada y avisar a los demás sistemas por eventos.

### Fuera del alcance (lo hace otro microproyecto)

| Necesidad | Responsable |
|---|---|
| Crear y mantener clientes | CRM-X |
| Catálogo de productos y existencias | INVENTA-X |
| Crear pedidos y calcular totales | SALES-X |
| Emitir facturas y registrar pagos | FINAN-X |
| PQRS y reclamos del cliente | SERVI-X |

> LOGISTI-X **consulta** pedidos y clientes, pero no los administra. Esa
> frontera es la decisión más importante del análisis: define qué tabla existe
> aquí y qué tabla no. Transportadores y rutas sí son de esta casa, y por eso
> son las únicas entidades ajenas al despacho con llave foránea real.

## 3. Actores

| Actor | Qué hace en LOGISTI-X |
|---|---|
| **Coordinador de despachos** | Registra, alista, asigna transportador, saca a ruta, cancela |
| **Transportador** | Confirma entregas y reporta novedades de su ruta |
| **Administrador** | Todo lo anterior + maestro de transportadores y rutas + auditoría |
| **Consulta** | Solo lee (gerencia, servicio al cliente) |
| **SALES-X** *(sistema)* | Provee el pedido; recibe de vuelta DESPACHADO y ENTREGADO |
| **CRM-X** *(sistema)* | Provee dirección y teléfono de entrega |
| **SERVI-X** *(sistema)* | Consulta trazabilidad al atender una PQRS |
| **FINAN-X** *(sistema)* | Escucha la entrega para cerrar el ciclo de cobro |

## 4. Requerimientos funcionales

Los once del enunciado, con dónde quedó implementado cada uno.

| RF | Enunciado | Implementación | Verificado en |
|---|---|---|---|
| RF-01 | Registrar despachos | `POST /api/v1/despachos` | `FlujoDespachoTest.flujoCompleto` |
| RF-02 | Asociar despachos a pedidos | `pedidoId` validado contra SALES-X + snapshot | `pedidoCanceladoNoSeDespacha`, `noSeDespachaDosVecesElMismoPedido` |
| RF-03 | Consultar información de despachos | `GET /despachos` con filtros, `/{id}`, `/numero/{n}`, `/guia/{g}` | `busquedaPorGuia` |
| RF-04 | Actualizar estado de entrega | `POST /despachos/{id}/estado` + acciones con nombre propio | `MaquinaEstadosTest` (42 casos) |
| RF-05 | Asignar transportadores | `POST /despachos/{id}/asignar` + CRUD `/transportadores` | `laCargaTieneQueCaberEnElVehiculo`, `transportadorInactivo` |
| RF-06 | Consultar historial de entregas | `GET /despachos/{id}/historial` | `flujoCompleto` |
| RF-07 | Generar número de guía | `GuiaServicio` (formato + dígito de verificación) | `GuiaTest` (20 casos) |
| RF-08 | Registrar novedades de transporte | `POST /despachos/{id}/novedades` | `novedadYReprogramacion`, `novedadInformativa` |
| RF-09 | Confirmar entregas realizadas | `POST /despachos/{id}/entregar` (exige receptor) | `entregaSinReceptorSeRechaza` |
| RF-10 | Consultar trazabilidad de despachos | `GET /despachos/{id}/trazabilidad` | `flujoCompleto` |
| RF-11 | Generar reportes logísticos | `GET /reportes/logistica` | `ReporteServicio` |

## 5. Requerimientos no funcionales

| RNF | Enunciado | Cómo se cumple |
|---|---|---|
| RNF-01 | Disponibilidad ≥ 95 % | `/actuator/health` con sondas *liveness* y *readiness*; un fallo de SALES-X o CRM-X devuelve 502 sin tumbar LOGISTI-X |
| RNF-02 | Actualización de estados en tiempo real | Canal **Server-Sent Events** en `GET /despachos/eventos`: el servidor empuja cada cambio al tablero, sin sondeo |
| RNF-03 | Arquitectura orientada a servicios | `/api/v1/...`, JSON, verbos y códigos HTTP estándar, errores RFC 7807, eventos hacia los demás sistemas |
| RNF-04 | Respuesta < 3 segundos | Índices en las columnas de filtro; agregaciones en SQL, no en memoria; carga con `join fetch` para evitar N+1; timeouts de 2 s / 3 s hacia otros sistemas |
| RNF-05 | Seguridad de acceso | JWT firmado (HS384), BCrypt, cinco roles, todo cerrado por defecto |
| RNF-06 | Registro histórico de movimientos | `despacho_estado_historial` (solo inserta) + tabla `auditoria` con antes/después en JSONB |
| RNF-07 | Compatibilidad móvil | Panel responsivo verificado en iPhone real emulado, sin desborde horizontal |
| RNF-08 | Documentación de interfaces | OpenAPI generado del código (`/v3/api-docs`), nunca escrito a mano, + `docs/04-contratos-integracion.md` |

## 6. Reglas de negocio

Estas son las reglas que el código hace cumplir; están listadas aparte porque
son lo que se prueba.

| # | Regla | Por qué |
|---|---|---|
| RN-01 | Solo un despacho **REGISTRADO o EN_PREPARACION** se puede modificar | Después ya hay una guía impresa y un rótulo pegado en la caja |
| RN-02 | No se prepara un despacho **sin carga** | Un despacho vacío es un camión que sale a nada |
| RN-03 | El **número de guía es inmutable** una vez generado | Está impreso en el rótulo; reasignar el transportador no reimprime la caja |
| RN-04 | No se sale a ruta **sin guía y sin transportador** | Sin eso no hay a quién reclamarle la carga |
| RN-05 | No hay entrega **sin nombre de quien recibió** | Es la única evidencia ante un reclamo |
| RN-06 | Máximo **3 intentos** de entrega; después solo queda devolver | Un cuarto viaje cuesta más que la devolución |
| RN-07 | Solo se permiten las **transiciones declaradas** | Cancelar algo ya entregado descuadra la contabilidad de otros dos sistemas |
| RN-08 | Un pedido **no puede tener dos despachos vivos** | Es como se despacha dos veces lo mismo |
| RN-09 | Solo se despacha un pedido que SALES-X tenga **CONFIRMADO, FACTURADO o DESPACHADO** | Sacar un pedido cancelado es pérdida pura |
| RN-10 | Una **novedad bloqueante** en ruta pasa el despacho a FALLIDO y suma un intento | El estado del sistema tiene que corresponder a lo que pasó en la calle |
| RN-11 | El **peso, el valor declarado y el flete** los calcula el servidor | Si el flete viniera del cliente HTTP, cualquiera se manda un envío nacional por mil pesos |
| RN-12 | La carga **tiene que caber** en el vehículo asignado | 3.000 kg de cemento no caben en una moto |
| RN-13 | No se despacha **más de lo que tiene el pedido**, ni un producto ajeno a él | Si no, el descuadre con inventario aparece semanas después |
| RN-14 | Un transportador **no se borra, se inactiva**, y no se inactiva con carga en la calle | Borrarlo dejaría despachos históricos sin responsable |

## 7. Supuestos

1. SALES-X y CRM-X exponen APIs REST con los identificadores comunes
   acordados (`pedidoId`, `clienteId`). Mientras no existan, LOGISTI-X trabaja
   contra dobles de prueba.
2. La moneda por omisión es COP. El flete se liquida como tarifa fija de la
   ruta más un costo por kilogramo, ambos configurables.
3. Un despacho pertenece a un solo pedido y a un solo cliente. Un pedido puede
   generar varios despachos a lo largo del tiempo, pero solo uno vivo a la vez.
4. El peso de los productos llega desde el pedido; si SALES-X no lo trae —vende
   unidades, no kilogramos— se indica al armar la carga.

## 8. Riesgos identificados y cómo se mitigaron

| Riesgo | Mitigación aplicada |
|---|---|
| Los otros equipos no publican su API a tiempo | Puertos + dobles de prueba; se cambia con una propiedad ([ADR-006](adr/ADR-006-dobles-de-prueba.md)) |
| Avisarle a SALES-X dentro de la transacción | Bandeja de salida transaccional ([ADR-007](adr/ADR-007-outbox-de-eventos.md)) |
| La entrega no se puede deshacer si el aviso falla | El aviso va por outbox con reintentos, no por compensación ([ADR-008](adr/ADR-008-aviso-a-sales-x.md)) |
| Un sistema caído tumba a LOGISTI-X | Timeouts cortos + traducción a HTTP 502 |
| Dos coordinadores despachan el mismo pedido a la vez | Índice único parcial en la base, no solo la comprobación en Java ([ADR-009](adr/ADR-009-un-despacho-vivo-por-pedido.md)) |
| Errores de redondeo en peso y flete | `BigDecimal` con escala y redondeo únicos ([ADR-004](adr/ADR-004-dinero-y-peso.md)) |

---

Siguiente: [02 · Modelo de la solución](02-modelo-de-la-solucion.md)
