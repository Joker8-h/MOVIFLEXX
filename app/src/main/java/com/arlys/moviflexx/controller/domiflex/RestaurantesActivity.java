package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Negocios;
import com.arlys.moviflexx.network.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RestaurantesActivity extends AppCompatActivity {
    RecyclerView rv;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurantes);
        rv = findViewById(R.id.rvNegocios);
        rv.setLayoutManager(new LinearLayoutManager(this));
        loadNegocios(null);
        findViewById(R.id.btnCarrito).setOnClickListener(v -> startActivity(new Intent(this, CarritoActivity.class)));
        findViewById(R.id.btnPerfil).setOnClickListener(v -> startActivity(new Intent(this, PerfilActivity.class)));
    }
    private void loadNegocios(String tipo) {
        RetrofitClient.getApiService().getNegocios(tipo, null).enqueue(new Callback<List<Negocios>>() {
            @Override public void onResponse(Call<List<Negocios>> call, Response<List<Negocios>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NegociosAdapter adapter = new NegociosAdapter(response.body(), n -> {
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
            }
            @Override public void onFailure(Call<List<Negocios>> call, Throwable t) {
                Toast.makeText(RestaurantesActivity.this, "Error: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
