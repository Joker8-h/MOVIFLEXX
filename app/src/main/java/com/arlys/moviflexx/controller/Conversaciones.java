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
    private int            idUsuario;
    private final List<Conversacion> lista = new ArrayList<>();

    // ── Polling ──────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversaciones);

        session   = new SessionManager(this);
        idUsuario = session.getIdUsuario();
        Log.d(TAG, "ID Usuario: " + idUsuario);

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
        cargarConversaciones();
        if (!pollingActivo) arrancarPolling();
    }

    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND
    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvConversaciones = findViewById(R.id.rvConversaciones);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        tvEmpty          = findViewById(R.id.tvEmpty);
        tvTitulo         = findViewById(R.id.tvTitulo);
        btnBack          = findViewById(R.id.btnBack);
    }

    private void configurarHeader() {
        if (tvTitulo != null) tvTitulo.setText("Mensajes");
        if (btnBack  != null) btnBack.setOnClickListener(v -> finish());
    }

    private void configurarRecycler() {
        rvConversaciones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversacionAdapter(lista, conv -> {
            Intent intent = new Intent(this, Chat.class);
            intent.putExtra("idConversacion", conv.getId());
            intent.putExtra("nombre", conv.getNombreContacto());
            startActivity(intent);
        });
        rvConversaciones.setAdapter(adapter);
    }

    private void configurarSwipe() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.turquoise, R.color.royal_blue);
            swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGAR — intenta getArray primero, luego getObject como fallback
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarConversaciones() {
        Log.d(TAG, "📥 GET: " + Constantes.CHAT_CONVERSACIONES);

        ConexionApi.getInstance(this).getArray(
                Constantes.CHAT_CONVERSACIONES,
                this::onArrayRecibido,
                this::onErrorArray
        );
    }

    // ── Callback éxito con array directo ─────────────────────────────────────
    private void onArrayRecibido(JSONArray arr) {
        Log.d(TAG, "✅ Array directo recibido: " + arr.length() + " items");
        procesarArray(arr);
    }

    // ── Fallback: el backend envolvió en objeto ───────────────────────────────
    private void onErrorArray(com.android.volley.VolleyError error) {
        Log.w(TAG, "⚠️ getArray falló, intentando getObject: " + error);
        ConexionApi.getInstance(this).getObject(
                Constantes.CHAT_CONVERSACIONES,
                response -> {
                    Log.d(TAG, "✅ getObject exitoso");
                    JSONArray arr = extraerArrayDeObjeto(response);
                    procesarArray(arr);
                },
                err2 -> runOnUiThread(() -> {
                    Log.e(TAG, "❌ Ambos métodos fallaron: " + err2);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    // Solo mostrar error si la lista está vacía
                    if (lista.isEmpty()) {
                        mostrarEmpty("Sin conexión. Desliza hacia abajo para reintentar.");
                    }
                })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PROCESAR ARRAY
    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void procesarArray(JSONArray arr) {
        try {
            lista.clear();

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    Conversacion c = Conversacion.fromJson(obj, idUsuario);
                    if (c.getId() > 0) {
                        lista.add(c);
                        Log.d(TAG, "Conv[" + i + "]: id=" + c.getId()
                                + " contacto=" + c.getNombreContacto());
                    }
                }
            }

            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                adapter.notifyDataSetChanged();

                if (lista.isEmpty()) {
                    mostrarEmpty("Aún no tienes conversaciones.\nLos chats aparecerán aquí.");
                } else {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                }
            });

            Log.d(TAG, "✅ " + lista.size() + " conversaciones cargadas");

        } catch (Exception e) {
            Log.e(TAG, "❌ procesarArray", e);
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (lista.isEmpty()) mostrarEmpty("Error al procesar datos.");
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private JSONArray extraerArrayDeObjeto(JSONObject response) {
        if (response == null) return null;
        if (response.has("content"))        return response.optJSONArray("content");
        if (response.has("conversaciones")) return response.optJSONArray("conversaciones");
        if (response.has("data"))           return response.optJSONArray("data");
        try {
            String raw = response.toString();
            if (raw.startsWith("[")) return new JSONArray(raw);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo parsear como array");
        }
        return null;
    }

    private void mostrarEmpty(String msg) {
        runOnUiThread(() -> {
            if (tvEmpty != null) {
                tvEmpty.setText(msg);
                tvEmpty.setVisibility(View.VISIBLE);
            }
            if (rvConversaciones != null)
                rvConversaciones.setVisibility(View.GONE);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POLLING
    // ─────────────────────────────────────────────────────────────────────────
    private void arrancarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollRunnable  = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                cargarConversaciones();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
        Log.d(TAG, "✅ Polling iniciado");
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "⏹ Polling detenido");
    }
}