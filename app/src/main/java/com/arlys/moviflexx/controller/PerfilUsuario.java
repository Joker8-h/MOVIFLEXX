package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class PerfilUsuario extends BaseActivity {

    private SessionManager session;
    private TextView tvNombre, tvEmail, tvTelefono, tvModelo, tvPlaca, tvAsientos;
    private LinearLayout layoutVehiculoRoot;
    private MaterialButton btnRegistrarV;
    private CardView cardVehiculo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_usuario);

        // Siempre crear nuevo SessionManager para leer datos frescos
        session = new SessionManager(this);

        tvNombre           = findViewById(R.id.tv_nombre);
        tvEmail            = findViewById(R.id.tv_email);
        tvTelefono         = findViewById(R.id.tv_telefono);
        layoutVehiculoRoot = findViewById(R.id.layout_vehiculo_root);
        btnRegistrarV      = findViewById(R.id.btn_registrar_vehiculo);
        cardVehiculo       = findViewById(R.id.card_info_vehiculo);
        tvModelo           = findViewById(R.id.tv_modelo_vehiculo);
        tvPlaca            = findViewById(R.id.tv_placa_vehiculo);
        tvAsientos         = findViewById(R.id.tv_asientos);

        tvNombre.setText(session.getNombre());
        tvEmail.setText(session.getEmail());
        tvTelefono.setText(session.getTelefono());

        if (btnRegistrarV != null)
            animateButton(btnRegistrarV,
                    () -> goTo(RegistrarVehiculo.class, Transition.SLIDE));

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null)
            btnBack.setOnClickListener(v -> finish());

        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Recrear SessionManager para leer siempre datos frescos de SharedPreferences
        session = new SessionManager(this);

        tvNombre.setText(session.getNombre());
        tvEmail.setText(session.getEmail());
        tvTelefono.setText(session.getTelefono());
        actualizarInterfaz();

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_perfil);
    }

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;

        nav.setSelectedItemId(R.id.nav_perfil);
        boolean esConductor = session.getIdRol() == 2;

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_perfil) return true;

            if (esConductor) {
                if      (id == R.id.nav_inicio)     goTo(HomeConductor.class,  Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,   Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,           Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,       Transition.NONE);
            } else {
                if      (id == R.id.nav_inicio)     goTo(HomePasajero.class,        Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(MisReservasActivity.class, Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,               Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,           Transition.NONE);
            }

            finish();
            return true;
        });
    }

    private void actualizarInterfaz() {
        if (session.getIdRol() == 2) {
            if (layoutVehiculoRoot != null) layoutVehiculoRoot.setVisibility(View.VISIBLE);
            if (session.tieneVehiculo()) {
                if (cardVehiculo  != null) cardVehiculo.setVisibility(View.VISIBLE);
                if (btnRegistrarV != null) btnRegistrarV.setVisibility(View.GONE);
                if (tvModelo   != null) tvModelo.setText(session.getVModelo());
                if (tvPlaca    != null) tvPlaca.setText("Placa: " + session.getVPlaca());
                if (tvAsientos != null) tvAsientos.setText("Capacidad: " + session.getVCapacidad() + " asientos");
            } else {
                if (cardVehiculo  != null) cardVehiculo.setVisibility(View.GONE);
                if (btnRegistrarV != null) btnRegistrarV.setVisibility(View.VISIBLE);
            }
        } else {
            if (layoutVehiculoRoot != null) layoutVehiculoRoot.setVisibility(View.GONE);
        }
    }

    public void irEditarPerfil(View v) {
        goTo(EditarPerfil.class, Transition.SLIDE);
    }

    public void irCerrarSesion(View v) {
        session.logout();
        goTo(Login.class, Transition.FADE, true);
    }
}