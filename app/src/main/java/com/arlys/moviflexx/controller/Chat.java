package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Mensaje;
import com.arlys.moviflexx.model.MensajeAdapter;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat.java — Moviflexx
 *
 * Responsabilidades:
 *  • Cargar historial de mensajes al abrir
 *  • Polling cada 2.5 s para mensajes nuevos (sin WebSocket)
 *  • Envío optimista: el mensaje aparece al instante, se confirma con el servidor
 *  • Indicador de estado online/offline en el header
 */
public class Chat extends AppCompatActivity {

    // ── Constantes ────────────────────────────────────────────────────────────
    private static final String TAG           = "CHAT";
    private static final long   POLL_INTERVAL = 2500L;   // ms entre polls

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView      rvMensajes;
    private MensajeAdapter    adapter;
    private TextInputEditText etMensaje;
    private ImageButton       btnEnviar;
    private TextView          tvNombreChat;
    private TextView          tvEstado;
    private View              dotEstado;
    private ImageButton       btnBack;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final List<Mensaje> listaMensajes = new ArrayList<>();
    private SessionManager session;
    private int    idUsuarioActual;
    private long   idConversacion = -1;
    private String nombreContacto = "Chat";

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;
    private       long     ultimoIdVisto = -1;

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        leerSesion();
        leerIntent();
        bindViews();
        configurarHeader();
        configurarRecycler();
        configurarInputBar();
        iniciarCarga();
    }

    @Override protected void onResume()  { super.onResume();  if (!pollingActivo) arrancarPolling(); }
    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────────────────────────────────
    private void leerSesion() {
        session         = new SessionManager(this);
        idUsuarioActual = session.getIdUsuario();
        Log.d(TAG, "Usuario actual: " + idUsuarioActual);
    }

    private void leerIntent() {
        idConversacion = getIntent().getLongExtra("idConversacion", -1);
        String nombre  = getIntent().getStringExtra("nombre");
        if (nombre != null && !nombre.isEmpty()) nombreContacto = nombre;
        Log.d(TAG, "Conversación: " + idConversacion + " | Contacto: " + nombreContacto);
    }

    private void bindViews() {
        rvMensajes   = findViewById(R.id.rvMensajes);
        etMensaje    = findViewById(R.id.etMensaje);
        btnEnviar    = findViewById(R.id.btnEnviar);
        tvNombreChat = findViewById(R.id.tvNombreChat);
        tvEstado     = findViewById(R.id.tvEstado);
        dotEstado    = findViewById(R.id.dotEstado);
        btnBack      = findViewById(R.id.btnBack);
    }

    private void configurarHeader() {
        if (tvNombreChat != null) tvNombreChat.setText(nombreContacto);
        if (tvEstado != null)     tvEstado.setText("Conectando...");
        if (btnBack != null)      btnBack.setOnClickListener(v -> finish());

        // Avatar con inicial del nombre
        TextView tvAvatar = findViewById(R.id.tvAvatarHeader);
        if (tvAvatar != null && !nombreContacto.isEmpty()) {
            tvAvatar.setText(String.valueOf(nombreContacto.charAt(0)).toUpperCase());
        }
    }

    private void configurarRecycler() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);          // los nuevos mensajes aparecen abajo
        rvMensajes.setLayoutManager(llm);
        adapter = new MensajeAdapter(listaMensajes, idUsuarioActual);
        rvMensajes.setAdapter(adapter);
    }

    private void configurarInputBar() {
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.4f);

        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hayTexto = s.toString().trim().length() > 0;
                btnEnviar.setEnabled(hayTexto);
                btnEnviar.setAlpha(hayTexto ? 1f : 0.4f);
            }
        });

        btnEnviar.setOnClickListener(v -> enviarMensaje());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGA INICIAL
    // ─────────────────────────────────────────────────────────────────────────
    private void iniciarCarga() {
        if (idConversacion == -1) {
            Log.e(TAG, "❌ idConversacion inválido");
            Toast.makeText(this, "Conversación inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cargarHistorial();
        arrancarPolling();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGAR HISTORIAL COMPLETO
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarHistorial() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        Log.d(TAG, "📥 Cargando historial: " + url);

        ConexionApi.getInstance(this).getObject(url,
                response -> runOnUiThread(() -> {
                    procesarMensajesIniciales(response);
                    setEstado(true);
                }),
                error -> runOnUiThread(() -> {
                    Log.e(TAG, "❌ Error cargando historial: " + error);
                    setEstado(false);
                    Toast.makeText(this, "Sin conexión", Toast.LENGTH_SHORT).show();
                })
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void procesarMensajesIniciales(JSONObject response) {
        try {
            JSONArray arr = extraerArrayMensajes(response);
            if (arr == null) { Log.w(TAG, "⚠️ Array nulo en respuesta"); return; }

            listaMensajes.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Mensaje m = Mensaje.fromJson(obj, idUsuarioActual);
                listaMensajes.add(m);
                if (m.getId() > ultimoIdVisto) ultimoIdVisto = m.getId();
            }
            adapter.notifyDataSetChanged();
            scrollAbajo();
            Log.d(TAG, "✅ " + listaMensajes.size() + " mensajes cargados | último ID: " + ultimoIdVisto);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error procesando historial", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POLLING — mensajes nuevos
    // ─────────────────────────────────────────────────────────────────────────
    private void arrancarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                pedirMensajesNuevos();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
        Log.d(TAG, "✅ Polling activo (c/" + POLL_INTERVAL + "ms)");
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "⏹ Polling detenido");
    }

    private void pedirMensajesNuevos() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getObject(url,
                response -> runOnUiThread(() -> {
                    agregarSoloNuevos(response);
                    setEstado(true);
                }),
                error -> runOnUiThread(() -> setEstado(false))
        );
    }

    private void agregarSoloNuevos(JSONObject response) {
        try {
            JSONArray arr = extraerArrayMensajes(response);
            if (arr == null) return;

            int nuevos = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Mensaje m = Mensaje.fromJson(obj, idUsuarioActual);
                if (m.getId() <= ultimoIdVisto) continue;   // ya lo tenemos

                // ¿Hay un mensaje optimista pendiente con el mismo texto?
                int idxOpt = encontrarOptimista(m.getContenido());
                if (idxOpt >= 0) {
                    listaMensajes.set(idxOpt, m);
                    adapter.notifyItemChanged(idxOpt);
                } else {
                    listaMensajes.add(m);
                    adapter.notifyItemInserted(listaMensajes.size() - 1);
                }
                ultimoIdVisto = m.getId();
                nuevos++;
            }
            if (nuevos > 0) scrollAbajo();
        } catch (Exception e) {
            Log.e(TAG, "❌ agregarSoloNuevos", e);
        }
    }

    private int encontrarOptimista(String contenido) {
        for (int i = listaMensajes.size() - 1; i >= 0; i--) {
            Mensaje m = listaMensajes.get(i);
            if (m.isEnviando() && contenido != null && contenido.equals(m.getContenido())) return i;
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENVIAR MENSAJE
    // ─────────────────────────────────────────────────────────────────────────
    private void enviarMensaje() {
        String texto = etMensaje.getText() != null
                ? etMensaje.getText().toString().trim() : "";
        if (texto.isEmpty()) return;

        // 1. Limpiar input inmediatamente
        etMensaje.setText("");
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.4f);

        // 2. Mensaje optimista — aparece al instante
        Mensaje optimista = new Mensaje();
        optimista.setId(-System.currentTimeMillis());
        optimista.setContenido(texto);
        optimista.setIdEmisor(idUsuarioActual);
        optimista.setEsPropio(true);
        optimista.setEnviando(true);
        optimista.setFechaEnvio(Instant.now().toString());
        listaMensajes.add(optimista);
        adapter.notifyItemInserted(listaMensajes.size() - 1);
        scrollAbajo();

        // 3. Enviar al servidor
        JSONObject body = new JSONObject();
        try {
            body.put("conversacionId", idConversacion);
            body.put("mensaje",        texto);
        } catch (JSONException e) {
            Log.e(TAG, "❌ JSON error", e);
            return;
        }

        ConexionApi.getInstance(this).post(Constantes.CHAT_MENSAJES, body,
                response -> runOnUiThread(() -> confirmarEnvio(optimista, response)),
                error    -> runOnUiThread(() -> marcarFallido(optimista))
        );
    }

    private void confirmarEnvio(Mensaje optimista, JSONObject response) {
        try {
            Mensaje real = Mensaje.fromJson(response, idUsuarioActual);
            int idx = listaMensajes.indexOf(optimista);
            if (idx >= 0) {
                listaMensajes.set(idx, real);
                adapter.notifyItemChanged(idx);
            }
            if (real.getId() > ultimoIdVisto) ultimoIdVisto = real.getId();
            Log.d(TAG, "✅ Mensaje confirmado ID: " + real.getId());
        } catch (Exception e) {
            Log.e(TAG, "❌ confirmarEnvio", e);
            marcarFallido(optimista);
        }
    }

    private void marcarFallido(Mensaje m) {
        m.setEnviando(false);
        m.setFallido(true);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "❌ No se pudo enviar", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Extrae el array de mensajes sin importar qué key use el backend */
    private JSONArray extraerArrayMensajes(JSONObject response) {
        if (response == null) return null;
        if (response.has("mensajes")) return response.optJSONArray("mensajes");
        if (response.has("content"))  return response.optJSONArray("content");
        if (response.has("data"))     return response.optJSONArray("data");
        try {
            String raw = response.toString();
            if (raw.startsWith("[")) return new JSONArray(raw);
        } catch (Exception ignored) {}
        return null;
    }

    private void setEstado(boolean online) {
        if (tvEstado == null || dotEstado == null) return;
        tvEstado.setText(online ? "En línea" : "Sin conexión");
        dotEstado.setBackgroundResource(
                online ? R.drawable.circle_online : R.drawable.circle_offline);
    }

    private void scrollAbajo() {
        if (!listaMensajes.isEmpty()) {
            rvMensajes.post(() ->
                    rvMensajes.scrollToPosition(listaMensajes.size() - 1));
        }
    }
}