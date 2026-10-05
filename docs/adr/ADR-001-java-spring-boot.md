# ADR-001 · Java 21 con Spring Boot 3.5

**Estado:** aceptada · 21 de agosto de 2026

## Contexto

El docente exige Java. Dentro de Java hay que elegir con que construir una API
REST con persistencia, seguridad y documentacion en cinco semanas.

## Decision

Java 21 (LTS) + Spring Boot 3.5, con PostgreSQL 16, Flyway, JPA/Hibernate,
Spring Security con JWT y springdoc-openapi.

## Por que

- Es el estandar de facto para servicios REST en Java: lo que se aprende aqui
  se usa igual en una empresa.
- Trae resueltas cosas que en cinco semanas no daria tiempo de escribir:
  validacion, manejo de errores, seguridad, documentacion automatica, sondas de
  salud.
- Java 21 aporta *records* (los DTO quedan en una linea), *pattern matching* en
  `switch` e hilos virtuales.
- springdoc genera el OpenAPI **del codigo**: la documentacion no se puede
  desactualizar.

## Consecuencias

- El arranque tarda unos 20 segundos: es lo que cuesta el marco.
- Hay que conocer las anotaciones; a cambio, el codigo propio es poco y
  concreto.
