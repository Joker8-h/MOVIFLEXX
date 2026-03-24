package com.arlys.moviflexx.controller;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.VolleyError;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Chat extends BaseActivity {

    private static final String TAG                    = "CHAT";
    private static final long   POLL_INTERVAL          = 2500L;
    private static final long   ONLINE_TIMEOUT_MS      = 5 * 60 * 1000L;
    private static final int    MAX_FALLOS_PARA_OFFLINE = 2;
    private static final long   TYPING_DEBOUNCE_MS     = 2000L;
    private static final long   TYPING_POLL_MS         = 2000L;

    private static final String[] SUGERENCIAS_PASAJERO = {
            "¡Hola! ¿Ya saliste hacia el punto de recogida?",
            "¿Dónde exactamente me recoges?",
            "¿Cuánto tardas en llegar?",
            "¿Cuántos pasajeros van en el viaje?",
            "¿Puedes recogerme en otro punto?",
            "¿El precio incluye el trayecto completo?",
            "¿Puedo llevar equipaje?",
            "Avísame cuando estés cerca, por favor",
            "¿Cuál es la ruta que tomarás?",
            "Perfecto, te espero en el punto acordado"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView      rvMensajes;
    private MensajeAdapter    adapter;
    private TextInputEditText etMensaje;
    private ImageButton       btnEnviar;
    private ImageButton       btnAudio;
    private ImageButton       btnMenuOpciones;

    private TextView          tvNombreChat;
    private TextView          tvEstado;
    private View              dotEstado;
    private View btnBack;
    private LinearLayout      panelSugerencias;
    private ChipGroup         chipGroupSugerencias;
    private boolean           sugerenciasOcultas = false;

    private LinearLayout panelGrabacion;
    private TextView     tvTiempoGrabacion;
    private TextView     tvDeslizarCancelar;

    // ── Grabación ─────────────────────────────────────────────────────────────
    private MediaRecorder mediaRecorder;
    private MediaPlayer   mediaPlayer;
    private String        rutaAudioTemp;
    private boolean       grabando        = false;
    private boolean       cancelado       = false;
    private long          inicioGrabacion = 0L;
    private float         xInicioTouch    = 0f;

    private final Handler  grabacionHandler  = new Handler(Looper.getMainLooper());
    private       Runnable grabacionRunnable = null;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final List<Mensaje> listaMensajes = new ArrayList<>();
    private SessionManager session;
    private int    idUsuarioActual;
    private long   idConversacion  = -1;
    private String nombreContacto  = "Chat";
    private String fotoContactoUrl = "";
    private String fcmTokenContacto = "";

    // ── Presencia ─────────────────────────────────────────────────────────────
    private long    timestampUltimoMensajeContacto = 0L;
    private boolean estabaOnline       = false;
    private boolean conexionActiva     = false;
    private int     fallosConsecutivos = 0;

    // ── Escribiendo ───────────────────────────────────────────────────────────
    private boolean        estabaEscribiendo      = false;
    private boolean        yoEstoyEscribiendo     = false;
    private int            puntosAnimacion        = 0;
    private final Handler  typingHandler          = new Handler(Looper.getMainLooper());
    private       Runnable typingDebounceRunnable = null;
    private final Handler  typingPollHandler      = new Handler(Looper.getMainLooper());
    private       Runnable typingPollRunnable     = null;

    // ── Leídos ────────────────────────────────────────────────────────────────
    private final Set<Long> idsLeidos        = new HashSet<>();
    private final Set<Long> idsEnviadosLeido = new HashSet<>();

    // ── Polling ───────────────────────────────────────────────────────────────
    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       boolean  pollingActivo = false;
    private       long     ultimoIdVisto = -1;
    private final Set<Long> idsAudioLocal = new HashSet<>();

    private final Runnable presenciaRunnable = new Runnable() {
        @Override public void run() {
            if (!estabaEscribiendo) actualizarIndicadorPresencia();
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_chat);

        session         = new SessionManager(this);
        idUsuarioActual = session.getIdUsuario();
        idConversacion  = getIntent().getLongExtra("idConversacion", -1);
        String nombre   = getIntent().getStringExtra("nombre");
        if (nombre != null && !nombre.isEmpty()) nombreContacto = nombre;

        bindViews();
        configurarHeader();
        configurarRecycler();
        configurarScrollListener();
        configurarInputBar();
        configurarSugerencias();
        configurarMenuOpciones();

        if (idConversacion == -1) {
            Toast.makeText(this, "Conversación inválida", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        boolean nombreGenerico = nombreContacto.equals("Chat") || nombreContacto.equals("Conductor")
                || nombreContacto.equals("Pasajero") || nombreContacto.isEmpty()
                || nombreContacto.startsWith("Conductor #") || nombreContacto.startsWith("Pasajero #");
        if (nombreGenerico) cargarNombreDesdeConversacion();

        setEstadoConectando();
        cargarHistorial();
        arrancarPolling();
        arrancarPollingEscribiendo();
        handler.post(presenciaRunnable);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!pollingActivo) arrancarPolling();
        arrancarPollingEscribiendo();
        rvMensajes.post(this::marcarVisiblesComoLeidos);
    }

    @Override protected void onPause() {
        super.onPause();
        detenerPolling(); detenerPollingEscribiendo();
        handler.removeCallbacks(presenciaRunnable);
        detenerContadorGrabacion();
        if (yoEstoyEscribiendo) enviarEstadoEscribiendo(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        detenerPolling(); detenerPollingEscribiendo();
        if (mediaRecorder != null) { try { mediaRecorder.release(); } catch (Exception ignored) {} }
        if (mediaPlayer   != null) { try { mediaPlayer.release();   } catch (Exception ignored) {} }
    }

    private void bindViews() {
        rvMensajes           = findViewById(R.id.rvMensajes);
        etMensaje            = findViewById(R.id.etMensaje);
        btnEnviar            = findViewById(R.id.btnEnviar);
        btnAudio             = findViewById(R.id.btnAudio);
        tvNombreChat         = findViewById(R.id.tvNombreChat);
        tvEstado             = findViewById(R.id.tvEstado);
        dotEstado            = findViewById(R.id.dotEstado);
        btnBack              = findViewById(R.id.btnBack);
        btnMenuOpciones      = findViewById(R.id.btnMenuOpciones);
        panelSugerencias     = findViewById(R.id.panel_sugerencias);
        chipGroupSugerencias = findViewById(R.id.chip_group_sugerencias);
        panelGrabacion       = findViewById(R.id.panelGrabacion);
        tvTiempoGrabacion    = findViewById(R.id.tvTiempoGrabacion);
        tvDeslizarCancelar   = findViewById(R.id.tvDeslizarCancelar);
    }

    // ── HEADER ────────────────────────────────────────────────────────────────
    private void configurarHeader() {
        if (tvNombreChat != null) tvNombreChat.setText(nombreContacto);
        setEstadoConectando();
        TextView tvAvatar = findViewById(R.id.tvAvatarHeader);
        if (tvAvatar != null && !nombreContacto.isEmpty())
            tvAvatar.setText(String.valueOf(nombreContacto.charAt(0)).toUpperCase());

        ImageView        ivFoto       = findViewById(R.id.iv_mi_foto_chat);
        MaterialCardView cardFoto     = findViewById(R.id.card_mi_foto_chat);
        MaterialCardView cardInicial  = findViewById(R.id.card_mi_inicial_chat);
        if (cardFoto    != null) cardFoto.setVisibility(View.GONE);
        if (cardInicial != null) cardInicial.setVisibility(View.VISIBLE);

        cargarFotoContactoEnHeader(ivFoto, cardFoto, cardInicial);
        if (cardFoto    != null) cardFoto.setOnClickListener(v -> mostrarFotoAmpliada());
        if (cardInicial != null) cardInicial.setOnClickListener(v -> mostrarFotoAmpliada());

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> { animarBotonBack(); new Handler().postDelayed(this::finish, 150); });
            btnBack.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.88f).scaleY(0.88f).alpha(0.8f).setDuration(110).start(); break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start(); break;
                } return false;
            });
        }
    }

    private void mostrarFotoAmpliada() {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_foto_perfil);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#CC000000")));
        ImageView iv = d.findViewById(R.id.ivFotoGrande);
        TextView  tn = d.findViewById(R.id.tvNombreOverlay);
        View      bc = d.findViewById(R.id.btnCerrarOverlay);
        if (tn != null) tn.setText(nombreContacto);
        if (iv != null) {
            if (!fotoContactoUrl.isEmpty()) Glide.with(this).load(fotoContactoUrl).placeholder(R.drawable.logomo).error(R.drawable.logomo).into(iv);
            else iv.setImageResource(R.drawable.logomo);
        }
        if (bc != null) bc.setOnClickListener(v -> d.dismiss());
        d.setCanceledOnTouchOutside(true); d.show();
    }

    private void cargarFotoContactoEnHeader(ImageView ivFoto, MaterialCardView cardFoto, MaterialCardView cardInicial) {
        ConexionApi.getInstance(this).getObject(Constantes.CHAT_CONVERSACIONES + "/" + idConversacion,
                response -> {
                    String foto = resolverFotoContacto(response);
                    runOnUiThread(() -> {
                        if (foto != null && !foto.isEmpty() && !foto.equals("null")) {
                            fotoContactoUrl = foto;
                            if (ivFoto != null) {
                                if (cardFoto    != null) cardFoto.setVisibility(View.VISIBLE);
                                if (cardInicial != null) cardInicial.setVisibility(View.GONE);
                                Glide.with(this).load(foto).circleCrop().placeholder(R.drawable.logomo).error(R.drawable.logomo).into(ivFoto);
                            }
                        }
                    });
                }, error -> Log.w(TAG, "cargarFotoContactoEnHeader error"));
    }

    private String resolverFotoContacto(JSONObject conv) {
        if (conv == null) return null;
        long idPas = conv.optLong("idPasajero",-1), idCond = conv.optLong("idConductor",-1);
        JSONObject pasObj = conv.optJSONObject("pasajero"), condObj = conv.optJSONObject("conductor");
        JSONObject co = (idPas == idUsuarioActual) ? condObj : (idCond == idUsuarioActual) ? pasObj : condObj;
        if (co != null) { for (String c : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl","profilePicture","avatar"}) { String v = co.optString(c,""); if (!v.isEmpty() && !v.equals("null")) return v; } }
        long idC = (idPas == idUsuarioActual) ? idCond : idPas;
        if (idC > 0) cargarFotoContactoPorId((int) idC);
        return null;
    }

    private void cargarFotoContactoPorId(int idContacto) {
        ConexionApi.getInstance(this).getObject(Constantes.authPorId((long) idContacto),
                perfil -> {
                    String foto = "";
                    for (String c : new String[]{"fotoPerfi","fotoPerfil","foto","photoUrl","avatar"}) { String v = perfil.optString(c,""); if(!v.isEmpty()&&!v.equals("null")){foto=v;break;} }
                    if (!foto.isEmpty()) {
                        final String ff = foto; fotoContactoUrl = ff;
                        runOnUiThread(() -> {
                            ImageView iv = findViewById(R.id.iv_mi_foto_chat);
                            MaterialCardView cf = findViewById(R.id.card_mi_foto_chat), ci = findViewById(R.id.card_mi_inicial_chat);
                            if (iv != null) { if(cf!=null) cf.setVisibility(View.VISIBLE); if(ci!=null) ci.setVisibility(View.GONE); Glide.with(this).load(ff).circleCrop().placeholder(R.drawable.logomo).error(R.drawable.logomo).into(iv); }
                        });
                    }
                }, error -> Log.w(TAG, "cargarFotoContactoPorId 404 id=" + idContacto));
    }

    // ── NOMBRE ────────────────────────────────────────────────────────────────
    private void cargarNombreDesdeConversacion() {
        ConexionApi.getInstance(this).getObject(Constantes.CHAT_CONVERSACIONES + "/" + idConversacion,
                response -> {
                    extraerTokenContacto(response);
                    String nom = resolverNombreContacto(response);
                    if (nom != null && !nom.isEmpty()) {
                        nombreContacto = nom;
                        runOnUiThread(() -> {
                            if (tvNombreChat != null) tvNombreChat.setText(nombreContacto);
                            TextView ta = findViewById(R.id.tvAvatarHeader);
                            if (ta != null) ta.setText(String.valueOf(nombreContacto.charAt(0)).toUpperCase());
                        });
                    }
                }, error -> Log.w(TAG, "No se pudo cargar nombre"));
    }

    private String resolverNombreContacto(JSONObject conv) {
        if (conv == null) return null;
        long idPas = conv.optLong("idPasajero",-1), idCond = conv.optLong("idConductor",-1);
        JSONObject pasObj = conv.optJSONObject("pasajero"), condObj = conv.optJSONObject("conductor");
        if (idPas>0&&idCond>0) { if(idPas==idUsuarioActual) return condObj!=null?extraerNombreChat(condObj):null; if(idCond==idUsuarioActual) return pasObj!=null?extraerNombreChat(pasObj):null; return condObj!=null?extraerNombreChat(condObj):null; }
        if (pasObj!=null&&condObj!=null) { long idP=leerIdSeguroChat(pasObj),idC=leerIdSeguroChat(condObj); if(idP==idUsuarioActual) return extraerNombreChat(condObj); if(idC==idUsuarioActual) return extraerNombreChat(pasObj); return extraerNombreChat(condObj); }
        JSONObject u1=conv.optJSONObject("usuario1"),u2=conv.optJSONObject("usuario2");
        if(u1!=null&&u2!=null) return leerIdSeguroChat(u1)==idUsuarioActual?extraerNombreChat(u2):extraerNombreChat(u1);
        String nom = conv.optString("nombreContacto",conv.optString("nombre_contacto",""));
        return nom.isEmpty()||nom.equals("null")?null:nom;
    }

    private long   leerIdSeguroChat(JSONObject o) { if(o==null) return -1; for(String c:new String[]{"id","idUsuarios","idUsuario","userId","user_id"}){long v=o.optLong(c,-1);if(v>0)return v;} return -1; }
    private String extraerNombreChat(JSONObject o) { if(o==null)return null; for(String c:new String[]{"nombre","nombreCompleto","name","nombres"}){String v=o.optString(c,"");if(!v.isEmpty()&&!v.equals("null"))return v;} String n=o.optString("nombres",""),a=o.optString("apellidos",""); if(!n.isEmpty()||!a.isEmpty())return(n+" "+a).trim(); return null; }

    private void extraerTokenContacto(JSONObject conv) {
        if (conv == null) return;
        long idPas = conv.optLong("idPasajero",-1), idCond = conv.optLong("idConductor",-1);
        JSONObject pasObj = conv.optJSONObject("pasajero"), condObj = conv.optJSONObject("conductor");
        JSONObject targetObj = null;
        if (idPas > 0 && idCond > 0) {
            targetObj = (idPas == idUsuarioActual) ? condObj : (idCond == idUsuarioActual ? pasObj : null);
        } else if (pasObj != null && condObj != null) {
            long idP = leerIdSeguroChat(pasObj), idC = leerIdSeguroChat(condObj);
            targetObj = (idP == idUsuarioActual) ? condObj : (idC == idUsuarioActual ? pasObj : null);
        } else {
            JSONObject u1 = conv.optJSONObject("usuario1"), u2 = conv.optJSONObject("usuario2");
            if (u1 != null && u2 != null) targetObj = leerIdSeguroChat(u1) == idUsuarioActual ? u2 : u1;
        }
        if (targetObj != null) {
            fcmTokenContacto = targetObj.optString("fcmToken", targetObj.optString("tokenFCM", ""));
        }
    }

    // ── ESCRIBIENDO EN TIEMPO REAL ────────────────────────────────────────────
    private void enviarEstadoEscribiendo(boolean escribiendo) {
        if (yoEstoyEscribiendo == escribiendo) return;
        yoEstoyEscribiendo = escribiendo;
        try {
            JSONObject body = new JSONObject();
            body.put("escribiendo", escribiendo);
            body.put("idUsuario", idUsuarioActual);
            String url = Constantes.BASE_URL + "/api/chat/conversaciones/" + idConversacion + "/escribiendo";
            ConexionApi.getInstance(this).put(url, body, r -> {}, e -> Log.w(TAG, "enviarEstadoEscribiendo error"));
        } catch (Exception e) { Log.e(TAG, "enviarEstadoEscribiendo", e); }
    }

    private void arrancarPollingEscribiendo() {
        detenerPollingEscribiendo();
        typingPollRunnable = new Runnable() {
            @Override public void run() {
                verificarContactoEscribiendo();
                typingPollHandler.postDelayed(this, TYPING_POLL_MS);
            }
        };
        typingPollHandler.postDelayed(typingPollRunnable, TYPING_POLL_MS);
    }

    private void detenerPollingEscribiendo() {
        if (typingPollRunnable    != null) { typingPollHandler.removeCallbacks(typingPollRunnable); typingPollRunnable = null; }
        if (typingDebounceRunnable!= null) { typingHandler.removeCallbacks(typingDebounceRunnable); typingDebounceRunnable = null; }
        typingHandler.removeCallbacksAndMessages(null);
    }

    private void verificarContactoEscribiendo() {
        String url = Constantes.BASE_URL + "/api/chat/conversaciones/" + idConversacion + "/escribiendo";
        ConexionApi.getInstance(this).getObject(url,
                response -> {
                    boolean escribiendo = response.optBoolean("escribiendo", false);
                    int idEscritor = response.optInt("idUsuario", response.optInt("idEmisor", -1));
                    boolean esElContacto = (idEscritor > 0 && idEscritor != idUsuarioActual);
                    boolean mostrar = escribiendo && esElContacto;
                    if (mostrar != estabaEscribiendo) {
                        estabaEscribiendo = mostrar;
                        runOnUiThread(() -> mostrarEscribiendo(mostrar));
                    }
                },
                error -> { if (estabaEscribiendo) { estabaEscribiendo = false; runOnUiThread(() -> mostrarEscribiendo(false)); } }
        );
    }

    private void mostrarEscribiendo(boolean escribiendo) {
        if (tvEstado == null || dotEstado == null) return;
        if (escribiendo) {
            dotEstado.setVisibility(View.GONE);
            tvEstado.setTextColor(Color.parseColor("#1AB99F"));
            puntosAnimacion = 0;
            animarPuntosEscribiendo();
        } else {
            typingHandler.removeCallbacksAndMessages(null);
            tvEstado.setTextColor(Color.parseColor("#CCF5F1"));
            actualizarIndicadorPresencia();
        }
    }

    private void animarPuntosEscribiendo() {
        if (!estabaEscribiendo) return;
        puntosAnimacion = (puntosAnimacion % 3) + 1;
        String puntos = puntosAnimacion == 1 ? "." : puntosAnimacion == 2 ? ".." : "...";
        if (tvEstado != null) tvEstado.setText("escribiendo" + puntos);
        typingHandler.postDelayed(this::animarPuntosEscribiendo, 400);
    }

    // ── RECYCLER ──────────────────────────────────────────────────────────────
    private void configurarRecycler() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true); rvMensajes.setLayoutManager(llm); rvMensajes.setItemAnimator(null);
        adapter = new MensajeAdapter(listaMensajes, idUsuarioActual);
        adapter.setOnMensajeVistoListener(m -> {});
        rvMensajes.setAdapter(adapter);
    }

    private void configurarScrollListener() {
        rvMensajes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { marcarVisiblesComoLeidos(); }
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int s) { if(s==RecyclerView.SCROLL_STATE_IDLE) marcarVisiblesComoLeidos(); }
        });
    }

    // ── INPUT BAR ─────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void configurarInputBar() {
        btnEnviar.setEnabled(false); btnEnviar.setAlpha(0.35f); btnEnviar.setVisibility(View.GONE);
        if (btnAudio != null) btnAudio.setVisibility(View.VISIBLE);

        etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hayTexto = s.toString().trim().length() > 0;

                // ── Escribiendo ──
                if (hayTexto) {
                    enviarEstadoEscribiendo(true);
                    if (typingDebounceRunnable != null) typingHandler.removeCallbacks(typingDebounceRunnable);
                    typingDebounceRunnable = () -> enviarEstadoEscribiendo(false);
                    typingHandler.postDelayed(typingDebounceRunnable, TYPING_DEBOUNCE_MS);
                } else {
                    enviarEstadoEscribiendo(false);
                }

                // ── Botones ──
                if (hayTexto) {
                    if (btnAudio != null && btnAudio.getVisibility() == View.VISIBLE) {
                        btnAudio.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(120).withEndAction(() -> btnAudio.setVisibility(View.GONE)).start();
                        btnEnviar.setVisibility(View.VISIBLE); btnEnviar.setScaleX(0f); btnEnviar.setScaleY(0f); btnEnviar.setAlpha(0f);
                        btnEnviar.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(150).withEndAction(() -> btnEnviar.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();
                    }
                    btnEnviar.setEnabled(true); btnEnviar.setAlpha(1f);
                } else {
                    if (btnEnviar.getVisibility() == View.VISIBLE) {
                        btnEnviar.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(120).withEndAction(() -> { btnEnviar.setVisibility(View.GONE); btnEnviar.setEnabled(false); btnEnviar.setAlpha(0.35f); }).start();
                        if (btnAudio != null) { btnAudio.setVisibility(View.VISIBLE); btnAudio.setScaleX(0f); btnAudio.setScaleY(0f); btnAudio.setAlpha(0f); btnAudio.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start(); }
                    }
                }
            }
        });

        btnEnviar.setOnClickListener(v -> enviarMensaje());

        if (btnAudio != null) {
            btnAudio.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: xInicioTouch = event.getRawX(); iniciarGrabacion(); return true;
                    case MotionEvent.ACTION_MOVE: if(grabando){float dx=xInicioTouch-event.getRawX();if(dx>dpToPx(120))cancelarGrabacion();else if(tvDeslizarCancelar!=null)tvDeslizarCancelar.setAlpha(Math.max(0f,1f-dx/dpToPx(120)));} return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: if(grabando)detenerYEnviarAudio(); return true;
                } return false;
            });
            btnAudio.setOnClickListener(v -> Toast.makeText(this,"Mantén presionado para grabar 🎤",Toast.LENGTH_SHORT).show());
        }
    }

    private int dpToPx(int dp) { return (int)(dp*getResources().getDisplayMetrics().density); }

    // ── GRABACIÓN ─────────────────────────────────────────────────────────────
    private void iniciarGrabacion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},101); return; }
        }
        try {
            rutaAudioTemp = getCacheDir().getAbsolutePath()+"/audio_"+System.currentTimeMillis()+".m4a";
            mediaRecorder = new MediaRecorder(); mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); mediaRecorder.setAudioSamplingRate(44100); mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(rutaAudioTemp); mediaRecorder.prepare(); mediaRecorder.start();
            grabando=true; cancelado=false; inicioGrabacion=System.currentTimeMillis();
            mostrarPanelGrabacion(true);
            if(btnAudio!=null){btnAudio.setColorFilter(Color.RED);pulsarBotonAudio(true);}
            iniciarContadorGrabacion();
        } catch(Exception e){Log.e(TAG,"❌ iniciarGrabacion",e);Toast.makeText(this,"No se pudo iniciar la grabación",Toast.LENGTH_SHORT).show();grabando=false;}
    }

    private void detenerYEnviarAudio() {
        if(!grabando||mediaRecorder==null) return; grabando=false;
        try{mediaRecorder.stop();mediaRecorder.release();mediaRecorder=null;}catch(Exception e){Log.e(TAG,"❌ stop recorder",e);}
        detenerContadorGrabacion(); mostrarPanelGrabacion(false);
        if(btnAudio!=null){btnAudio.clearColorFilter();pulsarBotonAudio(false);}
        if(cancelado){borrarAudioTemp();cancelado=false;return;}
        long durMs=System.currentTimeMillis()-inicioGrabacion;
        if(durMs<1000){borrarAudioTemp();Toast.makeText(this,"Audio muy corto",Toast.LENGTH_SHORT).show();return;}
        long seg=durMs/1000; enviarMensajeAudio(rutaAudioTemp,seg<60?seg+"s":(seg/60)+"m "+(seg%60)+"s");
    }

    private void cancelarGrabacion(){cancelado=true;detenerYEnviarAudio();Toast.makeText(this,"Grabación cancelada",Toast.LENGTH_SHORT).show();}
    private void borrarAudioTemp(){if(rutaAudioTemp!=null){new File(rutaAudioTemp).delete();rutaAudioTemp=null;}}

    private void mostrarPanelGrabacion(boolean mostrar) {
        if(panelGrabacion==null) return;
        if(mostrar){panelGrabacion.setVisibility(View.VISIBLE);panelGrabacion.setAlpha(0f);panelGrabacion.animate().alpha(1f).setDuration(180).start();if(etMensaje!=null)etMensaje.setVisibility(View.INVISIBLE);}
        else{panelGrabacion.animate().alpha(0f).setDuration(150).withEndAction(()->{panelGrabacion.setVisibility(View.GONE);if(etMensaje!=null)etMensaje.setVisibility(View.VISIBLE);if(tvTiempoGrabacion!=null)tvTiempoGrabacion.setText("0:00");if(tvDeslizarCancelar!=null)tvDeslizarCancelar.setAlpha(1f);}).start();}
    }

    private void iniciarContadorGrabacion() {
        detenerContadorGrabacion();
        grabacionRunnable=new Runnable(){@Override public void run(){if(!grabando)return;long e=(System.currentTimeMillis()-inicioGrabacion)/1000;if(tvTiempoGrabacion!=null)tvTiempoGrabacion.setText((e/60)+":"+String.format(Locale.getDefault(),"%02d",e%60));grabacionHandler.postDelayed(this,1000);}};
        grabacionHandler.post(grabacionRunnable);
    }

    private void detenerContadorGrabacion(){if(grabacionRunnable!=null){grabacionHandler.removeCallbacks(grabacionRunnable);grabacionRunnable=null;}}

    private void pulsarBotonAudio(boolean activar) {
        if(btnAudio==null) return;
        if(activar){android.animation.ObjectAnimator px=android.animation.ObjectAnimator.ofFloat(btnAudio,"scaleX",1f,1.3f,1f);px.setDuration(600);px.setRepeatCount(android.animation.ObjectAnimator.INFINITE);px.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());px.start();android.animation.ObjectAnimator py=android.animation.ObjectAnimator.ofFloat(btnAudio,"scaleY",1f,1.3f,1f);py.setDuration(600);py.setRepeatCount(android.animation.ObjectAnimator.INFINITE);py.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());py.start();}
        else{btnAudio.animate().scaleX(1f).scaleY(1f).setDuration(150).start();}
    }

    @Override public void onRequestPermissionsResult(int rc,@NonNull String[] p,@NonNull int[] r){super.onRequestPermissionsResult(rc,p,r);if(rc==101){if(r.length>0&&r[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Permiso concedido. Mantén el botón para grabar.",Toast.LENGTH_SHORT).show();else Toast.makeText(this,"Permiso de micrófono denegado",Toast.LENGTH_SHORT).show();}}

    private void enviarMensajeAudio(String ruta,String duracion){long idLocal=-System.currentTimeMillis();idsAudioLocal.add(idLocal);Mensaje opt=new Mensaje();opt.setId(idLocal);opt.setContenido("🎤 Audio ("+duracion+")");opt.setIdEmisor(idUsuarioActual);opt.setEsPropio(true);opt.setEnviando(true);opt.setLeido(false);opt.setTipoAudio(true);opt.setRutaAudioLocal(ruta);opt.setFechaEnvio(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.getDefault()).format(new Date()));listaMensajes.add(opt);adapter.notifyItemInserted(listaMensajes.size()-1);scrollAbajo();new Handler().postDelayed(()->runOnUiThread(()->{opt.setEnviando(false);int idx=listaMensajes.indexOf(opt);if(idx>=0)adapter.notifyItemChanged(idx);}),500);}

    // ── SUGERENCIAS ───────────────────────────────────────────────────────────
    private void configurarSugerencias() {
        if(session.isConductor()||panelSugerencias==null||chipGroupSugerencias==null){if(panelSugerencias!=null)panelSugerencias.setVisibility(View.GONE);return;}
        chipGroupSugerencias.removeAllViews();
        for(String texto:SUGERENCIAS_PASAJERO){Chip chip=new Chip(this);chip.setText(texto);chip.setClickable(true);chip.setCheckable(false);chip.setTextSize(13f);chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D8F5EF")));chip.setTextColor(Color.parseColor("#0D6B5C"));chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#A8EDE4")));chip.setChipStrokeWidth(1.5f);chip.setRippleColor(ColorStateList.valueOf(Color.parseColor("#B2EDE7")));chip.setOnClickListener(v->{if(etMensaje!=null){etMensaje.setText(texto);etMensaje.setSelection(texto.length());etMensaje.requestFocus();}});chipGroupSugerencias.addView(chip);}
        View btnCerrar=findViewById(R.id.btn_cerrar_sugerencias);if(btnCerrar!=null)btnCerrar.setOnClickListener(v->ocultarSugerencias(true));
        panelSugerencias.setVisibility(View.VISIBLE);panelSugerencias.setTranslationY(80f);panelSugerencias.setAlpha(0f);
        panelSugerencias.animate().translationY(0f).alpha(1f).setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
    }

    private void ocultarSugerencias(boolean animado){if(sugerenciasOcultas||panelSugerencias==null)return;sugerenciasOcultas=true;if(animado){panelSugerencias.animate().translationY(panelSugerencias.getHeight()).alpha(0f).setDuration(240).withEndAction(()->{panelSugerencias.setVisibility(View.GONE);panelSugerencias.setTranslationY(0f);panelSugerencias.setAlpha(1f);}).start();}else{panelSugerencias.setVisibility(View.GONE);}}

    // ── HISTORIAL ─────────────────────────────────────────────────────────────
    private void cargarHistorial(){ConexionApi.getInstance(this).getArray(Constantes.chatMensajesPorConversacion(idConversacion),this::onHistorialOk,this::onHistorialError);}

    private void onHistorialOk(JSONArray arr){fallosConsecutivos=0;conexionActiva=true;runOnUiThread(()->{procesarArray(arr,true);actualizarIndicadorPresencia();rvMensajes.post(this::marcarVisiblesComoLeidos);});}

    private void onHistorialError(VolleyError error){fallosConsecutivos++;ConexionApi.getInstance(this).getObject(Constantes.chatMensajesPorConversacion(idConversacion),resp->{fallosConsecutivos=0;conexionActiva=true;runOnUiThread(()->{procesarArray(extraerArray(resp),true);actualizarIndicadorPresencia();rvMensajes.post(this::marcarVisiblesComoLeidos);});},err2->runOnUiThread(()->{fallosConsecutivos++;conexionActiva=false;setEstadoSinConexion();}));}

    // ── PRESENCIA ─────────────────────────────────────────────────────────────
    private void actualizarIndicadorPresencia(){if(tvEstado==null||dotEstado==null)return;if(estabaEscribiendo)return;if(!conexionActiva||fallosConsecutivos>=MAX_FALLOS_PARA_OFFLINE){setEstadoSinConexion();return;}if(timestampUltimoMensajeContacto==0L){setEstadoDesconocido();return;}long diff=System.currentTimeMillis()-timestampUltimoMensajeContacto;if(diff<ONLINE_TIMEOUT_MS){if(!estabaOnline){estabaOnline=true;animarCambioEstado();}tvEstado.setText("En línea");tvEstado.setTextColor(Color.parseColor("#CCF5F1"));dotEstado.setBackgroundResource(R.drawable.circle_online);dotEstado.setAlpha(1f);dotEstado.setVisibility(View.VISIBLE);}else{if(estabaOnline){estabaOnline=false;animarCambioEstado();}tvEstado.setText("Última vez "+formatearTiempoRelativo(diff));tvEstado.setTextColor(Color.parseColor("#CCF5F1"));dotEstado.setBackgroundResource(R.drawable.circle_offline);dotEstado.setAlpha(1f);dotEstado.setVisibility(View.VISIBLE);}}
    private String formatearTiempoRelativo(long ms){long min=ms/60_000L;if(min<2)return"hace un momento";if(min<60)return"hace "+min+" min";long h=min/60;if(h<24)return"hace "+h+" h";long d=h/24;if(d==1)return"ayer";if(d<7)return"hace "+d+" días";return"hace mucho tiempo";}
    private void setEstadoConectando(){if(tvEstado==null||dotEstado==null)return;tvEstado.setText("Conectando...");tvEstado.setTextColor(Color.parseColor("#CCF5F1"));dotEstado.setBackgroundResource(R.drawable.circle_offline);dotEstado.setAlpha(0.5f);dotEstado.setVisibility(View.VISIBLE);}
    private void setEstadoSinConexion(){if(tvEstado==null||dotEstado==null)return;tvEstado.setText("Sin conexión");tvEstado.setTextColor(Color.parseColor("#CCF5F1"));dotEstado.setBackgroundResource(R.drawable.circle_offline);dotEstado.setAlpha(1f);dotEstado.setVisibility(View.VISIBLE);estabaOnline=false;}
    private void setEstadoDesconocido(){if(tvEstado==null||dotEstado==null)return;tvEstado.setText("");dotEstado.setVisibility(View.GONE);}
    private void animarCambioEstado(){if(tvEstado==null)return;tvEstado.animate().alpha(0f).setDuration(140).withEndAction(()->tvEstado.animate().alpha(1f).setDuration(180).start()).start();if(dotEstado!=null){dotEstado.setVisibility(View.VISIBLE);dotEstado.animate().scaleX(1.5f).scaleY(1.5f).setDuration(140).withEndAction(()->dotEstado.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();}}

    // ── PROCESAR MENSAJES ─────────────────────────────────────────────────────
    @SuppressLint("NotifyDataSetChanged")
    private void procesarArray(JSONArray arr, boolean limpiar) {
        if(arr==null) return;
        try {
            if(limpiar){List<Mensaje> al=new ArrayList<>();for(Mensaje m:listaMensajes)if(idsAudioLocal.contains(m.getId()))al.add(m);listaMensajes.clear();listaMensajes.addAll(al);}
            for(int i=0;i<arr.length();i++){JSONObject obj=arr.optJSONObject(i);if(obj==null)continue;Mensaje m=Mensaje.fromJson(obj,idUsuarioActual);
                if(limpiar){listaMensajes.add(m);}
                else{if(m.getId()>0&&m.getId()<=ultimoIdVisto){for(int j=0;j<listaMensajes.size();j++){if(listaMensajes.get(j).getId()==m.getId()){if(listaMensajes.get(j).isEsPropio()&&m.isLeido()&&!listaMensajes.get(j).isLeido()){listaMensajes.get(j).setLeido(true);adapter.notifyItemChanged(j);}break;}}continue;}int idx=m.isTipoAudio()?-1:buscarOptimista(m.getContenido());if(idx>=0){listaMensajes.set(idx,m);adapter.notifyItemChanged(idx);}else{listaMensajes.add(m);adapter.notifyItemInserted(listaMensajes.size()-1);}}
                if(m.getId()>ultimoIdVisto)ultimoIdVisto=m.getId();
                if(!m.isEsPropio()){long ts=parsearTimestamp(m.getFechaEnvio());if(ts>timestampUltimoMensajeContacto)timestampUltimoMensajeContacto=ts;}}
            if(limpiar){Collections.sort(listaMensajes,(a,b)->Long.compare(parsearTimestamp(a.getFechaEnvio()),parsearTimestamp(b.getFechaEnvio())));adapter.notifyDataSetChanged();}
            scrollAbajo();if(limpiar&&hayMensajesPropios())ocultarSugerencias(false);
        } catch(Exception e){Log.e(TAG,"❌ procesarArray",e);}
    }

    @SuppressLint("NotifyDataSetChanged")
    private void marcarVisiblesComoLeidos(){LinearLayoutManager llm=(LinearLayoutManager)rvMensajes.getLayoutManager();if(llm==null||listaMensajes.isEmpty())return;int primero=llm.findFirstCompletelyVisibleItemPosition(),ultimo=llm.findLastCompletelyVisibleItemPosition();if(primero<0)primero=llm.findFirstVisibleItemPosition();if(ultimo<0)ultimo=llm.findLastVisibleItemPosition();if(primero<0||ultimo<0)return;boolean cambios=false;for(int i=primero;i<=ultimo&&i<listaMensajes.size();i++){Mensaje m=listaMensajes.get(i);if(!m.isEsPropio()&&m.getId()>0&&!m.isLeido()){m.setLeido(true);idsLeidos.add(m.getId());cambios=true;enviarLeidoAlBackend(m.getId());}}if(cambios)rvMensajes.post(()->adapter.notifyDataSetChanged());}

    private void enviarLeidoAlBackend(long id){if(idsEnviadosLeido.contains(id))return;idsEnviadosLeido.add(id);ConexionApi.getInstance(this).put(Constantes.chatMarcarLeido(id),null,r->Log.d(TAG,"✅ leído "+id),e->{idsEnviadosLeido.remove(id);Log.w(TAG,"⚠️ no leído "+id);});}

    // ── POLLING ───────────────────────────────────────────────────────────────
    private void arrancarPolling(){if(pollingActivo)return;pollingActivo=true;pollRunnable=new Runnable(){@Override public void run(){if(!pollingActivo)return;pedirNuevos();handler.postDelayed(this,POLL_INTERVAL);}};handler.postDelayed(pollRunnable,POLL_INTERVAL);}
    private void detenerPolling(){pollingActivo=false;if(pollRunnable!=null)handler.removeCallbacks(pollRunnable);}

    private void pedirNuevos(){String url=Constantes.chatMensajesPorConversacion(idConversacion);ConexionApi.getInstance(this).getArray(url,arr->{fallosConsecutivos=0;conexionActiva=true;runOnUiThread(()->{procesarArray(arr,false);if(!estabaEscribiendo)actualizarIndicadorPresencia();rvMensajes.post(this::marcarVisiblesComoLeidos);});},err->ConexionApi.getInstance(this).getObject(url,resp->{fallosConsecutivos=0;conexionActiva=true;runOnUiThread(()->{procesarArray(extraerArray(resp),false);if(!estabaEscribiendo)actualizarIndicadorPresencia();rvMensajes.post(this::marcarVisiblesComoLeidos);});},err2->runOnUiThread(()->{fallosConsecutivos++;conexionActiva=fallosConsecutivos<MAX_FALLOS_PARA_OFFLINE;if(!estabaEscribiendo)actualizarIndicadorPresencia();})));}

    // ── ENVIAR MENSAJE ────────────────────────────────────────────────────────
    private void enviarMensaje(){if(etMensaje.getText()==null)return;String texto=etMensaje.getText().toString().trim();if(texto.isEmpty())return;enviarEstadoEscribiendo(false);ocultarSugerencias(true);etMensaje.setText("");btnEnviar.setEnabled(false);btnEnviar.setAlpha(0.35f);Mensaje opt=new Mensaje();opt.setId(-System.currentTimeMillis());opt.setContenido(texto);opt.setIdEmisor(idUsuarioActual);opt.setEsPropio(true);opt.setEnviando(true);opt.setLeido(false);opt.setFechaEnvio(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.getDefault()).format(new Date()));listaMensajes.add(opt);adapter.notifyItemInserted(listaMensajes.size()-1);scrollAbajo();JSONObject body=new JSONObject();try{body.put("idConversacion",idConversacion);body.put("idRemitente",idUsuarioActual);body.put("mensaje",texto);body.put("tipo","TEXTO");body.put("conversacionId",idConversacion);body.put("emisorId",idUsuarioActual);body.put("contenido",texto);}catch(JSONException e){Log.e(TAG,"❌ JSON body",e);marcarFallido(opt);return;}ConexionApi.getInstance(this).post(Constantes.CHAT_MENSAJES,body,response->runOnUiThread(()->confirmarEnvio(opt,response)),error->runOnUiThread(()->marcarFallido(opt)));}
    private void confirmarEnvio(Mensaje opt,JSONObject response){try{Mensaje real=Mensaje.fromJson(response,idUsuarioActual);real.setLeido(false);int idx=listaMensajes.indexOf(opt);if(idx>=0){listaMensajes.set(idx,real);adapter.notifyItemChanged(idx);}if(real.getId()>ultimoIdVisto)ultimoIdVisto=real.getId(); if (!fcmTokenContacto.isEmpty()) { com.arlys.moviflexx.model.PushNotificationHelper.enviarPush(this, fcmTokenContacto, "Nuevo mensaje", opt.getContenido(), "MENSAJE"); } }catch(Exception e){marcarFallido(opt);}}
    private void marcarFallido(Mensaje m){m.setEnviando(false);m.setFallido(true);adapter.notifyDataSetChanged();Toast.makeText(this,"No se pudo enviar el mensaje",Toast.LENGTH_SHORT).show();}

    // ── MENÚ ──────────────────────────────────────────────────────────────────
    private void configurarMenuOpciones(){if(btnMenuOpciones==null)return;btnMenuOpciones.setOnClickListener(this::mostrarMenuOpciones);}
    private void mostrarMenuOpciones(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add(0,1,0,"Buscar");p.getMenu().add(0,2,1,"Archivos, enlaces y docs.");p.getMenu().add(0,3,2,"Mensajes temporales");p.getMenu().add(0,4,3,"Tema del chat");p.getMenu().add(0,5,4,"Más ▶");p.setOnMenuItemClickListener(item->{switch(item.getItemId()){case 1:accionBuscar();return true;case 2:accionArchivosYDocs();return true;case 3:accionMensajesTemporales();return true;case 4:accionTemaChat();return true;case 5:mostrarSubMenuMas(anchor);return true;}return false;});p.show();}
    private void mostrarSubMenuMas(View anchor){PopupMenu s=new PopupMenu(this,anchor);s.getMenu().add(0,10,0,"Vaciar chat");s.getMenu().add(0,11,1,"Exportar chat");s.getMenu().add(0,12,2,"Crear acceso directo");s.getMenu().add(0,13,3,"Añadir a lista");s.setOnMenuItemClickListener(item->{switch(item.getItemId()){case 10:confirmarVaciarChat();return true;case 11:accionExportarChat();return true;case 12:accionAccesoDirecto();return true;case 13:accionAnadirALista();return true;}return false;});s.show();}
    private void accionBuscar(){Toast.makeText(this,"Buscar (próximamente)",Toast.LENGTH_SHORT).show();}
    private void accionArchivosYDocs(){Toast.makeText(this,"Archivos, enlaces y docs.",Toast.LENGTH_SHORT).show();}
    private void accionTemaChat(){Toast.makeText(this,"Tema del chat (próximamente)",Toast.LENGTH_SHORT).show();}
    private void accionAccesoDirecto(){Toast.makeText(this,"Acceso directo a "+nombreContacto+" creado",Toast.LENGTH_SHORT).show();}
    private void accionAnadirALista(){Toast.makeText(this,"Añadido a la lista de favoritos",Toast.LENGTH_SHORT).show();}
    private void accionMensajesTemporales(){new AlertDialog.Builder(this).setTitle("Mensajes temporales").setMessage("Los mensajes temporales se eliminan automáticamente.").setPositiveButton("Activar 24h",(d,w)->Toast.makeText(this,"Mensajes temporales: 24h",Toast.LENGTH_SHORT).show()).setNegativeButton("Desactivar",null).setNeutralButton("Cancelar",null).show();}
    private void confirmarVaciarChat(){new AlertDialog.Builder(this).setTitle("Vaciar chat").setMessage("¿Eliminar todos los mensajes?").setPositiveButton("Vaciar",(d,w)->{listaMensajes.clear();idsAudioLocal.clear();adapter.notifyDataSetChanged();Toast.makeText(this,"Chat vaciado",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancelar",null).show();}
    private void accionExportarChat(){if(listaMensajes.isEmpty()){Toast.makeText(this,"No hay mensajes",Toast.LENGTH_SHORT).show();return;}StringBuilder sb=new StringBuilder();sb.append("Chat con ").append(nombreContacto).append("\n─────────────────────\n");SimpleDateFormat sdf=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault());for(Mensaje m:listaMensajes){String quien=m.isEsPropio()?"Yo":nombreContacto;String hora="";try{long ts=Long.parseLong(m.getFechaEnvio());if(ts<10_000_000_000L)ts*=1000L;hora=" ["+sdf.format(new Date(ts))+"]";}catch(Exception ignored){}sb.append(quien).append(hora).append(": ").append(m.getContenido()).append("\n");}android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(android.content.Intent.EXTRA_TEXT,sb.toString());startActivity(android.content.Intent.createChooser(i,"Exportar chat"));}
    private void animarBotonBack(){if(btnBack==null)return;btnBack.animate().rotation(-12f).setDuration(90).withEndAction(()->btnBack.animate().rotation(0f).setDuration(90).start()).start();}

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private JSONArray extraerArray(JSONObject r){if(r==null)return null;if(r.has("mensajes"))return r.optJSONArray("mensajes");if(r.has("content"))return r.optJSONArray("content");if(r.has("data"))return r.optJSONArray("data");try{if(r.toString().startsWith("["))return new JSONArray(r.toString());}catch(Exception ignored){}return null;}
    private boolean hayMensajesPropios(){for(Mensaje m:listaMensajes)if(m.isEsPropio()||m.getIdEmisor()==idUsuarioActual)return true;return false;}
    private int buscarOptimista(String contenido){for(int i=listaMensajes.size()-1;i>=0;i--){Mensaje m=listaMensajes.get(i);if(m.isEnviando()&&!m.isTipoAudio()&&contenido!=null&&contenido.equals(m.getContenido()))return i;}return -1;}
    private long parsearTimestamp(String fecha){if(fecha==null||fecha.isEmpty())return 0L;try{long n=Long.parseLong(fecha);return n<10_000_000_000L?n*1000L:n;}catch(NumberFormatException ignored){}for(String f:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssZ"}){try{Date d=new SimpleDateFormat(f,Locale.getDefault()).parse(fecha);if(d!=null)return d.getTime();}catch(Exception ignored){}}return 0L;}
    private void scrollAbajo(){if(!listaMensajes.isEmpty())rvMensajes.post(()->rvMensajes.scrollToPosition(listaMensajes.size()-1));}
}