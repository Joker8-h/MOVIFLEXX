package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
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

public class Mapa extends AppCompatActivity {

    private static final int LOCATION_PERMISSION = 1;

    private MapView map;
    private Marker conductorMarker;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;

    // 🔥 Firebase
    private DatabaseReference refUbicacion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid config
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa);

        // MAPA
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        // GPS
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        // 🔥 Firebase (luego el ID será dinámico)
        refUbicacion = FirebaseDatabase
                .getInstance()
                .getReference("ubicaciones")
                .child("conductor_123");

        configurarSeguimientoGPS();
        configurarBottomNav();
    }

    // 📡 GPS EN TIEMPO REAL
    private void configurarSeguimientoGPS() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION
            );
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setInterval(3000); // cada 3 segundos
        request.setFastestInterval(2000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() == null) return;

                double lat = result.getLastLocation().getLatitude();
                double lon = result.getLastLocation().getLongitude();

                actualizarUbicacionConductor(lat, lon);
            }
        };

        locationClient.requestLocationUpdates(
                request,
                locationCallback,
                getMainLooper()
        );
    }

    // 🚗 ACTUALIZAR POSICIÓN + FIREBASE
    private void actualizarUbicacionConductor(double lat, double lon) {

        GeoPoint punto = new GeoPoint(lat, lon);

        if (conductorMarker == null) {
            conductorMarker = new Marker(map);
            conductorMarker.setIcon(getDrawable(R.drawable.ic_car));
            conductorMarker.setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_CENTER
            );
            map.getOverlays().add(conductorMarker);
        }

        conductorMarker.setPosition(punto);
        map.getController().animateTo(punto);
        map.invalidate();

        // 🔥 ENVIAR UBICACIÓN A FIREBASE
        enviarUbicacionFirebase(lat, lon);
    }

    // 🔥 FIREBASE
    private void enviarUbicacionFirebase(double lat, double lon) {
        refUbicacion.child("lat").setValue(lat);
        refUbicacion.child("lng").setValue(lon);
        refUbicacion.child("timestamp").setValue(System.currentTimeMillis());
    }

    // 🧭 BOTTOM NAV
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
    protected void onPause() {
        super.onPause();
        map.onPause();
        if (locationClient != null && locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }
}
