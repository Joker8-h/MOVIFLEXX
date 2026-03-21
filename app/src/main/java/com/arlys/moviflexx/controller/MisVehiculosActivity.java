package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.VehiculosAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisVehiculosActivity extends BaseActivity {

    private RecyclerView      recycler;
    private VehiculosAdapter  adapter;
    private LinearLayout      layoutEmpty;
    private TextView          txtContador;

    private final List<JSONObject> vehiculos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_mis_vehiculos);

        recycler     = findViewById(R.id.recyclerVehiculos);
        layoutEmpty  = findViewById(R.id.layoutEmpty);
        txtContador  = findViewById(R.id.txtContadorVehiculos);

        // ── Botón atrás ──────────────────────────────────────────────────────
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        // FAB agregar vehículo


        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehiculosAdapter(this, vehiculos);
        recycler.setAdapter(adapter);

        cargarVehiculos();
    }

    private void cargarVehiculos() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VEHICULOS,
                response -> {
                    vehiculos.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v != null) vehiculos.add(v);
                    }
                    adapter.notifyDataSetChanged();

                    int total = vehiculos.size();
                    if (txtContador != null)
                        txtContador.setText(total + (total == 1 ? " vehículo" : " vehículos"));

                    boolean vacio = vehiculos.isEmpty();
                    if (layoutEmpty != null)
                        layoutEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
                    recycler.setVisibility(vacio ? View.GONE : View.VISIBLE);
                },
                error -> Toast.makeText(this, "Error cargando vehículos", Toast.LENGTH_SHORT).show()
        );
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override public String getNombrePantalla() { return "Mis Vehículos"; }
    @Override public String getDescripcionPantalla() {
        int total = vehiculos.size();
        return total == 0 ? "No tienes vehículos registrados."
                : "Tienes " + total + (total == 1 ? " vehículo registrado." : " vehículos registrados.");
    }
    @Override public String getOpcionesPantalla() {
        return "Puedes decir: ir atrás, ir al inicio, o agregar vehículo.";
    }
}