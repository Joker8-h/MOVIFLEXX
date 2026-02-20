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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BuscarViajesActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProgressBar progress;
    private ViajesAdapter adapter;
    private final List<JSONObject> viajes = new ArrayList<>();

    private String origen;
    private String destino;
    private String fecha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buscar_viajes);

        recycler = findViewById(R.id.recyclerViajes);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViajesAdapter(this, viajes);
        recycler.setAdapter(adapter);

        // 🔥 Recibir datos del intent
        origen = getIntent().getStringExtra("origen");
        destino = getIntent().getStringExtra("destino");
        fecha = getIntent().getStringExtra("fecha");

        if (origen == null) origen = "";
        if (destino == null) destino = "";
        if (fecha == null) fecha = "";

        cargarViajes();
    }

    private void cargarViajes() {

        progress.setVisibility(View.VISIBLE);

        String url = Constantes.buscarViajes(origen, destino, fecha);

        ConexionApi.getInstance(this).getArray(
                url,
                response -> {

                    progress.setVisibility(View.GONE);
                    viajes.clear();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v != null) viajes.add(v);
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Error cargando viajes",
                            Toast.LENGTH_LONG).show();
                }
        );
    }
}
