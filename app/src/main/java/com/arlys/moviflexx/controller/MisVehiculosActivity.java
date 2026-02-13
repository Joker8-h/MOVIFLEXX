package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisVehiculosActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView txtSinVehiculos;
    private MaterialButton btnAgregarVehiculo;
    private final List<JSONObject> vehiculos = new ArrayList<>();
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_vehiculos);

        session = new SessionManager(this);

        recycler = findViewById(R.id.recyclerVehiculos);
        progress = findViewById(R.id.progress);


        recycler.setLayoutManager(new LinearLayoutManager(this));

        if (btnAgregarVehiculo != null) {
            btnAgregarVehiculo.setOnClickListener(v ->
                    startActivity(new Intent(this, RegistrarVehiculo.class))
            );
        }

        cargarVehiculos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarVehiculos();
    }

    private void cargarVehiculos() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (txtSinVehiculos != null) txtSinVehiculos.setVisibility(View.GONE);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VEHICULOS,
                response -> {
                    if (progress != null) progress.setVisibility(View.GONE);

                    vehiculos.clear();

                    for (int i = 0; i < response.length(); i++) {
                        vehiculos.add(response.optJSONObject(i));
                    }

                    if (vehiculos.isEmpty()) {
                        if (txtSinVehiculos != null) {
                            txtSinVehiculos.setVisibility(View.VISIBLE);
                            txtSinVehiculos.setText("No tienes vehículos registrados\n\nAgrega uno para publicar viajes");
                        }
                        Toast.makeText(this, "No tienes vehículos registrados", Toast.LENGTH_SHORT).show();
                    } else {
                        // TODO: Crear adapter para mostrar vehículos
                        Toast.makeText(this, "Vehículos cargados: " + vehiculos.size(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Error cargando vehículos", Toast.LENGTH_LONG).show();
                }
        );
    }
}