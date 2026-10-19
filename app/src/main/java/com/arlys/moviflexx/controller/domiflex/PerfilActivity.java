package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.google.android.material.appbar.MaterialToolbar;

public class PerfilActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        SharedPreferences prefs = getSharedPreferences("domiflex", MODE_PRIVATE);
        String nombre = prefs.getString("nombre", "Usuario Rapitipica");
        String rol = prefs.getString("rol", "CLIENTE");

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText(nombre);

        TextView tvRol = findViewById(R.id.tvRolPerfil);
        if (tvRol != null) tvRol.setText(rol + " • Popayán");

        findViewById(R.id.btnMisPedidos).setOnClickListener(v -> {
            startActivity(new Intent(this, PedidoDetalleActivity.class));
        });

        findViewById(R.id.btnFavoritos).setOnClickListener(v -> {
            Toast.makeText(this, "Mostrando tus restaurantes favoritos", Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.btnConfiguracion).setOnClickListener(v -> {
            Toast.makeText(this, "Notificaciones y ubicación activas", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnCerrarSesion).setOnClickListener(v -> {
            prefs.edit().clear().apply();
            CarritoManager.getInstance().clear();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
