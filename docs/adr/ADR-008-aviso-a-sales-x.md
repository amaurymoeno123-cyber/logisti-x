# ADR-008 · Avisarle a SALES-X por outbox, no por compensacion

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

Cuando LOGISTI-X entrega, SALES-X tiene que mover su pedido a ENTREGADO. La
duda era si hacerlo de forma sincrona dentro de la operacion —con una
compensacion tipo saga si algo falla— o de forma asincrona.

## Decision

**Asincrona, por la bandeja de salida.** La operacion local se confirma sola y
el aviso sale despues, con reintentos.

## Por que

Una saga con compensacion exige que la accion se pueda **deshacer**. Aqui no se
puede: si LOGISTI-X le dice a SALES-X "entregado" y despues el commit local
falla, no existe "des-entregar" — el cliente ya firmo y el paquete esta
adentro. Una compensacion que no puede compensar es peor que no tenerla, porque
da una falsa sensacion de seguridad.

El orden correcto es el inverso al de un sistema de reservas: **primero se
confirma lo que ya paso en el mundo real**, y despues se avisa, insistiendo
hasta que el otro conteste.

## Consecuencias

- Durante unos segundos, LOGISTI-X dice ENTREGADO y SALES-X todavia dice
  DESPACHADO. Es consistencia eventual, y es correcta: refleja que el aviso va
  en camino.
- SALES-X debe **tolerar reintentos** del mismo cambio. Esta escrito en el
  contrato de integracion.
- Si SALES-X estuvo caido una hora, al volver recibe los avisos pendientes.
