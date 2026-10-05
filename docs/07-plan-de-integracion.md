# 07 · Plan de integración

Cómo se conecta LOGISTI-X con los demás microproyectos **sin desarmar lo que
ya funciona** — de ninguno de los dos lados.

> El docente indicó que la integración se hace **después** de que cada equipo
> termine su parte. Este documento es el plan para esa fase.

---

## 1. El problema real de esta fase

Cuando seis sistemas que se construyeron por separado se juntan, aparecen tres
conversaciones, siempre las mismas:

| Lo que se dice | Lo que significa de verdad |
|---|---|
| «Tu API no me sirve» | Los campos se llaman distinto |
| «Cámbiame el modelo para que encaje con el mío» | Uno quiere que el otro pague el costo de la diferencia |
| «Mejor conectémonos a la misma base y ya» | Se rinden y matan el objeto de la asignatura |

Las tres tienen la misma raíz: **nadie definió dónde termina un sistema y
empieza el otro**. Aquí esa frontera está definida desde la semana 3, y por eso
la integración no debería costar más de una tarde.

## 2. La regla de oro

> **Nadie cambia su modelo por el modelo de otro. La diferencia se absorbe en
> la capa de traducción de quien consume.**

Si SALES-X llama `idPedido` a lo que LOGISTI-X llama `pedidoId`, **el que
consume traduce**. No se le pide al otro equipo que renombre su API, ni se
renombra el modelo propio.

En LOGISTI-X esa capa de traducción tiene nombre y apellido:
`VentasRest` y `ClientesRest`. Son las **únicas dos clases** de todo el
proyecto que saben cómo escribe SALES-X o CRM-X. El dominio —`Despacho`,
`EstadoDespacho`, las reglas— no sabe siquiera que existe HTTP.

```
        SALES-X real                 LOGISTI-X
    ┌──────────────────┐      ┌──────────────────────────────┐
    │ como ellos       │      │ VentasRest      │  Despacho  │
    │ decidan escribir ├─────►│ (traduce)       │  (dominio) │
    │ su JSON          │      │ ← aquí se paga  │  ← intacto │
    └──────────────────┘      └──────────────────────────────┘
```

**Consecuencia práctica:** cualquier cosa que ellos cambien cuesta, como
máximo, una línea en una clase. Nunca una migración de base de datos, nunca un
cambio de reglas, nunca volver a probar el dominio.

## 3. Lo que ya está blindado

| Lo que puede pasar en la integración | Qué pasa en LOGISTI-X |
|---|---|
| Sus campos se llaman distinto (`lineas` en vez de `items`, `idProducto` en vez de `productoId`) | Ya se aceptan varios nombres por campo (`@JsonAlias`). Si aparece uno nuevo, es **una línea** en `VentasRest` |
| Mandan campos de más (impuestos, vendedor, descuentos) | Se ignoran; no rompen nada |
| No mandan el peso de los productos | Se sigue: el peso lo pone LOGISTI-X al armar la carga |
| No mandan el consecutivo legible ni el nombre del cliente | Se sigue: son datos para mostrar, no para decidir |
| Llaman `APROBADO` a lo que aquí se espera como `CONFIRMADO` | Se agrega a `integrax.ventas.estados-despachables` en el YAML. **Sin recompilar** |
| Su servidor no está listo el día de la prueba | Se vuelve a `simulado: true` y todo sigue funcionando |
| Su servidor se cae en mitad de una entrega | La entrega **queda firme**; el aviso se reintenta solo (ADR-008) |
| Rechazan un aviso repetido con error 500 | Se reintenta hasta 5 veces y queda registrado; no se pierde |

Lo único **irrenunciable** de su respuesta son tres campos: `pedidoId`,
`clienteId` y `estado`. Sin esos tres no hay integración posible, y la razón es
del negocio, no del código: sin identificador no hay qué asociar, sin cliente
no hay a dónde llevar, y sin estado se despacharían pedidos cancelados.

## 4. El procedimiento, paso a paso

### Paso 1 — Antes de conectar nada: pedirles un ejemplo

No se pide "la API". Se pide **una respuesta de ejemplo**: el JSON exacto que
devuelve `GET /pedidos/{id}` para un pedido de verdad. Un pantallazo de Postman
sirve.

### Paso 2 — Probarlo sin que su servidor exista

