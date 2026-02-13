package com.arlys.moviflexx.controller;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MapaPasajero extends AppCompatActivity {

    private static final String TAG = "MapaPasajero";

    private MapView map;
    private int viajeId;
    private int rutaId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa_pasajero);

        viajeId = getIntent().getIntExtra("ID_VIAJE", 0);

        if (viajeId == 0) {
            Toast.makeText(this, "Viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        map = findViewById(R.id.map_pasajero);
        configurarMapa();
        cargarRutaViaje();
    }

    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(13.5);
    }

    private void cargarRutaViaje() {
        String endpoint = Constantes.viajePorId((long) viajeId);
        Log.d(TAG, "Cargando viaje: " + endpoint);

        ConexionApi.getInstance(this).getObject(
                endpoint,
                response -> {
                    try {
                        Log.d(TAG, "Respuesta completa: " + response.toString());

                        JSONObject ruta = response.optJSONObject("ruta");
                        if (ruta == null) {
                            Toast.makeText(this, "No se encontró la ruta", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        rutaId = ruta.optInt("idRuta", 0);
                        String origen = ruta.optString("origen", "");
                        String destino = ruta.optString("destino", "");

                        Log.d(TAG, "Origen: " + origen + ", Destino: " + destino);

                        cargarParadas(origen, destino);

                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando viaje", e);
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e(TAG, "Error cargando viaje", error);
                    Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void cargarParadas(String origen, String destino) {
        String endpoint = Constantes.paradasPorRuta((long) rutaId);
        Log.d(TAG, "Cargando paradas: " + endpoint);

        ConexionApi.getInstance(this).getObject(
                endpoint,
                response -> {
                    try {
                        JSONArray paradas = response.optJSONArray("paradas");
                        if (paradas == null) paradas = new JSONArray();

                        Log.d(TAG, "Paradas encontradas: " + paradas.length());
                        dibujarRuta(origen, destino, paradas);

                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando paradas", e);
                    }
                },
                error -> {
                    Log.e(TAG, "Error cargando paradas", error);
                    // Si no hay paradas, dibujar solo origen-destino
                    dibujarRutaSimple(origen, destino);
                }
        );
    }

    private void dibujarRutaSimple(String origen, String destino) {
        new Thread(() -> {
            try {
                GeoPoint pOrigen = geocodificar(origen);
                GeoPoint pDestino = geocodificar(destino);

                String coords = pOrigen.getLongitude() + "," + pOrigen.getLatitude() + ";" +
                        pDestino.getLongitude() + "," + pDestino.getLatitude();

                String url = "https://router.project-osrm.org/route/v1/driving/" + coords +
                        "?overview=full&geometries=geojson";

                JSONObject res = new JSONObject(http(url));
                JSONArray coordsArray = res.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coordsArray.length(); i++) {
                    JSONArray c = coordsArray.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                runOnUiThread(() -> mostrarEnMapa(puntos, pOrigen, pDestino, null, null));

            } catch (Exception e) {
                Log.e(TAG, "Error dibujando ruta simple", e);
            }
        }).start();
    }

    private void dibujarRuta(String origen, String destino, JSONArray paradas) {
        new Thread(() -> {
            try {
                GeoPoint pOrigen = geocodificar(origen);
                GeoPoint pDestino = geocodificar(destino);

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(pOrigen);

                ArrayList<GeoPoint> puntosParada = new ArrayList<>();
                ArrayList<String> nombresParada = new ArrayList<>();

                if (paradas.length() > 0) {
                    for (int i = 0; i < paradas.length(); i++) {
                        JSONObject p = paradas.getJSONObject(i);
                        GeoPoint punto = new GeoPoint(
                                p.getDouble("latitud"),
                                p.getDouble("longitud")
                        );
                        waypoints.add(punto);
                        puntosParada.add(punto);
                        nombresParada.add(p.optString("nombre", "Parada " + (i + 1)));
                    }
                }

                waypoints.add(pDestino);

                // Construir URL OSRM
                StringBuilder coordsStr = new StringBuilder();
                for (int i = 0; i < waypoints.size(); i++) {
                    GeoPoint p = waypoints.get(i);
                    if (i > 0) coordsStr.append(";");
                    coordsStr.append(p.getLongitude()).append(",").append(p.getLatitude());
                }

                String url = "https://router.project-osrm.org/route/v1/driving/" +
                        coordsStr.toString() + "?overview=full&geometries=geojson";

                JSONObject res = new JSONObject(http(url));
                JSONArray coords = res.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                ArrayList<GeoPoint> puntosRuta = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    puntosRuta.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                final ArrayList<GeoPoint> paradasFinal = puntosParada;
                final ArrayList<String> nombresFinal = nombresParada;

                runOnUiThread(() -> mostrarEnMapa(puntosRuta, pOrigen, pDestino, paradasFinal, nombresFinal));

            } catch (Exception e) {
                Log.e(TAG, "Error dibujando ruta completa", e);
            }
        }).start();
    }

    private void mostrarEnMapa(ArrayList<GeoPoint> ruta, GeoPoint origen, GeoPoint destino,
                               ArrayList<GeoPoint> paradas, ArrayList<String> nombres) {
        map.getOverlays().clear();

        // Línea de ruta
        Polyline linea = new Polyline();
        linea.setPoints(ruta);
        linea.setColor(Color.parseColor("#6C3BFF"));
        linea.setWidth(12f);
        map.getOverlays().add(linea);

        // Marcador origen
        Marker mOrigen = new Marker(map);
        mOrigen.setPosition(origen);
        mOrigen.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mOrigen.setTitle("🟢 Origen");
        map.getOverlays().add(mOrigen);

        // Marcadores paradas
        if (paradas != null) {
            for (int i = 0; i < paradas.size(); i++) {
                Marker m = new Marker(map);
                m.setPosition(paradas.get(i));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                String nombre = (nombres != null && i < nombres.size())
                        ? nombres.get(i)
                        : ("Parada " + (i + 1));
                m.setTitle("🔵 " + nombre);
                map.getOverlays().add(m);
            }
        }

        // Marcador destino
        Marker mDestino = new Marker(map);
        mDestino.setPosition(destino);
        mDestino.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mDestino.setTitle("🔴 Destino");
        map.getOverlays().add(mDestino);

        map.zoomToBoundingBox(linea.getBounds(), true, 150);
        map.invalidate();
    }

    private GeoPoint geocodificar(String direccion) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q=" +
                direccion.replace(" ", "+") + ",Popayan&format=json&limit=1";

        JSONArray arr = new JSONArray(http(url));
        if (arr.length() == 0) throw new Exception("No encontrado");

        JSONObject obj = arr.getJSONObject(0);
        return new GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"));
    }

    private String http(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestProperty("User-Agent", "Moviflexx-App");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        conn.disconnect();

        return builder.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}