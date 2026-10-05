# 05 · Pruebas

**Semana 4** — cierre 11 de septiembre de 2026
Estrategia, cobertura de reglas y cómo ejecutarlas

---

## 1. Resultado actual

```
Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Clase | Qué prueba | Casos | Necesita base de datos |
|---|---|---|---|
| `MaquinaEstadosTest` | Transiciones válidas e inválidas (RF-04) | 42 | No |
| `CalculoCargaTest` | Peso, valor, flete, capacidad, evidencia, intentos | 15 | No |
| `GuiaTest` | Formato, dígito de verificación y rótulo (RF-07) | 20 | No |
| `FlujoDespachoTest` | Flujo completo y reglas de integración | 14 | Sí |
| `ContratoVentasTest` | Que el JSON de SALES-X se entienda, venga como venga | 8 | No |
| **Total** | | **99** | |

## 2. Estrategia: dos niveles, cada uno con su trabajo

**Nivel 1 — dominio, sin Spring.** 85 de las 99 pruebas corren en menos de
medio segundo porque prueban clases que se construyen con `new`. Se puede
ejecutar la máquina de estados completa, el cálculo del flete y la generación
de guías sin levantar la aplicación ni tocar una base de datos. Eso es
consecuencia directa de haber puesto las reglas en el dominio y no en el
servicio: si `Despacho` necesitara Spring para validar una transición, cada
una de estas 85 pruebas tardaría segundos en vez de milisegundos.

**Nivel 2 — integración, contra PostgreSQL real.** Las 14 restantes levantan
la aplicación completa contra el **mismo motor de producción**. No se usa H2 en
memoria a propósito: H2 no tiene `jsonb`, ni `timestamptz` con la misma
semántica, ni índices únicos parciales — que es justamente donde vive la regla
RN-08. Una prueba verde en H2 puede quedar roja en PostgreSQL, y ahí las
pruebas dejan de servir para lo único que sirven: dar confianza.

Las pruebas de integración corren contra los **dobles** de SALES-X y CRM-X, así
que no salen a la red ni dependen de que otro equipo tenga su servicio arriba.

**Nivel 3 — contrato con el otro equipo.** `ContratoVentasTest` levanta un
SALES-X falso (con el `HttpServer` del JDK, sin dependencias nuevas) que
responde el JSON que ellos digan que van a responder, y comprueba que
LOGISTI-X lo entiende. Sirve para la fase de integración: se pega su JSON real
en una prueba nueva y en dos segundos se sabe si encaja, **sin necesidad de que
su servidor esté arriba**. Ver [07 · Plan de integración](07-plan-de-integracion.md).

## 3. Cobertura de las reglas de negocio

| Regla | Prueba que la cubre |
|---|---|
| RN-01 Solo se edita antes de asignar | `CalculoCargaTest.asignadoNoSeEdita` |
| RN-02 No se prepara sin carga | `CalculoCargaTest.sinCargaNoSePrepara` |
| RN-03 La guía es inmutable | `CalculoCargaTest.laGuiaEsInmutable` |
| RN-04 No se sale a ruta sin guía | `MaquinaEstadosTest` + restricción `ck_despacho_guia_obligatoria` |
| RN-05 No hay entrega sin evidencia | `CalculoCargaTest.entregaSinReceptorSeRechaza` |
| RN-06 Máximo 3 intentos | `CalculoCargaTest.intentosAgotados` |
| RN-07 Solo transiciones declaradas | `MaquinaEstadosTest` (42 casos) |
| RN-08 Un despacho vivo por pedido | `FlujoDespachoTest.noSeDespachaDosVecesElMismoPedido` y `cancelarLiberaElPedido` |
| RN-09 Solo pedidos despachables | `FlujoDespachoTest.pedidoCanceladoNoSeDespacha` |
| RN-10 Novedad bloqueante → FALLIDO | `CalculoCargaTest.novedadBloqueanteMarcaFallido`, `FlujoDespachoTest.novedadYReprogramacion` |
| RN-11 El servidor calcula la carga | `CalculoCargaTest.pesoTotalConGramos`, `calculoDelFlete` |
| RN-12 La carga cabe en el vehículo | `CalculoCargaTest.cargaQueNoCabeSeRechaza`, `FlujoDespachoTest.laCargaTieneQueCaberEnElVehiculo` |
| RN-13 No se despacha de más | `FlujoDespachoTest.noSeDespachaMasDeLoVendido`, `productoAjenoAlPedido` |
| RN-14 Transportador inactivo | `CalculoCargaTest.transportadorInactivoSeRechaza`, `FlujoDespachoTest.transportadorInactivo` |

## 4. Casos límite que se probaron a propósito

No basta con probar que el camino feliz funciona. Estos son los casos que se
escribieron **buscando romper el sistema**:

| Caso | Qué debe pasar | Prueba |
|---|---|---|
| Sumar 0,250 kg cuarenta veces | Exactamente 10,000 kg, no 9,999999998 | `pesoTotalConGramos` |
| Cancelar algo que ya salió a ruta | Se rechaza: el camión ya está afuera | `noSeCancelaLoQueYaSalio` |
| Entregar sin decir quién recibió | Se rechaza: sin evidencia no hay entrega | `entregaSinReceptorSeRechaza` |
| Entregar por el endpoint genérico de estado | Se rechaza y se indica el endpoint correcto | `entregarExigeSuEndpoint` |
| 3.000 kg de cemento en una moto | Se rechaza con el peso y la capacidad en el mensaje | `laCargaTieneQueCaberEnElVehiculo` |
| Reasignar el transportador | Se cambia el responsable pero **la guía no** | `laGuiaEsInmutable` |
| Cuarto intento de entrega | Se rechaza; solo queda devolver | `intentosAgotados` |
| Despachar dos veces el mismo pedido | Se rechaza con el número del despacho que ya existe | `noSeDespachaDosVecesElMismoPedido` |
| Cancelar y volver a despachar el pedido | **Sí se permite**: el índice parcial solo bloquea los vivos | `cancelarLiberaElPedido` |
| Despachar 25 unidades de un pedido de 10 | Se rechaza citando ambas cantidades | `noSeDespachaMasDeLoVendido` |
| Meter un producto que no estaba en el pedido | Se rechaza | `productoAjenoAlPedido` |
| Buscar la guía en minúsculas | La encuentra igual: se dicta por teléfono | `busquedaPorGuia` |
| Guía con un dígito cambiado | Se detecta como inválida | `GuiaTest.digitoEquivocado` |
| Guía con dos dígitos intercambiados | Se detecta: es el error típico al dictar | `GuiaTest.digitosIntercambiados` |
| SALES-X usa otros nombres de campo (`lineas`, `idProducto`) | Se entiende igual | `ContratoVentasTest.nombresDistintos` |
| SALES-X manda campos de más | Se ignoran | `ContratoVentasTest.camposDeMas` |
| SALES-X llama `APROBADO` a su estado despachable | Se resuelve por configuración, sin recompilar | `ContratoVentasTest.estadoConOtroNombre` |
| Su respuesta llega sin identificador | Falla rápido y con mensaje claro | `ContratoVentasTest.sinIdentificador` |
| Agregar un estado nuevo al enum y olvidar la tabla de transiciones | La prueba falla en vez de que explote en producción | `todoEstadoTieneTablaDeclarada` |
| La regla de Java y el índice de la base se separan | La prueba los amarra | `bloqueoDelPedidoCoincideConElIndiceParcial` |

## 5. Cómo ejecutarlas

```bash
# 1. Levantar PostgreSQL
docker compose up -d

