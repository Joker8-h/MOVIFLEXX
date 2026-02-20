package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomePasajero extends AppCompatActivity {

    private static final String TAG = "HomePasajero";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView             tvNombreUsuario;
    private TextView             tvSaludo;
    private ProgressBar          pbRutas;
    private LinearLayout         layoutRutas;
    private MaterialButton       btnBuscarViaje;
    private BottomNavigationView bottomNavigation;

    // ── Carrusel MoviFlex Info ─────────────────────────────────────────────────
    private RecyclerView         rvCarruselInfo;
    private LinearLayout         layoutDotsInfo;
    private CarruselInfoAdapter  carruselAdapter;
    private final Handler        carruselHandler  = new Handler(Looper.getMainLooper());
    private Runnable             carruselRunnable;
    private int                  carruselPos      = 0;

    // ── Data ──────────────────────────────────────────────────────────────────
    private SessionManager session;

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
        configurarListeners();
        configurarBottomNav();
        iniciarCarruselMoviflexInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRutasFrecuentesPasajero();
        iniciarAutoScrollCarrusel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerAutoScrollCarrusel();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BIND
    // ─────────────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvNombreUsuario  = findViewById(R.id.tv_nombre_usuario);
        tvSaludo         = findViewById(R.id.tv_saludo);
        pbRutas          = findViewById(R.id.pb_rutas);
        layoutRutas      = findViewById(R.id.layout_rutas_frecuentes);
        btnBuscarViaje   = findViewById(R.id.btn_buscar_viaje);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Carrusel informativo
        rvCarruselInfo  = findViewById(R.id.rv_carrusel_info);
        layoutDotsInfo  = findViewById(R.id.layout_dots_info);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NOMBRE + SALUDO
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
                :                           "Buenas noches 🌙";
        if (tvSaludo        != null) tvSaludo.setText(saludo);
        if (tvNombreUsuario != null) tvNombreUsuario.setText(nombre);
    }

    private void cargarNombreDesdeAPI() {
        int id = session.getIdUsuario();
        if (id <= 0) return;
        ConexionApi.getInstance(this).getObject(
                Constantes.usuarioDetalle((long) id),
                response -> {
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

    // =========================================================================
    //  RUTAS FRECUENTES — personalizadas por historial del pasajero
    //
    //  Flujo:
    //   1. Consulta mis-reservas del pasajero.
    //   2. Agrupa por destino y cuenta frecuencia.
    //   3. Ordena de mayor a menor uso.
    //   4. Muestra top-6 como cards en el HorizontalScrollView.
    //   5. Si no hay historial → fallback a rutas del sistema.
    // =========================================================================

    private void cargarRutasFrecuentesPasajero() {
        if (pbRutas == null || layoutRutas == null) return;
        pbRutas.setVisibility(View.VISIBLE);
        layoutRutas.removeAllViews();

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> {
                    // Contar frecuencia por destino
                    Map<String, Integer> frecuencia = new HashMap<>();
                    // clave → "destino|origen"
                    Map<String, String>  datos      = new HashMap<>();

                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r == null) continue;

                        // ✅ Solo rutas donde el conductor YA FINALIZÓ el viaje
                        String estadoReserva = r.optString("estado", "").toUpperCase();
                        String estadoViaje   = "";
                        JSONObject viaje     = r.optJSONObject("viaje");
                        if (viaje != null)
                            estadoViaje = viaje.optString("estado", "").toUpperCase();

                        boolean viajeTerminado = estadoViaje.equals("FINALIZADO")
                                || estadoViaje.equals("COMPLETADO")
                                || estadoReserva.equals("COMPLETADO")
                                || estadoReserva.equals("COMPLETADA");
                        if (!viajeTerminado) continue;

                        // Destino: preferir nombreParada, luego destino del viaje
                        String destino = r.optString("nombreParada", "").trim();
                        String origen  = "";

                        if (viaje != null) {
                            JSONObject ruta = viaje.optJSONObject("ruta");
                            if (ruta != null) {
                                if (destino.isEmpty())
                                    destino = ruta.optString("destino",
                                            ruta.optString("nombre", "")).trim();
                                origen = ruta.optString("origen", "").trim();
                            }
                        }
                        if (destino.isEmpty()) continue;

                        String key = destino.toLowerCase();
                        frecuencia.put(key, frecuencia.getOrDefault(key, 0) + 1);
                        if (!datos.containsKey(key))
                            datos.put(key, destino + "|" + origen);
                    }

                    // Ordenar por frecuencia descendente
                    List<Map.Entry<String, Integer>> lista = new ArrayList<>(frecuencia.entrySet());
                    lista.sort((a, b) -> b.getValue() - a.getValue());

                    List<RutaFrecuenteItem> items = new ArrayList<>();
                    for (int i = 0; i < Math.min(6, lista.size()); i++) {
                        String key   = lista.get(i).getKey();
                        int    veces = lista.get(i).getValue();
                        String raw   = datos.get(key);
                        String dest  = raw != null && raw.contains("|")
                                ? raw.split("\\|")[0] : (raw != null ? raw : key);
                        String orig  = raw != null && raw.contains("|")
                                ? raw.split("\\|")[1] : "";
                        items.add(new RutaFrecuenteItem(dest, orig, veces));
                    }

                    runOnUiThread(() -> {
                        pbRutas.setVisibility(View.GONE);
                        if (items.isEmpty()) {
                            cargarRutasSistemaFallback();
                        } else {
                            mostrarRutasFrecuentesPersonalizadas(items);
                        }
                    });
                },
                error -> runOnUiThread(() -> {
                    pbRutas.setVisibility(View.GONE);
                    cargarRutasSistemaFallback();
                })
        );
    }

    /** Fallback: rutas populares del sistema cuando el pasajero no tiene historial */
    private void cargarRutasSistemaFallback() {
        if (layoutRutas == null) return;
        ConexionApi.getInstance(this).getArray(
                Constantes.RUTAS,
                response -> runOnUiThread(() -> {
                    int[] colores = {
                            0xFF00897B, 0xFF00ACC1, 0xFF26A69A, 0xFF0097A7,
                            0xFF26C6DA, 0xFF4DB6AC
                    };
                    String[] emojis = {"\uD83D\uDCCD","\uD83D\uDE0F","\uD83D\uDE98","\uD83D\uDE09","⏱\uFE0F","⚡"};
                    int total = Math.min(response.length(), 6);
                    for (int i = 0; i < total; i++) {
                        JSONObject ruta = response.optJSONObject(i);
                        if (ruta != null)
                            agregarCardRutaSistema(ruta, emojis[i % emojis.length],
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
                error -> Log.e(TAG, "Error cargando rutas del sistema")
        );
    }

    /** Muestra las rutas personalizadas del pasajero (por historial) */
    private void mostrarRutasFrecuentesPersonalizadas(List<RutaFrecuenteItem> items) {
        if (layoutRutas == null) return;
        layoutRutas.removeAllViews();

        int[] colores = {
                0xFF009B8D, 0xFF0097A7, 0xFF00838F,
                0xFF006064, 0xFF26C6DA, 0xFF4DB6AC
        };

        for (int i = 0; i < items.size(); i++) {
            RutaFrecuenteItem item  = items.get(i);
            int               color = colores[i % colores.length];

            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lpCard =
                    new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT);
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

            // Cabecera de color con LOGO de MoviFlex
            LinearLayout cabecera = new LinearLayout(this);
            cabecera.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(85)));
            cabecera.setBackgroundColor(color);
            cabecera.setGravity(android.view.Gravity.CENTER);
            cabecera.setOrientation(LinearLayout.VERTICAL);

            // ── LOGO MOVIFLEX ──────────────────────────────────────────────
            // Coloca tu logo en res/drawable/logo_moviflexo (png o xml).
            // Si aún no lo tienes, se muestra el nombre "M" como placeholder.
            android.widget.ImageView ivLogo = new android.widget.ImageView(this);
            int logoResId = getResources().getIdentifier(
                    "logo_moviflex", "drawable", getPackageName());
            if (logoResId != 0) {
                ivLogo.setImageResource(logoResId);
                ivLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(dp(48), dp(48));
                ivLogo.setLayoutParams(lpLogo);
                cabecera.addView(ivLogo);
            } else {
                // Placeholder circular con "M" hasta que pongas el logo
                TextView tvM = new TextView(this);
                tvM.setText("M");
                tvM.setTextSize(26f);
                tvM.setTypeface(null, Typeface.BOLD);
                tvM.setTextColor(Color.WHITE);
                tvM.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(dp(48), dp(48));
                tvM.setLayoutParams(lpM);
                GradientDrawable circulo = new GradientDrawable();
                circulo.setShape(GradientDrawable.OVAL);
                circulo.setColor(Color.argb(60, 255, 255, 255));
                tvM.setBackground(circulo);
                cabecera.addView(tvM);
            }
            // ──────────────────────────────────────────────────────────────

            // Badge de usos (solo si hay más de 1)
            if (item.vecesUsada > 1) {
                TextView tvBadge = new TextView(this);
                tvBadge.setText(item.vecesUsada + " viajes");
                tvBadge.setTextSize(10f);
                tvBadge.setTypeface(null, Typeface.BOLD);
                tvBadge.setTextColor(Color.WHITE);
                tvBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
                GradientDrawable bgBadge = new GradientDrawable();
                bgBadge.setShape(GradientDrawable.RECTANGLE);
                bgBadge.setCornerRadius(dp(20));
                bgBadge.setColor(Color.argb(60, 0, 0, 0));
                tvBadge.setBackground(bgBadge);
                LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpB.topMargin = dp(4);
                tvBadge.setLayoutParams(lpB);
                cabecera.addView(tvBadge);
            }

            // Cuerpo con origen → destino
            LinearLayout cuerpo = new LinearLayout(this);
            cuerpo.setOrientation(LinearLayout.VERTICAL);
            cuerpo.setPadding(dp(12), dp(10), dp(12), dp(12));

            if (!item.origen.isEmpty()) {
                TextView tvOrigen = new TextView(this);
                tvOrigen.setText(truncar(item.origen, 16));
                tvOrigen.setTextColor(0xFF78909C);
                tvOrigen.setTextSize(10f);
                cuerpo.addView(tvOrigen);

                TextView tvArrow = new TextView(this);
                tvArrow.setText("↓");
                tvArrow.setTextColor(0xFFB0BEC5);
                tvArrow.setTextSize(10f);
                cuerpo.addView(tvArrow);
            }

            TextView tvDestino = new TextView(this);
            tvDestino.setText(truncar(item.destino, 18));
            tvDestino.setTextColor(0xFF004D40);
            tvDestino.setTextSize(13f);
            tvDestino.setTypeface(null, Typeface.BOLD);
            tvDestino.setMaxLines(2);
            LinearLayout.LayoutParams lpDes = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpDes.topMargin = dp(2);
            tvDestino.setLayoutParams(lpDes);
            cuerpo.addView(tvDestino);

            // Etiqueta "Tu ruta"
            TextView tvTag = new TextView(this);
            tvTag.setText("✅ Tu ruta");
            tvTag.setTextSize(10f);
            tvTag.setTypeface(null, Typeface.BOLD);
            tvTag.setTextColor(Color.WHITE);
            tvTag.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable bgTag = new GradientDrawable();
            bgTag.setShape(GradientDrawable.RECTANGLE);
            bgTag.setCornerRadius(dp(20));
            bgTag.setColor(color);
            tvTag.setBackground(bgTag);
            LinearLayout.LayoutParams lpTag = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTag.topMargin = dp(6);
            tvTag.setLayoutParams(lpTag);
            cuerpo.addView(tvTag);

            inner.addView(cabecera);
            inner.addView(cuerpo);
            card.addView(inner);

            final String destinoFinal = item.destino;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, MisReservasActivity.class);
                intent.putExtra("DESTINO_PRELLENADO", destinoFinal);
                startActivity(intent);
            });

            layoutRutas.addView(card);
        }
    }

    /** Card de ruta del sistema (fallback sin historial personal) */
    private void agregarCardRutaSistema(JSONObject ruta, String emoji, int color) {
        String origen  = ruta.optString("origen",  "Origen");
        String destino = ruta.optString("destino", "");
        if (destino.isEmpty()) destino = ruta.optString("nombre",
                ruta.optString("descripcion", "Destino"));
        int    idRuta = ruta.optInt("idRuta", ruta.optInt("id", 0));
        double dist   = ruta.optDouble("distancia", 0);

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

        LinearLayout cuerpo = new LinearLayout(this);
        cuerpo.setOrientation(LinearLayout.VERTICAL);
        cuerpo.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView tvOrigen = new TextView(this);
        tvOrigen.setText(truncar(origen, 15));
        tvOrigen.setTextColor(0xFF78909C);
        tvOrigen.setTextSize(10f);
        cuerpo.addView(tvOrigen);

        TextView tvArrow = new TextView(this);
        tvArrow.setText("↓");
        tvArrow.setTextColor(0xFFB0BEC5);
        tvArrow.setTextSize(10f);
        cuerpo.addView(tvArrow);

        TextView tvDestino = new TextView(this);
        tvDestino.setText(truncar(destino, 15));
        tvDestino.setTextColor(0xFF004D40);
        tvDestino.setTextSize(13f);
        tvDestino.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpD.topMargin = dp(2);
        tvDestino.setLayoutParams(lpD);
        cuerpo.addView(tvDestino);

        if (dist > 0) {
            TextView tvDist = new TextView(this);
            tvDist.setText(String.format("%.1f km", dist));
            tvDist.setTextColor(0xFF00897B);
            tvDist.setTextSize(11f);
            tvDist.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lpDist = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpDist.topMargin = dp(4);
            tvDist.setLayoutParams(lpDist);
            cuerpo.addView(tvDist);
        }

        inner.addView(cabecera);
        inner.addView(cuerpo);
        card.addView(inner);

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

    // =========================================================================
    //  CARRUSEL INFORMATIVO MOVIFLEX
    //  Auto-scroll cada 4 seg, 6 slides con tips/beneficios, dots indicadores
    // =========================================================================

    private void iniciarCarruselMoviflexInfo() {
        if (rvCarruselInfo == null) return;

        List<CarruselSlide> slides = crearSlidesMoviflex();

        LinearLayoutManager lm = new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false);
        rvCarruselInfo.setLayoutManager(lm);
        carruselAdapter = new CarruselInfoAdapter(slides);
        rvCarruselInfo.setAdapter(carruselAdapter);

        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(rvCarruselInfo);

        crearDots(slides.size());

        rvCarruselInfo.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View view = snap.findSnapView(lm);
                    if (view != null) {
                        int pos = lm.getPosition(view);
                        carruselPos = pos;
                        actualizarDots(pos, slides.size());
                    }
                }
            }
        });
    }

    private List<CarruselSlide> crearSlidesMoviflex() {
        List<CarruselSlide> slides = new ArrayList<>();
        slides.add(new CarruselSlide("🚗  Viaja más seguro",
                "Conductores verificados con documento y vehículo registrado en MoviFlex.",
                "#009B8D", "#00695C", "✅ Conductores verificados"));
        slides.add(new CarruselSlide("💰  Ahorra en cada viaje",
                "Comparte el trayecto y reduce el costo hasta un 60% vs. taxi tradicional.",
                "#0288D1", "#01579B", "📊 Hasta 60% más económico"));
        slides.add(new CarruselSlide("📍  Elige dónde bajarte",
                "Selecciona tu parada exacta dentro de la ruta. El conductor irá hasta allí.",
                "#7B1FA2", "#4A148C", "🗺️ Parada personalizada"));
        slides.add(new CarruselSlide("💬  Chat en tiempo real",
                "Comunícate con tu conductor directamente desde la app en cualquier momento.",
                "#F57F17", "#E65100", "📲 Chat instantáneo"));
        slides.add(new CarruselSlide("🌿  Movilidad sostenible",
                "Al compartir vehículo reduces tu huella de carbono. MoviFlex cuida el planeta.",
                "#388E3C", "#1B5E20", "🌎 +500 kg CO₂ ahorrado/mes"));
        slides.add(new CarruselSlide("⭐  Califica tu experiencia",
                "Tu opinión mejora el servicio. Califica al conductor al finalizar el viaje.",
                "#C62828", "#B71C1C", "🏅 Comunidad de confianza"));
        return slides;
    }

    private void crearDots(int count) {
        if (layoutDotsInfo == null) return;
        layoutDotsInfo.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dot.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(i == 0 ? Color.parseColor("#009B8D") : Color.parseColor("#B2DFDB"));
            dot.setBackground(gd);
            layoutDotsInfo.addView(dot);
        }
    }

    private void actualizarDots(int activo, int count) {
        if (layoutDotsInfo == null) return;
        for (int i = 0; i < layoutDotsInfo.getChildCount() && i < count; i++) {
            View dot = layoutDotsInfo.getChildAt(i);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            if (i == activo) {
                gd.setColor(Color.parseColor("#009B8D"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(8));
                lp.setMargins(dp(4), 0, dp(4), 0);
                dot.setLayoutParams(lp);
            } else {
                gd.setColor(Color.parseColor("#B2DFDB"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
                lp.setMargins(dp(4), 0, dp(4), 0);
                dot.setLayoutParams(lp);
            }
            dot.setBackground(gd);
        }
    }

    private void iniciarAutoScrollCarrusel() {
        if (rvCarruselInfo == null || carruselAdapter == null) return;
        detenerAutoScrollCarrusel();
        carruselRunnable = new Runnable() {
            @Override public void run() {
                if (carruselAdapter == null) return;
                int total = carruselAdapter.getItemCount();
                if (total == 0) return;
                carruselPos = (carruselPos + 1) % total;
                rvCarruselInfo.smoothScrollToPosition(carruselPos);
                actualizarDots(carruselPos, total);
                carruselHandler.postDelayed(this, 4000);
            }
        };
        carruselHandler.postDelayed(carruselRunnable, 4000);
    }

    private void detenerAutoScrollCarrusel() {
        if (carruselRunnable != null)
            carruselHandler.removeCallbacks(carruselRunnable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LISTENERS + BOTTOM NAV
    // ─────────────────────────────────────────────────────────────────────────

    private void configurarListeners() {
        if (btnBuscarViaje != null)
            btnBuscarViaje.setOnClickListener(v ->
                    startActivity(new Intent(this, MisReservasActivity.class)));
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

    private static String extraerNombreConductor(JSONObject cond) {
        if (cond == null) return "Conductor";
        String[] campos = {"nombre","nombres","nombreCompleto","name","fullName","displayName","nombreUsuario"};
        for (String c : campos) {
            String val = cond.optString(c, "");
            if (!val.isEmpty() && !val.equals("null")) return val;
        }
        String nombres   = cond.optString("nombres",   "");
        String apellidos = cond.optString("apellidos", "");
        if (!nombres.isEmpty() || !apellidos.isEmpty()) return (nombres + " " + apellidos).trim();
        return "Conductor";
    }

    // =========================================================================
    //  MODELOS
    // =========================================================================

    private static class RutaFrecuenteItem {
        String destino, origen;
        int    vecesUsada;
        RutaFrecuenteItem(String destino, String origen, int vecesUsada) {
            this.destino = destino; this.origen = origen; this.vecesUsada = vecesUsada;
        }
    }

    private static class CarruselSlide {
        String titulo, descripcion, colorInicio, colorFin, badge;
        CarruselSlide(String titulo, String desc, String c1, String c2, String badge) {
            this.titulo = titulo; this.descripcion = desc;
            this.colorInicio = c1; this.colorFin = c2; this.badge = badge;
        }
    }

    // =========================================================================
    //  ADAPTER — Carrusel informativo MoviFlex
    // =========================================================================

    static class CarruselInfoAdapter extends RecyclerView.Adapter<CarruselInfoAdapter.VH> {

        private final List<CarruselSlide> slides;
        CarruselInfoAdapter(List<CarruselSlide> slides) { this.slides = slides; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            float d   = ctx.getResources().getDisplayMetrics().density;
            int screenW = ctx.getResources().getDisplayMetrics().widthPixels;

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    screenW - (int)(32 * d), (int)(150 * d));
            lp.setMargins((int)(4 * d), 0, (int)(4 * d), 0);
            root.setLayoutParams(lp);
            root.setPadding((int)(20 * d), (int)(16 * d), (int)(20 * d), (int)(16 * d));
            root.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Badge
            TextView tvBadge = new TextView(ctx);
            tvBadge.setTag("badge");
            tvBadge.setTextSize(11f);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setPadding((int)(10 * d), (int)(3 * d), (int)(10 * d), (int)(3 * d));
            GradientDrawable bgB = new GradientDrawable();
            bgB.setShape(GradientDrawable.RECTANGLE);
            bgB.setCornerRadius(20 * d);
            bgB.setColor(Color.argb(60, 255, 255, 255));
            tvBadge.setBackground(bgB);
            LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBadge.bottomMargin = (int)(8 * d);
            tvBadge.setLayoutParams(lpBadge);
            root.addView(tvBadge);

            // Título
            TextView tvTitulo = new TextView(ctx);
            tvTitulo.setTag("titulo");
            tvTitulo.setTextSize(17f);
            tvTitulo.setTypeface(null, Typeface.BOLD);
            tvTitulo.setTextColor(Color.WHITE);
            tvTitulo.setMaxLines(1);
            root.addView(tvTitulo);

            // Descripción
            TextView tvDesc = new TextView(ctx);
            tvDesc.setTag("desc");
            tvDesc.setTextSize(13f);
            tvDesc.setTextColor(Color.argb(220, 255, 255, 255));
            tvDesc.setMaxLines(3);
            LinearLayout.LayoutParams lpDesc = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpDesc.topMargin = (int)(8 * d);
            tvDesc.setLayoutParams(lpDesc);
            root.addView(tvDesc);

            return new VH(root);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CarruselSlide slide = slides.get(position);
            LinearLayout  root  = (LinearLayout) holder.itemView;
            float d = root.getContext().getResources().getDisplayMetrics().density;

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor(slide.colorInicio), Color.parseColor(slide.colorFin)});
            bg.setCornerRadius(20 * d);
            root.setBackground(bg);

            ((TextView) root.findViewWithTag("badge")).setText(slide.badge);
            ((TextView) root.findViewWithTag("titulo")).setText(slide.titulo);
            ((TextView) root.findViewWithTag("desc")).setText(slide.descripcion);
        }

        @Override public int getItemCount() { return slides.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

}