Ese JSON se pega en una prueba nueva de `ContratoVentasTest` y se corre:

```bash
mvnw test -Dtest=ContratoVentasTest
```

En dos segundos se sabe si encaja. Si falla, el mensaje dice qué campo falta y
el arreglo es un alias más. **Esto se hace sin coordinar horarios con nadie.**

### Paso 3 — Apuntar al sistema real

```yaml
integrax:
  ventas:
    simulado: false
    url-base: http://<ip-del-compañero>:8080
    estados-despachables: [CONFIRMADO, FACTURADO, APROBADO]   # su vocabulario
  crm:
    simulado: false
    url-base: http://<ip-del-otro>:8081
```

Se reinicia. No se compila nada.

### Paso 4 — Diagnóstico en vivo

```http
GET /api/v1/integracion/diagnostico?pedidoId={un pedido real de ellos}
```

Devuelve un informe punto por punto:

```
listo para integrar: false
  [OK   ] SALES-X · responde
  [OK   ] SALES-X · campo pedidoId
  [FALLA] SALES-X · campo clienteId: Sin clienteId no se le puede pedir a
          CRM-X la dirección de entrega.
  [AVISO] SALES-X · peso de los productos: 2 de 2 líneas llegan sin peso.
```

**Esto es lo que reemplaza la discusión.** En vez de «no me sirve», se manda el
informe: dice exactamente qué campo falta y por qué se necesita. Un aviso no
bloquea; una falla sí, y explica la razón de negocio.

### Paso 5 — Prueba de ida y vuelta

Registrar un despacho con un pedido real de ellos, sacarlo a ruta y comprobar
que **su** pedido quedó en DESPACHADO. Ese es el momento en que la integración
está hecha: el dato dio la vuelta completa.

## 5. Qué se negocia y qué no

**Sí se negocia** (son acuerdos, no imposiciones):

- Los nombres de los campos: cada uno usa los suyos, el que consume traduce.
- El vocabulario de estados: se configura.
- Quién expone qué endpoint y con qué ruta.
- Las credenciales entre sistemas y las URL.

**No se negocia** (y conviene decirlo antes, no durante):

1. **Cada microproyecto tiene su propia base de datos.** Nadie se conecta a la
   base de otro. Si esto se rompe, no hay integración que evaluar y la
   asignatura pierde su objeto (ADR-002).
2. **Nadie cambia su modelo interno por el de otro.** La diferencia se traduce
   en la frontera.
3. **Los identificadores comunes** del enunciado (`pedidoId`, `clienteId`,
   `productoId`, `despachoId`) se respetan tal cual. Es lo único que el
   enunciado impone y es lo que hace posible cruzar los sistemas.
4. **Quien recibe un aviso repetido lo tolera.** La entrega de eventos es *al
   menos una vez*: por eso llevan `eventoId`.

## 6. Si un equipo no llega

LOGISTI-X vuelve a `simulado: true` y funciona completo: registra, asigna,
despacha, entrega, reporta. En la sustentación se muestra con los dobles y se
dice la verdad — «esta parte está lista y probada; falta que el equipo X
publique su endpoint». Eso es muy distinto a llegar sin nada.

**Lo que no se debe hacer:** copiar la base de datos del otro, o pedirle su
`.sql` para levantarlo localmente. Eso es exactamente el atajo que convierte
seis sistemas en uno solo.

## 7. Acta de acuerdo (para la reunión)

> Los equipos de INTEGRAX acordamos:
>
> 1. Cada microproyecto es dueño exclusivo de su base de datos. Ningún otro se
>    conecta a ella. La información se comparte por **API REST**.
> 2. Respetamos los identificadores comunes del enunciado: `clienteId`,
>    `productoId`, `pedidoId`, `facturaId`, `despachoId`, `casoId`.
> 3. Cada equipo publica **una respuesta de ejemplo** de sus endpoints antes de
>    la fecha de integración. Un JSON de ejemplo, no una promesa.
> 4. Los cambios de contrato se avisan; los nombres de campo son problema de
>    quien consume, no de quien publica.
> 5. Quien reciba un aviso de otro sistema tolera que llegue repetido.
>
> Fecha de integración acordada: ____________
> Responsable por equipo: ____________

---

Anterior: [06 · Demostración](06-demostracion.md) · Volver al [índice](../README.md)
