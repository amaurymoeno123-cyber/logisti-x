# ADR-003 · Referencias externas sin llave foranea, con snapshot

**Estado:** aceptada · 28 de agosto de 2026

## Contexto

`despacho` necesita saber a que pedido corresponde (`pedidoId`, de SALES-X), a
que cliente (`clienteId`, de CRM-X) y que productos lleva (`productoId`, de
INVENTA-X). Esas tablas no existen en esta base de datos (ADR-002).

## Decision

Guardar el identificador **sin llave foranea**, y ademas una **copia local
(snapshot)** de los datos que se usaron: nombre y documento del cliente,
direccion de entrega, SKU y nombre del producto, numero del pedido.

## Por que

**Sin FK** porque no se puede: la tabla referenciada esta en otra base de datos.

**Con snapshot** por una razon que no es tecnica sino documental: una guia
impresa dice "Almacenes La 14 S.A.S., Carrera 48 # 32-45". Si manana CRM-X
corrige la razon social o el cliente se muda, el despacho de la semana pasada
**no puede cambiar**: describe algo que ya ocurrio en el mundo fisico.

## Consecuencias

- La base no impide guardar un `pedidoId` inexistente: por eso se valida contra
  SALES-X antes de crear el despacho.
- El snapshot puede quedar viejo respecto al maestro. Es intencional.
- Para el dato de hoy se consulta al otro sistema; el snapshot es historia.