# 2. Crear la base de pruebas (solo la primera vez)
docker exec logistix-postgres psql -U logistix -d postgres \
  -c "CREATE DATABASE logistix_pruebas OWNER logistix"

# 3. Ejecutar todo
mvnw test

# Solo las de dominio (sin base de datos, medio segundo)
mvnw test -Dtest='MaquinaEstadosTest,CalculoCargaTest,GuiaTest'
```

Flyway crea el esquema en la base de pruebas al arrancar, así que no hay pasos
manuales de preparación. Cada prueba de integración corre dentro de una
transacción que se revierte al terminar: el orden de ejecución no las afecta y
la base queda como estaba.

## 6. Prueba manual del recorrido completo

Además de las automatizadas, el flujo se verificó contra la aplicación en
marcha (los identificadores son los del doble de SALES-X):

```bash
T=$(curl -s -X POST localhost:8085/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"coordinador","password":"logistix2026"}' | jq -r .token)

# registrar → alistar → asignar (genera guía) → despachar → entregar
curl -s -X POST localhost:8085/api/v1/despachos -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" \
  -d '{"pedidoId":"55555555-0001-4000-8000-000000000001"}'
```

Resultado observado en el log de la aplicación:

```
Despacho DES-2026-000001 creado para el pedido PED-2026-000001 (1010.000 kg) por coordinador
Despacho DES-2026-000001 asignado a Jhon Alexander Ruiz con guia LGX-2026-000001-3
Despacho DES-2026-000001 entregado a Ana Maria Gil (a tiempo)
SALES-X (simulado): el pedido 55555555-… quedo en ENTREGADO por reporte de LOGISTI-X
```

Esa última línea es la que cierra el círculo de la integración: LOGISTI-X
recibió el pedido de SALES-X y le devolvió el resultado de la entrega.

## 7. Lo que NO se probó y por qué

Ser honesto con los límites es parte del trabajo:

- **Carga y concurrencia real.** No hay pruebas de rendimiento con miles de
  despachos simultáneos. El bloqueo optimista y el índice único parcial están
  puestos y probados en su lógica, pero no bajo carga.
- **El canal SSE.** Se verificó a mano en el navegador (el tablero se actualiza
  solo al mover un despacho desde otra pestaña), no con una prueba
  automatizada.
- **Los adaptadores REST reales** (`VentasRest`, `ClientesRest`). No se pueden
  probar de verdad hasta que los otros equipos publiquen su API; lo que sí está
  probado es que el sistema completo funciona a través del puerto, que es la
  parte que no cambia.

---

Anterior: [04 · Contratos de integración](04-contratos-integracion.md) ·
Siguiente: [06 · Demostración](06-demostracion.md) ·
Ver también: [07 · Plan de integración](07-plan-de-integracion.md)
