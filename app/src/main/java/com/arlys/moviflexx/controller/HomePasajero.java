package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomePasajero extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_pasajero);

        bottomNavigation = findViewById(R.id.bottom_navigation);

        if (bottomNavigation == null) {
            // Evita crash si el layout cambia por error
            return;
        }

        // Marcar item actual
        bottomNavigation.setSelectedItemId(R.id.nav_inicio);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                return true; // ya estamos aquí
            }

            Intent intent = null;

            if (id == R.id.nav_mis_viajes) {
                intent = new Intent(this, RutasFrecuentes.class);

            } else if (id == R.id.nav_mapa) {
                intent = new Intent(this, MapaPasajero.class);

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
}
