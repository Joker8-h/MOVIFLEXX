package com.arlys.moviflexx.controller;

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

/**
 * Mensajes.java — Moviflexx
 *
 * Lista de conversaciones del usuario con:
 *  • Pull-to-refresh
 *  • Buscador por nombre de contacto
 *  • Polling cada 5 s para detectar nuevas conversaciones o mensajes
 *  • Badge de no leídos
 */
public class Mensajes extends AppCompatActivity {

    private static final String TAG              = "MENSAJES";
    private static final long   POLLING_INTERVAL = 5000L;

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
        swipeRefresh.setOnRefreshListener(this::cargarConversaciones);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGAR CONVERSACIONES
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarConversaciones() {
        Log.d(TAG, "📥 Cargando: " + Constantes.CHAT_CONVERSACIONES);

        ConexionApi.getInstance(this).getObject(
                Constantes.CHAT_CONVERSACIONES,
                this::procesarRespuesta,
                error -> runOnUiThread(() -> {
                    Log.e(TAG, "❌ Error: " + error);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    mostrarVacio("Sin conexión. Desliza para reintentar.");
                })
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void procesarRespuesta(JSONObject response) {
        Log.d(TAG, "✅ Respuesta: " + response.toString().substring(0,
                Math.min(response.toString().length(), 200)));
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

            // Aplicar filtro de búsqueda si hay texto activo
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
                    mostrarVacio("Aún no tienes conversaciones.\n" +
                            "Los chats con conductores aparecerán aquí.");
                } else {
                    if (tvSinResultados != null) tvSinResultados.setVisibility(View.GONE);
                    rvConversaciones.setVisibility(View.VISIBLE);
                }
            });

            Log.d(TAG, "✅ " + lista.size() + " conversaciones cargadas");
        } catch (Exception e) {
            Log.e(TAG, "❌ procesarRespuesta", e);
            runOnUiThread(() -> {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
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
        Log.d(TAG, "✅ Polling activo (c/" + (POLLING_INTERVAL/1000) + "s)");
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollingRunnable != null) handler.removeCallbacks(pollingRunnable);
        Log.d(TAG, "⏹ Polling detenido");
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
            if (texto.isEmpty() ||
                    conv.getNombreContacto().toLowerCase().contains(texto)) {
                filtrada.add(conv);
            }
        }
        adapter.notifyDataSetChanged();
        if (filtrada.isEmpty() && !lista.isEmpty()) {
            mostrarVacio("No se encontraron conversaciones");
        } else if (!filtrada.isEmpty()) {
            if (tvSinResultados != null) tvSinResultados.setVisibility(View.GONE);
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
        if (response.has("content"))       return response.optJSONArray("content");
        if (response.has("conversaciones"))return response.optJSONArray("conversaciones");
        if (response.has("data"))          return response.optJSONArray("data");
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

    // ─────────────────────────────────────────────────────────────────────────
    //  SUPPRESS — usado en procesarRespuesta
    // ─────────────────────────────────────────────────────────────────────────
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @interface SuppressLint { String[] value(); }
}