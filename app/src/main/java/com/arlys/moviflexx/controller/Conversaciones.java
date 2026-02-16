package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Conversaciones extends AppCompatActivity {

    private static final String TAG           = "CONVERSACIONES";
    private static final long   POLL_INTERVAL = 4000;

    // ── UI ───────────────────────────────────────────────────────────────────
    private RecyclerView        rvConversaciones;
    private ConversacionAdapter adapter;
    private SwipeRefreshLayout  swipeRefresh;
    private TextView            tvEmpty;
    private TextView            tvTitulo;
    private ImageButton         btnBack;

    // ── Datos ────────────────────────────────────────────────────────────────
    private SessionManager session;
    private String token;
    private int    idUsuario;

    private final List<Conversacion> lista = new ArrayList<>();

    // ── Polling ──────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversaciones);

        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📋 INICIANDO CONVERSACIONES");
        Log.d(TAG, "═══════════════════════════════════════");

        leerSesion();
        bindViews();
        configurarHeader();
        configurarRecycler();
        configurarSwipe();
        cargarConversaciones();
        arrancarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "→ onResume: Actualizando");
        cargarConversaciones();
        if (!pollingActivo) arrancarPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "→ onPause");
        detenerPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerPolling();
    }

    // ─── Sesión ───────────────────────────────────────────────────────────────
    private void leerSesion() {
        session   = new SessionManager(this);
        token     = session.getToken();
        idUsuario = session.getIdUsuario();

        Log.d(TAG, "Token: " + (token != null && !token.isEmpty() ? "✅" : "❌"));
        Log.d(TAG, "ID Usuario: " + idUsuario);
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvConversaciones = findViewById(R.id.rvConversaciones);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        tvEmpty          = findViewById(R.id.tvEmpty);
        tvTitulo         = findViewById(R.id.tvTitulo);
        btnBack          = findViewById(R.id.btnBack);
    }

    // ─── Header ───────────────────────────────────────────────────────────────
    private void configurarHeader() {
        if (tvTitulo != null) tvTitulo.setText("Mensajes");
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────
    private void configurarRecycler() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(lista, conv -> {
            Log.d(TAG, "═══════════════════════════════════════");
            Log.d(TAG, "🚀 ABRIENDO CHAT");
            Log.d(TAG, "ID: " + conv.getId());
            Log.d(TAG, "Contacto: " + conv.getNombreContacto());
            Log.d(TAG, "═══════════════════════════════════════");

            Intent intent = new Intent(this, Chat.class);
            intent.putExtra("idConversacion", conv.getId());
            intent.putExtra("nombre", conv.getNombreContacto());
            startActivity(intent);
        });
        rvConversaciones.setAdapter(adapter);
    }

    // ─── SwipeRefresh ─────────────────────────────────────────────────────────
    private void configurarSwipe() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.turquoise, R.color.royal_blue);
            swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
        }
    }

    // ─── Cargar conversaciones ────────────────────────────────────────────────
    private void cargarConversaciones() {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📥 CARGANDO CONVERSACIONES");
        Log.d(TAG, "URL: " + Constantes.CHAT_CONVERSACIONES);
        Log.d(TAG, "═══════════════════════════════════════");

        ConexionApi.getInstance(this).getObject(
                Constantes.CHAT_CONVERSACIONES,
                this::procesarConversaciones,
                error -> {
                    Log.e(TAG, "❌ Error cargando", error);
                    runOnUiThread(() -> {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        mostrarEmpty("Sin conexión. Desliza para reintentar.");
                    });
                }
        );
    }

    // ─── Procesar JSON ────────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void procesarConversaciones(JSONObject response) {
        Log.d(TAG, "✅ Respuesta recibida");
        Log.d(TAG, "JSON: " + response.toString());

        try {
            JSONArray arr = extraerArrayConversaciones(response);

            if (arr == null) {
                Log.w(TAG, "⚠️ No se encontró array");
                runOnUiThread(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    mostrarEmpty("No se pudieron cargar las conversaciones");
                });
                return;
            }

            lista.clear();
            Log.d(TAG, "Procesando " + arr.length() + " conversaciones");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;

                Conversacion c = Conversacion.fromJson(obj, idUsuario);

                if (c.getId() > 0) {
                    lista.add(c);
                    Log.d(TAG, "Conv " + i + ": id=" + c.getId() +
                            ", contacto=" + c.getNombreContacto());
                }
            }

            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                adapter.notifyDataSetChanged();

                if (lista.isEmpty()) {
                    mostrarEmpty("Aún no tienes conversaciones.\nLos chats con conductores aparecerán aquí.");
                } else {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                }
            });

            Log.d(TAG, "✅ " + lista.size() + " conversaciones cargadas");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error procesando", e);
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                mostrarEmpty("Error procesando datos");
            });
        }
    }

    private JSONArray extraerArrayConversaciones(JSONObject response) {
        if (response.has("content")) {
            return response.optJSONArray("content");
        }
        if (response.has("conversaciones")) {
            return response.optJSONArray("conversaciones");
        }
        if (response.has("data")) {
            return response.optJSONArray("data");
        }

        try {
            String raw = response.toString();
            if (raw.startsWith("[")) {
                return new JSONArray(raw);
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo parsear como array");
        }

        return null;
    }

    private void mostrarEmpty(String msg) {
        if (tvEmpty != null) {
            tvEmpty.setText(msg);
            tvEmpty.setVisibility(View.VISIBLE);
        }
        rvConversaciones.setVisibility(View.GONE);
    }

    // ─── Polling ─────────────────────────────────────────────────────────────
    private void arrancarPolling() {
        if (pollingActivo) return;

        pollingActivo = true;
        pollRunnable = () -> {
            if (pollingActivo) {
                Log.d(TAG, "🔄 Polling: Actualizando...");
                cargarConversaciones();
                handler.postDelayed(pollRunnable, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
        Log.d(TAG, "✅ Polling iniciado");
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        Log.d(TAG, "⏹ Polling detenido");
    }
}