package co.unisabaneta.integrax.logistix.integracion.crm;

import java.util.UUID;

/**
 * Puerto de salida hacia CRM-X (microproyecto 2.1 - Gestion de Clientes).
 *
 * <p>De CRM-X, LOGISTI-X solo necesita los datos de entrega. No los
 * administra ni los guarda como maestro: toma una copia en el momento de
 * crear el despacho (snapshot) y con esa copia se imprime la guia.</p>
 */
public interface PuertoClientes {

    /**
     * @throws co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException
     *         si CRM-X no responde o el cliente no existe alla
     */
    ClienteExterno obtener(UUID clienteId);

    String descripcion();
}
