-- =====================================================================
-- LOGISTI-X  |  V2: maestro logistico inicial
--
-- Transportadores y rutas con los que arranca la operacion. Van en una
-- migracion y no en codigo de arranque porque son DATOS DEL NEGOCIO, no
-- datos de prueba: sin al menos una ruta y un transportador el sistema no
-- puede asignar nada, y quien despliegue no tiene por que cargarlos a mano.
--
-- Los identificadores son fijos para que las pruebas automatizadas y la
-- demostracion sean repetibles.
--
-- Lo que NO va aqui: usuarios con contrasena. Un hash BCrypt de una clave
-- conocida escrito en el repositorio es tan bueno como la clave en texto
-- plano. Los usuarios los crea SembradorUsuarios solo si estan habilitados
-- por configuracion.
-- =====================================================================

SET search_path TO logistix;

-- ---------------------------------------------------------------------
-- Transportadores (RF-05)
-- ---------------------------------------------------------------------
-- Las capacidades son reales y estan escogidas para que la regla RN-12
-- (la carga tiene que caber en el vehiculo) se pueda ver funcionando:
-- 3.000 kg de cemento no caben en la moto ni en la camioneta.
INSERT INTO transportador (id, documento, nombre, telefono, empresa,
                           tipo_vehiculo, placa, capacidad_kg, zona, activo)
VALUES
    ('bbbbbbbb-0001-4000-8000-000000000001', '71234567',
     'Jhon Alexander Ruiz', '3115552211', 'Transportes Aburra S.A.S.',
     'CAMION', 'TKM123', 8000.000, 'Valle de Aburra', true),

    ('bbbbbbbb-0002-4000-8000-000000000002', '43876512',
     'Diana Marcela Ospina', '3125553344', 'Transportes Aburra S.A.S.',
     'CAMIONETA', 'WXY456', 1200.000, 'Valle de Aburra', true),

    ('bbbbbbbb-0003-4000-8000-000000000003', '1017556677',
     'Kevin Andres Zapata', '3135556677', 'Mensajeria Rapida Medellin',
     'MOTO', 'ABC12D', 25.000, 'Medellin centro', true),

    ('bbbbbbbb-0004-4000-8000-000000000004', '98765432',
     'Carlos Mario Restrepo', '3145558899', 'Rutas del Occidente Ltda.',
     'FURGON', 'FGH789', 3500.000, 'Occidente antioqueno', true),

    -- Inactivo a proposito: sirve para probar que no se le puede asignar carga.
    ('bbbbbbbb-0005-4000-8000-000000000005', '80112233',
     'Transportador Inactivo de Prueba', NULL, NULL,
     'MOTO', 'ZZZ99Z', 20.000, 'Sin zona', false);


-- ---------------------------------------------------------------------
-- Rutas de entrega
-- ---------------------------------------------------------------------
-- dias_estimados es lo que se le promete al cliente y contra lo que
-- despues se mide el cumplimiento en el reporte (RF-11).
INSERT INTO ruta (id, codigo, nombre, ciudad_origen, ciudad_destino,
                  zona, dias_estimados, tarifa_base, activa)
VALUES
    ('cccccccc-0001-4000-8000-000000000001', 'MED-MED',
     'Urbana Medellin', 'Medellin', 'Medellin',
     'Valle de Aburra', 1, 8000.00, true),

    ('cccccccc-0002-4000-8000-000000000002', 'MED-BEL',
     'Medellin - Bello', 'Medellin', 'Bello',
     'Valle de Aburra', 1, 10000.00, true),

    ('cccccccc-0003-4000-8000-000000000003', 'MED-ITA',
     'Medellin - Itagui', 'Medellin', 'Itagui',
     'Valle de Aburra', 1, 10000.00, true),

    ('cccccccc-0004-4000-8000-000000000004', 'MED-RIO',
     'Medellin - Rionegro', 'Medellin', 'Rionegro',
     'Oriente antioqueno', 2, 22000.00, true),

    ('cccccccc-0005-4000-8000-000000000005', 'MED-APA',
     'Medellin - Apartado', 'Medellin', 'Apartado',
     'Uraba', 3, 65000.00, true),

    ('cccccccc-0006-4000-8000-000000000006', 'MED-BOG',
     'Medellin - Bogota', 'Medellin', 'Bogota',
     'Nacional', 3, 48000.00, true);
