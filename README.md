# LOGISTI-X · Gestión de Despachos y Entregas

[![Pruebas](https://github.com/amaurymoeno123-cyber/logisti-x/actions/workflows/pruebas.yml/badge.svg)](https://github.com/amaurymoeno123-cyber/logisti-x/actions/workflows/pruebas.yml)
![Java 21](https://img.shields.io/badge/Java-21-informational)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-informational)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-informational)

Microservicio que controla la distribución desde que el pedido sale de bodega
hasta que el cliente firma: rutas, transportadores, número de guía, estados de
despacho, novedades de transporte y trazabilidad. Se integra con los sistemas
de ventas y clientes por contratos REST y publica eventos hacia finanzas.

![Tablero de despachos](docs/capturas/02%20-%20Tablero%20de%20despachos.png)

## Lo que vale la pena mirar

- **Puertos y adaptadores.** El dominio no conoce HTTP ni la base de datos: 85 de
  las 99 pruebas corren sin levantar Spring.
- **Bandeja de salida (outbox) en vez de saga.** Una entrega firmada no se puede
  compensar, así que el aviso a ventas se guarda en la misma transacción y se
  reintenta ([ADR-008](docs/adr/ADR-008-aviso-a-sales-x.md)).
- **Máquina de estados explícita** para el despacho, con transiciones validadas
  en el dominio ([ADR-005](docs/adr/ADR-005-maquina-de-estados.md)).
- **Pruebas contra PostgreSQL real**, no H2, también en el CI.
- **Pruebas de contrato** del JSON de otros equipos sin que su servidor exista.
- **Seguridad cerrada por defecto:** JWT con 5 roles, BCrypt, y la app no
  arranca si falta el secreto (sin valores por defecto en producción).
- **Tiempo real con SSE** para el tablero operativo
  ([ADR-011](docs/adr/ADR-011-tiempo-real-con-sse.md)).

```
Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · JWT · OpenAPI · SSE
99 pruebas automatizadas · 11 decisiones de arquitectura · 20 casos de uso
```

> Proyecto académico: microproyecto 2.5 de la plataforma **INTEGRAX**, Electiva
> Profesional II (Integración de Aplicaciones y Sistemas), Unisabaneta, 2026-2.

---

## Arrancar en tres pasos

```bash
docker compose up -d                                    # PostgreSQL en el 5434
mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # la aplicación en el 8085
```

| Qué | Dónde |
|---|---|
| Panel operativo | http://localhost:8085 |
| API documentada | http://localhost:8085/swagger-ui.html |
| Contrato OpenAPI | http://localhost:8085/v3/api-docs |
| Salud | http://localhost:8085/actuator/health |

Usuarios del perfil `dev` (contraseña `logistix2026`): `admin`,
`coordinador`, `transportador`, `consulta`, `salesx`, `servix`.

> El perfil `dev` es **solo local**. Sin él, la aplicación no arranca si falta
> `LOGISTIX_JWT_SECRETO`, y eso es a propósito.

## Pruebas

```bash
docker exec logistix-postgres psql -U logistix -d postgres \
  -c "CREATE DATABASE logistix_pruebas OWNER logistix"     # solo la primera vez
mvnw test
```

```
Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
```

## Documentación

| Documento | Qué contiene |
|---|---|
| [01 · Análisis y requisitos](docs/01-analisis-y-requisitos.md) | Problema, alcance, actores, 11 RF, 8 RNF y 14 reglas de negocio |
| [02 · Modelo de la solución](docs/02-modelo-de-la-solucion.md) | Casos de uso, diagrama de clases, modelo E-R, máquina de estados y secuencias |
| [03 · Arquitectura](docs/03-arquitectura.md) | Capas, puertos y adaptadores, despliegue, errores, seguridad y rendimiento |
| [04 · Contratos de integración](docs/04-contratos-integracion.md) | Lo que se le pide a cada equipo y lo que se les ofrece |
| [05 · Pruebas](docs/05-pruebas.md) | Estrategia, cobertura de reglas y casos límite |
| [06 · Demostración](docs/06-demostracion.md) | Guion de sustentación de doce minutos |
| [07 · Plan de integración](docs/07-plan-de-integracion.md) | Cómo conectar con los otros equipos sin desarmar nada |
| [08 · Casos de uso](docs/08-casos-de-uso.md) | Los 20 casos de uso completos: flujos, excepciones y trazabilidad RF → CU → servicio → prueba |
| [ADR](docs/adr/) | Las 11 decisiones de arquitectura, con su porqué |

Los diagramas están además como imagen en [`docs/diagramas/`](docs/diagramas/).

## Cómo está organizado el código

```
src/main/java/co/unisabaneta/integrax/logistix/
├── despacho/
│   ├── dominio/        Despacho (raíz), EstadoDespacho, Novedad, DespachoItem
│   ├── aplicacion/     DespachoServicio, GuiaServicio, ReporteServicio, EventosEnVivo
│   ├── repositorio/    consultas JPA + Specifications + proyecciones de reporte
│   └── api/            controladores REST y DTO
├── catalogo/           transportadores y rutas (maestro propio)
├── integracion/
│   ├── ventas/         PuertoVentas → SALES-X (real y simulado)
│   ├── crm/            PuertoClientes → CRM-X (real y simulado)
│   └── eventos/        bandeja de salida y publicador
├── seguridad/          JWT, filtro, usuarios
├── auditoria/          rastro de quién hizo qué
├── comun/              Dinero, Peso, excepciones, manejador de errores
└── config/             seguridad, OpenAPI, integración, propiedades
```

**La regla que ordena todo:** las dependencias apuntan hacia adentro. El
dominio no conoce HTTP ni la base de datos, y por eso 85 de las 99 pruebas
corren sin levantar Spring.

## La API en una pantalla

```
POST   /api/v1/auth/login

POST   /api/v1/despachos                        registrar (RF-01, RF-02)
GET    /api/v1/despachos                        buscar con filtros (RF-03)
GET    /api/v1/despachos/{id}
GET    /api/v1/despachos/guia/{numeroGuia}      como pregunta el cliente
GET    /api/v1/despachos/pedido/{pedidoId}
PATCH  /api/v1/despachos/{id}                   corregir destino
POST   /api/v1/despachos/{id}/items             armar la carga
POST   /api/v1/despachos/{id}/preparar          alistar
POST   /api/v1/despachos/{id}/asignar           transportador + guía (RF-05, RF-07)
GET    /api/v1/despachos/{id}/guia              rótulo para imprimir
POST   /api/v1/despachos/{id}/despachar         salir a ruta
POST   /api/v1/despachos/{id}/entregar          con evidencia (RF-09)
POST   /api/v1/despachos/{id}/novedades         novedad de transporte (RF-08)
POST   /api/v1/despachos/{id}/reprogramar       nuevo intento
POST   /api/v1/despachos/{id}/devolver
POST   /api/v1/despachos/{id}/cancelar
POST   /api/v1/despachos/{id}/estado            para otro microproyecto (RF-04)
GET    /api/v1/despachos/{id}/historial         (RF-06)
GET    /api/v1/despachos/{id}/trazabilidad      (RF-10)
GET    /api/v1/despachos/eventos                canal en vivo, SSE (RNF-02)

GET    /api/v1/transportadores                  maestro (RF-05)
GET    /api/v1/rutas
GET    /api/v1/reportes/logistica               (RF-11)
GET    /api/v1/novedades/abiertas
GET    /api/v1/integracion/estado               con quién está hablando
GET    /api/v1/integracion/diagnostico          ¿la API del otro equipo sirve?
```

## Integración con los demás microproyectos

| Sistema | Relación |
|---|---|
| **SALES-X** | Le pide el pedido a despachar y le devuelve DESPACHADO / ENTREGADO |
| **CRM-X** | Le pide la dirección y el teléfono de entrega |
| **FINAN-X** | Recibe el evento `despacho.entregado` |
| **SERVI-X** | Consulta la trazabilidad al atender una PQRS |
| **INVENTA-X** | No se consulta: el peso llega dentro del pedido |

Mientras los otros equipos no publiquen su API, LOGISTI-X corre contra dobles
de prueba con datos coherentes. Pasar a real es cambiar una propiedad:

```yaml
integrax:
  ventas:
    simulado: false
    url-base: https://sales-x.integrax.co
```

## Seguridad

- Ni un secreto escrito en el repositorio: todo por variable de entorno
  (`.env.example` lista cuáles).
- `.env` está en `.gitignore` desde el primer commit.
- JWT firmado, BCrypt para las contraseñas, cinco roles, todo cerrado por
  defecto.
- Los usuarios de demostración solo existen si
  `logistix.seguridad.sembrar-demo: true`, y la aplicación lo advierte en el
  log al arrancar.
