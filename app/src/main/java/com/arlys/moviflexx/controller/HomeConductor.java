package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.ViajesAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeConductor extends AppCompatActivity {

    private RecyclerView rvViajes;
    private ViajesAdapter adapter;
    private ProgressBar progress;

    private MaterialButton btnPublicarViaje;
    private MaterialButton btnMisRutas;
    private MaterialButton btnMisVehiculos;
    private MaterialButton btnMisReservas;

    private final List<JSONObject> viajes = new ArrayList<>();

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_conductor);

        // ================= VALIDAR SESIÓN =================
        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Por favor inicia sesión", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Cargar datos en memoria (IMPORTANTE)
        session.loadSessionToMemory();

        // Verificar rol
        if (!session.isConductor()) {
            Toast.makeText(this, "Esta pantalla es solo para conductores", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, HomePasajero.class));
            finish();
            return;
        }

        // ================= INICIALIZAR =================

        rvViajes = findViewById(R.id.rv_viajes_activos);
        progress = findViewById(R.id.progress);

        btnPublicarViaje = findViewById(R.id.btn_publicar_viaje);
        btnMisRutas = findViewById(R.id.btn_mis_rutas);
        btnMisVehiculos = findViewById(R.id.btn_mis_vehiculos);
        btnMisReservas = findViewById(R.id.btn_mis_reservas);

        rvViajes.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);

        if (btnPublicarViaje != null) {
            btnPublicarViaje.setOnClickListener(v ->
                    startActivity(new Intent(this, PublicarRuta.class))
            );
        }

        if (btnMisRutas != null) {
            btnMisRutas.setOnClickListener(v ->
                    startActivity(new Intent(this, MisRutasActivity.class))
            );
        }

        if (btnMisVehiculos != null) {
            btnMisVehiculos.setOnClickListener(v ->
                    startActivity(new Intent(this, MisVehiculosActivity.class))
            );
        }

        if (btnMisReservas != null) {
            btnMisReservas.setOnClickListener(v ->
                    startActivity(new Intent(this, MisReservasActivity.class))
            );
        }

        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisViajes();

        // ⚡ IMPORTANTE: Actualizar el item seleccionado del BottomNav
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) {
            nav.setSelectedItemId(R.id.nav_inicio);
        }
    }

    // ================= API MIS VIAJES =================

    private void cargarMisViajes() {

        progress.setVisibility(View.VISIBLE);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {

                    progress.setVisibility(View.GONE);
                    viajes.clear();

                    for (int i = 0; i < response.length(); i++) {

                        JSONObject viaje = response.optJSONObject(i);

                        if (viaje != null)
                            viajes.add(viaje);
                    }

                    adapter.notifyDataSetChanged();

                    if (viajes.isEmpty()) {

                        Toast.makeText(
                                this,
                                "No tienes viajes publicados",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                },
                error -> {

                    progress.setVisibility(View.GONE);

                    Toast.makeText(
                            this,
                            "Error cargando viajes",
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    // 🧭 BOTTOM NAV
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_inicio);

        nav.setOnItemSelectedListener(item -> {

            if (item.getItemId() == R.id.map_mini) {
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
}