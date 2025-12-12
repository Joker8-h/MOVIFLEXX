package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.arlys.moviflexx.R;

public class Perfil extends AppCompatActivity {

    // Views comunes
    private TextView tvNombre, tvEmail, tvTelefono, tvTipoUsuario;
    private TextView tvViajesRealizados, tvCalificacion, tvMiembroDesde;
    private ImageView imgPerfil;

    // Views específicas de conductor
    private LinearLayout layoutConductor;
    private TextView tvPlaca, tvModelo, tvColor;
    private TextView tvViajesCompletados, tvPorcentajeAceptacion;
    private ImageView imgVehiculo;

    private String tipoUsuario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        cargarDatosUsuario();
        setupButtons();
    }

    private void initViews() {
        // Views comunes
        tvNombre = findViewById(R.id.tv_nombre);
        tvEmail = findViewById(R.id.tv_email);
        tvTelefono = findViewById(R.id.tv_telefono);
        tvTipoUsuario = findViewById(R.id.tv_tipo_usuario);
        tvViajesRealizados = findViewById(R.id.tv_viajes_realizados);
        tvCalificacion = findViewById(R.id.tv_calificacion);
        tvMiembroDesde = findViewById(R.id.tv_miembro_desde);
        imgPerfil = findViewById(R.id.img_perfil);

        // Views de conductor
        layoutConductor = findViewById(R.id.layout_conductor);
        tvPlaca = findViewById(R.id.tv_placa);
        tvModelo = findViewById(R.id.tv_modelo);
        tvColor = findViewById(R.id.tv_color);
        tvViajesCompletados = findViewById(R.id.tv_viajes_completados);
        tvPorcentajeAceptacion = findViewById(R.id.tv_porcentaje_aceptacion);
        imgVehiculo = findViewById(R.id.img_vehiculo);
    }

    private void cargarDatosUsuario() {
        SharedPreferences prefs = getSharedPreferences("userData", MODE_PRIVATE);

        String nombre = prefs.getString("nombre", "Usuario");
        String email = prefs.getString("email", "correo@ejemplo.com");
        String telefono = prefs.getString("phone", "+57 300 000 0000");
        tipoUsuario = prefs.getString("rol", "Pasajero");

        // Datos comunes
        tvNombre.setText(nombre);
        tvEmail.setText(email);
        tvTelefono.setText(telefono);
        tvTipoUsuario.setText(tipoUsuario);

        // Estadísticas simuladas (en producción vendrían de una BD o API)
        if (tipoUsuario.equalsIgnoreCase("Conductor")) {
            layoutConductor.setVisibility(View.VISIBLE);

            // Datos del vehículo
            String placa = prefs.getString("placa", "N/A");
            String modelo = prefs.getString("modelo", "N/A");
            String color = prefs.getString("color", "N/A");

            tvPlaca.setText(placa);
            tvModelo.setText(modelo);
            tvColor.setText(color);

            // Estadísticas de conductor
            tvViajesCompletados.setText("234 viajes");
            tvCalificacion.setText("4.9 ⭐");
            tvPorcentajeAceptacion.setText("95%");
            tvMiembroDesde.setText("Miembro desde: Jun 2022");

        } else {
            layoutConductor.setVisibility(View.GONE);

            // Estadísticas de pasajero
            tvViajesRealizados.setText("47 viajes");
            tvCalificacion.setText("4.8 ⭐");
            tvMiembroDesde.setText("Miembro desde: Mar 2023");
        }
    }

    private void setupButtons() {
        Button btnEditarPerfil = findViewById(R.id.btn_editar_perfil);
        Button btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);
        Button btnHistorial = findViewById(R.id.btn_historial);

        btnEditarPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(Perfil.this, EditProfileActivity.class);
            startActivity(intent);
        });

        btnHistorial.setOnClickListener(v -> {
            Intent intent = new Intent(Perfil.this, HistorialActivity.class);
            startActivity(intent);
        });

        btnCerrarSesion.setOnClickListener(v -> {
            // Limpiar datos de sesión
            SharedPreferences prefs = getSharedPreferences("userData", MODE_PRIVATE);
            prefs.edit().clear().apply();

            // Volver al login
            Intent intent = new Intent(Perfil.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    public void irBack(View view) {
        finish();
    }
}