package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PublicarRuta extends AppCompatActivity {

    private static final int REQ_LOCATION = 1001;

    private TextInputEditText editOrigen, editDestino;
    private TextView txtInfoRuta;
    private MapView map;
    private ProgressBar loader;
    private Button btnCalcular, btnPublicar;

    private GeoPoint origenPoint;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline rutaActual;
    private Marker marcadorOrigen, marcadorDestino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_publicar_ruta);

        initViews();
        configurarMapa();
        verificarPermisosUbicacion();
        configurarBottomNav();

        btnCalcular.setOnClickListener(v -> buscarRutaEnMapa());
        btnPublicar.setOnClickListener(v -> crearRutaTecnica());
    }

    private void initViews() {
        editOrigen = findViewById(R.id.edit_origen);
        editDestino = findViewById(R.id.edit_destino);
        txtInfoRuta = findViewById(R.id.txt_info_ruta);
        map = findViewById(R.id.map_mini);
        loader = findViewById(R.id.loader_ruta);
        btnCalcular = findViewById(R.id.btn_calcular_ruta);
        btnPublicar = findViewById(R.id.btn_publicar_ruta);
    }

    /* ================= MAPA ================= */

    private void configurarMapa() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);
    }

    /* ================= GPS ================= */

    private void verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION
            );
        } else {
            activarUbicacion();
        }
    }

    private void activarUbicacion() {
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), map);

        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        map.getOverlays().add(myLocationOverlay);

        myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            origenPoint = myLocationOverlay.getMyLocation();
            if (origenPoint != null) {
                mostrarOrigen(origenPoint);
            }
        }));
    }

    private void mostrarOrigen(GeoPoint punto) {
        map.getController().animateTo(punto);

        if (marcadorOrigen != null) {
            map.getOverlays().remove(marcadorOrigen);
        }

        marcadorOrigen = new Marker(map);
        marcadorOrigen.setPosition(punto);
        marcadorOrigen.setIcon(getDrawable(R.drawable.ic_car));
        marcadorOrigen.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcadorOrigen.setTitle("Mi ubicación actual");
        map.getOverlays().add(marcadorOrigen);

        editOrigen.setText(obtenerDireccion(punto));
        map.invalidate();
    }

    /* ================= RUTA ================= */

    private void buscarRutaEnMapa() {
        if (origenPoint == null) return;

        String destinoTxt = editDestino.getText().toString().trim();
        if (destinoTxt.isEmpty()) return;

        loader.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                        + destinoTxt + ",Popayan&format=json&limit=1";

                JSONArray geoArr = new JSONArray(peticionHttp(geoUrl));
                if (geoArr.length() == 0) return;

                JSONObject obj = geoArr.getJSONObject(0);
                GeoPoint destinoPoint = new GeoPoint(
                        obj.getDouble("lat"),
                        obj.getDouble("lon")
                );

                String osrmUrl = "https://router.project-osrm.org/route/v1/driving/"
                        + origenPoint.getLongitude() + "," + origenPoint.getLatitude() + ";"
                        + destinoPoint.getLongitude() + "," + destinoPoint.getLatitude()
                        + "?overview=full&geometries=geojson";

                JSONObject res = new JSONObject(peticionHttp(osrmUrl));
                JSONObject route = res.getJSONArray("routes").getJSONObject(0);

                double km = route.getDouble("distance") / 1000;
                JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    puntos.add(new GeoPoint(
                            coords.getJSONArray(i).getDouble(1),
                            coords.getJSONArray(i).getDouble(0)
                    ));
                }

                runOnUiThread(() -> dibujarRuta(puntos, km, destinoPoint));

            } catch (Exception e) {
                runOnUiThread(() -> loader.setVisibility(View.GONE));
            }
        }).start();
    }

    private void dibujarRuta(ArrayList<GeoPoint> puntos, double km, GeoPoint destino) {
        loader.setVisibility(View.GONE);

        if (rutaActual != null) map.getOverlays().remove(rutaActual);
        if (marcadorDestino != null) map.getOverlays().remove(marcadorDestino);

        rutaActual = new Polyline();
        rutaActual.setPoints(puntos);
        rutaActual.setColor(Color.parseColor("#6C3BFF"));
        rutaActual.setWidth(12f);
        map.getOverlays().add(rutaActual);

        marcadorDestino = new Marker(map);
        marcadorDestino.setPosition(destino);
        marcadorDestino.setTitle("Destino");
        marcadorDestino.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marcadorDestino);

        map.zoomToBoundingBox(rutaActual.getBounds(), true, 150);
        txtInfoRuta.setText(String.format("Distancia aproximada: %.1f km", km));
        map.invalidate();
    }

    /* ================= DIRECCIÓN ================= */

    private String obtenerDireccion(GeoPoint punto) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(
                    punto.getLatitude(),
                    punto.getLongitude(),
                    1
            );

            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                return a.getAddressLine(0);
            }
        } catch (Exception ignored) {}
        return "Mi ubicación actual";
    }

    /* ================= API ================= */

    private String peticionHttp(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Moviflexx");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder b = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) b.append(l);
        return b.toString();
    }

    /* ================= PUBLICAR ================= */

    private void crearRutaTecnica() {
        if (editDestino.getText().toString().isEmpty()) return;

        try {
            JSONObject body = new JSONObject();
            body.put("nombre", "Ruta desde ubicación actual");
            body.put("descripcion", "Ruta generada desde GPS");
            body.put("origen", editOrigen.getText().toString());
            body.put("destino", editDestino.getText().toString());
            body.put("estado", "DISPONIBLE");

            ConexionApi.getInstance(this).post(Constantes.RUTAS, body,
                    response -> {
                        int idRuta = response.optInt("idRuta");
                        Intent i = new Intent(this, PublicarViaje.class);
                        i.putExtra("ID_RUTA_CREADA", idRuta);
                        startActivity(i);
                        finish();
                    },
                    error -> Toast.makeText(this, "Error al crear ruta", Toast.LENGTH_SHORT).show()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
                finish();
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        if (code == REQ_LOCATION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            activarUbicacion();
        }
    }
}
