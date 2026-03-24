package com.arlys.moviflexx.model.Manager;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pruebas unitarias para PrecioTramoPasajeroManager.
 * Se centran en la lógica matemática y de formateo que no depende del contexto de Android.
 */
public class PrecioTramoPasajeroManagerTest {

    private static final double DELTA = 0.01;

    @Test
    public void testHaversineKm_DistanciaConocida() {
        // Coordenadas aproximadas
        // Popayán, Cauca: 2.4419, -76.6063
        // Timbío, Cauca: 2.3456, -76.6806
        double latPopayan = 2.4419;
        double lngPopayan = -76.6063;
        double latTimbio = 2.3456;
        double lngTimbio = -76.6806;

        double distancia = PrecioTramoPasajeroManager.haversineKm(latPopayan, lngPopayan, latTimbio, lngTimbio);

        // La distancia en línea recta debería ser alrededor de 13 - 14 km
        assertTrue("La distancia en línea recta debería ser mayor a 0", distancia > 0);
        assertTrue("La distancia Popayán - Timbío en línea recta es ~13.5km", distancia > 10 && distancia < 20);
    }

    @Test
    public void testHaversineKm_MismoPunto() {
        // Distancia a sí mismo debe ser 0
        double lat = 2.4419;
        double lng = -76.6063;
        
        double distancia = PrecioTramoPasajeroManager.haversineKm(lat, lng, lat, lng);
        assertEquals("La distancia al mismo punto debe ser 0", 0.0, distancia, DELTA);
    }

    @Test
    public void testRedondear_PrecioMinimo() {
        // Cualquier precio debajo de 500 debe redondearse al mínimo de 500
        assertEquals(500.0, PrecioTramoPasajeroManager.redondear(100.0), DELTA);
        assertEquals(500.0, PrecioTramoPasajeroManager.redondear(499.0), DELTA);
        assertEquals(500.0, PrecioTramoPasajeroManager.redondear(-50.0), DELTA); // Edge case
    }

    @Test
    public void testRedondear_HaciaArriba() {
        // Redondeo se hace en bloques de 100, hacia el techo (ceil)
        assertEquals(600.0, PrecioTramoPasajeroManager.redondear(501.0), DELTA);
        assertEquals(1300.0, PrecioTramoPasajeroManager.redondear(1250.0), DELTA);
        assertEquals(2000.0, PrecioTramoPasajeroManager.redondear(2000.0), DELTA);
        assertEquals(2100.0, PrecioTramoPasajeroManager.redondear(2001.0), DELTA);
    }

    @Test
    public void testFormatear() {
        // Formato COP esperado: $ x.xxx COP
        String formateado1 = PrecioTramoPasajeroManager.formatear(1500.0);
        assertTrue(formateado1.contains("1.500") || formateado1.contains("1,500"));
        assertTrue(formateado1.contains("$"));
        assertTrue(formateado1.contains("COP"));

        String formateado2 = PrecioTramoPasajeroManager.formatear(15000.0);
        assertTrue(formateado2.contains("15.000") || formateado2.contains("15,000"));
    }
}
