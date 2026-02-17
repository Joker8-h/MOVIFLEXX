package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Manager.RouteManager;
import com.arlys.moviflexx.model.pojo.RouteOption;
import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.arlys.moviflexx.utils.GeoJsonHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class Mapa extends AppCompatActivity {

    private static final String TAG = "Mapa";
    private static final int LOCATION_PERMISSION = 1;

    private MapView map;
    private Marker conductorMarker;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    // Rutas pintadas actualmente (para poder borrarlas si se refrescan)
    private final List<Polyline> rutasPintadas = new ArrayList<>();

    // Destino del viaje activo (viene del Intent)
    private double destinoLat = 0;
    private double destinoLng = 0;
    private boolean rutaSolicitada = false;

    private RouteManager routeManager;

    // Firebase
    private DatabaseReference refUbicacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa);

        destinoLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
        destinoLng = getIntent().getDoubleExtra("DESTINO_LNG", 0);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        routeManager = new RouteManager();

        refUbicacion = FirebaseDatabase.getInstance()
                .getReference("ubicaciones")
                .child("conductor_123");

        configurarSeguimientoGPS();
        configurarBottomNav();
    }

    // GPS EN TIEMPO REAL
    private void configurarSeguimientoGPS() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION);
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setInterval(3000);
        request.setFastestInterval(2000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() == null) return;
                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();
                actualizarUbicacionConductor(lat, lon);
                if (destinoLat != 0 && destinoLng != 0 && !rutaSolicitada) {
                    rutaSolicitada = true;
                    solicitarRutas(lat, lon);
                }
            }
        };

        locationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    // POSICION CONDUCTOR + FIREBASE
    private void actualizarUbicacionConductor(double lat, double lon) {
        GeoPoint punto = new GeoPoint(lat, lon);

        if (conductorMarker == null) {
            conductorMarker = new Marker(map);
            conductorMarker.setIcon(getDrawable(R.drawable.ic_car));
            conductorMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            map.getOverlays().add(conductorMarker);
        }

        conductorMarker.setPosition(punto);
        map.getController().animateTo(punto);
        map.invalidate();
        enviarUbicacionFirebase(lat, lon);
    }

    private void enviarUbicacionFirebase(double lat, double lon) {
        refUbicacion.child("lat").setValue(lat);
        refUbicacion.child("lng").setValue(lon);
        refUbicacion.child("timestamp").setValue(System.currentTimeMillis());
    }

    // SOLICITAR Y PINTAR RUTAS
    private void solicitarRutas(double origenLat, double origenLng) {
        Log.d(TAG, "Solicitando rutas al backend...");

        routeManager.fetchRoutes(
                origenLat, origenLng,
                destinoLat, destinoLng,
                "FASTEST",
                new RouteManager.RouteCallback() {
                    @Override
                    public void onSuccess(RouteOptionsResponse response) {
                        runOnUiThread(() -> pintarRutasEnMapa(response));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "Error rutas: " + errorMessage);
                            Toast.makeText(Mapa.this, "No se pudieron cargar rutas", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void pintarRutasEnMapa(RouteOptionsResponse response) {
        if (response.routes == null || response.routes.isEmpty()) return;

        // Borrar rutas anteriores
        for (Polyline p : rutasPintadas) map.getOverlays().remove(p);
        rutasPintadas.clear();

        // Construir lista para GeoJsonHelper
        List<GeoJsonHelper.RutaSimple> rutas = new ArrayList<>();
        for (RouteOption r : response.routes) {
            rutas.add(new GeoJsonHelper.RutaSimple(
                    r.id, r.geojson, r.distanceKm, r.durationMin, r.fuelCostCop
            ));
        }

        // Pintar todas las rutas con colores diferenciados
        List<Polyline> pintadas = GeoJsonHelper.pintarRutas(rutas, map);
        rutasPintadas.addAll(pintadas);

        // Zoom para encuadrar todas las rutas
        GeoJsonHelper.zoomARutas(rutasPintadas, map);

        // Conductor siempre encima
        if (conductorMarker != null) {
            map.getOverlays().remove(conductorMarker);
            map.getOverlays().add(conductorMarker);
        }

        map.invalidate();

        // Toast con la mejor ruta
        RouteOption mejor = response.routes.get(0);
        Toast.makeText(this,
                "Mejor ruta: " + mejor.distanceKm + " km | "
                        + (int) mejor.durationMin + " min | "
                        + "$" + (int) mejor.fuelCostCop + " COP",
                Toast.LENGTH_LONG).show();
    }

    // BOTTOM NAV
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_mapa);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
            } else if (item.getItemId() == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarRuta.class));
            } else if (item.getItemId() == R.id.nav_mensajes) {
                startActivity(new Intent(this, Mensajes.class));
            } else if (item.getItemId() == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilUsuario.class));
            } else return true;
            finish();
            return true;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            configurarSeguimientoGPS();
        } else {
            Toast.makeText(this, "Permiso de ubicacion requerido", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        if (locationClient != null && locationCallback != null)
            locationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }
}