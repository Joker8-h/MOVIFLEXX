package com.arlys.moviflexx.model.Manager;

import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONException;

import java.util.Map;

/**
 * Pruebas unitarias para RouteManager.
 * Se centra en verificar la lógica de parseo de JSON, asegurando de que
 * variables cruciales como el geojson y el fuel_cost_cop se mapeen correctamente a la clase POJO.
 */
public class RouteManagerTest {

    private static final double DELTA = 0.001;

    @Test
    public void testParseResponse_RespuestaValida() throws Exception {
        RouteManager manager = new RouteManager();
        
        // Simular un JSON de respuesta del backend de FastAPI
        String jsonReal = "{"
            + "\"preference\": \"FASTEST\","
            + "\"requested\": 3,"
            + "\"returned\": 1,"
            + "\"routes\": ["
            + "  {"
            + "    \"id\": \"ruta_1\","
            + "    \"distance_km\": 15.5,"
            + "    \"duration_min\": 25.3,"
            + "    \"fuel_liters\": 1.2,"
            + "    \"fuel_cost_cop\": 3600.0,"
            + "    \"score\": 9.5,"
            + "    \"geojson\": {"
            + "      \"type\": \"LineString\","
            + "      \"coordinates\": ["
            + "        [-76.6063, 2.4419],"
            + "        [-76.6806, 2.3456]"
            + "      ]"
            + "    }"
            + "  }"
            + "]}";

        RouteOptionsResponse response = manager.parseResponse(jsonReal);

        // Verificar mapeos a nivel raíz
        assertNotNull(response);
        assertEquals("FASTEST", response.preference);
        assertEquals(3, response.requested);
        assertEquals(1, response.returned);
        assertNotNull(response.routes);
        assertEquals(1, response.routes.size());

        // Verificar mapeo de RouteOption
        RouteOption option = response.routes.get(0);
        assertEquals("ruta_1", option.id);
        assertEquals(15.5, option.distanceKm, DELTA);
        assertEquals(25.3, option.durationMin, DELTA);
        assertEquals(1.2, option.fuelLiters, DELTA);
        assertEquals(3600.0, option.fuelCostCop, DELTA);
        assertEquals(9.5, option.score, DELTA);

        // Verificar parseo recursivo de geojson
        assertNotNull(option.geojson);
        assertTrue(option.geojson.containsKey("type"));
        assertEquals("LineString", option.geojson.get("type"));
        
        // Verificamos que 'coordinates' existe en el mapa parseado
        assertTrue(option.geojson.containsKey("coordinates"));
        Object coordsObj = option.geojson.get("coordinates");
        assertTrue("Coordinates debe ser de tipo List o Array", coordsObj instanceof java.util.List);
    }

    @Test
    public void testParseResponse_ArrayVacio() throws Exception {
        RouteManager manager = new RouteManager();
        
        String jsonVacio = "{"
            + "\"preference\": \"SHORTEST\","
            + "\"requested\": 3,"
            + "\"returned\": 0,"
            + "\"routes\": []"
            + "}";

        RouteOptionsResponse response = manager.parseResponse(jsonVacio);

        assertNotNull(response);
        assertEquals("SHORTEST", response.preference);
        assertEquals(0, response.returned);
        assertNotNull(response.routes);
        assertTrue(response.routes.isEmpty());
    }

    @Test(expected = JSONException.class)
    public void testParseResponse_JsonInvalido() throws Exception {
        RouteManager manager = new RouteManager();
        manager.parseResponse("ESTO_NO_ES_JSON");
    }
}
