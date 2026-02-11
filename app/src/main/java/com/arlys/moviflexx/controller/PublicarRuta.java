package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
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
import org.osmdroid.views.overlay.*;
import org.osmdroid.views.overlay.mylocation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class PublicarRuta extends AppCompatActivity {

    private static final int REQ_LOCATION = 1001;

    private TextInputEditText editOrigen, editDestino;
    private TextView txtInfoRuta;
    private MapView map;
    private ProgressBar loader;
    private Button btnCalcular, btnPublicar;

    private GeoPoint origenPoint, destinoPoint;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline rutaActual;
    private Marker marcadorOrigen, marcadorDestino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_publicar_ruta);

        editOrigen = findViewById(R.id.edit_origen);
        editDestino = findViewById(R.id.edit_destino);
        txtInfoRuta = findViewById(R.id.txt_info_ruta);
        map = findViewById(R.id.map_mini);
        loader = findViewById(R.id.loader_ruta);
        btnCalcular = findViewById(R.id.btn_calcular_ruta);
        btnPublicar = findViewById(R.id.btn_publicar_ruta);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        verificarPermisosUbicacion();

        btnCalcular.setOnClickListener(v -> buscarRutaEnMapa());
        btnPublicar.setOnClickListener(v -> crearRuta());
    }

    /* ================= GPS ================= */

    private void verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION);
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

        myLocationOverlay.runOnFirstFix(() ->
                runOnUiThread(() -> {
                    origenPoint = myLocationOverlay.getMyLocation();
                    if (origenPoint != null) {
                        editOrigen.setText(obtenerDireccion(origenPoint));
                        map.getController().animateTo(origenPoint);
                    }
                }));
    }

    /* ================= BUSCAR RUTA ================= */

    private void buscarRutaEnMapa() {

        if (origenPoint == null) {
            Toast.makeText(this,"Esperando GPS...",Toast.LENGTH_SHORT).show();
            return;
        }

        String destinoTxt = editDestino.getText().toString().trim();
        if (destinoTxt.isEmpty()) {
            Toast.makeText(this,"Ingrese destino",Toast.LENGTH_SHORT).show();
            return;
        }

        loader.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {

                String geoUrl = "https://nominatim.openstreetmap.org/search?q="
                        + destinoTxt + ",Popayan&format=json&limit=1";

                JSONArray geoArr = new JSONArray(peticionHttp(geoUrl));
                if (geoArr.length() == 0) return;

                JSONObject obj = geoArr.getJSONObject(0);

                destinoPoint = new GeoPoint(
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

                runOnUiThread(() -> dibujarRuta(puntos, km));

            } catch (Exception e) {
                runOnUiThread(() -> loader.setVisibility(View.GONE));
            }
        }).start();
    }

    private void dibujarRuta(ArrayList<GeoPoint> puntos, double km) {

        loader.setVisibility(View.GONE);

        if (rutaActual != null) map.getOverlays().remove(rutaActual);

        rutaActual = new Polyline();
        rutaActual.setPoints(puntos);
        rutaActual.setColor(Color.parseColor("#6C3BFF"));
        rutaActual.setWidth(12f);
        map.getOverlays().add(rutaActual);

        txtInfoRuta.setText("Distancia: " + String.format("%.1f km", km));
        map.invalidate();
    }

    /* ================= PUBLICAR ================= */

    private void crearRuta() {

        if (rutaActual == null || destinoPoint == null) {
            Toast.makeText(this,
                    "Primero calcula la ruta",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("nombre","Ruta desde GPS");
            body.put("descripcion","Ruta generada automáticamente");
            body.put("origen", editOrigen.getText().toString());
            body.put("destino", editDestino.getText().toString());
            body.put("latOrigen", origenPoint.getLatitude());
            body.put("lngOrigen", origenPoint.getLongitude());
            body.put("latDestino", destinoPoint.getLatitude());
            body.put("lngDestino", destinoPoint.getLongitude());
            body.put("estado","DISPONIBLE");

            ConexionApi.getInstance(this).post(
                    Constantes.RUTAS,
                    body,
                    response -> {

                        int idRuta = 0;

                        if (response.has("idRuta"))
                            idRuta = response.optInt("idRuta");
                        else if (response.has("id"))
                            idRuta = response.optInt("id");

                        if (idRuta == 0) {
                            Toast.makeText(this,
                                    "No se obtuvo ID de ruta",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        Intent i = new Intent(this, PublicarViaje.class);
                        i.putExtra("ID_RUTA_CREADA", idRuta);
                        startActivity(i);
                        finish();
                    },
                    error -> Toast.makeText(this,
                            "Error creando ruta",
                            Toast.LENGTH_LONG).show()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String peticionHttp(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent","Moviflexx");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder b = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) b.append(l);
        return b.toString();
    }

    private String obtenerDireccion(GeoPoint punto) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(
                    punto.getLatitude(),
                    punto.getLongitude(),
                    1);
            if (list != null && !list.isEmpty())
                return list.get(0).getAddressLine(0);
        } catch (Exception ignored){}
        return "Ubicación actual";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            activarUbicacion();
        }
    }
}
