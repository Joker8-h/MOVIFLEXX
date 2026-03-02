package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Notificaciones extends AppCompatActivity {

    private static final String TAG = "Notificaciones";

    // Formatos de fecha — confirmado en Logcat: "2026-02-27T16:11:42.943Z"
    private static final SimpleDateFormat SDF_ISO_Z  =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
    private static final SimpleDateFormat SDF_ISO    =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat SDF_MYSQL  =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private RecyclerView   rvNotificaciones;
    private ProgressBar    progressBar;
    private LinearLayout   layoutEmpty;
    private ImageView      btnBack;
    private MaterialButton btnMarcarTodas;

    private NotificacionesAdapter adapter;
    private final List<NotifItem> lista       = new ArrayList<>();
    private final Set<Long>       idsYaVistos = new HashSet<>();

    private SessionManager session;
    private int idUsuario;

    // ─── SoundPool ────────────────────────────────────────────────────────────
    private SoundPool soundPool;
    private int       soundId    = -1;
    private boolean   soundListo = false;

    // ═════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notificaciones);

        session   = new SessionManager(this);
        idUsuario = session.getIdUsuario();

        inicializarSonido();
        bindViews();
        configurarRecyclerView();
        configurarListeners();
        cargarNotificaciones();
    }

    @Override protected void onResume()  { super.onResume();  cargarNotificaciones(); }
    @Override protected void onDestroy() { super.onDestroy(); if (soundPool != null) { soundPool.release(); soundPool = null; } }

    // ═════════════════════════════════════════════════════════════════════════
    //  SONIDO
    //  El archivo res/raw/notificacion.mp3 ya fue colocado por el usuario.
    // ═════════════════════════════════════════════════════════════════════════

    private void inicializarSonido() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
        } else {
            soundPool = new SoundPool(2, AudioManager.STREAM_NOTIFICATION, 0);
        }
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            soundListo = (status == 0);
            Log.d(TAG, "Sonido listo: " + soundListo);
        });
        soundId = soundPool.load(this, R.raw.notificacion, 1);
    }

    private void reproducirSonido() {
        if (soundPool != null && soundListo && soundId > 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
            return;
        }
        // Fallback: vibración corta
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                else
                    v.vibrate(180);
            }
        } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BIND & CONFIG
    // ═════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        rvNotificaciones = findViewById(R.id.rv_notificaciones);
        progressBar      = findViewById(R.id.progress_notificaciones);
        layoutEmpty      = findViewById(R.id.layout_empty_notif);
        btnBack          = findViewById(R.id.btn_back);
        btnMarcarTodas   = findViewById(R.id.btn_marcar_todas);
    }

    private void configurarRecyclerView() {
        adapter = new NotificacionesAdapter(lista);
        rvNotificaciones.setLayoutManager(new LinearLayoutManager(this));
        rvNotificaciones.setAdapter(adapter);
        rvNotificaciones.setNestedScrollingEnabled(false);
    }

    private void configurarListeners() {
        if (btnBack != null)        btnBack.setOnClickListener(v -> onBackPressed());
        if (btnMarcarTodas != null) btnMarcarTodas.setOnClickListener(v -> marcarTodasLeidas());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGAR NOTIFICACIONES
    // ═════════════════════════════════════════════════════════════════════════

    private void cargarNotificaciones() {
        if (idUsuario <= 0) { mostrarVacio(); return; }
        if (progressBar      != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmpty      != null) layoutEmpty.setVisibility(View.GONE);
        if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.GONE);

        String url = Constantes.misNotificaciones(idUsuario);
        ConexionApi.getInstance(this).getArrayNoCache(url,
                this::procesarRespuesta,
                error -> ConexionApi.getInstance(this).getObjectNoCache(url,
                        response -> {
                            JSONArray arr = response.optJSONArray("items");
                            if (arr == null) arr = extraerArray(response);
                            if (arr != null) procesarRespuesta(arr);
                            else runOnUiThread(this::mostrarVacio);
                        },
                        err2 -> runOnUiThread(this::mostrarVacio)
                )
        );
    }

    private JSONArray extraerArray(JSONObject r) {
        if (r == null) return null;
        for (String k : new String[]{"content","notificaciones","data","items","results"})
            if (r.has(k)) return r.optJSONArray(k);
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PROCESAR RESPUESTA
    // ═════════════════════════════════════════════════════════════════════════

    private void procesarRespuesta(JSONArray response) {
        Set<Long> idsNuevos = new HashSet<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject obj = response.optJSONObject(i);
            if (obj == null) continue;
            long id = obj.optLong("idNotificacion", obj.optLong("id", -1));
            if (id != -1 && !idsYaVistos.contains(id)) idsNuevos.add(id);
        }

        lista.clear();
        for (int i = 0; i < response.length(); i++) {
            JSONObject obj = response.optJSONObject(i);
            if (obj == null) continue;

            NotifItem item     = new NotifItem();
            item.id            = obj.optLong("idNotificacion", obj.optLong("id", i));
            item.titulo        = obj.optString("titulo", "Notificación");
            item.mensaje       = obj.optString("mensaje", "");
            item.tipo          = obj.optString("tipo", "SISTEMA").toUpperCase();
            item.leido         = obj.optInt("leido", 0) == 1 || obj.optBoolean("leido", false);
            item.fechaCreacion = obj.optString("fechaCreacion",
                    obj.optString("createdAt",
                            obj.optString("fecha", "")));

            // 1. Intentar leer idReferencia directamente del JSON
            item.idReferencia = obj.optLong("idReferencia",
                    obj.optLong("idConversacion",
                            obj.optLong("idViaje",
                                    obj.optLong("idReserva", -1))));

            // 2. Intentar desde objeto anidado "data" / "extra"
            if (item.idReferencia == -1) {
                JSONObject data = obj.optJSONObject("data");
                if (data == null) data = obj.optJSONObject("extra");
                if (data != null)
                    item.idReferencia = data.optLong("idConversacion",
                            data.optLong("idViaje",
                                    data.optLong("id", -1)));
            }

            // 3. Fallback: intentar extraer ID del texto del mensaje
            if (item.idReferencia == -1)
                item.idReferencia = extraerIdDelMensaje(item.mensaje);

            Log.d(TAG, "Notif id=" + item.id + " tipo=" + item.tipo
                    + " idRef=" + item.idReferencia
                    + " fecha='" + item.fechaCreacion + "'"
                    + " → '" + formatearFecha(item.fechaCreacion) + "'");

            lista.add(item);
            idsYaVistos.add(item.id);
        }

        final boolean hayNuevas = !idsNuevos.isEmpty();
        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (lista.isEmpty()) { mostrarVacio(); return; }

            if (layoutEmpty      != null) layoutEmpty.setVisibility(View.GONE);
            if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();

            long noLeidas = 0;
            for (NotifItem n : lista) if (!n.leido) noLeidas++;
            if (btnMarcarTodas != null)
                btnMarcarTodas.setVisibility(noLeidas > 0 ? View.VISIBLE : View.GONE);

            if (hayNuevas) reproducirSonido();
        });
    }

    private void mostrarVacio() {
        if (progressBar      != null) progressBar.setVisibility(View.GONE);
        if (layoutEmpty      != null) layoutEmpty.setVisibility(View.VISIBLE);
        if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.GONE);
        if (btnMarcarTodas   != null) btnMarcarTodas.setVisibility(View.GONE);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN AL HACER CLICK
    //
    //  Con ID  → abre la pantalla exacta (chat específico / viaje específico)
    //  Sin ID  → abre la lista correspondiente (Conversaciones / MisViajes)
    //            así SIEMPRE hay una ventana a la que ir.
    //
    //  Chat.java recibe:              getLongExtra("idConversacion", -1)
    //  DetalleViajeActivity recibe:   getIntExtra("ID_VIAJE", 0)
    // ═════════════════════════════════════════════════════════════════════════

    private void abrirDestino(NotifItem item) {
        switch (item.tipo) {

            case "MENSAJE":
            case "CHAT": {
                if (item.idReferencia > 0) {
                    // ✅ Tenemos el id → abrir chat específico
                    Intent intent = new Intent(this, Chat.class);
                    intent.putExtra("idConversacion", item.idReferencia);
                    String nombre = extraerNombreDelMensaje(item.mensaje);
                    if (!nombre.isEmpty()) intent.putExtra("nombre", nombre);
                    startActivity(intent);
                } else {
                    // ⚠️ Sin id → abrir lista de conversaciones
                    Log.w(TAG, "MENSAJE sin idConversacion → abriendo Conversaciones");
                    startActivity(new Intent(this, Conversaciones.class));
                }
                break;
            }

            case "VIAJE":
            case "RESERVA": {
                if (item.idReferencia > 0) {
                    // ✅ Tenemos el id → abrir detalle del viaje específico
                    Intent intent = new Intent(this, DetalleViajeActivity.class);
                    intent.putExtra("ID_VIAJE", (int) item.idReferencia);
                    startActivity(intent);
                } else {
                    // ⚠️ Sin id → abrir lista de mis viajes
                    Log.w(TAG, "VIAJE/RESERVA sin idViaje → abriendo MisViajes");
                    startActivity(new Intent(this, MisViajesActivity.class));
                }
                break;
            }

            default:
                // SISTEMA, BIENVENIDA, CALIFICACION, PAGO → sin navegación
                Log.d(TAG, "Tipo sin navegación: " + item.tipo);
                break;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS DE EXTRACCIÓN
    // ═════════════════════════════════════════════════════════════════════════

    /** Intenta extraer un ID numérico del texto del mensaje como último recurso */
    private long extraerIdDelMensaje(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) return -1;
        Pattern[] patrones = {
                Pattern.compile("(?:viaje|trip)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:reserva|booking)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:conversaci[oó]n|chat)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bid\\s*[:=#]?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE),
        };
        for (Pattern p : patrones) {
            Matcher m = p.matcher(mensaje);
            if (m.find()) {
                try { return Long.parseLong(m.group(1)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    /** Extrae "Juan" de "Tienes un nuevo mensaje de Juan: ..." */
    private String extraerNombreDelMensaje(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) return "";
        Pattern p = Pattern.compile(
                "(?:mensaje de|message from)\\s+([A-ZÁÉÍÓÚÑa-záéíóúñ][a-záéíóúñA-ZÁÉÍÓÚÑ ]+?)(?:\\s*:|\")",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(mensaje);
        return m.find() ? m.group(1).trim() : "";
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MARCAR LEÍDAS
    // ═════════════════════════════════════════════════════════════════════════

    private void marcarTodasLeidas() {
        ConexionApi.getInstance(this).patch(
                Constantes.notificacionesMarcarTodas(idUsuario), new JSONObject(),
                r -> runOnUiThread(() -> {
                    for (NotifItem n : lista) n.leido = true;
                    adapter.notifyDataSetChanged();
                    if (btnMarcarTodas != null) btnMarcarTodas.setVisibility(View.GONE);
                }),
                e -> Log.e(TAG, "Error marcar todas leídas")
        );
    }

    private void marcarUnaLeida(NotifItem item, int position) {
        if (!item.leido) {
            ConexionApi.getInstance(this).patch(
                    Constantes.notificacionMarcarLeida(item.id), new JSONObject(),
                    r -> runOnUiThread(() -> {
                        item.leido = true;
                        adapter.notifyItemChanged(position);
                        long noLeidas = 0;
                        for (NotifItem n : lista) if (!n.leido) noLeidas++;
                        if (btnMarcarTodas != null && noLeidas == 0)
                            btnMarcarTodas.setVisibility(View.GONE);
                        abrirDestino(item);      // navegar DESPUÉS de marcar
                    }),
                    e -> abrirDestino(item)      // navegar igual aunque falle el PATCH
            );
        } else {
            abrirDestino(item);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODELO
    // ═════════════════════════════════════════════════════════════════════════

    static class NotifItem {
        long    id;
        long    idReferencia = -1;
        String  titulo, mensaje, tipo, fechaCreacion;
        boolean leido;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ═════════════════════════════════════════════════════════════════════════

    class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.VH> {

        private final List<NotifItem> items;
        NotificacionesAdapter(List<NotifItem> items) { this.items = items; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_notificacion, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            NotifItem item = items.get(position);

            holder.tvTitulo.setText(item.titulo);
            holder.tvMensaje.setText(item.mensaje);
            holder.tvFecha.setText(formatearFecha(item.fechaCreacion));
            holder.tvTipo.setText(obtenerEmojiTipo(item.tipo) + " " + item.tipo);

            if (!item.leido) {
                holder.card.setCardBackgroundColor(0xFFFFFFFF);
                holder.card.setStrokeColor(0xFF2EC4B6);
                holder.card.setStrokeWidth(3);
                holder.puntito.setVisibility(View.VISIBLE);
                holder.tvTitulo.setTextColor(0xFF1A1A1A);
            } else {
                holder.card.setCardBackgroundColor(0xFFF7F7F7);
                holder.card.setStrokeWidth(0);
                holder.puntito.setVisibility(View.GONE);
                holder.tvTitulo.setTextColor(0xFF888888);
            }

            holder.card.setOnClickListener(v ->
                    marcarUnaLeida(item, holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView tvTitulo, tvMensaje, tvFecha, tvTipo;
            View     puntito;
            VH(View v) {
                super(v);
                card      = v.findViewById(R.id.card_notif);
                tvTitulo  = v.findViewById(R.id.tv_notif_titulo);
                tvMensaje = v.findViewById(R.id.tv_notif_mensaje);
                tvFecha   = v.findViewById(R.id.tv_notif_fecha);
                tvTipo    = v.findViewById(R.id.tv_notif_tipo);
                puntito   = v.findViewById(R.id.view_puntito);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FORMATO DE FECHA — "Hoy · 16:11" / "Ayer · 09:00" / "Lunes · 14:30"
    // ═════════════════════════════════════════════════════════════════════════

    private String formatearFecha(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Date date = parsearFecha(raw);
        if (date == null) {
            // Extraer al menos la hora del string: "2026-02-27T16:11:..."
            try { return raw.substring(11, 16); } catch (Exception ignored) {}
            return raw;
        }

        Locale esCol = new Locale("es", "CO");
        String hora  = new SimpleDateFormat("HH:mm", esCol).format(date);

        Calendar hoy  = Calendar.getInstance();
        Calendar ayer = Calendar.getInstance();
        ayer.add(Calendar.DAY_OF_YEAR, -1);
        Calendar fc = Calendar.getInstance();
        fc.setTime(date);

        if (mismodia(hoy,  fc)) return "Hoy · " + hora;
        if (mismodia(ayer, fc)) return "Ayer · " + hora;

        long dias = (hoy.getTimeInMillis() - fc.getTimeInMillis()) / 86_400_000L;
        if (dias < 7) {
            String dia = new SimpleDateFormat("EEEE", esCol).format(date);
            return dia.substring(0, 1).toUpperCase() + dia.substring(1) + " · " + hora;
        }
        return new SimpleDateFormat("dd MMM yyyy", esCol).format(date) + " · " + hora;
    }

    private Date parsearFecha(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { synchronized (SDF_ISO_Z) { return SDF_ISO_Z.parse(raw); } } catch (ParseException ignored) {}
        try { synchronized (SDF_ISO)   { return SDF_ISO.parse(raw);   } } catch (ParseException ignored) {}
        try { synchronized (SDF_MYSQL) { return SDF_MYSQL.parse(raw); } } catch (ParseException ignored) {}
        try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(raw); } catch (Exception ignored) {}
        return null;
    }

    private boolean mismodia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String obtenerEmojiTipo(String tipo) {
        if (tipo == null) return "🔔";
        switch (tipo.toUpperCase()) {
            case "VIAJE":        return "🚗";
            case "RESERVA":      return "📋";
            case "PAGO":         return "💳";
            case "CHAT":
            case "MENSAJE":      return "💬";
            case "SISTEMA":      return "⚙️";
            case "BIENVENIDA":   return "👋";
            case "CALIFICACION": return "⭐";
            default:             return "🔔";
        }
    }
}