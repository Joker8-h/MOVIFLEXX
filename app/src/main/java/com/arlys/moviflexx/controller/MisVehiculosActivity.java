package com.arlys.moviflexx.controller;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.adapter.VehiculosAdapter;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MisVehiculosActivity extends BaseActivity {

    private static final String TAG               = "MisVehiculos";
    private static final int    MAX_REINTENTOS    = 3;
    private static final long   DELAY_MS          = 2000L;

    private RecyclerView      recycler;
    private VehiculosAdapter  adapter;
    private LinearLayout      layoutEmpty;
    private TextView          txtContador;

    private final List<JSONObject> vehiculos = new ArrayList<>();
    private int intentoActual = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_mis_vehiculos);

        recycler    = findViewById(R.id.recyclerVehiculos);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        txtContador = findViewById(R.id.txtContadorVehiculos);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehiculosAdapter(this, vehiculos);
        recycler.setAdapter(adapter);

        cargarVehiculos();
    }

    // =========================================================================
    //  CARGA CON REINTENTOS PARA 429
    // =========================================================================
    private void cargarVehiculos() {
        Log.d(TAG, "Cargando vehículos — intento " + (intentoActual + 1)
                + " | URL: " + Constantes.MIS_VEHICULOS);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VEHICULOS,
                response -> {
                    intentoActual = 0;
                    procesarRespuesta(response);
                },
                error -> {
                    int code = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;

                    Log.e(TAG, "Error cargando vehículos — HTTP: " + code);

                    if (code == 429 && intentoActual < MAX_REINTENTOS) {
                        // Rate limit: esperar y reintentar con backoff
                        intentoActual++;
                        long delay = DELAY_MS * intentoActual;
                        Log.w(TAG, "429 Rate limit — reintentando en " + delay + "ms");
                        new Handler(Looper.getMainLooper())
                                .postDelayed(this::cargarVehiculos, delay);
                    } else {
                        String msg = code == 429
                                ? "Demasiadas peticiones. Intenta en un momento."
                                : code == 401
                                ? "Sesión expirada. Vuelve a iniciar sesión."
                                : "Error cargando vehículos (HTTP " + code + ")";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Fallo definitivo: " + msg);
                        if (vehiculos.isEmpty()) mostrarVacio(true);
                    }
                }
        );
    }

    private void procesarRespuesta(JSONArray response) {
        runOnUiThread(() -> {
            vehiculos.clear();

            if (response == null || response.length() == 0) {
                Log.d(TAG, "Respuesta vacía");
                mostrarVacio(true);
                actualizarContador(0);
                return;
            }

            Log.d(TAG, "Vehículos recibidos: " + response.length());
            for (int i = 0; i < response.length(); i++) {
                JSONObject v = response.optJSONObject(i);
                if (v != null) {
                    Log.d(TAG, "Vehículo[" + i + "]: " + v.toString()
                            .substring(0, Math.min(200, v.toString().length())));
                    vehiculos.add(v);
                }
            }

            adapter.notifyDataSetChanged();
            actualizarContador(vehiculos.size());
            mostrarVacio(vehiculos.isEmpty());
        });
    }

    private void actualizarContador(int total) {
        if (txtContador != null)
            txtContador.setText(total + (total == 1 ? " vehículo" : " vehículos"));
    }

    private void mostrarVacio(boolean vacio) {
        if (layoutEmpty != null) layoutEmpty.setVisibility(vacio ? View.VISIBLE : View.GONE);
        if (recycler    != null) recycler.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override public String getNombrePantalla() { return "Mis Vehículos"; }
    @Override public String getDescripcionPantalla() {
        int total = vehiculos.size();
        return total == 0 ? "No tienes vehículos registrados."
                : "Tienes " + total + (total == 1
                ? " vehículo registrado." : " vehículos registrados.");
    }
    @Override public String getOpcionesPantalla() {
        return "Puedes decir: ir atrás, ir al inicio, o agregar vehículo.";
    }
}