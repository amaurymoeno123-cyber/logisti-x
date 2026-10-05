# ADR-007 · Bandeja de salida transaccional

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

Cuando un despacho sale a ruta o se entrega hay que avisarle a SALES-X, a
FINAN-X y a SERVI-X. La forma ingenua es llamar su API dentro de la transaccion
que guarda el cambio.

## Decision

Patron **Transactional Outbox**: en la misma transaccion que cambia el
despacho se inserta una fila en `outbox_evento`. Un publicador aparte
(`PublicadorEventos`) lee las pendientes cada cinco segundos y las entrega con
reintentos.

## Por que

Llamar al otro sistema dentro de la transaccion tiene dos finales, los dos
malos:

1. El otro sistema esta caido y su excepcion **tumba la operacion local**: el
   camion salio de verdad, pero el sistema dice que no.
2. Se atrapa la excepcion y se sigue: el aviso se pierde en silencio y los dos
   sistemas quedan desalineados sin que nadie se entere.

Con la bandeja de salida, el evento y el cambio se guardan juntos o no se
guarda ninguno de los dos, y la entrega al otro sistema se reintenta sola.

## Consecuencias

- La entrega es **al menos una vez**, no exactamente una vez. Por eso el
  mensaje lleva `eventoId` estable y el consumidor debe descartar repetidos.
- Hay un retardo de segundos entre el cambio y el aviso. En logistica eso no
  cambia nada.
- Un evento que agota los reintentos queda FALLIDO y hay que revisarlo a mano;
  el log lo dice con nivel ERROR.
