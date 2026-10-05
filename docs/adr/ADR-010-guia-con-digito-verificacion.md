# ADR-010 · Numero de guia con digito de verificacion

**Estado:** aceptada · 4 de septiembre de 2026

## Contexto

El RF-07 pide "generar numero de guia". Lo minimo seria un consecutivo o un
UUID. La pregunta era para que se usa realmente una guia.

## Decision

Formato `LGX-AAAA-NNNNNN-D`, donde el consecutivo lo entrega una **secuencia de
PostgreSQL** y `D` es un digito de verificacion modulo 11 con pesos ciclicos
(el mismo esquema del NIT en Colombia).

## Por que

- **Un UUID no sirve** para lo que sirve una guia: nadie dicta
  `8f3c1a2e-...` por telefono.
- **Un consecutivo pelado se teclea mal.** Transponer dos cifras es el error
  tipico, y sin digito de control el sistema mostraria alegremente el envio de
  otro cliente. Con modulo 11 y pesos por posicion, esa transposicion se
  detecta.
- **La secuencia la da la base**, no un `max(numero)+1`: dos coordinadores
  asignando a la vez generarian la misma guia con la segunda opcion.
- El ano en el numero permite reiniciar el consecutivo cada ano sin colisiones
  historicas.

## Consecuencias

- La guia es inmutable una vez generada (RN-03): esta impresa en el rotulo.
- `GuiaServicio.esValida()` permite rechazar una guia mal escrita antes de ir
  a la base de datos.
- Hay 20 pruebas sobre esto, incluidas las de digito cambiado y digitos
  intercambiados.
