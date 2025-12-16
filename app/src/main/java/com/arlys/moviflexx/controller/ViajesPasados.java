package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ViajesPasados extends AppCompatActivity {

    BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viajes_pasados);

        // Inicializar correctamente
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Marcar el item actual (mensajes / detalle)
        bottomNavigation.setSelectedItemId(R.id.nav_mapa);

        bottomNavigation.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));

            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarViaje.class));

            } else if (id == R.id.nav_mapa) {
                return true;

            } else if (id == R.id.nav_mensajes) {
                startActivity(new Intent(this, Mensajes.class));

            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilUsuario.class));
            }

            overridePendingTransition(0, 0);
            finish();
            return true;
        });
    }
    public void irDetalle(View view) {
        Intent siguiente = new Intent(ViajesPasados.this, DetalleViajePasado.class);
        startActivity(siguiente);
    }
}