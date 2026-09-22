package com.arlys.moviflexx.controller.domiflex;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.network.RetrofitClient;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrackingActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int pedidoId;
    private TextView tvInfo;
    private WebView mapWeb;
    private boolean mapaListo;

    private final Runnable refrescar = new Runnable() {
        @Override public void run() {
            cargar();
            handler.postDelayed(this, 15000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);
        pedidoId = getIntent().getIntExtra("pedidoId", 0);
        tvInfo = findViewById(R.id.tvInfo);
        mapWeb = findViewById(R.id.mapWeb);
        mapWeb.getSettings().setJavaScriptEnabled(false);
        tvInfo.setText("Pedido #" + pedidoId + "\nCargando estado…");
    }

    @Override protected void onStart() {
        super.onStart();
        handler.removeCallbacks(refrescar);
        handler.post(refrescar);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refrescar);
        super.onStop();
    }

    private void cargar() {
        String token = getSharedPreferences("domiflex", MODE_PRIVATE).getString("token", "");
        if (token.isEmpty() || pedidoId == 0) {
            tvInfo.setText("No hay sesión o pedido para seguir.");
            return;
        }
        RetrofitClient.getApiService().getPedidoJson("Bearer " + token, pedidoId).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().has("error")) {
                    tvInfo.setText("No se pudo leer el pedido #" + pedidoId);
                    return;
                }
                JsonObject p = response.body();
                String estado = p.has("estado") ? p.get("estado").getAsString() : "CREADO";
                String total = p.has("total") ? p.get("total").getAsString() : "0";
                String dir = p.has("dirEntrega") && !p.get("dirEntrega").isJsonNull() ? p.get("dirEntrega").getAsString() : "";
                tvInfo.setText("Pedido #" + pedidoId + " · " + estado + "\nTotal $" + total + (dir.isEmpty() ? "" : "\n" + dir));
                if (!mapaListo && p.has("latEntrega") && p.has("lngEntrega") && !p.get("latEntrega").isJsonNull()) {
                    double lat = p.get("latEntrega").getAsDouble();
                    double lng = p.get("lngEntrega").getAsDouble();
                    double delta = 0.02;
                    String url = "https://www.openstreetmap.org/export/embed.html?bbox="
                            + (lng - delta) + "%2C" + (lat - delta) + "%2C" + (lng + delta) + "%2C" + (lat + delta)
                            + "&layer=mapnik&marker=" + lat + "%2C" + lng;
                    mapWeb.loadUrl(url);
                    mapaListo = true;
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                tvInfo.setText("Sin conexión. Reintentando…");
            }
        });
    }
}
