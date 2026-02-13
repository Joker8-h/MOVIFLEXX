package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisReservasActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private final List<JSONObject> reservas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_reservas);

        recycler = findViewById(R.id.recyclerReservas);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        cargarReservas();
    }

    private void cargarReservas() {

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {

                    reservas.clear();

                    for (int i = 0; i < response.length(); i++) {
                        reservas.add(response.optJSONObject(i));
                    }

                    Toast.makeText(this,
                            "Reservas: " + reservas.size(),
                            Toast.LENGTH_SHORT).show();
                },
                error -> Toast.makeText(this,
                        "Error cargando reservas",
                        Toast.LENGTH_LONG).show()
        );
    }
}
