package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomePasajero extends AppCompatActivity {

    // ── Views (IDs exactos del activity_home_pasajero.xml) ───────────────────
    private TextView             tvNombreUsuario;
    private TextView             tvSaludo;
    private ProgressBar          pbRutas;
    private LinearLayout         layoutRutas;
    private ProgressBar          pbViajes;
    private TextView             tvContadorViajes;
    private TextView             tvEmptyViajes;
    private RecyclerView         rvViajes;
    private MaterialButton       btnBuscarViaje;
    private BottomNavigationView bottomNavigation;

    // ── Data ──────────────────────────────────────────────────────────────────
    private SessionManager         session;
    private final List<JSONObject> viajes = new ArrayList<>();
    private ViajesAdapter          adapterViajes;

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_pasajero);

        session = new SessionManager(this);
        session.loadSessionToMemory();

        bindViews();
        mostrarNombreUsuario();
        configurarRecycler();
        configurarListeners();
        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRutasFrecuentes();
        cargarViajesDisponibles();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND
    // ─────────────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvNombreUsuario  = findViewById(R.id.tv_nombre_usuario);
        tvSaludo         = findViewById(R.id.tv_saludo);
        pbRutas          = findViewById(R.id.pb_rutas);
        layoutRutas      = findViewById(R.id.layout_rutas_frecuentes);
        pbViajes         = findViewById(R.id.pb_viajes);
        tvContadorViajes = findViewById(R.id.tv_contador_viajes);
        tvEmptyViajes    = findViewById(R.id.tv_empty_viajes);
        rvViajes         = findViewById(R.id.rv_viajes_disponibles);
        btnBuscarViaje   = findViewById(R.id.btn_buscar_viaje);
        bottomNavigation = findViewById(R.id.bottom_navigation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NOMBRE + SALUDO  →  desde SessionManager o GET /api/auth/{id}
    // ─────────────────────────────────────────────────────────────────────────

    private void mostrarNombreUsuario() {
        String nombre = session.getNombre();
        if (nombre != null && !nombre.isEmpty() && !nombre.equals("Usuario")) {
            aplicarSaludo(nombre);
        } else {
            cargarNombreDesdeAPI();
        }
    }

    private void aplicarSaludo(String nombre) {
        int hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String saludo = hora >= 5  && hora < 12 ? "Buenos días ☀️"
                : hora >= 12 && hora < 18 ? "Buenas tardes 🌤️"
                : "Buenas noches 🌙";
        if (tvSaludo        != null) tvSaludo.setText(saludo);
        if (tvNombreUsuario != null) tvNombreUsuario.setText(nombre);
    }

    private void cargarNombreDesdeAPI() {
        int id = session.getIdUsuario();
        if (id <= 0) return;
        ConexionApi.getInstance(this).getObject(
                Constantes.usuarioPorId((long) id),
                response -> {
                    // Busca el nombre en múltiples campos posibles del JSON
                    String nombre = response.optString("nombre", "");
                    if (nombre.isEmpty()) nombre = response.optString("nombres", "");
                    if (nombre.isEmpty()) nombre = response.optString("nombreCompleto", "");
                    if (nombre.isEmpty()) nombre = "Usuario";

                    String email    = response.optString("email",    "");
                    String telefono = response.optString("telefono", "");
                    int    idRol    = response.optInt("idRol", 1);
                    session.saveUser(nombre, email, telefono, idRol, id);
                    final String n = nombre;
                    runOnUiThread(() -> aplicarSaludo(n));
                },
                error -> {}
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RECYCLER
    // ─────────────────────────────────────────────────────────────────────────

    private void configurarRecycler() {
        rvViajes.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapterViajes = new ViajesAdapter(viajes, viaje -> {
            // Al tocar una tarjeta → abrir DetalleViajeActivity
            int idViaje = viaje.optInt("idViaje", viaje.optInt("id", 0));
            if (idViaje > 0) {
                Intent i = new Intent(this, DetalleViajeActivity.class);
                i.putExtra("ID_VIAJE", idViaje);
                startActivity(i);
            }
        });
        rvViajes.setAdapter(adapterViajes);
        rvViajes.setNestedScrollingEnabled(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API: RUTAS FRECUENTES  →  GET /api/rutas
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarRutasFrecuentes() {
        pbRutas.setVisibility(View.VISIBLE);
        layoutRutas.removeAllViews();

        ConexionApi.getInstance(this).getArray(
                Constantes.RUTAS,
                response -> runOnUiThread(() -> {
                    pbRutas.setVisibility(View.GONE);

                    int[] colores = {
                            0xFF00897B, 0xFF00ACC1, 0xFF26A69A, 0xFF0097A7,
                            0xFF26C6DA, 0xFF4DB6AC, 0xFF00796B, 0xFF4DD0E1
                    };
                    String[] emojis = {"📍","✈️","🎓","🚌","🏥","⛪","🌳","🏙️"};

                    int total = Math.min(response.length(), 10);
                    for (int i = 0; i < total; i++) {
                        JSONObject ruta = response.optJSONObject(i);
                        if (ruta != null)
                            agregarCardRuta(ruta, emojis[i % emojis.length],
                                    colores[i % colores.length]);
                    }

                    if (total == 0) {
                        TextView empty = new TextView(this);
                        empty.setText("No hay rutas registradas aún");
                        empty.setTextColor(0xFF90A4AE);
                        empty.setTextSize(13f);
                        layoutRutas.addView(empty);
                    }
                }),
                error -> runOnUiThread(() -> pbRutas.setVisibility(View.GONE))
        );
    }

    private void agregarCardRuta(JSONObject ruta, String emoji, int color) {
        // Extraer datos del JSON
        String origen  = ruta.optString("origen",  "Origen");
        String destino = ruta.optString("destino", "Destino");
        int    idRuta  = ruta.optInt("idRuta", ruta.optInt("id", 0));
        double dist    = ruta.optDouble("distancia", 0);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard =
                new LinearLayout.LayoutParams(dp(156), ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(lpCard);
        card.setRadius(dp(18));
        card.setCardElevation(dp(4));
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Cabecera con color y emoji
        LinearLayout cabecera = new LinearLayout(this);
        cabecera.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(90)));
        cabecera.setBackgroundColor(color);
        cabecera.setGravity(android.view.Gravity.CENTER);
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(38f);
        tvEmoji.setGravity(android.view.Gravity.CENTER);
        cabecera.addView(tvEmoji);

        // Cuerpo con texto dinámico
        LinearLayout cuerpo = new LinearLayout(this);
        cuerpo.setOrientation(LinearLayout.VERTICAL);
        cuerpo.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView tvOrigen = new TextView(this);
        tvOrigen.setText(truncar(origen, 15));
        tvOrigen.setTextColor(0xFF78909C);
        tvOrigen.setTextSize(10f);

        TextView tvArrow = new TextView(this);
        tvArrow.setText("↓");
        tvArrow.setTextColor(0xFFB0BEC5);
        tvArrow.setTextSize(10f);

        TextView tvDestino = new TextView(this);
        tvDestino.setText(truncar(destino, 15));
        tvDestino.setTextColor(0xFF004D40);
        tvDestino.setTextSize(13f);
        tvDestino.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpD.topMargin = dp(2);
        tvDestino.setLayoutParams(lpD);

        cuerpo.addView(tvOrigen);
        cuerpo.addView(tvArrow);
        cuerpo.addView(tvDestino);

        if (dist > 0) {
            TextView tvDist = new TextView(this);
            tvDist.setText(String.format("%.1f km", dist));
            tvDist.setTextColor(0xFF00897B);
            tvDist.setTextSize(11f);
            tvDist.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpDist = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpDist.topMargin = dp(4);
            tvDist.setLayoutParams(lpDist);
            cuerpo.addView(tvDist);
        }

        inner.addView(cabecera);
        inner.addView(cuerpo);
        card.addView(inner);

        // Click → buscar viajes de esa ruta
        final String destinoFinal = destino;
        final int    idRutaFinal  = idRuta;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, RutasFrecuentes.class);
            intent.putExtra("destino", destinoFinal);
            intent.putExtra("idRuta",  idRutaFinal);
            startActivity(intent);
        });

        layoutRutas.addView(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API: VIAJES DISPONIBLES  →  GET /api/viajes/buscar
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarViajesDisponibles() {
        pbViajes.setVisibility(View.VISIBLE);
        tvEmptyViajes.setVisibility(View.GONE);
        rvViajes.setVisibility(View.GONE);

        ConexionApi.getInstance(this).getArray(
                Constantes.BUSCAR_VIAJES,
                response -> runOnUiThread(() -> {
                    pbViajes.setVisibility(View.GONE);
                    viajes.clear();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v == null) continue;
                        String estado = v.optString("estado", "").toUpperCase();
                        if (estado.equals("CREADO") || estado.equals("DISPONIBLE")
                                || estado.equals("PROGRAMADO")) {
                            viajes.add(v);
                        }
                    }

                    adapterViajes.notifyDataSetChanged();

                    if (viajes.isEmpty()) {
                        tvEmptyViajes.setVisibility(View.VISIBLE);
                        tvContadorViajes.setText("Sin viajes ahora");
                    } else {
                        rvViajes.setVisibility(View.VISIBLE);
                        tvContadorViajes.setText(viajes.size() + " viaje"
                                + (viajes.size() == 1 ? "" : "s") + " disponible"
                                + (viajes.size() == 1 ? "" : "s"));
                    }
                }),
                error -> runOnUiThread(() -> {
                    pbViajes.setVisibility(View.GONE);
                    tvEmptyViajes.setVisibility(View.VISIBLE);
                    tvEmptyViajes.setText("⚠️ Error al cargar viajes");
                })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LISTENERS + BOTTOM NAV
    // ─────────────────────────────────────────────────────────────────────────

    private void configurarListeners() {
        if (btnBuscarViaje != null)
            btnBuscarViaje.setOnClickListener(v ->
                    startActivity(new Intent(this, RutasFrecuentes.class)));
    }

    private void configurarBottomNav() {
        if (bottomNavigation == null) return;
        bottomNavigation.setSelectedItemId(R.id.nav_inicio);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_inicio)     { return true; }
            else if (id == R.id.nav_mis_viajes) { startActivity(new Intent(this, MisReservasActivity.class)); return true; }
            else if (id == R.id.nav_mapa)       { startActivity(new Intent(this, Mapa.class));                return true; }
            else if (id == R.id.nav_mensajes)   { startActivity(new Intent(this, Mensajes.class));            return true; }
            else if (id == R.id.nav_perfil)     { startActivity(new Intent(this, PerfilUsuario.class));       return true; }
            return false;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private String truncar(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** Extrae el nombre del conductor buscando en múltiples campos posibles */
    private static String extraerNombreConductor(JSONObject cond) {
        if (cond == null) return "Conductor";
        String[] campos = {
                "nombre", "nombres", "nombreCompleto", "name",
                "fullName", "displayName", "nombreUsuario"
        };
        for (String c : campos) {
            String v = cond.optString(c, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        // Intentar construir: nombres + apellidos
        String nombres   = cond.optString("nombres",   "");
        String apellidos = cond.optString("apellidos", "");
        if (!nombres.isEmpty() || !apellidos.isEmpty())
            return (nombres + " " + apellidos).trim();
        return "Conductor";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ADAPTER: tarjetas de viaje con destino + conductor + precio + cupos
    //           botón "RESERVAR" funcional → abre DetalleViajeActivity
    // ═══════════════════════════════════════════════════════════════════════

    static class ViajesAdapter
            extends RecyclerView.Adapter<ViajesAdapter.VH> {

        interface OnClick { void onClick(JSONObject viaje); }

        private final List<JSONObject> items;
        private final OnClick          listener;

        ViajesAdapter(List<JSONObject> items, OnClick listener) {
            this.items = items; this.listener = listener;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int vt) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            float d = parent.getContext().getResources().getDisplayMetrics().density;
            lp.setMargins(0, 0, 0, (int)(12 * d));
            card.setLayoutParams(lp);
            card.setRadius((int)(18 * d));
            card.setCardElevation((int)(4 * d));
            card.setCardBackgroundColor(Color.WHITE);
            card.setUseCompatPadding(true);
            card.setClickable(true);
            card.setFocusable(true);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            JSONObject viaje  = items.get(position);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            card.removeAllViews();

            float d   = card.getContext().getResources().getDisplayMetrics().density;
            int   p16 = (int)(16 * d);
            int   p12 = (int)(12 * d);
            int   p8  = (int)(8  * d);
            int   p4  = (int)(4  * d);

            // ── 1. Extraer todos los datos del JSON ───────────────────────

            // Ruta (puede venir anidada en "ruta" o en la raíz)
            JSONObject rutaObj = viaje.optJSONObject("ruta");
            String origen  = rutaObj != null
                    ? rutaObj.optString("origen",  viaje.optString("origen",  "Origen"))
                    : viaje.optString("origen",  "Origen");
            String destino = rutaObj != null
                    ? rutaObj.optString("destino", viaje.optString("destino", "Destino"))
                    : viaje.optString("destino", "Destino");
            double distancia = rutaObj != null ? rutaObj.optDouble("distancia", 0) : 0;

            // Conductor
            JSONObject condObj  = viaje.optJSONObject("conductor");
            String nomConductor = extraerNombreConductor(condObj);

            // Precio, cupos, estado
            double precio = viaje.optDouble("precio", 0);
            int    cupos  = viaje.optInt("cuposDisponibles",
                    viaje.optInt("cupos", 0));
            String estado = viaje.optString("estado", "DISPONIBLE").toUpperCase();

            // ID del viaje para navegar
            int idViaje = viaje.optInt("idViaje", viaje.optInt("id", 0));

            // ── 2. Layout principal ───────────────────────────────────────
            LinearLayout root = new LinearLayout(card.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(0, 0, 0, 0);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // ── 3. Cabecera coloreada con origen → destino ────────────────
            LinearLayout cabecera = new LinearLayout(card.getContext());
            cabecera.setOrientation(LinearLayout.VERTICAL);
            cabecera.setPadding(p16, p12, p16, p12);
            cabecera.setBackgroundColor(0xFF00897B);

            TextView tvRuta = new TextView(card.getContext());
            tvRuta.setText("📍 " + origen + "  →  " + destino);
            tvRuta.setTextColor(Color.WHITE);
            tvRuta.setTextSize(15f);
            tvRuta.setTypeface(null, android.graphics.Typeface.BOLD);

            // Badge estado
            TextView tvEstado = new TextView(card.getContext());
            String badgeTexto = estado.equals("INICIADO") ? "🚗 En curso"
                    : estado.equals("PROGRAMADO") ? "📅 Programado" : "✅ Disponible";
            tvEstado.setText(badgeTexto);
            tvEstado.setTextColor(0xFFB2DFDB);
            tvEstado.setTextSize(11f);
            LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpBadge.topMargin = p4;
            tvEstado.setLayoutParams(lpBadge);

            cabecera.addView(tvRuta);
            cabecera.addView(tvEstado);

            // ── 4. Cuerpo con detalles ────────────────────────────────────
            LinearLayout cuerpo = new LinearLayout(card.getContext());
            cuerpo.setOrientation(LinearLayout.VERTICAL);
            cuerpo.setPadding(p16, p12, p16, p12);

            // Conductor
            TextView tvConductor = new TextView(card.getContext());
            tvConductor.setText("👤 " + nomConductor);
            tvConductor.setTextSize(13f);
            tvConductor.setTextColor(0xFF004D40);

            // Separador
            View sep = new View(card.getContext());
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * d));
            lpSep.topMargin = p8; lpSep.bottomMargin = p8;
            sep.setLayoutParams(lpSep);
            sep.setBackgroundColor(0xFFE0F2F1);

            // Fila precio + cupos
            LinearLayout filaInfo = new LinearLayout(card.getContext());
            filaInfo.setOrientation(LinearLayout.HORIZONTAL);
            filaInfo.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvPrecio = new TextView(card.getContext());
            tvPrecio.setText("💵 $" + String.format("%,.0f", precio));
            tvPrecio.setTextSize(16f);
            tvPrecio.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPrecio.setTextColor(0xFFFF6F00);
            tvPrecio.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Chip de cupos con color según disponibilidad
            TextView tvCupos = new TextView(card.getContext());
            tvCupos.setText("💺 " + cupos + " cupo" + (cupos == 1 ? "" : "s"));
            tvCupos.setTextSize(12f);
            tvCupos.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCupos.setTextColor(cupos > 0 ? 0xFF2E7D32 : 0xFFC62828);
            tvCupos.setPadding(p8, p4, p8, p4);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setShape(GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(dp20(d));
            chipBg.setColor(cupos > 0 ? 0xFFE8F5E9 : 0xFFFFEBEE);
            tvCupos.setBackground(chipBg);

            filaInfo.addView(tvPrecio);
            filaInfo.addView(tvCupos);

            // Distancia (si viene del JSON)
            if (distancia > 0) {
                TextView tvDist = new TextView(card.getContext());
                tvDist.setText("📏 " + String.format("%.1f km", distancia));
                tvDist.setTextSize(11f);
                tvDist.setTextColor(0xFF90A4AE);
                LinearLayout.LayoutParams lpDist = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpDist.topMargin = p4;
                tvDist.setLayoutParams(lpDist);
                cuerpo.addView(tvConductor);
                cuerpo.addView(sep);
                cuerpo.addView(filaInfo);
                cuerpo.addView(tvDist);
            } else {
                cuerpo.addView(tvConductor);
                cuerpo.addView(sep);
                cuerpo.addView(filaInfo);
            }

            // ── 5. Botón RESERVAR (funcional) ─────────────────────────────
            // Solo visible si hay cupos y el viaje está disponible
            boolean puedeReservar = cupos > 0
                    && (estado.equals("CREADO") || estado.equals("DISPONIBLE")
                    || estado.equals("PROGRAMADO"));

            LinearLayout footerLayout = new LinearLayout(card.getContext());
            footerLayout.setOrientation(LinearLayout.HORIZONTAL);
            footerLayout.setPadding(p16, 0, p16, p12);
            footerLayout.setGravity(android.view.Gravity.END);

            if (puedeReservar) {
                android.widget.Button btnReservar =
                        new android.widget.Button(card.getContext());
                btnReservar.setText("🎫  RESERVAR");
                btnReservar.setTextColor(Color.WHITE);
                btnReservar.setAllCaps(false);
                btnReservar.setTextSize(13f);
                btnReservar.setTypeface(null, android.graphics.Typeface.BOLD);

                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setShape(GradientDrawable.RECTANGLE);
                btnBg.setCornerRadius((int)(14 * d));
                btnBg.setColor(0xFF00897B);
                btnReservar.setBackground(btnBg);

                LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, (int)(44 * d));
                lpBtn.topMargin = p8;
                btnReservar.setLayoutParams(lpBtn);
                btnReservar.setPadding((int)(24 * d), 0, (int)(24 * d), 0);

                // Click → DetalleViajeActivity con el ID real del viaje
                final int idFinal = idViaje;
                btnReservar.setOnClickListener(v -> {
                    android.content.Context ctx = card.getContext();
                    Intent intent = new Intent(ctx, DetalleViajeActivity.class);
                    intent.putExtra("ID_VIAJE", idFinal);
                    ctx.startActivity(intent);
                });

                footerLayout.addView(btnReservar);
            } else if (cupos == 0) {
                // Badge "Sin cupos" cuando está lleno
                TextView tvSinCupos = new TextView(card.getContext());
                tvSinCupos.setText("Sin cupos disponibles");
                tvSinCupos.setTextSize(11f);
                tvSinCupos.setTextColor(0xFF90A4AE);
                LinearLayout.LayoutParams lpSC = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpSC.topMargin = p8;
                tvSinCupos.setLayoutParams(lpSC);
                footerLayout.addView(tvSinCupos);
            }

            // ── 6. Ensamblar todo ─────────────────────────────────────────
            root.addView(cabecera);
            root.addView(cuerpo);
            root.addView(footerLayout);
            card.addView(root);

            // Click en la tarjeta completa → también abre el detalle
            final int idFinalCard = idViaje;
            card.setOnClickListener(v -> listener.onClick(viaje));
        }

        // Evitar crear objeto float solo para el radio del chip
        private float dp20(float d) { return 20 * d; }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(android.view.View v) { super(v); }
        }
    }
}