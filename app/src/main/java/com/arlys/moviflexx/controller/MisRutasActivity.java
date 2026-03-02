package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.RutasAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisRutasActivity extends AppCompatActivity {

    private RecyclerView   recycler;
    private RutasAdapter   adapter;
    private LinearLayout   layoutEmpty;
    private TextView       txtContador;

    private final List<JSONObject> rutas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_rutas);

        recycler    = findViewById(R.id.recyclerRutas);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        txtContador = findViewById(R.id.txtContadorRutas);

        // ── Botón atrás ──────────────────────────────────────────────────────
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RutasAdapter(this, rutas);
        recycler.setAdapter(adapter);

        cargarRutas();
    }

    private void cargarRutas() {
        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RUTAS,
                response -> {
                    rutas.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r != null) rutas.add(r);
                    }
                    adapter.notifyDataSetChanged();

                    int total = rutas.size();
                    if (txtContador != null)
                        txtContador.setText(total + (total == 1 ? " ruta" : " rutas"));

                    boolean vacio = rutas.isEmpty();
                    if (layoutEmpty != null)
                        layoutEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
                    recycler.setVisibility(vacio ? View.GONE : View.VISIBLE);
                },
                error -> Toast.makeText(this, "Error cargando rutas", Toast.LENGTH_SHORT).show()
        );
    }
}