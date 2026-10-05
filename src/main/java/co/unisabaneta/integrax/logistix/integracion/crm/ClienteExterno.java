package co.unisabaneta.integrax.logistix.integracion.crm;

import java.util.UUID;

/**
 * Cliente tal como lo entrega CRM-X.
 *
 * <p>LOGISTI-X lo consulta por una razon concreta: la <b>direccion de
 * entrega</b> y el telefono de contacto. El pedido dice que hay que llevar;
 * CRM-X dice a donde y a quien llamar si nadie abre.</p>
 */
public record ClienteExterno(
        UUID clienteId,
        String tipo,
        String nombre,
        String documento,
        String telefono,
        String direccion,
        String ciudad,
        String departamento,
        boolean activo) {
}
