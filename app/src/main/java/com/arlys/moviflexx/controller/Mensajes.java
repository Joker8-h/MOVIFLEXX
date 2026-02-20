package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Conversacion;
import com.arlys.moviflexx.model.ConversacionAdapter;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Mensajes extends AppCompatActivity {

    private static final String TAG              = "MENSAJES";
    private static final long   POLLING_INTERVAL = 5000L;
    private static final int    MAX_REINTENTOS   = 3;

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView        rvConversaciones;
    private ConversacionAdapter adapter;
    private EditText            etBuscar;
    private TextView            tvSinResultados;
    private SwipeRefreshLayout  swipeRefresh;


    // ── Datos ─────────────────────────────────────────────────────────────────
    private final List<Conversacion> lista    = new ArrayList<>();
    private final List<Conversacion> filtrada = new ArrayList<>();
    private int idUsuarioActual;
    private int intentosFallidos = 0;
    private boolean cargaInicial = true;

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private boolean  pollingActivo = false;

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mensajes);

        SessionManager sm = new SessionManager(this);
        idUsuarioActual = sm.getIdUsuario();
        Log.d(TAG, "Usuario: " + idUsuarioActual);

        bindViews();
        configurarRecycler();
        configurarBuscador();
        configurarSwipeRefresh();
        configurarBottomNav();
        cargarConversaciones();
        iniciarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarConversaciones();
        if (!pollingActivo) iniciarPolling();
    }

    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND
    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvConversaciones = findViewById(R.id.rvConversaciones);
        etBuscar         = findViewById(R.id.etBuscar);
        tvSinResultados  = findViewById(R.id.tvSinResultados);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
    }

    private void configurarRecycler() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(filtrada, this::abrirChat);
        rvConversaciones.setAdapter(adapter);
    }

    private void configurarSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.turquoise, R.color.royal_blue);
        swipeRefresh.setOnRefreshListener(() -> {
            intentosFallidos = 0;    // resetear contador al refrescar manualmente
            cargarConversaciones();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGAR CONVERSACIONES
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarConversaciones() {
        Log.d(TAG, "📥 Cargando: " + Constantes.CHAT_CONVERSACIONES);

        ConexionApi.getInstance(this).getArray(
                Constantes.CHAT_CONVERSACIONES,
                arr -> {
                    intentosFallidos = 0;
                    cargaInicial = false;
                    procesarRespuestaDesdeArray(arr);
                },
                error -> {
                    Log.w(TAG, "⚠️ getArray falló, intentando getObject");
                    ConexionApi.getInstance(this).getObject(
                            Constantes.CHAT_CONVERSACIONES,
                            response -> {
                                intentosFallidos = 0;
                                cargaInicial = false;
                                procesarRespuesta(response);
                            },
                            err2 -> runOnUiThread(() -> {
                                intentosFallidos++;
                                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                                Log.e(TAG, "❌ Error #" + intentosFallidos + ": " + err2);
                                if (lista.isEmpty()) {
                                    if (intentosFallidos >= MAX_REINTENTOS) {
                                        mostrarVacio("Sin conexión. Desliza hacia abajo para reintentar.");
                                    } else {
                                        mostrarVacio("Cargando conversaciones...");
                                    }
                                }
                            })
                    );
                }
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void procesarRespuestaDesdeArray(JSONArray arr) {
        try {
            lista.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    Conversacion c = Conversacion.fromJson(obj, idUsuarioActual);
                    if (c.getId() > 0) lista.add(c);
                }
            }
            String filtroActual = etBuscar != null
                    ? etBuscar.getText().toString().toLowerCase().trim() : "";
            filtrada.clear();
            for (Conversacion c : lista) {
                if (filtroActual.isEmpty() ||
                        c.getNombreContacto().toLowerCase().contains(filtroActual))
                    filtrada.add(c);
            }
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                adapter.notifyDataSetChanged();
                if (lista.isEmpty())
                    mostrarVacio("Aún no tienes conversaciones.\nLos chats con conductores aparecerán aquí.");
                else {
                    if (tvSinResultados != null) tvSinResultados.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                }
            });
            Log.d(TAG, "✅ " + lista.size() + " conversaciones (desde array)");
        } catch (Exception e) {
            Log.e(TAG, "❌ procesarRespuestaDesdeArray", e);
            runOnUiThread(() -> { if (swipeRefresh != null) swipeRefresh.setRefreshing(false); });
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void procesarRespuesta(JSONObject response) {
        Log.d(TAG, "✅ Respuesta recibida");
        try {
            JSONArray arr = extraerArray(response);

            lista.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    Conversacion c = Conversacion.fromJson(obj, idUsuarioActual);
                    if (c.getId() > 0) lista.add(c);
                }
            }

            // Aplicar filtro activo
            String filtroActual = etBuscar != null
                    ? etBuscar.getText().toString().toLowerCase().trim() : "";
            filtrada.clear();
            for (Conversacion c : lista) {
                if (filtroActual.isEmpty() ||
                        c.getNombreContacto().toLowerCase().contains(filtroActual)) {
                    filtrada.add(c);
                }
            }

            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                adapter.notifyDataSetChanged();

                if (lista.isEmpty()) {
                    mostrarVacio("Aún no tienes conversaciones.\nLos chats con conductores aparecerán aquí.");
                } else {
                    if (tvSinResultados  != null) tvSinResultados.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                }
            });

            Log.d(TAG, "✅ " + lista.size() + " conversaciones");
        } catch (Exception e) {
            Log.e(TAG, "❌ procesarRespuesta", e);
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (lista.isEmpty()) mostrarVacio("Error al cargar. Desliza para reintentar.");
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POLLING
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollingRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                cargarConversaciones();
                handler.postDelayed(this, POLLING_INTERVAL);
            }
        };
        handler.postDelayed(pollingRunnable, POLLING_INTERVAL);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollingRunnable != null) handler.removeCallbacks(pollingRunnable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BUSCADOR
    // ─────────────────────────────────────────────────────────────────────────
    private void configurarBuscador() {
        if (etBuscar == null) return;
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                aplicarFiltro(s.toString().toLowerCase().trim());
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void aplicarFiltro(String texto) {
        filtrada.clear();
        for (Conversacion conv : lista) {
            if (texto.isEmpty() || conv.getNombreContacto().toLowerCase().contains(texto)) {
                filtrada.add(conv);
            }
        }
        adapter.notifyDataSetChanged();
        if (filtrada.isEmpty() && !lista.isEmpty()) {
            mostrarVacio("No se encontraron conversaciones con \"" + texto + "\"");
        } else if (!filtrada.isEmpty()) {
            if (tvSinResultados  != null) tvSinResultados.setVisibility(View.GONE);
            rvConversaciones.setVisibility(View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ABRIR CHAT
    // ─────────────────────────────────────────────────────────────────────────
    private void abrirChat(Conversacion c) {
        Log.d(TAG, "🚀 Abriendo chat ID:" + c.getId() + " con " + c.getNombreContacto());
        Intent i = new Intent(this, Chat.class);
        i.putExtra("idConversacion", c.getId());
        i.putExtra("nombre",         c.getNombreContacto());
        startActivity(i);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BOTTOM NAV
    // ─────────────────────────────────────────────────────────────────────────
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;
        nav.setSelectedItemId(R.id.nav_mensajes);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, Home.class)); finish();
            } else if (id == R.id.nav_mapa) {
                startActivity(new Intent(this, Mapa.class)); finish();
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilUsuario.class)); finish();
            }
            return true;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private JSONArray extraerArray(JSONObject response) {
        if (response == null) return null;
        if (response.has("content"))        return response.optJSONArray("content");
        if (response.has("conversaciones")) return response.optJSONArray("conversaciones");
        if (response.has("data"))           return response.optJSONArray("data");
        try {
            String raw = response.toString();
            if (raw.startsWith("[")) return new JSONArray(raw);
        } catch (Exception ignored) {}
        return null;
    }

    private void mostrarVacio(String msg) {
        runOnUiThread(() -> {
            if (tvSinResultados != null) {
                tvSinResultados.setText(msg);
                tvSinResultados.setVisibility(View.VISIBLE);
            }
            rvConversaciones.setVisibility(View.GONE);
        });
    }
}