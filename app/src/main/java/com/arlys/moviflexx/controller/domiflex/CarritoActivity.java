package com.arlys.moviflexx.controller.domiflex;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.network.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class CarritoActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 41;

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
            if (cm.size() == 0) {
                Toast.makeText(this, "Carrito vacío", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
                return;
            }
            buscarUbicacionYPedir();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            buscarUbicacionYPedir();
        } else if (requestCode == REQ_LOCATION) {
            Toast.makeText(this, "Necesitamos tu ubicación para calcular el domicilio.", Toast.LENGTH_LONG).show();
        }
    }

    private void buscarUbicacionYPedir() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location loc = null;
        try {
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) { }
        if (loc == null) {
            Toast.makeText(this, "Activa el GPS y espera unos segundos.", Toast.LENGTH_LONG).show();
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, new android.location.LocationListener() {
                    @Override public void onLocationChanged(@NonNull Location location) {
                        lm.removeUpdates(this);
                        crearPedido(location.getLatitude(), location.getLongitude());
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "No pudimos leer el GPS.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        crearPedido(loc.getLatitude(), loc.getLongitude());
    }

    private void crearPedido(double latEntrega, double lngEntrega) {
        SharedPreferences prefs = getSharedPreferences("domiflex", MODE_PRIVATE);
        String token = prefs.getString("token", "");
        if (token.isEmpty()) {
            Toast.makeText(this, "Inicia sesión de nuevo.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        double latRecogida = Double.parseDouble(prefs.getString("negocioLat", "0"));
        double lngRecogida = Double.parseDouble(prefs.getString("negocioLng", "0"));
        if (latRecogida == 0 || lngRecogida == 0) {
            Toast.makeText(this, "Abre el restaurante de nuevo para tomar su ubicación.", Toast.LENGTH_LONG).show();
            return;
        }
        CarritoManager cm = CarritoManager.getInstance();
        JsonObject body = new JsonObject();
        body.addProperty("negocioId", prefs.getInt("negocioId", 0));
        body.addProperty("latRecogida", latRecogida);
        body.addProperty("lngRecogida", lngRecogida);
        body.addProperty("latEntrega", latEntrega);
        body.addProperty("lngEntrega", lngEntrega);
        body.addProperty("nombreRecogida", prefs.getString("negocioNombre", "Restaurante"));
        body.addProperty("dirRecogida", prefs.getString("negocioDireccion", ""));
        body.addProperty("nombreEntrega", "Mi casa");
        body.addProperty("dirEntrega", "Ubicación actual");
        body.addProperty("detallePedido", "Pedido móvil DomiFlex");
        body.addProperty("tipoPago", "EFECTIVO");
        JsonArray items = new JsonArray();
        for (int i = 0; i < cm.getItems().size(); i++) {
            JsonObject it = new JsonObject();
            it.addProperty("menuItemId", cm.getItems().get(i).getId());
            it.addProperty("cantidad", cm.getCantidades().get(i));
            items.add(it);
        }
        body.add("items", items);
        RetrofitClient.getApiService().crearPedido("Bearer " + token, body).enqueue(new retrofit2.Callback<com.arlys.moviflexx.model.pojo.Pedidos>() {
            @Override public void onResponse(retrofit2.Call<com.arlys.moviflexx.model.pojo.Pedidos> call, retrofit2.Response<com.arlys.moviflexx.model.pojo.Pedidos> r) {
                if (r.isSuccessful() && r.body() != null) {
                    Toast.makeText(CarritoActivity.this, "Pedido #" + r.body().getIdPedido() + " creado", Toast.LENGTH_LONG).show();
                    cm.clear();
                    startActivity(new Intent(CarritoActivity.this, TrackingActivity.class).putExtra("pedidoId", r.body().getIdPedido()));
                } else {
                    Toast.makeText(CarritoActivity.this, "Error al crear pedido", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(retrofit2.Call<com.arlys.moviflexx.model.pojo.Pedidos> call, Throwable t) {
                Toast.makeText(CarritoActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
