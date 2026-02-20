package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeConductor extends AppCompatActivity {

    private static final String TAG = "HomeConductor";

    private RecyclerView    rvViajes;
    private ViajesAdapter   adapter;
    private ProgressBar     progress;
    private TextView        txtSinViajes;   // mensaje cuando no hay viajes (opcional)

    private MaterialButton btnPublicarViaje;
    private MaterialButton btnMisRutas;
    private MaterialButton btnMisVehiculos;
    private MaterialButton btnMisReservas;

    private final List<JSONObject> viajes = new ArrayList<>();

    private SessionManager session;
    private int conductorId = -1;

    // ─── LIFECYCLE ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_conductor);

        session = new SessionManager(this);

        // ── Validar sesión ────────────────────────────────────────────────────
        if (!session.isLoggedIn()) {
            irAlLogin();
            return;
        }

        session.loadSessionToMemory();

        if (!session.isConductor()) {
            Toast.makeText(this, "Esta pantalla es solo para conductores", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, HomePasajero.class));
            finish();
            return;
        }

        conductorId = session.getIdUsuario();

        Log.d(TAG, "Conductor ID: " + conductorId
                + " | nombre: " + session.getNombre()
                + " | token: " + (session.getToken() != null ? "presente" : "NULO ⚠️"));

        enlazarVistas();
        configurarRecycler();
        configurarBotones();
        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisViajes();

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_inicio);
    }

    // ─── ENLAZAR VISTAS ──────────────────────────────────────────────────────
    private void enlazarVistas() {
        rvViajes         = findViewById(R.id.rv_viajes_activos);
        progress         = findViewById(R.id.progress);
         // puede ser null si no existe en el layout

        btnPublicarViaje = findViewById(R.id.btn_publicar_viaje);
        btnMisRutas      = findViewById(R.id.btn_mis_rutas);
        btnMisVehiculos  = findViewById(R.id.btn_mis_vehiculos);

    }

    // ─── RECYCLER ────────────────────────────────────────────────────────────
    private void configurarRecycler() {
        rvViajes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);
    }

    // ─── BOTONES ─────────────────────────────────────────────────────────────
    private void configurarBotones() {
        if (btnPublicarViaje != null)
            btnPublicarViaje.setOnClickListener(v ->
                    startActivity(new Intent(this, PublicarRuta.class)));

        if (btnMisRutas != null)
            btnMisRutas.setOnClickListener(v ->
                    startActivity(new Intent(this, MisRutasActivity.class)));

        if (btnMisVehiculos != null)
            btnMisVehiculos.setOnClickListener(v ->
                    startActivity(new Intent(this, MisVehiculosActivity.class)));

        if (btnMisReservas != null)
            btnMisReservas.setOnClickListener(v ->
                    startActivity(new Intent(this, MisReservasActivity.class)));
    }

    // ─── CARGAR VIAJES ────────────────────────────────────────────────────────
    private void cargarMisViajes() {
        if (conductorId == -1) {
            Log.e(TAG, "conductorId es -1 — no se puede cargar viajes");
            Toast.makeText(this, "Error de sesión. Vuelve a iniciar sesión.", Toast.LENGTH_LONG).show();
            irAlLogin();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        if (txtSinViajes != null) txtSinViajes.setVisibility(View.GONE);

        // ── Construir la URL con el ID del conductor ───────────────────────
        // Prueba primero: /api/viajes/conductor/{id}
        // Si no funciona, intenta: /api/viajes?conductorId={id}
        // o simplemente: Constantes.MIS_VIAJES (si ya filtra por token)
        String url = Constantes.MIS_VIAJES;

        // Si MIS_VIAJES no filtra por conductor, usa esta línea en su lugar:
        // String url = "/api/viajes/conductor/" + conductorId;

        Log.d(TAG, "Cargando viajes desde: " + url);

        ConexionApi.getInstance(this).getArray(
                url,
                response -> {
                    progress.setVisibility(View.GONE);
                    viajes.clear();

                    Log.d(TAG, "Respuesta del backend — total items: " + response.length());

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject viaje = response.optJSONObject(i);
                        if (viaje != null) {
                            Log.d(TAG, "Viaje[" + i + "]: " + viaje.toString());
                            viajes.add(viaje);
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (viajes.isEmpty()) {
                        if (txtSinViajes != null) {
                            txtSinViajes.setText("No tienes viajes publicados aún 🚗");
                            txtSinViajes.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(this,
                                    "No tienes viajes publicados",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                error -> {
                    progress.setVisibility(View.GONE);
                    Log.e(TAG, "Error cargando viajes: " + error.toString());

                    Toast.makeText(this,
                            "❌ Error cargando viajes. Verifica tu conexión.",
                            Toast.LENGTH_LONG).show();
                }
        );
    }

    // ─── BOTTOM NAV ──────────────────────────────────────────────────────────
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;

        nav.setSelectedItemId(R.id.nav_inicio);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_inicio) {
                return true;
            } else if (id == R.id.nav_mis_viajes) {
                startActivity(new Intent(this, PublicarRuta.class));
            } else if (id == R.id.nav_mapa) {
                startActivity(new Intent(this, Mapa.class));
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

    // ─── NAVEGACIÓN ───────────────────────────────────────────────────────────
    private void irAlLogin() {
        startActivity(new Intent(this, Login.class));
        finish();
    }
}