# ADR-009 · Un solo despacho vivo por pedido (indice unico parcial)

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

Despachar dos veces el mismo pedido es uno de los errores mas caros de la
operacion: sale un segundo camion con mercancia que ya se entrego. Pero un
pedido **si** puede tener varios despachos a lo largo del tiempo: si el primero
se devolvio o se cancelo, hay que poder volver a despacharlo.

## Decision

Un **indice unico parcial** en PostgreSQL:

```sql
CREATE UNIQUE INDEX uk_despacho_pedido_activo
    ON despacho (pedido_id)
    WHERE estado NOT IN ('ENTREGADO', 'DEVUELTO', 'CANCELADO');
```

Mas una comprobacion en Java que da el mensaje claro citando el despacho que ya
existe.

## Por que

La comprobacion en Java sola no basta: entre el `SELECT` que no encuentra nada
y el `INSERT`, otro coordinador puede haber insertado. Solo la base de datos
puede garantizar unicidad bajo concurrencia.

El indice solo tampoco basta: daria un error tecnico de violacion de
restriccion en vez de "el pedido PED-2026-000001 ya tiene el despacho
DES-2026-000001 en estado EN_RUTA".

Y es **parcial** porque la regla no es "un despacho por pedido", sino "un
despacho **vivo** por pedido". Un indice unico normal impediria volver a
despachar algo devuelto.

## Consecuencias

- Hay una prueba (`bloqueoDelPedidoCoincideConElIndiceParcial`) que amarra la
  regla de Java con la definicion del indice: si una cambia sin la otra, falla.
- La condicion del indice y el metodo `EstadoDespacho.bloqueaElPedido()` tienen
  que decir lo mismo.
