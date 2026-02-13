package com.arlys.moviflexx.controller;

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
import android.content.Intent;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisViajesActivity extends AppCompatActivity {

    RecyclerView recycler;
    ProgressBar progress;

    List<JSONObject> listaViajes;
    ViajesAdapter adapter;
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_viajes);

        // ⚡ VALIDAR SESIÓN
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        recycler = findViewById(R.id.recyclerViajes);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));

        listaViajes = new ArrayList<>();

        adapter = new ViajesAdapter(this, listaViajes);

        recycler.setAdapter(adapter);

        cargarMisViajes();
    }

    private void cargarMisViajes() {
        progress.setVisibility(View.VISIBLE);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VIAJES,
                response -> {
                    progress.setVisibility(View.GONE);
                    listaViajes.clear();
                    for (int i = 0; i < response.length(); i++) {
                        listaViajes.add(response.optJSONObject(i));
                    }

                    adapter.notifyDataSetChanged();

                    if (listaViajes.isEmpty()) {
                        Toast.makeText(
                                this,
                                "No tienes viajes aún",
                                Toast.LENGTH_LONG
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
}