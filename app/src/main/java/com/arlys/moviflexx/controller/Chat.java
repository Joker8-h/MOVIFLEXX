package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.VolleyError;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Chat extends AppCompatActivity {

    private static final String TAG           = "CHAT";
    private static final long   POLL_INTERVAL = 2500L;

    private static final String[] SUGERENCIAS_PASAJERO = {
            "👋 ¡Hola! ¿Ya saliste hacia el punto de recogida?",
            "📍 ¿Dónde exactamente me recoges?",
            "⏱️ ¿Cuánto tardas en llegar?",
            "💺 ¿Cuántos pasajeros van en el viaje?",
            "🅿️ ¿Puedes recogerme en otro punto?",
            "💰 ¿El precio incluye el trayecto completo?",
            "🧳 ¿Puedo llevar equipaje?",
            "🔔 Avísame cuando estés cerca, por favor",
            "🛣️ ¿Cuál es la ruta que tomarás?",
            "✅ Perfecto, te espero en el punto acordado"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView      rvMensajes;
    private MensajeAdapter    adapter;
    private TextInputEditText etMensaje;
    private ImageButton       btnEnviar;
    private TextView          tvNombreChat;
    private TextView          tvEstado;
    private View              dotEstado;
    private ImageButton       btnBack;
    private LinearLayout      panelSugerencias;
    private ChipGroup         chipGroupSugerencias;
    private boolean           sugerenciasOcultas = false;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final List<Mensaje> listaMensajes = new ArrayList<>();
    private SessionManager session;
    private int    idUsuarioActual;
    private long   idConversacion = -1;
    private String nombreContacto = "Chat";

    // ── Cola de leídos pendientes de subir al backend ─────────────────────────
    // Mensajes que el usuario YA vio en pantalla pero el backend aún no sabe
    private final Set<Long> pendientesLeido = new HashSet<>();
    private boolean sincronizando           = false;

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

        session         = new SessionManager(this);
        idUsuarioActual = session.getIdUsuario();

        idConversacion  = getIntent().getLongExtra("idConversacion", -1);
        String nombre   = getIntent().getStringExtra("nombre");
        if (nombre != null && !nombre.isEmpty()) nombreContacto = nombre;

        bindViews();
        configurarHeader();
        configurarRecycler();
        configurarInputBar();
        configurarSugerencias();

        if (idConversacion == -1) {
            Toast.makeText(this, "Conversación inválida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cargarHistorial();
        arrancarPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!pollingActivo) arrancarPolling();
        // Al volver a la pantalla, intentar sincronizar leídos pendientes
        sincronizarPendientes();
    }

    @Override protected void onPause()   { super.onPause();   detenerPolling(); }
    @Override protected void onDestroy() { super.onDestroy(); detenerPolling(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND VIEWS
    // ─────────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvMensajes           = findViewById(R.id.rvMensajes);
        etMensaje            = findViewById(R.id.etMensaje);
        btnEnviar            = findViewById(R.id.btnEnviar);
        tvNombreChat         = findViewById(R.id.tvNombreChat);
        tvEstado             = findViewById(R.id.tvEstado);
        dotEstado            = findViewById(R.id.dotEstado);
        btnBack              = findViewById(R.id.btnBack);
        panelSugerencias     = findViewById(R.id.panel_sugerencias);
        chipGroupSugerencias = findViewById(R.id.chip_group_sugerencias);
    }

    private void configurarHeader() {
        if (tvNombreChat != null) tvNombreChat.setText(nombreContacto);
        if (tvEstado     != null) tvEstado.setText("Conectando...");
        if (btnBack      != null) btnBack.setOnClickListener(v -> finish());

        TextView tvAvatar = findViewById(R.id.tvAvatarHeader);
        if (tvAvatar != null && !nombreContacto.isEmpty())
            tvAvatar.setText(String.valueOf(nombreContacto.charAt(0)).toUpperCase());
    }

    private void configurarRecycler() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvMensajes.setLayoutManager(llm);

        adapter = new MensajeAdapter(listaMensajes, idUsuarioActual);

        // Cada vez que el adapter renderiza un mensaje recibido no leído,
        // lo marcamos localmente y lo agregamos a la cola pendiente
        adapter.setOnMensajeVistoListener(mensaje -> {
            if (!mensaje.isLeido() && !mensaje.isEsPropio() && mensaje.getId() > 0) {
                mensaje.setLeido(true);                   // 1. UI inmediata, sin esperar backend
                pendientesLeido.add(mensaje.getId());     // 2. Encolar para subir al backend
            }
        });

        rvMensajes.setAdapter(adapter);
    }

    private void configurarInputBar() {
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.4f);
        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hay = s.toString().trim().length() > 0;
                btnEnviar.setEnabled(hay);
                btnEnviar.setAlpha(hay ? 1f : 0.4f);
            }
        });
        btnEnviar.setOnClickListener(v -> enviarMensaje());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SUGERENCIAS
    // ─────────────────────────────────────────────────────────────────────────
    private void configurarSugerencias() {
        if (session.isConductor() || panelSugerencias == null || chipGroupSugerencias == null) {
            if (panelSugerencias != null) panelSugerencias.setVisibility(View.GONE);
            return;
        }
        chipGroupSugerencias.removeAllViews();
        for (String texto : SUGERENCIAS_PASAJERO) {
            Chip chip = new Chip(this);
            chip.setText(texto);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setTextSize(13f);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E0F2F1")));
            chip.setTextColor(Color.parseColor("#004D40"));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#80CBC4")));
            chip.setChipStrokeWidth(2f);
            chip.setRippleColor(ColorStateList.valueOf(Color.parseColor("#B2DFDB")));
            chip.setOnClickListener(v -> {
                if (etMensaje != null) {
                    etMensaje.setText(texto);
                    etMensaje.setSelection(texto.length());
                    etMensaje.requestFocus();
                }
            });
            chipGroupSugerencias.addView(chip);
        }
        View btnCerrar = findViewById(R.id.btn_cerrar_sugerencias);
        if (btnCerrar != null) btnCerrar.setOnClickListener(v -> ocultarSugerencias(true));
        panelSugerencias.setVisibility(View.VISIBLE);
    }

    private void ocultarSugerencias(boolean animado) {
        if (sugerenciasOcultas || panelSugerencias == null) return;
        sugerenciasOcultas = true;
        if (animado) {
            panelSugerencias.animate()
                    .translationY(panelSugerencias.getHeight()).alpha(0f).setDuration(260)
                    .withEndAction(() -> {
                        panelSugerencias.setVisibility(View.GONE);
                        panelSugerencias.setTranslationY(0f);
                        panelSugerencias.setAlpha(1f);
                    }).start();
        } else {
            panelSugerencias.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CARGAR HISTORIAL
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarHistorial() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        Log.d(TAG, "📥 GET historial: " + url);

        ConexionApi.getInstance(this).getArray(url,
                this::onHistorialOk,
                this::onHistorialError
        );
    }

    private void onHistorialOk(JSONArray arr) {
        runOnUiThread(() -> {
            procesarArray(arr, true);
            setEstado(true);
        });
    }

    private void onHistorialError(VolleyError error) {
        Log.w(TAG, "getArray falló → intentando getObject");
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getObject(url,
                resp -> runOnUiThread(() -> {
                    procesarArray(extraerArray(resp), true);
                    setEstado(true);
                }),
                err2 -> runOnUiThread(() -> {
                    Log.e(TAG, "❌ No se pudo cargar historial");
                    setEstado(false);
                })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PROCESAR MENSAJES
    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void procesarArray(JSONArray arr, boolean limpiar) {
        if (arr == null) return;
        try {
            if (limpiar) listaMensajes.clear();

            boolean huboCambios = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Mensaje m = Mensaje.fromJson(obj, idUsuarioActual);

                if (limpiar) {
                    listaMensajes.add(m);
                } else {
                    if (m.getId() <= ultimoIdVisto) continue;
                    int idx = buscarOptimista(m.getContenido());
                    if (idx >= 0) {
                        listaMensajes.set(idx, m);
                        adapter.notifyItemChanged(idx);
                    } else {
                        listaMensajes.add(m);
                        adapter.notifyItemInserted(listaMensajes.size() - 1);
                    }
                    huboCambios = true;
                }
                if (m.getId() > ultimoIdVisto) ultimoIdVisto = m.getId();
            }

            if (limpiar) {
                adapter.notifyDataSetChanged();
            }

            scrollAbajo();

            // Ocultar sugerencias si ya hay mensajes del usuario
            if (limpiar && hayMensajesPropios()) ocultarSugerencias(false);

            // ── CLAVE: marcar como leídos localmente los mensajes recibidos ──
            // y encolarlos para subir al backend DESPUÉS
            marcarRecibidosComoLeidos();

            Log.d(TAG, "✅ " + listaMensajes.size() + " mensajes | ultimoId=" + ultimoIdVisto);
        } catch (Exception e) {
            Log.e(TAG, "❌ procesarArray", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LÓGICA DE LEÍDOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PASO 1 — LOCAL (inmediato):
     * Recorre la lista visible. Cualquier mensaje recibido (no propio)
     * que tenga leido=false → lo marca true LOCALMENTE y lo encola.
     * La UI se actualiza de inmediato sin esperar al backend.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void marcarRecibidosComoLeidos() {
        boolean huboCambios = false;
        for (Mensaje m : listaMensajes) {
            if (m.isEsPropio() || m.getId() <= 0) continue;
            if (!m.isLeido()) {
                m.setLeido(true);                   // actualiza UI local
                pendientesLeido.add(m.getId());     // encola para backend
                huboCambios = true;
            }
        }
        if (huboCambios) {
            adapter.notifyDataSetChanged();         // refresca puntos verdes / ticks
            sincronizarPendientes();                // intenta subir al backend
        }
    }

    /**
     * PASO 2 — BACKEND (diferido):
     * Sube al backend los IDs pendientes.
     *
     * Estrategia A: PUT /conversaciones/{id}/leer  (marca toda la conversación)
     * Estrategia B si A falla: PUT /mensajes/{id}/leer por cada mensaje individual
     *
     * Si ambas fallan (sin conexión), los IDs quedan en pendientesLeido
     * y se reintenta en el próximo onResume o polling.
     */
    private void sincronizarPendientes() {
        if (pendientesLeido.isEmpty() || sincronizando) return;
        sincronizando = true;

        // ── Estrategia A: marcar toda la conversación de una vez ─────────────
        String url = Constantes.chatMarcarLeido(idConversacion);
        Log.d(TAG, "👁 PUT marcar leídos (conversación): " + url);

        ConexionApi.getInstance(this).put(url, null,
                response -> {
                    Log.d(TAG, "✅ Conversación marcada leída en backend");
                    pendientesLeido.clear();
                    sincronizando = false;
                },
                error -> {
                    int codigo = error.networkResponse != null
                            ? error.networkResponse.statusCode : -1;
                    Log.w(TAG, "⚠️ PUT /leer falló (HTTP " + codigo + ") → estrategia B");
                    estrategiaB();
                }
        );
    }

    /**
     * Estrategia B: si el endpoint de conversación no existe,
     * marcamos cada mensaje uno a uno con PUT /mensajes/{id}/leer
     */
    private void estrategiaB() {
        if (pendientesLeido.isEmpty()) { sincronizando = false; return; }

        // Copiar para iterar sin ConcurrentModificationException
        Long[] ids = pendientesLeido.toArray(new Long[0]);
        final int[] restantes = {ids.length};

        for (long idMsg : ids) {
            String url = Constantes.chatMensajeMarcarLeido(idMsg);
            Log.d(TAG, "👁 PUT marcar leído (msg " + idMsg + "): " + url);

            ConexionApi.getInstance(this).put(url, null,
                    resp -> {
                        pendientesLeido.remove(idMsg);
                        restantes[0]--;
                        if (restantes[0] <= 0) sincronizando = false;
                        Log.d(TAG, "✅ Mensaje " + idMsg + " marcado leído");
                    },
                    err -> {
                        // No borrar de pendientesLeido: se reintentará
                        restantes[0]--;
                        if (restantes[0] <= 0) sincronizando = false;
                        Log.w(TAG, "⚠️ No se pudo marcar mensaje " + idMsg
                                + " — se reintentará más tarde");
                    }
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POLLING
    // ─────────────────────────────────────────────────────────────────────────
    private void arrancarPolling() {
        if (pollingActivo) return;
        pollingActivo = true;
        pollRunnable = new Runnable() {
            @Override public void run() {
                if (!pollingActivo) return;
                pedirNuevos();
                // Aprovechar el polling para reintentar leídos pendientes
                if (!pendientesLeido.isEmpty()) sincronizarPendientes();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    private void detenerPolling() {
        pollingActivo = false;
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
    }

    private void pedirNuevos() {
        String url = Constantes.chatMensajesPorConversacion(idConversacion);
        ConexionApi.getInstance(this).getArray(url,
                arr -> runOnUiThread(() -> { procesarArray(arr, false); setEstado(true); }),
                err -> ConexionApi.getInstance(this).getObject(url,
                        resp -> runOnUiThread(() -> {
                            procesarArray(extraerArray(resp), false);
                            setEstado(true);
                        }),
                        err2 -> runOnUiThread(() -> setEstado(false))
                )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ENVIAR MENSAJE
    // ─────────────────────────────────────────────────────────────────────────
    private void enviarMensaje() {
        if (etMensaje.getText() == null) return;
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        ocultarSugerencias(true);
        etMensaje.setText("");
        btnEnviar.setEnabled(false);
        btnEnviar.setAlpha(0.4f);

        // Mensaje optimista — aparece de inmediato
        Mensaje opt = new Mensaje();
        opt.setId(-System.currentTimeMillis());
        opt.setContenido(texto);
        opt.setIdEmisor(idUsuarioActual);
        opt.setEsPropio(true);
        opt.setEnviando(true);
        opt.setLeido(false);
        opt.setFechaEnvio(String.valueOf(System.currentTimeMillis()));
        listaMensajes.add(opt);
        adapter.notifyItemInserted(listaMensajes.size() - 1);
        scrollAbajo();

        // Body con todos los nombres posibles que el backend puede esperar
        JSONObject body = new JSONObject();
        try {
            body.put("idConversacion", idConversacion);   // nombre columna real BD
            body.put("idRemitente",    idUsuarioActual);  // nombre columna real BD
            body.put("mensaje",        texto);             // nombre columna real BD
            body.put("tipo",           "TEXTO");           // nombre columna real BD
            body.put("conversacionId", idConversacion);   // alias Spring común
            body.put("emisorId",       idUsuarioActual);  // alias Spring común
            body.put("contenido",      texto);             // alias Spring común
        } catch (JSONException e) {
            Log.e(TAG, "❌ JSON body", e);
            marcarFallido(opt);
            return;
        }

        ConexionApi.getInstance(this).post(Constantes.CHAT_MENSAJES, body,
                response -> runOnUiThread(() -> confirmarEnvio(opt, response)),
                error    -> runOnUiThread(() -> marcarFallido(opt))
        );
    }

    private void confirmarEnvio(Mensaje opt, JSONObject response) {
        try {
            Mensaje real = Mensaje.fromJson(response, idUsuarioActual);
            real.setLeido(false); // recién enviado, receptor aún no lo leyó
            int idx = listaMensajes.indexOf(opt);
            if (idx >= 0) {
                listaMensajes.set(idx, real);
                adapter.notifyItemChanged(idx);
            }
            if (real.getId() > ultimoIdVisto) ultimoIdVisto = real.getId();
        } catch (Exception e) {
            Log.e(TAG, "❌ confirmarEnvio", e);
            marcarFallido(opt);
        }
    }

    private void marcarFallido(Mensaje m) {
        m.setEnviando(false);
        m.setFallido(true);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "No se pudo enviar el mensaje", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private JSONArray extraerArray(JSONObject response) {
        if (response == null) return null;
        if (response.has("mensajes"))  return response.optJSONArray("mensajes");
        if (response.has("content"))   return response.optJSONArray("content");
        if (response.has("data"))      return response.optJSONArray("data");
        try {
            String raw = response.toString();
            if (raw.startsWith("[")) return new JSONArray(raw);
        } catch (Exception ignored) {}
        return null;
    }

    private boolean hayMensajesPropios() {
        for (Mensaje m : listaMensajes)
            if (m.isEsPropio() || m.getIdEmisor() == idUsuarioActual) return true;
        return false;
    }

    private int buscarOptimista(String contenido) {
        for (int i = listaMensajes.size() - 1; i >= 0; i--) {
            Mensaje m = listaMensajes.get(i);
            if (m.isEnviando() && contenido != null && contenido.equals(m.getContenido()))
                return i;
        }
        return -1;
    }

    private void setEstado(boolean online) {
        if (tvEstado == null || dotEstado == null) return;
        tvEstado.setText(online ? "En línea" : "Sin conexión");
        dotEstado.setBackgroundResource(
                online ? R.drawable.circle_online : R.drawable.circle_offline);
    }

    private void scrollAbajo() {
        if (!listaMensajes.isEmpty())
            rvMensajes.post(() -> rvMensajes.scrollToPosition(listaMensajes.size() - 1));
    }
}