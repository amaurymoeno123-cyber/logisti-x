# 06 · Demostración

**Semana 5** — entrega definitiva, 18 de septiembre de 2026
Guion para sustentar LOGISTI-X en doce minutos

---

## 1. Antes de empezar (dos minutos, en privado)

```bash
cd Proyectos/logisti-x
docker compose up -d
mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Abrir en pestañas separadas:

| Pestaña | URL |
|---|---|
| Panel | http://localhost:8085 |
| API documentada | http://localhost:8085/swagger-ui.html |
| Salud | http://localhost:8085/actuator/health |

Usuarios de demostración (perfil `dev`, contraseña `logistix2026`):

| Usuario | Rol | Para mostrar |
|---|---|---|
| `coordinador` | COORDINADOR | El recorrido completo |
| `transportador` | TRANSPORTADOR | Entrega y novedades |
| `consulta` | CONSULTA | Que un rol de solo lectura no puede tocar nada |
| `admin` | ADMIN | Reportes y auditoría |

## 2. Guion (doce minutos)

### Minuto 0–1 · Qué problema resuelve

> "La empresa vende, factura y despacha. El despacho era el único de los tres
> sin sistema: el estado del envío vivía en un chat de WhatsApp. LOGISTI-X
> controla la distribución desde que el pedido sale de bodega hasta que el
> cliente firma."

Mostrar el tablero con despachos en distintos estados. Señalar el indicador
**en vivo** de la barra superior.

### Minuto 1–3 · Registrar un despacho (RF-01, RF-02)

Pulsar **Registrar despacho** y elegir un pedido de la lista.

> "Este pedido no lo tengo yo: se lo pido a SALES-X. Antes de guardar nada,
> LOGISTI-X comprueba tres cosas: que el pedido se pueda despachar, que no
> tenga ya un despacho vivo, y que el cliente tenga dirección en CRM-X."

Señalar en el detalle:
- La dirección la trajo **CRM-X**, no se escribió.
- La carga se copió del pedido y el **peso lo calculó el servidor**.

> **Prueba que lo demuestra:** intentar registrar el pedido `PED-2026-000005`,
> que está CANCELADO en SALES-X. Responde 409 `PEDIDO_NO_DESPACHABLE`.

### Minuto 3–5 · Asignar y generar la guía (RF-05, RF-07)

Alistar → **Asignar transportador**.

> "El sistema solo me ofrece los transportadores cuyo vehículo aguanta esta
> carga. Si intento meter tres toneladas de cemento en una moto, lo rechaza."

Mostrarlo: elegir el pedido pesado (3.000 kg) y ver el mensaje con el peso y la
capacidad. Después asignar el camión y señalar:

- El **número de guía** apareció: `LGX-2026-000001-4`.
- Ese `-4` final es un **dígito de verificación**: si alguien dicta mal la guía
  por teléfono, el sistema lo detecta en vez de mostrar el envío de otro.
- La **fecha prometida** la puso la ruta y el **flete** se calculó con el peso.

Pulsar **Ver rótulo** para mostrar lo que se imprime y se pega en la caja.

### Minuto 5–7 · La calle: novedades (RF-08)

Sacar a ruta y luego **Registrar novedad → Cliente ausente**.

> "Aquí está la diferencia con un Excel. Una novedad que impide entregar no es
> un comentario: el despacho pasa solo a FALLIDO y suma un intento. El estado
> del sistema corresponde a lo que de verdad pasó en la calle."

Señalar el contador de intentos. Reprogramar y volver a fallar dos veces más.

> "Al tercer intento el sistema ya no deja reprogramar: solo queda devolver.
> Un cuarto viaje cuesta más que la devolución."

### Minuto 7–9 · Entrega con evidencia (RF-09) y tiempo real (RNF-02)

**Antes de entregar**, abrir una segunda ventana del navegador con el tablero
al lado.

Confirmar la entrega con el nombre de quien recibe.

> "Sin el nombre de quien recibió no hay entrega. Es la única evidencia que
> queda cuando el cliente reclama que nunca le llegó."

Señalar la otra ventana: **la fila cambió sola**, sin refrescar.

> "Eso es Server-Sent Events. El servidor empuja el cambio. Con treinta
> tableros abiertos, preguntar cada cinco segundos serían veintiún mil
> consultas por hora; así son cero."

### Minuto 9–10 · Trazabilidad (RF-10)

Abrir el detalle del despacho entregado y bajar a la línea de tiempo.

> "Esta es la respuesta a '¿dónde va mi pedido?'. Movimientos de estado y
> novedades mezclados en orden, con quién, cuándo y dónde. Esto es lo que
> consume SERVI-X cuando el cliente abre una PQRS."

### Minuto 10–11 · La integración de verdad

Mostrar el log de la aplicación:

```
SALES-X (simulado): el pedido 55555555-… quedo en ENTREGADO por reporte de LOGISTI-X
```

> "Ahí se cierra el círculo: SALES-X me dijo qué despachar y yo le devolví cómo
> terminó. Y ese aviso **no** se manda dentro de la transacción de la entrega:
> se deja en una bandeja de salida y se entrega después con reintentos. El
> camión ya llegó; la entrega no se puede deshacer porque SALES-X esté caído."

Abrir `GET /api/v1/integracion/estado` y mostrar que dice si está contra los
sistemas reales o contra los dobles.

> "El día que SALES-X publique su API, cambio una propiedad y no toco una línea
> de código."

### Minuto 11–12 · Reportes y cierre

`GET /api/v1/reportes/logistica` desde Swagger, o el panel con `admin`.

> "Cuatro cortes que responden cuatro preguntas: en qué va la operación, quién
> responde, por qué se cae y si estamos cumpliendo. La efectividad por
> transportador es la que decide a quién se le sigue dando carga."

Cerrar con las pruebas:

```bash
mvnw test
# Tests run: 91, Failures: 0, Errors: 0
```

> "Noventa y una pruebas. Setenta y siete corren sin base de datos porque las
> reglas están en el dominio, no repartidas en el código."

## 3. Preguntas que pueden hacer, y la respuesta corta

| Pregunta | Respuesta |
|---|---|
| ¿Por qué no comparten la base de datos con los otros equipos? | Porque entonces no habría integración que evaluar. Cada microproyecto es dueño de su base y todo se comparte por REST (ADR-002). |
| ¿Y si SALES-X cambia su contrato? | El cambio se absorbe en `VentasRest`, que es una clase. El dominio no se entera (puertos y adaptadores). |
| ¿Por qué no hay llave foránea a `pedido`? | Porque esa tabla vive en otra base de datos. Se guarda el identificador y una copia de los datos usados (ADR-003). |
| ¿Qué pasa si se cae la conexión al confirmar la entrega? | La transacción no se confirma y el despacho queda EN_RUTA. Nada a medias. |
| ¿Y si SALES-X está caído cuando entrego? | La entrega queda firme y el aviso se reintenta solo, hasta cinco veces (ADR-008). |
| ¿Cómo evitan despachar dos veces el mismo pedido? | Comprobación en Java para el mensaje y un índice único **parcial** en la base para la garantía real (ADR-009). |
| ¿Por qué `BigDecimal` y no `double`? | Porque sumar 0,250 kg cuarenta veces con `double` no da 10. Hay una prueba de eso. |
| ¿El panel es el sistema? | No. El sistema es la API; el panel es una forma cómoda de operarla. Todo lo que hace el panel se puede hacer desde Swagger. |

## 4. Lo que falta y se sabe que falta

- Los adaptadores REST reales no se han probado contra los sistemas de los
  otros equipos, porque todavía no están publicados.
- No hay pruebas de carga.
- La reasignación masiva de despachos (cuando un transportador se vara con diez
  entregas encima) hoy se hace uno por uno.

---

Anterior: [05 · Pruebas](05-pruebas.md) · Volver al [índice](../README.md)
