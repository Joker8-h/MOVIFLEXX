package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class EditarPerfil extends AppCompatActivity {

    BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_editar_perfil);


        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Marcar item actual
        bottomNavigation.setSelectedItemId(R.id.nav_inicio);

        bottomNavigation.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));

            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarViaje.class));

            } else if (id == R.id.nav_mapa) {
                startActivity(new Intent(this, ViajesPasados.class));

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
    public void irPerfil(View view) {
        startActivity(new Intent(this, PerfilUsuario.class));
    }


}