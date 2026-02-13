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

public class MisRutasActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView txtSinRutas;
    private MaterialButton btnCrearRuta;
    private final List<JSONObject> rutas = new ArrayList<>();
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_rutas);

        session = new SessionManager(this);

        recycler = findViewById(R.id.recyclerRutas);
        progress = findViewById(R.id.progress);


        recycler.setLayoutManager(new LinearLayoutManager(this));

        if (btnCrearRuta != null) {
            btnCrearRuta.setOnClickListener(v ->
                    startActivity(new Intent(this, PublicarRuta.class))
            );
        }

        cargarMisRutas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisRutas();
    }

    private void cargarMisRutas() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (txtSinRutas != null) txtSinRutas.setVisibility(View.GONE);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RUTAS,
                response -> {
                    if (progress != null) progress.setVisibility(View.GONE);

                    rutas.clear();

                    for (int i = 0; i < response.length(); i++) {
                        rutas.add(response.optJSONObject(i));
                    }

                    if (rutas.isEmpty()) {
                        if (txtSinRutas != null) {
                            txtSinRutas.setVisibility(View.VISIBLE);
                            txtSinRutas.setText("No tienes rutas creadas\n\nCrea una ruta para publicar viajes");
                        }
                        Toast.makeText(this, "No tienes rutas creadas", Toast.LENGTH_SHORT).show();
                    } else {
                        // TODO: Crear adapter para mostrar rutas
                        Toast.makeText(this, "Rutas cargadas: " + rutas.size(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Error cargando rutas", Toast.LENGTH_LONG).show();
                }
        );
    }
}