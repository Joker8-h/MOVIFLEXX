package com.arlys.moviflexx.controller.domiflex;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Producto;
import com.arlys.moviflexx.network.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RestauranteDetalleActivity extends AppCompatActivity {
    int negocioId;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurante_detalle);
        negocioId = getIntent().getIntExtra("negocioId", 0);
        String nombre = getIntent().getStringExtra("negocioNombre");
        ((TextView)findViewById(R.id.tvTitulo)).setText(nombre != null ? nombre : "Restaurante");
        RecyclerView rv = findViewById(R.id.rvProductos);
        rv.setLayoutManager(new LinearLayoutManager(this));
        RetrofitClient.getApiService().getProductos(negocioId).enqueue(new Callback<List<Producto>>() {
            @Override public void onResponse(Call<List<Producto>> call, Response<List<Producto>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    ProductoAdapter a = new ProductoAdapter(r.body(), p -> {
                        CarritoManager.getInstance().add(p);
                        Toast.makeText(RestauranteDetalleActivity.this, p.getNombre()+" al carrito", Toast.LENGTH_SHORT).show();
                    });
                    rv.setAdapter(a);
                }
            }
            @Override public void onFailure(Call<List<Producto>> call, Throwable t) {
                Toast.makeText(RestauranteDetalleActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnVerCarrito).setOnClickListener(v -> startActivity(new android.content.Intent(this, CarritoActivity.class)));
    }
}
