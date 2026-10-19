package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Negocios;
import com.arlys.moviflexx.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RestaurantesActivity extends AppCompatActivity {
    RecyclerView rv;
    private List<Negocios> allNegocios = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurantes);
        rv = findViewById(R.id.rvNegocios);
        rv.setLayoutManager(new LinearLayoutManager(this));
        loadNegocios(null);
        findViewById(R.id.btnCarrito).setOnClickListener(v -> startActivity(new Intent(this, CarritoActivity.class)));
        findViewById(R.id.btnPerfil).setOnClickListener(v -> startActivity(new Intent(this, PerfilActivity.class)));

        EditText etBuscar = findViewById(R.id.etBuscar);
        if (etBuscar != null) {
            etBuscar.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filtrarNegocios(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void filtrarNegocios(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Negocios> filtrados = new ArrayList<>();
        for (Negocios n : allNegocios) {
            String nombre = n.getNombre() != null ? n.getNombre().toLowerCase(Locale.ROOT) : "";
            String tipo = n.getTipo() != null ? n.getTipo().toLowerCase(Locale.ROOT) : "";
            String desc = n.getDescripcion() != null ? n.getDescripcion().toLowerCase(Locale.ROOT) : "";
            if (q.isEmpty() || nombre.contains(q) || tipo.contains(q) || desc.contains(q)) {
                filtrados.add(n);
            }
        }
        renderNegocios(filtrados);
    }

    private void renderNegocios(List<Negocios> lista) {
        NegociosAdapter adapter = new NegociosAdapter(lista, n -> {
            Intent i = new Intent(RestaurantesActivity.this, RestauranteDetalleActivity.class);
            i.putExtra("negocioId", n.getId());
            i.putExtra("negocioNombre", n.getNombre());
            i.putExtra("negocioDireccion", n.getDireccion());
            i.putExtra("negocioLat", n.getLatitud());
            i.putExtra("negocioLng", n.getLongitud());
            startActivity(i);
        });
        rv.setAdapter(adapter);
    }

    private void loadNegocios(String tipo) {
        RetrofitClient.getApiService().getNegocios(tipo, null).enqueue(new Callback<List<Negocios>>() {
            @Override public void onResponse(Call<List<Negocios>> call, Response<List<Negocios>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allNegocios = response.body();
                    renderNegocios(allNegocios);
                }
            }
            @Override public void onFailure(Call<List<Negocios>> call, Throwable t) {
                Toast.makeText(RestaurantesActivity.this, "Error: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
