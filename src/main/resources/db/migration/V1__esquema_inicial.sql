-- =====================================================================
-- LOGISTI-X  |  Microproyecto INTEGRAX - Gestion de Despachos y Entregas
-- V1: esquema inicial
--
-- Motor: PostgreSQL 16
-- Esquema: logistix  (LOGISTI-X es dueno EXCLUSIVO de esta base de datos;
--                     ningun otro microproyecto se conecta aqui, la
--                     informacion se comparte por API REST -- ver ADR-002)
--
-- Convenciones:
--   * Identificadores en snake_case y en espanol (dominio del enunciado).
--   * Dinero SIEMPRE numeric(14,2). Nunca float/double (ver ADR-004).
--   * Peso en numeric(10,3): en logistica el gramo importa al cubicar.
--   * Fechas en timestamptz (UTC en la base, se presenta en America/Bogota).
--   * Los estados se guardan como varchar + CHECK, no como ENUM nativo:
--     agregar un estado nuevo no exige ALTER TYPE y mapea directo a Java.
--
-- Frontera del microproyecto: aqui viven transportadores, rutas, despachos,
-- novedades y entregas. NO viven clientes (CRM-X), productos (INVENTA-X) ni
-- pedidos (SALES-X): de esos solo se guarda el identificador comun y una
-- copia de los datos usados (snapshot).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS logistix;
SET search_path TO logistix;

-- pgcrypto: gen_random_uuid(). En PG 13+ tambien existe nativo,
-- se deja la extension para compatibilidad con instalaciones viejas.
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ---------------------------------------------------------------------
-- 1. USUARIO  (RNF-05: seguridad de acceso)
-- ---------------------------------------------------------------------
-- Los usuarios que OPERAN LOGISTI-X. No son los clientes: los clientes
-- viven en CRM-X y aqui solo se referencian.
CREATE TABLE usuario (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username        varchar(60)  NOT NULL,
    password_hash   varchar(100) NOT NULL,          -- BCrypt, nunca texto plano
    nombre          varchar(120) NOT NULL,
    rol             varchar(20)  NOT NULL,
    activo          boolean      NOT NULL DEFAULT true,
    creado_en       timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT ck_usuario_rol CHECK (rol IN
        ('ADMIN', 'COORDINADOR', 'TRANSPORTADOR', 'CONSULTA', 'SISTEMA'))
);

COMMENT ON TABLE  usuario     IS 'Operadores de LOGISTI-X. El rol SISTEMA se usa para los otros microproyectos INTEGRAX que consumen esta API.';
COMMENT ON COLUMN usuario.rol IS 'ADMIN: todo | COORDINADOR: crea, asigna y despacha | TRANSPORTADOR: solo actualiza entregas y novedades | CONSULTA: solo lectura | SISTEMA: integracion maquina-a-maquina';


-- ---------------------------------------------------------------------
-- 2. TRANSPORTADOR   RF-05: asignar transportadores
-- ---------------------------------------------------------------------
-- Entidad PROPIA de LOGISTI-X: el transportador es del dominio logistico,
-- no lo administra ningun otro microproyecto. Por eso aqui si hay llave
-- foranea real desde despacho.
CREATE TABLE transportador (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    documento          varchar(40)  NOT NULL,
    nombre             varchar(160) NOT NULL,
    telefono           varchar(40),
    empresa            varchar(160),
    tipo_vehiculo      varchar(30)  NOT NULL DEFAULT 'MOTO',
    placa              varchar(10),
    capacidad_kg       numeric(10,3) NOT NULL DEFAULT 100,
    zona               varchar(80),
    activo             boolean      NOT NULL DEFAULT true,
    creado_en          timestamptz  NOT NULL DEFAULT now(),
    actualizado_en     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_transportador_documento UNIQUE (documento),
    CONSTRAINT ck_transportador_vehiculo CHECK (tipo_vehiculo IN
        ('MOTO', 'AUTOMOVIL', 'CAMIONETA', 'CAMION', 'FURGON', 'BICICLETA')),
    CONSTRAINT ck_transportador_capacidad CHECK (capacidad_kg > 0)
);

