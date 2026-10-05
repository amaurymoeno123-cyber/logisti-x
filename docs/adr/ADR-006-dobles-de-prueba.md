# ADR-006 · Puertos con dobles de prueba

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

LOGISTI-X necesita SALES-X (el pedido) y CRM-X (la direccion) para funcionar.
Los seis equipos trabajan en paralelo y ninguno tenia su API publicada al
empezar la semana 3.

## Decision

Definir dos interfaces —`PuertoVentas` y `PuertoClientes`— y dos
implementaciones de cada una: la real por HTTP y un doble en memoria. Cual se
usa lo decide una propiedad al arrancar.

## Por que

Esperar habria costado semanas de las cinco disponibles. Con los puertos,
LOGISTI-X se construyo y se probo completo desde el primer dia.

Los dobles **no** son objetos vacios: `VentasSimuladas` guarda el estado que
LOGISTI-X le reporta, de modo que en la demostracion se ve el pedido pasar a
DESPACHADO y luego a ENTREGADO del otro lado. Y traen datos escogidos para
probar reglas: un pedido cancelado, uno de 3.000 kg, un cliente sin direccion.

## Consecuencias

- Pasar a los sistemas reales es cambiar `integrax.ventas.simulado: false`.
- Los adaptadores REST no se pueden probar de verdad hasta que exista el otro
  sistema; lo que si esta probado es todo lo que pasa a traves del puerto.
- Hay que mantener los dobles coherentes entre si (los mismos `clienteId` en
  ambos), o la demostracion se cae en la primera pantalla.
