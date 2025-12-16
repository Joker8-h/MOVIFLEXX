package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class PerfilUsuario extends AppCompatActivity {

    BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_perfil_usuario);

        // Inicializar correctamente
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Marcar el item actual (mensajes / detalle)
        bottomNavigation.setSelectedItemId(R.id.nav_perfil);

        bottomNavigation.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));

            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarViaje.class));

            } else if (id == R.id.nav_mapa) {
                startActivity(new Intent(this, ViajesPasados.class));

            } else if (id == R.id.nav_mensajes) {
                // Ya estás aquí
                startActivity(new Intent(this, Mensajes.class));

            } else if (id == R.id.nav_perfil) {
                return true;

            }

            overridePendingTransition(0, 0);
            finish();
            return true;
        });
    }
    public void irEditarPerfil(View view) {
        Intent siguiente = new Intent(PerfilUsuario.this, EditarPerfil.class);
        startActivity(siguiente);
    }
    public void irCerrarSesion(View view) {
        Intent siguiente = new Intent(PerfilUsuario.this, Login.class);
        startActivity(siguiente);
    }
}