package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Notificaciones extends BaseActivity {

    private static final String TAG = "Notificaciones";

    private static final SimpleDateFormat SDF_ISO_Z =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
    private static final SimpleDateFormat SDF_ISO =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat SDF_MYSQL =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private RecyclerView   rvNotificaciones;
    private ProgressBar    progressBar;
    private LinearLayout   layoutEmpty;
    private MaterialButton btnMarcarTodas;

    // ── Permiso POST_NOTIFICATIONS (Android 13 / API 33+) ────────────────────
    // Registrado ANTES de onCreate usando el contrato de Activity Result API
    private final ActivityResultLauncher<String> permisoPushLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            Log.d(TAG, "✅ Permiso POST_NOTIFICATIONS concedido");
                            refrescarTokenFCM();
                        } else {
                            Log.w(TAG, "⚠️ Permiso POST_NOTIFICATIONS denegado por el usuario");
                            android.widget.Toast.makeText(this,
                                    "Activa las notificaciones en Ajustes para recibir avisos",
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });

    private BroadcastReceiver notifReceiver;

    private NotificacionesAdapter adapter;
    private final List<NotifItem> lista             = new ArrayList<>();
    private final Set<Long>       idsYaVistos       = new HashSet<>();
    private boolean               primerasCargaCompleta = false;

    private SessionManager session;
    private int            idUsuario;

    private SoundPool soundPool;
    private int       soundId    = -1;
    private boolean   soundListo = false;

    // ═══════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_notificaciones);

        session   = new SessionManager(this);
        idUsuario = session.getIdUsuario();

        inicializarSonido();
        bindViews();
        configurarRecyclerView();
        configurarListeners();

        // ── PASO CLAVE: pedir permiso + refrescar token FCM ─────────────────
        solicitarPermisoNotificaciones();

        // Receptor broadcast: recarga la lista cuando llega FCM con la pantalla abierta
        notifReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                cargarNotificaciones();
                reproducirSonido();
            }
        };

        cargarNotificaciones();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                notifReceiver,
                new IntentFilter(MyFirebaseMessagingService.ACTION_NUEVA_NOTIF));
        cargarNotificaciones();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notifReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) { soundPool.release(); soundPool = null; }
    }

    // ═══════════════════════════════════════════════════════
    //  PERMISO POST_NOTIFICATIONS — Android 13+ (API 33)
    //  Sin este permiso, ninguna notificación llega al celular
    // ═══════════════════════════════════════════════════════

    private void solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: hay que pedir el permiso explícitamente
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permisoPushLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Ya tiene permiso → sólo refrescamos el token
                refrescarTokenFCM();
            }
        } else {
            // Android 12 e inferior: el permiso es automático, sólo refrescamos token
            refrescarTokenFCM();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  REFRESCAR TOKEN FCM Y ENVIARLO AL BACKEND
    //  Garantiza que CUALQUIER dispositivo reciba las push
    // ═══════════════════════════════════════════════════════

    private void refrescarTokenFCM() {
        if (idUsuario <= 0) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d(TAG, "🔑 Token FCM: " + token);
                    enviarTokenAlBackend(token);
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "⚠️ No se pudo obtener token FCM: " + e.getMessage()));
    }

    private void enviarTokenAlBackend(String token) {
        if (token == null || token.isEmpty()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("token",     token);
            body.put("idUsuario", idUsuario);
            ConexionApi.getInstance(this).post(
                    Constantes.BASE_URL + "/api/usuarios/" + idUsuario + "/fcm-token",
                    body,
                    response -> Log.d(TAG, "✅ Token FCM guardado en backend"),
                    error    -> Log.w(TAG, "⚠️ Error guardando token: " + error.toString())
            );
        } catch (Exception e) {
            Log.e(TAG, "❌ Error preparando token: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  SONIDO / VIBRACIÓN
    // ═══════════════════════════════════════════════════════

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
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> soundListo = (status == 0));
        soundId = soundPool.load(this, R.raw.notificacion, 1);
    }

    private void reproducirSonido() {
        if (soundPool != null && soundListo && soundId > 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
            return;
        }
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(android.os.VibrationEffect.createOneShot(200,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                else
                    v.vibrate(200);
            }
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════
    //  BIND & CONFIG
    // ═══════════════════════════════════════════════════════

    private void bindViews() {
        rvNotificaciones = findViewById(R.id.rv_notificaciones);
        progressBar      = findViewById(R.id.progress_notificaciones);
        layoutEmpty      = findViewById(R.id.layout_empty_notif);
        View btnBackView = findViewById(R.id.btn_back);
        if (btnBackView != null) btnBackView.setOnClickListener(v -> onBackPressed());
        btnMarcarTodas = findViewById(R.id.btn_marcar_todas);
    }

    private void configurarRecyclerView() {
        adapter = new NotificacionesAdapter(lista);
        rvNotificaciones.setLayoutManager(new LinearLayoutManager(this));
        rvNotificaciones.setAdapter(adapter);
        rvNotificaciones.setNestedScrollingEnabled(false);
    }

    private void configurarListeners() {
        if (btnMarcarTodas != null)
            btnMarcarTodas.setOnClickListener(v -> marcarTodasLeidas());
    }

    // ═══════════════════════════════════════════════════════
    //  CARGAR NOTIFICACIONES
    // ═══════════════════════════════════════════════════════

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
        for (String k : new String[]{"content", "notificaciones", "data", "items", "results"})
            if (r.has(k)) return r.optJSONArray(k);
        return null;
    }

    // ═══════════════════════════════════════════════════════
    //  PROCESAR — más reciente PRIMERO
    // ═══════════════════════════════════════════════════════

    private void procesarRespuesta(JSONArray response) {
        Set<Long> idsNuevos = new HashSet<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject obj = response.optJSONObject(i);
            if (obj == null) continue;
            long id = obj.optLong("idNotificacion", obj.optLong("id", -1));
            if (id != -1 && !idsYaVistos.contains(id) && primerasCargaCompleta)
                idsNuevos.add(id);
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
                    obj.optString("createdAt", obj.optString("fecha", "")));

            item.idReferencia = obj.optLong("idReferencia", -1);
            if (item.idReferencia == -1) item.idReferencia = obj.optLong("idConversacion",  -1);
            if (item.idReferencia == -1) item.idReferencia = obj.optLong("conversacionId",  -1);
            if (item.idReferencia == -1) item.idReferencia = obj.optLong("idViaje",         -1);
            if (item.idReferencia == -1) item.idReferencia = obj.optLong("idReserva",       -1);
            if (item.idReferencia == -1) {
                for (String key : new String[]{"data", "extra", "metadata", "payload"}) {
                    JSONObject nested = obj.optJSONObject(key);
                    if (nested == null) continue;
                    item.idReferencia = nested.optLong("idConversacion", nested.optLong("id", -1));
                    if (item.idReferencia != -1) break;
                }
            }
            if (item.idReferencia == -1)
                item.idReferencia = extraerIdDelMensaje(item.mensaje);

            lista.add(item);
            idsYaVistos.add(item.id);
        }

        lista.sort((a, b) -> {
            Date da = parsearFecha(a.fechaCreacion);
            Date db = parsearFecha(b.fechaCreacion);
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        final boolean hayNuevas = !idsNuevos.isEmpty();
        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (lista.isEmpty()) { cargarPagosPendientesComoNotificaciones(); return; }
            if (layoutEmpty      != null) layoutEmpty.setVisibility(View.GONE);
            if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            rvNotificaciones.scrollToPosition(0);
            actualizarBotonMarcarTodas();
            if (hayNuevas) reproducirSonido();
            primerasCargaCompleta = true;
            cargarPagosPendientesComoNotificaciones();
        });
    }

    // ═══════════════════════════════════════════════════════
    //  PAGOS PENDIENTES COMO NOTIFICACIONES
    // ═══════════════════════════════════════════════════════

    private void cargarPagosPendientesComoNotificaciones() {
        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.MIS_RESERVAS,
                response -> {
                    List<NotifItem> pagoItems       = new ArrayList<>();
                    Set<Integer>    viajesAgregados = new HashSet<>();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject reserva = response.optJSONObject(i);
                        if (reserva == null) continue;
                        JSONObject viaje = reserva.optJSONObject("viaje");
                        if (viaje == null) continue;
                        String estViaje = viaje.optString("estado", "").toUpperCase().trim();
                        if (!"FINALIZADO".equals(estViaje) && !"COMPLETADO".equals(estViaje)) continue;

                        int viajeId = reserva.optInt("idViajes", 0);
                        if (viajeId == 0)
                            viajeId = viaje.optInt("idViajes", viaje.optInt("id", 0));
                        if (viajeId == 0) continue;
                        if (viajesAgregados.contains(viajeId)) continue;
                        viajesAgregados.add(viajeId);

                        boolean yaTiene = false;
                        for (NotifItem n : lista) if (n.id == -(long) viajeId) { yaTiene = true; break; }
                        if (yaTiene) continue;

                        double     precio   = viaje.optDouble("precio", 0);
                        String     destino  = "";
                        JSONObject ruta     = viaje.optJSONObject("ruta");
                        if (ruta != null)
                            destino = ruta.optString("destino", ruta.optString("nombre", ""));

                        String     nomConductor = "";
                        JSONObject conductor    = viaje.optJSONObject("conductor");
                        if (conductor != null) {
                            for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                                String val = conductor.optString(k, "");
                                if (!val.isEmpty() && !val.equals("null")) { nomConductor = val; break; }
                            }
                        }

                        NotifItem item    = new NotifItem();
                        item.id           = -(long) viajeId;
                        item.tipo         = "PAGO";
                        item.leido        = false;
                        item.idReferencia = viajeId;
                        item.titulo       = nomConductor.isEmpty()
                                ? "Pago pendiente" : "Pago pendiente a " + nomConductor;

                        StringBuilder msg = new StringBuilder();
                        if (!destino.isEmpty()) msg.append("Destino: ").append(destino);
                        if (precio > 0) {
                            if (msg.length() > 0) msg.append("  •  ");
                            msg.append("$")
                                    .append(String.format(Locale.getDefault(), "%,.0f", precio))
                                    .append(" COP");
                        }
                        if (msg.length() == 0) msg.append("Tienes un viaje sin pagar");
                        item.mensaje       = msg.toString();
                        item.fechaCreacion = viaje.optString("fechaHoraSalida",
                                viaje.optString("fechaSalida", ""));
                        pagoItems.add(item);
                    }

                    if (!pagoItems.isEmpty()) {
                        pagoItems.sort((a, b) -> {
                            Date da = parsearFecha(a.fechaCreacion);
                            Date db = parsearFecha(b.fechaCreacion);
                            if (da == null && db == null) return 0;
                            if (da == null) return 1;
                            if (db == null) return -1;
                            return db.compareTo(da);
                        });
                        runOnUiThread(() -> {
                            lista.addAll(0, pagoItems);
                            adapter.notifyDataSetChanged();
                            rvNotificaciones.scrollToPosition(0);
                            if (layoutEmpty      != null) layoutEmpty.setVisibility(View.GONE);
                            if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.VISIBLE);
                            if (btnMarcarTodas   != null) btnMarcarTodas.setVisibility(View.VISIBLE);
                        });
                    } else if (lista.isEmpty()) {
                        runOnUiThread(this::mostrarVacio);
                    }
                },
                error -> {
                    Log.w(TAG, "cargarPagosPendientesComoNotificaciones error");
                    if (lista.isEmpty()) runOnUiThread(this::mostrarVacio);
                }
        );
    }

    private void mostrarVacio() {
        if (progressBar      != null) progressBar.setVisibility(View.GONE);
        if (layoutEmpty      != null) layoutEmpty.setVisibility(View.VISIBLE);
        if (rvNotificaciones != null) rvNotificaciones.setVisibility(View.GONE);
        if (btnMarcarTodas   != null) btnMarcarTodas.setVisibility(View.GONE);
    }

    private void actualizarBotonMarcarTodas() {
        if (btnMarcarTodas == null) return;
        long noLeidas = 0;
        for (NotifItem n : lista) if (!n.leido) noLeidas++;
        btnMarcarTodas.setVisibility(noLeidas > 0 ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════════
    //  BOTTOM SHEET
    // ═══════════════════════════════════════════════════════

    private void mostrarOpcionesBottomSheet(NotifItem item, int position) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetTheme);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_notif_opciones, null);
        dialog.setContentView(sheetView);

        MaterialCardView bsCardAvatar = sheetView.findViewById(R.id.bs_card_avatar);
        TextView         bsTvInicial  = sheetView.findViewById(R.id.bs_tv_inicial);
        TextView         bsTvTitulo   = sheetView.findViewById(R.id.bs_tv_titulo);

        String nombre    = extraerNombreDelMensaje(item.mensaje);
        String contenido = extraerContenidoMensaje(item.mensaje);
        String inicial   = !nombre.isEmpty()
                ? String.valueOf(nombre.charAt(0)).toUpperCase() : inicialPorTipo(item.tipo);
        bsTvInicial.setText(inicial);
        bsCardAvatar.setCardBackgroundColor(colorPorTipo(item.tipo));
        bsTvTitulo.setText(construirTextoFacebook(item.tipo, nombre, contenido, item.titulo));

        // Opción 1: Marcar leída / no leída
        LinearLayout opLeida    = sheetView.findViewById(R.id.bs_opcion_leida);
        ImageView    iconLeida  = sheetView.findViewById(R.id.bs_icon_leida);
        TextView     textoLeida = sheetView.findViewById(R.id.bs_texto_leida);
        if (item.leido) {
            textoLeida.setText("Marcar como no leída");
            iconLeida.setImageResource(R.drawable.ic_notifications);
        } else {
            textoLeida.setText("Marcar como leída");
            iconLeida.setImageResource(R.drawable.ic_check_circle);
        }
        opLeida.setOnClickListener(v -> {
            dialog.dismiss();
            if (!item.leido) {
                if (item.id < 0) {
                    item.leido = true;
                    adapter.notifyItemChanged(position);
                    actualizarBotonMarcarTodas();
                    return;
                }
                ConexionApi.getInstance(this).patch(
                        Constantes.notificacionMarcarLeida(item.id), new JSONObject(),
                        r -> runOnUiThread(() -> {
                            item.leido = true;
                            adapter.notifyItemChanged(position);
                            actualizarBotonMarcarTodas();
                        }),
                        e -> Log.e(TAG, "Error al marcar leída")
                );
            } else {
                item.leido = false;
                adapter.notifyItemChanged(position);
                if (btnMarcarTodas != null) btnMarcarTodas.setVisibility(View.VISIBLE);
            }
        });

        // Opción 2: Abrir destino
        LinearLayout opAbrir    = sheetView.findViewById(R.id.bs_opcion_abrir);
        ImageView    iconAbrir  = sheetView.findViewById(R.id.bs_icon_abrir);
        TextView     textoAbrir = sheetView.findViewById(R.id.bs_texto_abrir);
        switch (item.tipo) {
            case "MENSAJE": case "CHAT":
                iconAbrir.setImageResource(R.drawable.ic_chat);       textoAbrir.setText("Ir al chat");   break;
            case "VIAJE":
                iconAbrir.setImageResource(R.drawable.ic_directions_car); textoAbrir.setText("Ver viaje");  break;
            case "RESERVA":
                iconAbrir.setImageResource(R.drawable.ic_description); textoAbrir.setText("Ver reserva"); break;
            case "PAGO":
                iconAbrir.setImageResource(R.drawable.ic_credit_card); textoAbrir.setText("Ir a pagar");  break;
            default:
                iconAbrir.setImageResource(R.drawable.ic_info);        textoAbrir.setText("Ver detalles");
        }
        opAbrir.setOnClickListener(v -> {
            dialog.dismiss();
            if (!item.leido && item.id > 0) {
                ConexionApi.getInstance(this).patch(
                        Constantes.notificacionMarcarLeida(item.id), new JSONObject(),
                        r -> runOnUiThread(() -> { item.leido = true; adapter.notifyItemChanged(position); }),
                        e -> {}
                );
            } else { item.leido = true; adapter.notifyItemChanged(position); }
            abrirDestino(item);
        });

        // Opción 3: Eliminar
        sheetView.findViewById(R.id.bs_opcion_eliminar)
                .setOnClickListener(v -> { dialog.dismiss(); eliminarNotificacion(item, position); });

        // Opción 4: Desactivar tipo
        LinearLayout opDesactivar       = sheetView.findViewById(R.id.bs_opcion_desactivar);
        TextView     textoDesactivar    = sheetView.findViewById(R.id.bs_texto_desactivar);
        TextView     subtextoDesactivar = sheetView.findViewById(R.id.bs_subtexto_desactivar);
        String tipoLabel = obtenerLabelTipo(item.tipo);
        textoDesactivar.setText("Desactivar notificaciones de " + tipoLabel);
        subtextoDesactivar.setText("Ya no recibirás avisos de este tipo");
        opDesactivar.setOnClickListener(v -> {
            dialog.dismiss();
            for (int i2 = lista.size() - 1; i2 >= 0; i2--) {
                if (lista.get(i2).tipo.equals(item.tipo)) {
                    lista.remove(i2);
                    adapter.notifyItemRemoved(i2);
                }
            }
            adapter.notifyItemRangeChanged(0, lista.size());
            if (lista.isEmpty()) mostrarVacio();
            android.widget.Toast.makeText(this,
                    "Notificaciones de " + tipoLabel + " ocultadas",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private String obtenerLabelTipo(String tipo) {
        switch (tipo) {
            case "MENSAJE": case "CHAT": return "mensajes";
            case "VIAJE":                return "viajes";
            case "RESERVA":              return "reservas";
            case "PAGO":                 return "pagos";
            default:                     return "este tipo";
        }
    }

    private int colorPorTipo(String tipo) {
        switch (tipo) {
            case "MENSAJE": case "CHAT": return 0xFF0A7A72;
            case "VIAJE":                return 0xFF006064;
            case "RESERVA":              return 0xFFE65100;
            case "PAGO":                 return 0xFFEF5350;
            case "BIENVENIDA":           return 0xFF880E4F;
            default:                     return 0xFF4A148C;
        }
    }

    private String inicialPorTipo(String tipo) {
        switch (tipo) {
            case "VIAJE":      return "V";
            case "RESERVA":    return "R";
            case "PAGO":       return "$";
            case "BIENVENIDA": return "B";
            default:           return "M";
        }
    }

    // ═══════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ═══════════════════════════════════════════════════════

    private void abrirDestino(NotifItem item) {
        switch (item.tipo) {
            case "MENSAJE": case "CHAT":
                if (item.idReferencia > 0)
                    abrirChatPorId(item.idReferencia, extraerNombreDelMensaje(item.mensaje));
                else
                    buscarConversacionPorConductorYAbrir(extraerNombreDelMensaje(item.mensaje));
                break;
            case "PAGO":
                if (item.idReferencia > 0) {
                    Intent i = new Intent(this, DetalleViajeActivity.class);
                    i.putExtra("ID_VIAJE", (int) item.idReferencia);
                    startActivity(i);
                } else startActivity(new Intent(this, MisReservasActivity.class));
                break;
            case "VIAJE": case "RESERVA":
                if (item.idReferencia > 0) {
                    Intent i = new Intent(this, DetalleViajeActivity.class);
                    i.putExtra("ID_VIAJE", (int) item.idReferencia);
                    startActivity(i);
                } else startActivity(new Intent(this, MisViajesActivity.class));
                break;
            default:
                Log.d(TAG, "Tipo sin navegación: " + item.tipo);
        }
    }

    private void abrirChatPorId(long idConversacion, String nombre) {
        Intent intent = new Intent(this, Chat.class);
        intent.putExtra("idConversacion", idConversacion);
        if (nombre != null && !nombre.isEmpty()) intent.putExtra("nombre", nombre);
        startActivity(intent);
    }

    private void buscarConversacionPorConductorYAbrir(String nombreBuscado) {
        if (nombreBuscado == null || nombreBuscado.isEmpty()) {
            startActivity(new Intent(this, Conversaciones.class));
            return;
        }
        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.CHAT_CONVERSACIONES,
                conversaciones -> {
                    long   idConvEncontrada = -1;
                    String nombreFinal      = nombreBuscado;
                    for (int i = 0; i < conversaciones.length(); i++) {
                        JSONObject conv      = conversaciones.optJSONObject(i);
                        if (conv == null) continue;
                        JSONObject conductor = conv.optJSONObject("conductor");
                        JSONObject pasajero  = conv.optJSONObject("pasajero");
                        String nc = conductor != null ? conductor.optString("nombre", "") : "";
                        String np = pasajero  != null ? pasajero.optString("nombre",  "") : "";
                        String nb = nombreBuscado.trim().toLowerCase();
                        if (!nc.isEmpty() && nc.trim().toLowerCase().contains(nb)) {
                            idConvEncontrada = conv.optLong("idConversacion", -1); nombreFinal = nc; break;
                        }
                        if (!np.isEmpty() && np.trim().toLowerCase().contains(nb)) {
                            idConvEncontrada = conv.optLong("idConversacion", -1); nombreFinal = np; break;
                        }
                    }
                    final long   idF = idConvEncontrada;
                    final String nF  = nombreFinal;
                    runOnUiThread(() -> {
                        if (idF > 0) abrirChatPorId(idF, nF);
                        else startActivity(new Intent(this, Conversaciones.class));
                    });
                },
                error -> runOnUiThread(() -> startActivity(new Intent(this, Conversaciones.class)))
        );
    }

    // ═══════════════════════════════════════════════════════
    //  MARCAR LEÍDAS
    // ═══════════════════════════════════════════════════════

    private void marcarTodasLeidas() {
        for (NotifItem n : lista) if (n.id < 0) n.leido = true;
        ConexionApi.getInstance(this).patch(
                Constantes.notificacionesMarcarTodas(idUsuario), new JSONObject(),
                r -> runOnUiThread(() -> {
                    for (NotifItem n : lista) n.leido = true;
                    adapter.notifyDataSetChanged();
                    if (btnMarcarTodas != null) btnMarcarTodas.setVisibility(View.GONE);
                }),
                e -> runOnUiThread(() -> {
                    for (NotifItem n : lista) n.leido = true;
                    adapter.notifyDataSetChanged();
                    if (btnMarcarTodas != null) btnMarcarTodas.setVisibility(View.GONE);
                    Log.e(TAG, "Error marcar todas");
                })
        );
    }

    private void marcarUnaLeida(NotifItem item, int position) {
        if (!item.leido) {
            if (item.id < 0) {
                item.leido = true;
                adapter.notifyItemChanged(position);
                actualizarBotonMarcarTodas();
                abrirDestino(item);
                return;
            }
            ConexionApi.getInstance(this).patch(
                    Constantes.notificacionMarcarLeida(item.id), new JSONObject(),
                    r -> runOnUiThread(() -> {
                        item.leido = true;
                        adapter.notifyItemChanged(position);
                        actualizarBotonMarcarTodas();
                        abrirDestino(item);
                    }),
                    e -> abrirDestino(item)
            );
        } else {
            abrirDestino(item);
        }
    }

    private void eliminarNotificacion(NotifItem item, int position) {
        if (item.id < 0) {
            if (position < lista.size()) {
                lista.remove(position);
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, lista.size());
                if (lista.isEmpty()) mostrarVacio();
            }
            return;
        }
        ConexionApi.getInstance(this).delete(
                Constantes.BASE_URL + "/api/notificaciones/" + item.id,
                r -> runOnUiThread(() -> {
                    if (position < lista.size()) {
                        lista.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, lista.size());
                        if (lista.isEmpty()) mostrarVacio();
                    }
                }),
                e -> runOnUiThread(() -> {
                    if (position < lista.size()) {
                        lista.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, lista.size());
                        if (lista.isEmpty()) mostrarVacio();
                    }
                })
        );
    }

    // ═══════════════════════════════════════════════════════
    //  MODELO
    // ═══════════════════════════════════════════════════════

    static class NotifItem {
        long    id;
        long    idReferencia = -1;
        String  titulo, mensaje, tipo, fechaCreacion;
        boolean leido;
    }

    // ═══════════════════════════════════════════════════════
    //  ADAPTER
    // ═══════════════════════════════════════════════════════

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
            NotifItem item     = items.get(position);
            String    nombre   = extraerNombreDelMensaje(item.mensaje);
            String    textoMsg = extraerContenidoMensaje(item.mensaje);
            holder.tvTexto.setText(construirTextoFacebook(item.tipo, nombre, textoMsg, item.titulo));
            holder.tvFecha.setText(formatearFecha(item.fechaCreacion));

            String inicial = !nombre.isEmpty()
                    ? String.valueOf(nombre.charAt(0)).toUpperCase() : inicialPorTipo(item.tipo);
            holder.tvInicial.setText(inicial);
            holder.cardAvatar.setCardBackgroundColor(colorPorTipo(item.tipo));
            holder.ivBadge.setImageResource(iconoPorTipo(item.tipo));
            holder.cardBadge.setCardBackgroundColor(colorPorTipo(item.tipo));

            if (!item.leido) {
                holder.itemRoot.setBackgroundColor(0xFFE8F8F7);
                holder.tvFecha.setTextColor(0xFF0A8A81);
                holder.viewDot.setVisibility(View.VISIBLE);
            } else {
                holder.itemRoot.setBackgroundColor(0xFFFFFFFF);
                holder.tvFecha.setTextColor(0xFF65676B);
                holder.viewDot.setVisibility(View.GONE);
            }

            holder.itemRoot.setAlpha(0f);
            holder.itemRoot.setTranslationY(10f);
            holder.itemRoot.animate()
                    .alpha(1f).translationY(0f).setDuration(220)
                    .setStartDelay(position * 30L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();

            holder.itemRoot.setOnClickListener(v -> marcarUnaLeida(item, holder.getAdapterPosition()));
            holder.btnMore.setOnClickListener(v -> mostrarOpcionesBottomSheet(item, holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return items.size(); }

        private int iconoPorTipo(String tipo) {
            switch (tipo) {
                case "MENSAJE": case "CHAT": return R.drawable.ic_chat;
                case "VIAJE":                return R.drawable.ic_directions_car;
                case "RESERVA":              return R.drawable.ic_description;
                case "PAGO":                 return R.drawable.ic_credit_card;
                case "BIENVENIDA":           return R.drawable.ic_waving_hand;
                default:                     return R.drawable.ic_info;
            }
        }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout     itemRoot;
            MaterialCardView cardAvatar, cardBadge;
            TextView         tvInicial, tvTexto, tvFecha;
            ImageView        ivBadge, btnMore;
            View             viewDot;

            VH(View v) {
                super(v);
                itemRoot   = v.findViewById(R.id.item_root);
                cardAvatar = v.findViewById(R.id.card_avatar);
                cardBadge  = v.findViewById(R.id.card_badge);
                tvInicial  = v.findViewById(R.id.tv_inicial);
                tvTexto    = v.findViewById(R.id.tv_notif_texto);
                tvFecha    = v.findViewById(R.id.tv_notif_fecha);
                ivBadge    = v.findViewById(R.id.iv_badge_icon);
                btnMore    = v.findViewById(R.id.btn_more_notif);
                viewDot    = v.findViewById(R.id.view_dot);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS DE TEXTO
    // ═══════════════════════════════════════════════════════

    private String extraerNombreDelMensaje(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) return "";
        Pattern p = Pattern.compile(
                "(?:mensaje de|message from)\\s+([A-ZÁÉÍÓÚÑa-záéíóúñ][A-ZÁÉÍÓÚÑa-záéíóúñ ]{0,40}?)(?:\\s*[:\"])",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(mensaje);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extraerContenidoMensaje(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) return "";
        Pattern p = Pattern.compile("[\"\u201C\u201D]([^\"\u201C\u201D]+)[\"\u201C\u201D]\\s*$");
        Matcher m = p.matcher(mensaje);
        if (m.find()) return m.group(1).trim();
        int idx = mensaje.lastIndexOf(':');
        if (idx != -1 && idx < mensaje.length() - 1)
            return mensaje.substring(idx + 1).replaceAll("[\"\u201C\u201D\\s]", "").trim();
        return "";
    }

    private long extraerIdDelMensaje(String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) return -1;
        Pattern[] patrones = {
                Pattern.compile("(?:viaje|trip)\\s*#?\\s*(\\d+)",            Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:reserva|booking)\\s*#?\\s*(\\d+)",       Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:conversaci[oó]n|chat)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bid\\s*[:=#]?\\s*(\\d+)\\b",              Pattern.CASE_INSENSITIVE),
        };
        for (Pattern p : patrones) {
            Matcher m = p.matcher(mensaje);
            if (m.find()) {
                try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    private SpannableString construirTextoFacebook(String tipo, String nombre,
                                                   String contenido, String tituloOriginal) {
        String texto;
        switch (tipo) {
            case "MENSAJE": case "CHAT":
                if (!nombre.isEmpty() && !contenido.isEmpty())
                    texto = nombre + " te envió un mensaje: \"" + contenido + "\"";
                else if (!nombre.isEmpty())
                    texto = nombre + " te envió un mensaje.";
                else
                    texto = tituloOriginal;
                break;
            case "VIAJE":      texto = tituloOriginal.isEmpty() ? "Tu viaje fue actualizado."   : tituloOriginal; break;
            case "RESERVA":    texto = tituloOriginal.isEmpty() ? "Tu reserva fue actualizada." : tituloOriginal; break;
            case "PAGO":       texto = tituloOriginal.isEmpty() ? "Tienes un pago pendiente."   : tituloOriginal; break;
            case "BIENVENIDA": texto = "¡Bienvenido a MoviFlexx! Encuentra tu próximo viaje.";  break;
            default:           texto = tituloOriginal;
        }
        SpannableString ss = new SpannableString(texto);
        if (!nombre.isEmpty() && texto.startsWith(nombre))
            ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    0, nombre.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    // ═══════════════════════════════════════════════════════
    //  FECHA RELATIVA
    // ═══════════════════════════════════════════════════════

    private String formatearFecha(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Date date = parsearFecha(raw);
        if (date == null) {
            try { return raw.substring(11, 16); } catch (Exception ignored) {}
            return raw;
        }
        long diff = System.currentTimeMillis() - date.getTime();
        long seg  = diff / 1000;
        long min  = seg  / 60;
        long hora = min  / 60;
        long dias = hora / 24;
        long sem  = dias / 7;
        long mes  = dias / 30;
        long anio = dias / 365;
        if (seg  < 60)  return "hace un momento";
        if (min  < 60)  return "hace " + min  + " min";
        if (hora < 24)  return "hace " + hora + (hora == 1 ? " hora"  : " horas");
        if (dias < 7)   return "hace " + dias + (dias == 1 ? " día"   : " días");
        if (sem  < 4)   return "hace " + sem  + " sem";
        if (mes  < 12)  return "hace " + mes  + (mes  == 1 ? " mes"   : " meses");
        return "hace " + anio + (anio == 1 ? " año" : " años");
    }

    private Date parsearFecha(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { synchronized (SDF_ISO_Z) { return SDF_ISO_Z.parse(raw); } } catch (ParseException ignored) {}
        try { synchronized (SDF_ISO)   { return SDF_ISO.parse(raw);   } } catch (ParseException ignored) {}
        try { synchronized (SDF_MYSQL) { return SDF_MYSQL.parse(raw); } } catch (ParseException ignored) {}
        try { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.getDefault()).parse(raw); } catch (Exception ignored) {}
        return null;
    }
}