COMMENT ON TABLE  transportador IS 'Quien mueve la carga. Entidad propia de LOGISTI-X (RF-05).';
COMMENT ON COLUMN transportador.capacidad_kg IS 'Tope de carga del vehiculo; el sistema no asigna un despacho que lo supere (RN-12).';

CREATE INDEX ix_transportador_activo ON transportador (activo);
CREATE INDEX ix_transportador_zona   ON transportador (zona);


-- ---------------------------------------------------------------------
-- 3. RUTA   (administrar rutas de entrega)
-- ---------------------------------------------------------------------
CREATE TABLE ruta (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo             varchar(20)  NOT NULL,
    nombre             varchar(120) NOT NULL,
    ciudad_origen      varchar(80)  NOT NULL,
    ciudad_destino     varchar(80)  NOT NULL,
    zona               varchar(80),
    dias_estimados     integer      NOT NULL DEFAULT 1,
    tarifa_base        numeric(14,2) NOT NULL DEFAULT 0,
    activa             boolean      NOT NULL DEFAULT true,
    creado_en          timestamptz  NOT NULL DEFAULT now(),
    actualizado_en     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_ruta_codigo UNIQUE (codigo),
    CONSTRAINT ck_ruta_dias   CHECK (dias_estimados BETWEEN 0 AND 60),
    CONSTRAINT ck_ruta_tarifa CHECK (tarifa_base >= 0)
);

COMMENT ON TABLE  ruta               IS 'Trayectos de distribucion. La ruta define los dias estimados de entrega y la tarifa base del flete.';
COMMENT ON COLUMN ruta.dias_estimados IS 'Se suman a la fecha de despacho para calcular la fecha estimada de entrega.';

CREATE INDEX ix_ruta_activa  ON ruta (activa);
CREATE INDEX ix_ruta_destino ON ruta (ciudad_destino);


-- ---------------------------------------------------------------------
-- 4. DESPACHO  (cabecera)   RF-01, RF-02, RF-03, RF-04, RF-07
-- ---------------------------------------------------------------------
-- pedido_id es REFERENCIA EXTERNA a SALES-X y cliente_id a CRM-X: NO llevan
-- llave foranea porque esas tablas no viven en esta base de datos (ADR-003).
-- Los campos cliente_* y pedido_numero son un SNAPSHOT tomado al registrar
-- el despacho: si manana CRM-X corrige el nombre, la guia ya impresa no
-- puede cambiar.
CREATE SEQUENCE seq_despacho_numero START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_guia_consecutivo START WITH 1 INCREMENT BY 1;

