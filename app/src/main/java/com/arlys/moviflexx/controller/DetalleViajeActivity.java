package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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

public class DetalleViajeActivity extends AppCompatActivity {

    private MapView map;
    private TextView txtInfoRuta, txtEstado, txtConductor, txtVehiculo;
    private BottomNavigationView bottomNavigation;

    private int viajeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_detalle_viaje2);

        // ===== ID VIAJE =====
        viajeId = getIntent().getIntExtra("ID_VIAJE", 0);
        if (viajeId == 0) {
            Toast.makeText(this, "Viaje no válido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ===== VISTAS =====
        map = findViewById(R.id.map_mini);
        txtInfoRuta = findViewById(R.id.txt_info_ruta);
        txtEstado = findViewById(R.id.txt_estado);
        txtConductor = findViewById(R.id.txt_conductor);
        txtVehiculo = findViewById(R.id.txt_vehiculo);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        configurarMapa();
        configurarBottomNav();
        cargarDetalleViaje();
    }

    // ================= MAPA =================
    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        GeoPoint popayan = new GeoPoint(2.4448, -76.6147);
        map.getController().setZoom(13.5);
        map.getController().setCenter(popayan);
    }

    // ================= CARGAR DETALLE =================
    private void cargarDetalleViaje() {

        String url = Constantes.VIAJES + "/" + viajeId;

        ConexionApi.getInstance(this).getObject(
                url,
                response -> {
                    try {

                        System.out.println("DETALLE VIAJE => " + response);

                        String estado = response.optString("estado", "DESCONOCIDO");

                        // ===== RUTA =====
                        JSONObject ruta = response.optJSONObject("ruta");
                        if (ruta == null) {
                            Toast.makeText(this, "Ruta no disponible", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String origen = ruta.optString("origen", "");
                        String destino = ruta.optString("destino", "");

                        if (origen.isEmpty() || destino.isEmpty()) {
                            Toast.makeText(this, "Origen o destino inválidos", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // ===== CONDUCTOR =====
                        JSONObject conductor = response.optJSONObject("conductor");
                        String nombreConductor = conductor != null
                                ? conductor.optString("nombre", "Conductor")
                                : "Conductor";

                        // ===== VEHÍCULO =====
                        JSONObject vehiculo = response.optJSONObject("vehiculo");
                        String vehiculoTxt = vehiculo != null
                                ? vehiculo.optString("marca", "") + " " +
                                vehiculo.optString("modelo", "") +
                                " • " + vehiculo.optString("placa", "")
                                : "Vehículo no asignado";

                        // ===== UI =====
                        txtInfoRuta.setText("📍 " + origen + " → " + destino);
                        txtEstado.setText("📌 Estado: " + estado);
                        txtConductor.setText("👤 " + nombreConductor);
                        txtVehiculo.setText("🚘 " + vehiculoTxt);

                        // ===== MAPA =====
                        dibujarRuta(origen, destino);

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error procesando viaje", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    Toast.makeText(this, "Error consultando el viaje", Toast.LENGTH_SHORT).show();
                }
        );
    }

    // ================= RUTA REAL =================
    private void dibujarRuta(String origenTxt, String destinoTxt) {

        new Thread(() -> {
            try {
                GeoPoint origen = geocodificar(origenTxt);
                GeoPoint destino = geocodificar(destinoTxt);

                if (origen == null || destino == null) return;

                String urlOsrm =
                        "https://router.project-osrm.org/route/v1/driving/" +
                                origen.getLongitude() + "," + origen.getLatitude() + ";" +
                                destino.getLongitude() + "," + destino.getLatitude() +
                                "?overview=full&geometries=geojson";

                JSONObject res = new JSONObject(peticionHttp(urlOsrm));
                JSONArray coords = res.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                runOnUiThread(() -> {
                    map.getOverlays().clear();

                    Polyline linea = new Polyline();
                    linea.setPoints(puntos);
                    linea.setColor(Color.parseColor("#6C3BFF"));
                    linea.setWidth(10f);
                    map.getOverlays().add(linea);

                    Marker mOrigen = new Marker(map);
                    mOrigen.setPosition(origen);
                    mOrigen.setTitle("Origen");
                    map.getOverlays().add(mOrigen);

                    Marker mDestino = new Marker(map);
                    mDestino.setPosition(destino);
                    mDestino.setTitle("Destino");
                    map.getOverlays().add(mDestino);

                    map.zoomToBoundingBox(linea.getBounds(), true, 150);
                    map.invalidate();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ================= GEO =================
    private GeoPoint geocodificar(String lugar) throws Exception {
        String url =
                "https://nominatim.openstreetmap.org/search?q=" +
                        lugar.replace(" ", "+") +
                        ",Popayan&format=json&limit=1";

        JSONArray arr = new JSONArray(peticionHttp(url));
        if (arr.length() == 0) return null;

        JSONObject o = arr.getJSONObject(0);
        return new GeoPoint(o.getDouble("lat"), o.getDouble("lon"));
    }

    private String peticionHttp(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "Moviflexx");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder b = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) b.append(l);
        return b.toString();
    }

    // ================= NAV =================
    private void configurarBottomNav() {
        if (bottomNavigation == null) return;

        bottomNavigation.setSelectedItemId(R.id.nav_inicio);
        bottomNavigation.setOnItemSelectedListener(item -> {
            Intent i = null;

            if (item.getItemId() == R.id.nav_inicio) {
                i = new Intent(this, RutasFrecuentes.class);
            } else if (item.getItemId() == R.id.nav_mapa) {
                i = new Intent(this, MapaPasajero.class);
            } else if (item.getItemId() == R.id.nav_perfil) {
                i = new Intent(this, PerfilUsuario.class);
            }

            if (i != null) {
                startActivity(i);
                finish();
            }
            return true;
        });
    }
}
