# ADR-005 · La maquina de estados vive en el dominio

**Estado:** aceptada · 28 de agosto de 2026

## Contexto

Un despacho pasa por ocho estados y no todos los movimientos son legales. Esa
logica podia quedar en tres sitios: en `if` dentro de los controladores, en el
servicio de aplicacion, o en el dominio.

## Decision

Una tabla de transiciones dentro del `enum EstadoDespacho`, y un unico metodo
`Despacho.moverA(...)` por el que pasan todos los cambios de estado.

## Por que

1. **Una sola verdad.** Cualquiera que pregunte "se puede cancelar esto?"
   obtiene la misma respuesta, venga del panel, de la API o de otro
   microproyecto.
2. **Se prueba sin levantar nada.** `MaquinaEstadosTest` ejercita 42 casos en
   centesimas de segundo porque no necesita Spring ni base de datos.
3. **Un estado nuevo se agrega en un solo lugar**, y hay una prueba que falla
   si alguien lo agrega al enum y olvida declarar sus transiciones.

## Consecuencias

- Los controladores no deciden nada sobre estados: solo traducen a HTTP.
- La restriccion `CHECK` de la base repite la lista de estados. Es duplicacion
  a proposito: ni la aplicacion ni un `INSERT` hecho a mano pueden meter un
  estado que el otro no conozca.
