package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeConductor extends AppCompatActivity {

    private RecyclerView rvViajes;
    private ViajesAdapter adapter;
    private final List<JSONObject> viajes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_conductor);

        // ===== RECYCLER =====
        rvViajes = findViewById(R.id.rv_viajes_activos);
        rvViajes.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);

        // ===== PUBLICAR VIAJE =====
        findViewById(R.id.btn_publicar_viaje)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, PublicarViaje.class))
                );

        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisViajes();
    }

    // ================= CARGAR MIS VIAJES =================
    private void cargarMisViajes() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    viajes.clear();

                    System.out.println("MIS VIAJES => " + response);

                    if (response.length() == 0) {
                        Toast.makeText(
                                this,
                                "No tienes viajes publicados",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v != null) viajes.add(v);
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> {
                    Toast.makeText(
                            this,
                            "Error cargando tus viajes",
                            Toast.LENGTH_LONG
                    ).show();

                    if (error.networkResponse != null) {
                        System.out.println(
                                new String(error.networkResponse.data)
                        );
                    }
                }
        );
    }

    // ================= BOTTOM NAV =================
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;

        nav.setSelectedItemId(R.id.nav_inicio);

        nav.setOnItemSelectedListener(item -> {

            Intent intent = null;

            if (item.getItemId() == R.id.nav_inicio) {
                return true;

            } else if (item.getItemId() == R.id.nav_mapa) {
                intent = new Intent(this, Mapa.class);

            } else if (item.getItemId() == R.id.nav_mis_viajes) {
                intent = new Intent(this, PublicarRuta.class);

            } else if (item.getItemId() == R.id.nav_mensajes) {
                intent = new Intent(this, Mensajes.class);

            } else if (item.getItemId() == R.id.nav_perfil) {
                intent = new Intent(this, PerfilUsuario.class);
            }

            if (intent != null) {
                startActivity(intent);
                finish();
            }

            return true;
        });
    }
}
