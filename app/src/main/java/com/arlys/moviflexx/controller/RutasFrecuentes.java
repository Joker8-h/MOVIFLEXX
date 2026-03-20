package com.arlys.moviflexx.controller;

import android.os.Bundle;
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

public class RutasFrecuentes extends BaseActivity {

    private RecyclerView rvViajes;
    private ViajesAdapter adapter;
    private final List<JSONObject> viajes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rutas_frecuentes);

        rvViajes = findViewById(R.id.rv_viajes);
        rvViajes.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ViajesAdapter(this, viajes);
        rvViajes.setAdapter(adapter);

        cargarViajes();
    }

    private void cargarViajes() {
        ConexionApi.getInstance(this).getArray(
                Constantes.buscarViajes(),
                response -> {
                    viajes.clear();

                    System.out.println("VIAJES PASAJERO => " + response);

                    if (response.length() == 0) {
                        Toast.makeText(
                                this,
                                "No hay viajes disponibles",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v != null) viajes.add(v);
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> Toast.makeText(
                        this,
                        "Error cargando viajes",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }
}
