# ADR-004 · BigDecimal para dinero y para peso

**Estado:** aceptada · 28 de agosto de 2026

## Contexto

LOGISTI-X maneja dos magnitudes que no admiten error: el **peso** (decide el
flete y si la carga cabe en el vehiculo) y el **dinero** (flete y valor
declarado del seguro).

## Decision

- Dinero: `BigDecimal` con escala **2** y redondeo `HALF_UP`, en `Dinero`.
- Peso: `BigDecimal` con escala **3** (el gramo) y `HALF_UP`, en `Peso`.
- En la base: `numeric(14,2)` y `numeric(10,3)`.
- El redondeo se aplica **por linea**, no al final.

## Por que

Con `double`, `0.1 + 0.2` no da `0.3`. En peso es peor que en dinero: 0,250 kg
de guantes por 800 unidades deberian ser 200 kg exactos, y con coma flotante
salen 199,99999999999997. Ese error se propaga al flete y a la comprobacion de
capacidad del vehiculo.

Tres decimales en el peso y no dos porque la unidad comercial de la paqueteria
es el gramo: redondear a dos por linea desviaria la liquidacion del flete.

## Consecuencias

- Nunca se usa `==` para comparar: siempre `compareTo`.
- Las pruebas comparan con `isEqualByComparingTo`, no con `isEqualTo`, porque
  `10.000` y `10.00` son el mismo numero con distinta escala.
