package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Pedidos;
import com.arlys.moviflexx.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PedidoDetalleActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pedido_detalle);
        int pedidoId = getIntent().getIntExtra("pedidoId", 0);
        TextView tvEstado = findViewById(R.id.tvEstado);
        TextView tvTotal = findViewById(R.id.tvTotal);
        String token = getSharedPreferences("domiflex", MODE_PRIVATE).getString("token","");
        RetrofitClient.getApiService().getPedidoById("Bearer "+token, pedidoId).enqueue(new Callback<Pedidos>() {
            @Override public void onResponse(Call<Pedidos> call, Response<Pedidos> r){
                if(r.isSuccessful() && r.body()!=null){
                    tvEstado.setText("Estado: "+r.body().getEstado());
                    tvTotal.setText("Total: $"+r.body().getTotal());
                }
            }
            @Override public void onFailure(Call<Pedidos> call, Throwable t){ tvEstado.setText("Error: "+t.getMessage()); }
        });
        findViewById(R.id.btnTracking).setOnClickListener(v-> startActivity(new Intent(this, TrackingActivity.class).putExtra("pedidoId", pedidoId)));
    }
}
