package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class PerfilUsuario extends AppCompatActivity {

    private SessionManager session;
    private TextView tvNombre, tvEmail, tvTelefono, tvModelo, tvPlaca, tvAsientos;
    private LinearLayout layoutVehiculoRoot;
    private MaterialButton btnRegistrarV;
    private CardView cardVehiculo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_usuario);

        session = new SessionManager(this);

        // 1. Inicializar Vistas
        tvNombre = findViewById(R.id.tv_nombre);
        tvEmail = findViewById(R.id.tv_email);
        tvTelefono = findViewById(R.id.tv_telefono);
        layoutVehiculoRoot = findViewById(R.id.layout_vehiculo_root);
        btnRegistrarV = findViewById(R.id.btn_registrar_vehiculo);
        cardVehiculo = findViewById(R.id.card_info_vehiculo);
        tvModelo = findViewById(R.id.tv_modelo_vehiculo);
        tvPlaca = findViewById(R.id.tv_placa_vehiculo);
        tvAsientos = findViewById(R.id.tv_asientos);

        // 2. Cargar Datos Básicos
        tvNombre.setText(session.getNombre());
        tvEmail.setText(session.getEmail());
        tvTelefono.setText(session.getTelefono());

        // 3. Configurar Eventos
        btnRegistrarV.setOnClickListener(v -> startActivity(new Intent(this, RegistrarVehiculo.class)));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 4. Llamar a la configuración del menú
        configurarBottomNav();
    }

    // --- MÉTODOS DE SOPORTE ---

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_perfil); // Cambiado a nav_perfil porque estamos en Perfil

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarRuta.class));
            } else if (id == R.id.nav_mensajes) {
                startActivity(new Intent(this, Mensajes.class));
            } else if (id == R.id.nav_perfil) {
                return true; // Ya estamos aquí
            } else {
                return false;
            }

            finish();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarInterfaz();
    }

    private void actualizarInterfaz() {
        if (session.getIdRol() == 2) { // Conductor
            layoutVehiculoRoot.setVisibility(View.VISIBLE);

            if (session.tieneVehiculo()) {
                cardVehiculo.setVisibility(View.VISIBLE);
                btnRegistrarV.setVisibility(View.GONE);
                tvModelo.setText(session.getVModelo());
                tvPlaca.setText("Placa: " + session.getVPlaca());
                tvAsientos.setText("Capacidad: " + session.getVCapacidad() + " asientos");
            } else {
                cardVehiculo.setVisibility(View.GONE);
                btnRegistrarV.setVisibility(View.VISIBLE);
            }
        } else {
            layoutVehiculoRoot.setVisibility(View.GONE);
        }
    }

    // --- MÉTODOS PARA EL XML (onClick) ---

    public void irEditarPerfil(View v) {
        Toast.makeText(this, "Función de editar perfil en desarrollo", Toast.LENGTH_SHORT).show();
    }

    public void irCerrarSesion(View v) {
        session.logout();
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}