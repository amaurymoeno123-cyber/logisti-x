# ADR-002 · Una base de datos por microproyecto

**Estado:** aceptada · 21 de agosto de 2026

## Contexto

Los seis microproyectos de INTEGRAX manejan datos relacionados: clientes,
productos, pedidos, facturas, despachos y casos. La pregunta que se hizo el
grupo fue si armar **una sola base compartida** para los seis o una por equipo.

## Decision

**Una base de datos por microproyecto.** LOGISTI-X es dueno exclusivo del
esquema `logistix`; ningun otro sistema se conecta ahi. Todo lo que otro
equipo necesite se pide por API REST.

## Por que

1. **Es el objeto de la asignatura.** Se llama Integracion de Aplicaciones y
   Sistemas. Si los seis comparten tablas, no hay integracion que evaluar: hay
   un solo sistema con seis carpetas.
2. **Independencia real.** LOGISTI-X puede cambiar su esquema, migrar o
   desplegarse sin coordinar con cinco equipos.
3. **La frontera obliga a pensar.** Al no poder hacer `join` con `pedido`, hay
   que decidir explicitamente que se copia y que se consulta. Ese ejercicio es
   el aprendizaje.

## Consecuencias

- No hay integridad referencial entre sistemas: se resuelve con validacion por
  API y snapshot (ADR-003).
- Un reporte que cruce datos de dos sistemas exige dos llamadas, no un `join`.
- Cada equipo tiene que exponer y documentar su API de verdad, no confiar en
  que el otro "lea la tabla".