CREATE TABLE despacho (
    -- despachoId: identificador comun obligatorio de la plataforma INTEGRAX
    despacho_id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    numero               varchar(20)  NOT NULL,      -- legible: DES-2026-000001

    -- RF-07: numero de guia. Nulo hasta que se asigna transportador; una vez
    -- generado es INMUTABLE (va impreso en el rotulo de la caja).
    numero_guia          varchar(30),

    -- referencias externas (sin FK) + snapshot
    pedido_id            uuid         NOT NULL,      -- pedidoId de SALES-X
    pedido_numero        varchar(30),
    cliente_id           uuid         NOT NULL,      -- clienteId de CRM-X
    cliente_nombre       varchar(200) NOT NULL,
    cliente_documento    varchar(40),
    cliente_telefono     varchar(40),

    -- destino de la entrega
    direccion_entrega    varchar(300) NOT NULL,
    ciudad_entrega       varchar(80)  NOT NULL,
    departamento_entrega varchar(80),
    referencia_direccion varchar(200),

    estado               varchar(20)  NOT NULL DEFAULT 'REGISTRADO',

    -- asignacion (RF-05)
    transportador_id     uuid,
    ruta_id              uuid,
    asignado_en          timestamptz,

    -- carga: la recalcula SIEMPRE el servidor a partir de las lineas (RN-11)
    peso_total_kg        numeric(10,3) NOT NULL DEFAULT 0,
    unidades_totales     integer       NOT NULL DEFAULT 0,
    valor_declarado      numeric(14,2) NOT NULL DEFAULT 0,
    costo_flete          numeric(14,2) NOT NULL DEFAULT 0,

    -- fechas del ciclo de entrega
    fecha_estimada_entrega date,
    despachado_en        timestamptz,
    entregado_en         timestamptz,
    cancelado_en         timestamptz,

    -- evidencia de la entrega (RF-09)
    receptor_nombre      varchar(160),
    receptor_documento   varchar(40),
    observacion_entrega  varchar(500),

    intentos_entrega     integer      NOT NULL DEFAULT 0,
    observaciones        varchar(500),
    motivo_cancelacion   varchar(300),

    -- trazabilidad
    creado_por           varchar(60)  NOT NULL,
    creado_en            timestamptz  NOT NULL DEFAULT now(),
    actualizado_en       timestamptz  NOT NULL DEFAULT now(),

    -- bloqueo optimista: coordinador y transportador no pueden pisarse
    version              bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uk_despacho_numero UNIQUE (numero),
    CONSTRAINT uk_despacho_guia   UNIQUE (numero_guia),
    CONSTRAINT fk_despacho_transportador FOREIGN KEY (transportador_id)
        REFERENCES transportador (id),
    CONSTRAINT fk_despacho_ruta FOREIGN KEY (ruta_id)
        REFERENCES ruta (id),
    CONSTRAINT ck_despacho_estado CHECK (estado IN (
        'REGISTRADO', 'EN_PREPARACION', 'ASIGNADO', 'EN_RUTA',
        'ENTREGADO', 'FALLIDO', 'DEVUELTO', 'CANCELADO')),
    CONSTRAINT ck_despacho_carga CHECK (
        peso_total_kg >= 0 AND unidades_totales >= 0
        AND valor_declarado >= 0 AND costo_flete >= 0),
    CONSTRAINT ck_despacho_intentos CHECK (intentos_entrega >= 0),
    -- RN-04: no se puede estar en ruta o entregado sin guia y sin transportador
    CONSTRAINT ck_despacho_guia_obligatoria CHECK (
        estado NOT IN ('EN_RUTA', 'ENTREGADO', 'FALLIDO', 'DEVUELTO')
        OR (numero_guia IS NOT NULL AND transportador_id IS NOT NULL)),
    -- RN-05: no hay entrega sin evidencia de quien recibio
    CONSTRAINT ck_despacho_evidencia CHECK (
        estado <> 'ENTREGADO'
        OR (receptor_nombre IS NOT NULL AND entregado_en IS NOT NULL)),
    CONSTRAINT ck_despacho_cancelado CHECK (
        (estado = 'CANCELADO' AND cancelado_en IS NOT NULL)
        OR (estado <> 'CANCELADO' AND cancelado_en IS NULL))
);

COMMENT ON TABLE  despacho              IS 'Cabecera del despacho. Agregado raiz del dominio: nada se toca sin pasar por aqui.';
COMMENT ON COLUMN despacho.pedido_id    IS 'pedidoId de SALES-X. Referencia externa, SIN llave foranea (ADR-003).';
COMMENT ON COLUMN despacho.cliente_id   IS 'clienteId de CRM-X. Referencia externa, SIN llave foranea (ADR-003).';
COMMENT ON COLUMN despacho.numero_guia  IS 'RF-07. Formato LGX-AAAA-NNNNNN-D, con digito de verificacion. Inmutable una vez generado.';
COMMENT ON COLUMN despacho.costo_flete  IS 'Recalculado por el servidor: tarifa de la ruta + costo por kilogramo (RN-11).';
COMMENT ON COLUMN despacho.version      IS 'Bloqueo optimista de JPA (@Version).';

-- RN-08: un pedido no puede tener DOS despachos vivos al mismo tiempo.
-- Indice unico PARCIAL: solo aplica a los estados no finales, de modo que un
-- despacho devuelto o cancelado si permite volver a despachar el pedido.
CREATE UNIQUE INDEX uk_despacho_pedido_activo
    ON despacho (pedido_id)
    WHERE estado NOT IN ('ENTREGADO', 'DEVUELTO', 'CANCELADO');

