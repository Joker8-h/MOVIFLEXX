package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapaPasajero extends AppCompatActivity {

    private MapView map;
    private Marker conductorMarker;
    private DatabaseReference refUbicacion;
    private BottomNavigationView bottomNavigation; // Agregada la declaración

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración necesaria para OpenStreetMap (OSM)
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mapa_pasajero);

        // 1. Configurar Navegación
        configurarNavegacion();

        // 2. Configurar Mapa
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);

        // 3. Referencia a Firebase (Aquí va el ID del conductor que el pasajero sigue)
        refUbicacion = FirebaseDatabase
                .getInstance()
                .getReference("ubicaciones")
                .child("conductor_123");

        escucharUbicacion();
    }

    private void configurarNavegacion() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation == null) return;

        // Seleccionar el icono del mapa ya que estamos en esta pantalla
        bottomNavigation.setSelectedItemId(R.id.nav_mapa);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent = null;

            if (id == R.id.nav_mapa) return true; // Ya estamos aquí

            if (id == R.id.nav_inicio) {
                intent = new Intent(this, HomePasajero.class); // Cambiado a HomePasajero
            } else if (id == R.id.nav_mis_viajes) {
                intent = new Intent(this, RutasFrecuentes.class);
            } else if (id == R.id.nav_mensajes) {
                intent = new Intent(this, Mensajes.class);
            } else if (id == R.id.nav_perfil) {
                intent = new Intent(this, PerfilUsuario.class);
            }

            if (intent != null) {
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            }
            return true;
        });
    }

    // 👂 ESCUCHAR UBICACIÓN DEL CONDUCTOR EN TIEMPO REAL
    private void escucharUbicacion() {
        refUbicacion.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // Extraer coordenadas de Firebase
                Double lat = snapshot.child("lat").getValue(Double.class);
                Double lng = snapshot.child("lng").getValue(Double.class);

                if (lat != null && lng != null) {
                    actualizarConductor(lat, lng);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void actualizarConductor(double lat, double lng) {
        GeoPoint punto = new GeoPoint(lat, lng);

        if (conductorMarker == null) {
            conductorMarker = new Marker(map);
            conductorMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            conductorMarker.setIcon(getDrawable(R.drawable.ic_car)); // Asegúrate de que ic_car exista
            conductorMarker.setTitle("Tu Conductor");
            map.getOverlays().add(conductorMarker);
        }

        conductorMarker.setPosition(punto);

        // Mover la cámara para seguir al conductor
        map.getController().animateTo(punto);
        map.invalidate();
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