package co.unisabaneta.integrax.logistix.catalogo.dominio;

/**
 * Vehiculos con los que opera la distribucion.
 *
 * <p>Los valores coinciden uno a uno con el {@code CHECK} de la columna
 * {@code transportador.tipo_vehiculo}: la lista se declara en dos lugares a
 * proposito, para que ni la aplicacion ni un {@code INSERT} hecho a mano
 * puedan meter un tipo que el otro no conozca.</p>
 */
public enum TipoVehiculo {
    MOTO,
    AUTOMOVIL,
    CAMIONETA,
    CAMION,
    FURGON,
    BICICLETA
}