CREATE INDEX ix_despacho_estado         ON despacho (estado);
CREATE INDEX ix_despacho_pedido         ON despacho (pedido_id);
CREATE INDEX ix_despacho_cliente        ON despacho (cliente_id);
CREATE INDEX ix_despacho_transportador  ON despacho (transportador_id);
CREATE INDEX ix_despacho_creado_en      ON despacho (creado_en DESC);
-- consulta tipica del tablero y del reporte (RF-11): estado + fecha
CREATE INDEX ix_despacho_estado_fecha   ON despacho (estado, creado_en DESC);
CREATE INDEX ix_despacho_ciudad         ON despacho (ciudad_entrega);


-- ---------------------------------------------------------------------
-- 5. DESPACHO_ITEM  (contenido de la carga)
-- ---------------------------------------------------------------------
-- producto_id es referencia externa a INVENTA-X (sin FK). El peso y el valor
-- se CONGELAN al armar el despacho: la guia impresa declara ese peso y ese
-- valor asegurado, y no puede cambiar porque manana editen el catalogo.
CREATE TABLE despacho_item (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    despacho_id         uuid          NOT NULL,
    linea               integer       NOT NULL,

    producto_id         uuid          NOT NULL,     -- productoId de INVENTA-X
    producto_sku        varchar(40)   NOT NULL,
    producto_nombre     varchar(200)  NOT NULL,

    cantidad            integer       NOT NULL,
    peso_unitario_kg    numeric(10,3) NOT NULL DEFAULT 0,
    valor_unitario      numeric(14,2) NOT NULL DEFAULT 0,

    -- derivados, calculados por el servidor
    peso_linea_kg       numeric(10,3) NOT NULL DEFAULT 0,
    valor_linea         numeric(14,2) NOT NULL DEFAULT 0,

    CONSTRAINT fk_item_despacho FOREIGN KEY (despacho_id)
        REFERENCES despacho (despacho_id) ON DELETE CASCADE,
    -- un producto no puede aparecer dos veces en el mismo despacho:
    -- se suma la cantidad a la linea existente
    CONSTRAINT uk_item_despacho_producto UNIQUE (despacho_id, producto_id),
    CONSTRAINT uk_item_despacho_linea    UNIQUE (despacho_id, linea),
    CONSTRAINT ck_item_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_item_peso     CHECK (peso_unitario_kg >= 0),
    CONSTRAINT ck_item_valor    CHECK (valor_unitario >= 0)
);

COMMENT ON TABLE  despacho_item             IS 'Que va dentro de la caja. Solo se modifica mientras el despacho no haya salido.';
COMMENT ON COLUMN despacho_item.producto_id IS 'productoId de INVENTA-X. Referencia externa, SIN llave foranea (ADR-003).';

CREATE INDEX ix_item_despacho ON despacho_item (despacho_id);
CREATE INDEX ix_item_producto ON despacho_item (producto_id);


-- ---------------------------------------------------------------------
-- 6. DESPACHO_ESTADO_HISTORIAL   RF-04, RF-06, RF-10, RNF-06
-- ---------------------------------------------------------------------
-- Es la columna vertebral de la TRAZABILIDAD (RF-10): cada movimiento deja
-- fila, con el lugar donde ocurrio. Nunca se actualiza ni se borra.
CREATE TABLE despacho_estado_historial (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    despacho_id     uuid         NOT NULL,
    estado_anterior varchar(20),                    -- NULL en la creacion
    estado_nuevo    varchar(20)  NOT NULL,
    motivo          varchar(300),
    ubicacion       varchar(160),                   -- donde ocurrio el movimiento
    usuario         varchar(60)  NOT NULL,
    origen          varchar(30)  NOT NULL DEFAULT 'LOGISTI-X',
    ocurrido_en     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_historial_despacho FOREIGN KEY (despacho_id)
        REFERENCES despacho (despacho_id) ON DELETE CASCADE
);

