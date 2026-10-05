# ADR-011 · Tiempo real con Server-Sent Events

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

El RNF-02 pide "actualizacion de estados en tiempo real". Habia tres caminos:
sondeo desde el navegador, WebSocket o Server-Sent Events.

## Decision

**Server-Sent Events** en `GET /api/v1/despachos/eventos`. El servidor
mantiene la conexion abierta y empuja cada cambio de despacho.

## Por que

- **Contra el sondeo:** con treinta tableros abiertos, preguntar cada cinco
  segundos son 21.600 consultas por hora que casi siempre responden "nada
  nuevo". Con SSE son cero.
- **Contra WebSocket:** el trafico va en un solo sentido, del servidor al
  tablero. WebSocket es bidireccional y trae su propio protocolo, su
  negociacion y sus problemas con proxies. SSE es HTTP normal: atraviesa
  cualquier infraestructura y el navegador reconecta solo.

## Consecuencias

- El `EventSource` del navegador **no admite cabeceras**, asi que esa ruta
  —y solo esa— acepta el token como parametro `access_token`. Queda
  documentado en el filtro y en el documento de arquitectura: un token en la
  URL aparece en los registros del servidor, de modo que no se admite como
  forma general de autenticacion.
- Los emisores se guardan en memoria: si hubiera varias instancias de
  LOGISTI-X, cada una avisaria solo a sus propios tableros. Con una instancia
  no es problema; con varias habria que pasar por un canal comun.
- Que el aviso al tablero falle no puede tumbar la operacion: se captura y se
  sigue.
