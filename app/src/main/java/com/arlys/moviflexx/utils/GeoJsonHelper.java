package com.arlys.moviflexx.utils;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GeoJsonHelper
 * -------------
 * Convierte el GeoJSON que devuelve el backend FastAPI en Polylines
 * que se pueden pintar directamente sobre un MapView de OSMDroid.
 *
 * Uso típico:
 *   List<Polyline> lineas = GeoJsonHelper.rutasAPolylines(response.routes, map);
 *   GeoJsonHelper.pintarEnMapa(lineas, map);
 */
public class GeoJsonHelper {

    // ─────────────────────────────────────────────
    // 🎨 COLORES POR POSICIÓN DE RUTA
    // r1 = mejor ruta  → azul vibrante
    // r2 = alternativa → naranja
    // r3 = alternativa → gris
    // ─────────────────────────────────────────────
    private static final int[] COLORES = {
            Color.parseColor("#1A73E8"),  // r1 - azul Google Maps
            Color.parseColor("#FF6D00"),  // r2 - naranja
            Color.parseColor("#9E9E9E"),  // r3 - gris
            Color.parseColor("#00897B"),  // r4 - verde azulado
            Color.parseColor("#8E24AA"),  // r5 - morado
    };

    private static final float ANCHO_MEJOR   = 14f;
    private static final float ANCHO_ALTERNA = 10f;

    // ─────────────────────────────────────────────
    // 🔧 CONVERTIR UNA RUTA (Map<String,Object>) A POLYLINE
    // ─────────────────────────────────────────────

    /**
     * Convierte el geojson de un RouteOption en una Polyline de OSMDroid.
     *
     * @param geojson   El campo geojson del RouteOption (Map<String, Object>)
     * @param map       El MapView donde se va a dibujar
     * @param color     Color ARGB de la línea
     * @param ancho     Ancho de la línea en dp
     * @param routeId   Id de la ruta ("r1", "r2"...) para el título
     * @return          Polyline lista para agregar al mapa, o null si hay error
     */
    public static Polyline geojsonAPolyline(Map<String, Object> geojson,
                                            MapView map,
                                            int color,
                                            float ancho,
                                            String routeId) {
        try {
            // El geojson viene como Map, lo convertimos a JSONObject para leerlo fácil
            JSONObject geoJson = new JSONObject(geojson);
            JSONArray coordinates = geoJson.getJSONArray("coordinates");

            ArrayList<GeoPoint> puntos = new ArrayList<>();
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray coord = coordinates.getJSONArray(i);
                double lng = coord.getDouble(0);
                double lat = coord.getDouble(1);
                puntos.add(new GeoPoint(lat, lng));
            }

            if (puntos.isEmpty()) return null;

            Polyline polyline = new Polyline(map);
            polyline.setPoints(puntos);
            polyline.setColor(color);
            polyline.setWidth(ancho);
            polyline.getOutlinePaint().setStrokeWidth(ancho);
            polyline.setTitle(routeId.equals("r1") ? "✅ Mejor ruta" : "🔀 Ruta alternativa " + routeId);

            return polyline;

        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────
    // 🗺️ PINTAR LISTA DE RUTAS EN EL MAPA
    // ─────────────────────────────────────────────

    /**
     * Recibe una lista de RouteOption (ya como pares id+geojson) y pinta
     * todas las rutas en el mapa con colores diferenciados.
     * La primera ruta (índice 0) siempre es la mejor y se pinta más gruesa.
     *
     * @param rutas  Lista de objetos con id y geojson
     * @param map    El MapView destino
     * @return       Lista de Polylines pintadas (por si necesitas manipularlas luego)
     */
    public static List<Polyline> pintarRutas(List<RutaSimple> rutas, MapView map) {
        List<Polyline> resultado = new ArrayList<>();

        // Pintamos primero las alternativas (quedan "debajo" de la mejor)
        for (int i = rutas.size() - 1; i >= 0; i--) {
            RutaSimple ruta = rutas.get(i);
            int color = i < COLORES.length ? COLORES[i] : Color.GRAY;
            float ancho = (i == 0) ? ANCHO_MEJOR : ANCHO_ALTERNA;

            // Ajustar alpha: mejor ruta opaca, alternativas semitransparentes
            if (i > 0) {
                color = ajustarAlpha(color, 160); // ~63% opacidad
            }

            Polyline polyline = geojsonAPolyline(
                    ruta.geojson, map, color, ancho, ruta.id
            );

            if (polyline != null) {
                map.getOverlays().add(polyline);
                resultado.add(0, polyline); // insertar al frente para mantener orden
            }
        }

        map.invalidate();
        return resultado;
    }

    // ─────────────────────────────────────────────
    // 📐 ZOOM AUTOMÁTICO A TODAS LAS RUTAS
    // ─────────────────────────────────────────────

    /**
     * Hace zoom para encuadrar todas las rutas visibles en la pantalla.
     */
    public static void zoomARutas(List<Polyline> polylines, MapView map) {
        if (polylines == null || polylines.isEmpty()) return;

        double north = -90, south = 90, east = -180, west = 180;

        for (Polyline p : polylines) {
            if (p.getPoints().isEmpty()) continue;
            BoundingBox box = p.getBounds();
            if (box.getLatNorth() > north) north = box.getLatNorth();
            if (box.getLatSouth() < south) south = box.getLatSouth();
            if (box.getLonEast()  > east)  east  = box.getLonEast();
            if (box.getLonWest()  < west)  west  = box.getLonWest();
        }

        BoundingBox total = new BoundingBox(north, east, south, west);
        map.zoomToBoundingBox(total, true, 120);
    }

    // ─────────────────────────────────────────────
    // 🛠️ HELPERS INTERNOS
    // ─────────────────────────────────────────────

    private static int ajustarAlpha(int color, int alpha) {
        return Color.argb(alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    // ─────────────────────────────────────────────
    // 📦 DATA CLASS SIMPLE
    // ─────────────────────────────────────────────

    /**
     * Contenedor mínimo para pasar rutas a pintarRutas().
     * Crea una instancia por cada RouteOption que recibas del backend.
     */
    public static class RutaSimple {
        public String id;
        public Map<String, Object> geojson;
        public double distanceKm;
        public double durationMin;
        public double fuelCostCop;

        public RutaSimple(String id,
                          Map<String, Object> geojson,
                          double distanceKm,
                          double durationMin,
                          double fuelCostCop) {
            this.id = id;
            this.geojson = geojson;
            this.distanceKm = distanceKm;
            this.durationMin = durationMin;
            this.fuelCostCop = fuelCostCop;
        }
    }
}