COMMENT ON TABLE  despacho_estado_historial        IS 'Linea de tiempo del despacho (RF-10). Solo se inserta, jamas se actualiza.';
COMMENT ON COLUMN despacho_estado_historial.origen IS 'LOGISTI-X, SALES-X, SERVI-X... deja ver que microproyecto movio el despacho.';

CREATE INDEX ix_historial_despacho ON despacho_estado_historial (despacho_id, ocurrido_en);


-- ---------------------------------------------------------------------
-- 7. NOVEDAD   RF-08: registrar novedades de transporte
-- ---------------------------------------------------------------------
-- Lo que pasa en la calle: direccion errada, cliente ausente, via cerrada,
-- averia. Una novedad BLOQUEANTE mueve el despacho a FALLIDO automaticamente.
CREATE TABLE novedad (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    despacho_id     uuid         NOT NULL,
    tipo            varchar(30)  NOT NULL,
    descripcion     varchar(500) NOT NULL,
    ubicacion       varchar(160),
    bloqueante      boolean      NOT NULL DEFAULT false,
    reportada_por   varchar(60)  NOT NULL,
    ocurrida_en     timestamptz  NOT NULL DEFAULT now(),
    resuelta        boolean      NOT NULL DEFAULT false,
    resolucion      varchar(500),
    resuelta_en     timestamptz,

    CONSTRAINT fk_novedad_despacho FOREIGN KEY (despacho_id)
        REFERENCES despacho (despacho_id) ON DELETE CASCADE,
    CONSTRAINT ck_novedad_tipo CHECK (tipo IN (
        'DIRECCION_ERRADA', 'CLIENTE_AUSENTE', 'CLIENTE_RECHAZA',
        'DEMORA_EN_VIA', 'AVERIA_VEHICULO', 'CARGA_AVERIADA',
        'ZONA_DE_RIESGO', 'DATOS_INCOMPLETOS', 'OTRA')),
    CONSTRAINT ck_novedad_resolucion CHECK (
        (resuelta = false) OR (resuelta = true AND resuelta_en IS NOT NULL))
);

COMMENT ON TABLE  novedad            IS 'Novedades de transporte (RF-08). Una novedad bloqueante marca el despacho como FALLIDO.';
COMMENT ON COLUMN novedad.bloqueante IS 'true = impide la entrega ahora mismo (cliente ausente, direccion errada).';

CREATE INDEX ix_novedad_despacho ON novedad (despacho_id, ocurrida_en DESC);
CREATE INDEX ix_novedad_abiertas ON novedad (resuelta, ocurrida_en DESC) WHERE resuelta = false;


-- ---------------------------------------------------------------------
-- 8. AUDITORIA   RNF-06: registro historico de movimientos
-- ---------------------------------------------------------------------
-- Tabla generica: guarda el antes y el despues en JSONB. Se escribe desde
-- la aplicacion (no por trigger) porque el trigger no conoce el usuario
-- autenticado ni la IP de origen.
CREATE TABLE auditoria (
    id            bigserial    PRIMARY KEY,
    entidad       varchar(40)  NOT NULL,            -- DESPACHO, TRANSPORTADOR, RUTA
    entidad_id    varchar(60)  NOT NULL,
    accion        varchar(30)  NOT NULL,            -- CREAR, ACTUALIZAR, ASIGNAR, ENTREGAR...
    usuario       varchar(60)  NOT NULL,
    ip_origen     varchar(45),                      -- cabe IPv6
    datos_antes   jsonb,
    datos_despues jsonb,
    ocurrido_en   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_id, ocurrido_en DESC);
CREATE INDEX ix_auditoria_fecha   ON auditoria (ocurrido_en DESC);


