package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.network.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class CarritoActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_carrito);
        RecyclerView rv = findViewById(R.id.rvCarrito);
        rv.setLayoutManager(new LinearLayoutManager(this));
        TextView tvTotal = findViewById(R.id.tvTotal);
        Button btnPedir = findViewById(R.id.btnPedir);
        CarritoManager cm = CarritoManager.getInstance();
        tvTotal.setText("Total: $"+cm.getTotal());
        CarritoAdapter adapter = new CarritoAdapter(cm.getItems(), cm.getCantidades());
        rv.setAdapter(adapter);
        btnPedir.setOnClickListener(v -> {
            if(cm.size()==0){ Toast.makeText(this,"Carrito vacío",Toast.LENGTH_SHORT).show(); return; }
            // Crear pedido - lat/lng demo (Popayán)
            JsonObject body = new JsonObject();
            body.addProperty("latRecogida", 2.4448);
            body.addProperty("lngRecogida", -76.6067);
            body.addProperty("latEntrega", 2.4420);
            body.addProperty("lngEntrega", -76.6000);
            body.addProperty("detallePedido", "Pedido móvil DomiFlex");
            body.addProperty("nombreRecogida", "Restaurante");
            body.addProperty("nombreEntrega", "Mi casa");
            JsonArray items = new JsonArray();
            for(int i=0;i<cm.getItems().size();i++){
                JsonObject it = new JsonObject();
                it.addProperty("menuItemId", cm.getItems().get(i).getId());
                it.addProperty("cantidad", cm.getCantidades().get(i));
                it.addProperty("precio", cm.getItems().get(i).getPrecio());
                items.add(it);
            }
            body.add("items", items);
            String token = getSharedPreferences("domiflex", MODE_PRIVATE).getString("token","");
            RetrofitClient.getApiService().crearPedido("Bearer "+token, body).enqueue(new retrofit2.Callback<com.arlys.moviflexx.model.pojo.Pedidos>() {
                @Override public void onResponse(retrofit2.Call<com.arlys.moviflexx.model.pojo.Pedidos> call, retrofit2.Response<com.arlys.moviflexx.model.pojo.Pedidos> r){
                    if(r.isSuccessful()){
                        Toast.makeText(CarritoActivity.this, "Pedido #"+r.body().getIdPedido()+" creado", Toast.LENGTH_LONG).show();
                        cm.clear();
                        startActivity(new Intent(CarritoActivity.this, PedidoDetalleActivity.class).putExtra("pedidoId", r.body().getIdPedido()));
                    } else Toast.makeText(CarritoActivity.this, "Error al crear pedido", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(retrofit2.Call<com.arlys.moviflexx.model.pojo.Pedidos> call, Throwable t){ Toast.makeText(CarritoActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show(); }
            });
        });
    }
}