-- ---------------------------------------------------------------------
-- 9. OUTBOX_EVENTO   (patron Transactional Outbox)
-- ---------------------------------------------------------------------
-- Cuando un despacho sale a ruta o se entrega hay que avisarle a SALES-X
-- (para que mueva el pedido a DESPACHADO / ENTREGADO) y a SERVI-X. Si se
-- llamara su API dentro de la transaccion, una caida de ESE sistema tumbaria
-- la operacion logistica, que si ocurrio en el mundo real: el camion ya salio.
-- Solucion: en la MISMA transaccion se inserta el evento aqui, y un publicador
-- aparte lo entrega despues con reintentos (ver ADR-007 y ADR-008).
CREATE TABLE outbox_evento (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo           varchar(60)  NOT NULL,           -- despacho.en-ruta, despacho.entregado
    agregado_tipo  varchar(40)  NOT NULL DEFAULT 'DESPACHO',
    agregado_id    uuid         NOT NULL,
    payload        jsonb        NOT NULL,
    estado         varchar(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos       integer      NOT NULL DEFAULT 0,
    ultimo_error   varchar(500),
    creado_en      timestamptz  NOT NULL DEFAULT now(),
    publicado_en   timestamptz,

    CONSTRAINT ck_outbox_estado CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO'))
);

CREATE INDEX ix_outbox_pendientes ON outbox_evento (estado, creado_en) WHERE estado = 'PENDIENTE';


-- ---------------------------------------------------------------------
-- 10. IDEMPOTENCIA
-- ---------------------------------------------------------------------
-- Si el coordinador pierde la senal despues de enviar POST /despachos y
-- reintenta, NO puede quedar el despacho duplicado. El cliente manda la
-- cabecera Idempotency-Key y aqui se recuerda la respuesta original.
CREATE TABLE idempotencia (
    clave          varchar(120) PRIMARY KEY,
    metodo         varchar(10)  NOT NULL,
    ruta           varchar(200) NOT NULL,
    huella_cuerpo  varchar(64)  NOT NULL,           -- SHA-256 del body
    codigo_http    integer      NOT NULL,
    respuesta      jsonb        NOT NULL,
    creado_en      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_idempotencia_creado ON idempotencia (creado_en);


-- ---------------------------------------------------------------------
-- 11. Trigger de actualizado_en
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_actualizar_timestamp()
RETURNS trigger AS $$
BEGIN
    NEW.actualizado_en = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_despacho_actualizado
    BEFORE UPDATE ON despacho
    FOR EACH ROW EXECUTE FUNCTION fn_actualizar_timestamp();

CREATE TRIGGER tg_transportador_actualizado
    BEFORE UPDATE ON transportador
    FOR EACH ROW EXECUTE FUNCTION fn_actualizar_timestamp();

CREATE TRIGGER tg_ruta_actualizada
    BEFORE UPDATE ON ruta
    FOR EACH ROW EXECUTE FUNCTION fn_actualizar_timestamp();


-- ---------------------------------------------------------------------
-- 12. Vista de apoyo para el reporte logistico (RF-11)
-- ---------------------------------------------------------------------
CREATE VIEW v_despachos_diarios AS
SELECT
    date_trunc('day', d.creado_en)      AS dia,
    d.estado,
    d.ciudad_entrega,
    count(*)                            AS cantidad,
    coalesce(sum(d.peso_total_kg), 0)   AS peso_kg,
    coalesce(sum(d.costo_flete), 0)     AS flete
FROM despacho d
GROUP BY 1, 2, 3;

COMMENT ON VIEW v_despachos_diarios IS 'Apoyo al reporte logistico. La API expone el reporte ya filtrado por rango de fechas.';

-- Cumplimiento de la promesa de entrega: entregados a tiempo vs. tarde.
CREATE VIEW v_cumplimiento_entregas AS
SELECT
    d.despacho_id,
    d.numero_guia,
    d.transportador_id,
    d.ciudad_entrega,
    d.fecha_estimada_entrega,
    d.entregado_en,
    (d.entregado_en::date <= d.fecha_estimada_entrega) AS a_tiempo,
    extract(epoch FROM (d.entregado_en - d.despachado_en)) / 3600.0 AS horas_en_ruta
FROM despacho d
WHERE d.estado = 'ENTREGADO'
  AND d.entregado_en IS NOT NULL
  AND d.despachado_en IS NOT NULL;

COMMENT ON VIEW v_cumplimiento_entregas IS 'Base del indicador de cumplimiento y del tiempo promedio en ruta (RF-11).';
