package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PerfilUsuario extends BaseActivity {

    private SessionManager session;

    // ── Datos personales ──────────────────────────────────────────────────────
    private TextView      tvNombre, tvEmail, tvTelefono, tvTipoUsuario, tvInicialAvatar;
    private ImageView     ivAvatar;
    private MaterialCardView cardAvatarFoto, cardAvatarInicial;

    // ── Sección vehículo (solo conductor) ─────────────────────────────────────
    private LinearLayout layoutVehiculoRoot;
    private CardView     cardVehiculo;
    private TextView     tvModelo, tvPlaca, tvAsientos;

    // ── Selector de vehículo ──────────────────────────────────────────────────
    private LinearLayout layoutSelectorVehiculo;
    private TextView     tvVehiculoActual, tvCambiarVehiculo;

    // ── Aviso "sin vehículo" ──────────────────────────────────────────────────
    private LinearLayout   layoutSinVehiculo;
    private MaterialButton btnRegistrarVWeb;

    // ── Indicador de carga ────────────────────────────────────────────────────
    private ProgressBar        progressVehiculo;
    private SwipeRefreshLayout swipeRefresh;

    // ── Botones acción ────────────────────────────────────────────────────────
    private MaterialButton btnEditarPerfil, btnCerrarSesion;

    // ── Sección calificaciones ────────────────────────────────────────────────
    private LinearLayout layoutCalificacionesContainer;

    // ── Estado interno ────────────────────────────────────────────────────────
    private final List<JSONObject> listaVehiculos = new ArrayList<>();
    private int vehiculoSeleccionadoIndex = 0;

    // ── Datos de ranking ──────────────────────────────────────────────────────
    private int       rankingPosicion     = -1;
    private int       rankingTotal        = 0;
    private double    promedioGlobal      = 0;
    private int       totalGlobal         = 0;
    private JSONArray calificacionesGlobal = null;

    private static final String WEB_URL =
            "https://moviflexconreact-production.up.railway.app/";

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_perfil_usuario);

        session = new SessionManager(this);

        // ══════════════════════════════════════════════════════
        //  NUEVO: Inicializa el Navigation Drawer
        //  (BaseActivity busca drawer_layout y nav_view en el XML)
        // ══════════════════════════════════════════════════════
        setupDrawer(R.id.nav_perfil);
        sincronizarHeaderDrawer();

        enlazarVistas();
        configurarSwipeRefresh();
        configurarBotones();
        configurarBottomNav();

        // ══════════════════════════════════════════════════════
        //  NUEVO: El botón ⚙️ abre el drawer (antes no hacía nada útil)
        // ══════════════════════════════════════════════════════
        View btnConf = findViewById(R.id.btn_configuraciones);
        if (btnConf != null) btnConf.setOnClickListener(v -> abrirDrawer());
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = new SessionManager(this);
        rellenarDatos();

        if (session.isConductor()) {
            cargarVehiculosDesdeApi();
        } else {
            if (layoutVehiculoRoot != null) layoutVehiculoRoot.setVisibility(View.GONE);
        }

        cargarCalificaciones();

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) nav.setSelectedItemId(R.id.nav_perfil);
    }

    // =========================================================================
    //  NUEVO: Sincroniza el header del drawer con SessionManager
    //  Lee exactamente los mismos datos que rellenarDatos() usa para el perfil
    // =========================================================================

    // ─── REEMPLAZA el método sincronizarHeaderDrawer() en PerfilUsuario.java ───
// El resto del archivo NO cambia.

    private void sincronizarHeaderDrawer() {
        if (navView == null) return;
        View header = navView.getHeaderView(0);
        if (header == null) return;

        String nombre  = session.getNombre();
        String email   = session.getEmail();
        String rol     = session.isConductor() ? "Conductor" : "Pasajero";
        String fotoUrl = session.getFotoPerfil();

        // ── Textos ─────────────────────────────────────────────────────────────
        TextView navNombre = header.findViewById(R.id.nav_tv_nombre);
        if (navNombre != null) navNombre.setText(nombre);

        TextView navEmail = header.findViewById(R.id.nav_tv_email);
        if (navEmail != null) navEmail.setText(email.isEmpty() ? "Sin correo" : email);

        Chip navChip = header.findViewById(R.id.nav_chip_rol);
        if (navChip != null) navChip.setText(rol);

        // ── Avatar ─────────────────────────────────────────────────────────────
        MaterialCardView navCardFoto    = header.findViewById(R.id.nav_card_avatar_foto);
        MaterialCardView navCardInicial = header.findViewById(R.id.nav_card_avatar_inicial);
        ImageView        navIvFoto      = header.findViewById(R.id.nav_iv_avatar);
        TextView         navTvInicial   = header.findViewById(R.id.nav_tv_inicial);

        if (fotoUrl != null && !fotoUrl.isEmpty() && !fotoUrl.equals("null")) {
            if (navCardFoto    != null) navCardFoto.setVisibility(View.VISIBLE);
            if (navCardInicial != null) navCardInicial.setVisibility(View.GONE);
            if (navIvFoto != null)
                Glide.with(this)
                        .load(fotoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.logomo)
                        .error(R.drawable.logomo)
                        .into(navIvFoto);
        } else {
            if (navCardFoto    != null) navCardFoto.setVisibility(View.GONE);
            if (navCardInicial != null) navCardInicial.setVisibility(View.VISIBLE);
            if (navTvInicial != null && nombre != null && !nombre.isEmpty())
                navTvInicial.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }

        // ── Click en el header → cerrar drawer ────────────────────────────────
        header.setOnClickListener(v -> {
            if (drawerLayout != null)
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        });

        // ══════════════════════════════════════════════════════════════════════
        //  FIX: ocultar/mostrar grupo conductor DESPUÉS de que el drawer
        //  termine de inflar su menú (post al hilo principal)
        // ══════════════════════════════════════════════════════════════════════
        navView.post(() -> {
            if (navView.getMenu() == null) return;
            boolean esConductor = session.isConductor();

            // Grupo exclusivo de conductores
            navView.getMenu().setGroupVisible(R.id.group_conductor, esConductor);

            // "Mis Reservas" solo visible para pasajeros
            android.view.MenuItem itemReservas = navView.getMenu().findItem(R.id.nav_reservas);
            if (itemReservas != null) itemReservas.setVisible(!esConductor);
        });
    }

    // =========================================================================
    //  ENLAZAR VISTAS  (sin cambios)
    // =========================================================================

    private void enlazarVistas() {
        tvNombre        = findViewById(R.id.tv_nombre);
        tvEmail         = findViewById(R.id.tv_email);
        tvTelefono      = findViewById(R.id.tv_telefono);
        tvTipoUsuario   = findViewById(R.id.tv_tipo_usuario);
        tvInicialAvatar = findViewById(R.id.tv_inicial_avatar);

        ivAvatar         = findViewById(R.id.iv_avatar);
        cardAvatarFoto   = findViewById(R.id.card_avatar_foto);
        cardAvatarInicial = findViewById(R.id.card_avatar_inicial);

        layoutVehiculoRoot     = findViewById(R.id.layout_vehiculo_root);
        cardVehiculo           = findViewById(R.id.card_info_vehiculo);
        tvModelo               = findViewById(R.id.tv_modelo_vehiculo);
        tvPlaca                = findViewById(R.id.tv_placa_vehiculo);
        tvAsientos             = findViewById(R.id.tv_asientos);

        layoutSelectorVehiculo = findViewById(R.id.layout_selector_vehiculo);
        tvVehiculoActual       = findViewById(R.id.tv_vehiculo_actual);
        tvCambiarVehiculo      = findViewById(R.id.tv_cambiar_vehiculo);

        layoutSinVehiculo = findViewById(R.id.layout_sin_vehiculo);
        btnRegistrarVWeb  = findViewById(R.id.btn_registrar_vehiculo_web);

        progressVehiculo = findViewById(R.id.progress_vehiculo);
        swipeRefresh     = findViewById(R.id.swipe_refresh);

        btnEditarPerfil = findViewById(R.id.btn_editar_perfil);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);

        layoutCalificacionesContainer = findViewById(R.id.layout_calificaciones_container);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> navegarAtras());
    }

    // =========================================================================
    //  NAVEGACIÓN ATRÁS  (sin cambios)
    // =========================================================================

    private void navegarAtras() {
        if (isTaskRoot()) {
            if (session.isConductor()) goTo(HomeConductor.class, Transition.NONE);
            else                       goTo(HomePasajero.class,  Transition.NONE);
            finish();
            return;
        }
        finish();
    }

    // =========================================================================
    //  SWIPE REFRESH  — añade sincronizarHeaderDrawer al refrescar
    // =========================================================================

    private void configurarSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.teal_500);
        swipeRefresh.setOnRefreshListener(() -> {
            rellenarDatos();
            sincronizarHeaderDrawer(); // ← actualiza el drawer también
            cargarCalificaciones();
            if (session.isConductor()) cargarVehiculosDesdeApi();
            else                       swipeRefresh.setRefreshing(false);
        });
    }

    // =========================================================================
    //  RELLENAR DATOS  (sin cambios — usa SessionManager igual que antes)
    // =========================================================================

    private void rellenarDatos() {
        String nombre   = session.getNombre();
        String email    = session.getEmail();
        String telefono = session.getTelefono();

        if (tvNombre   != null) tvNombre.setText(nombre);
        if (tvEmail    != null) tvEmail.setText(email.isEmpty() ? "Sin correo registrado" : email);
        if (tvTelefono != null) tvTelefono.setText(telefono.isEmpty() ? "Sin teléfono registrado" : telefono);
        if (tvTipoUsuario != null)
            tvTipoUsuario.setText(session.isConductor() ? "Conductor" : "Pasajero");

        String fotoUrl = session.getFotoPerfil();

        if (ivAvatar != null && !fotoUrl.isEmpty() && !fotoUrl.equals("null")) {
            if (cardAvatarFoto    != null) cardAvatarFoto.setVisibility(View.VISIBLE);
            if (cardAvatarInicial != null) cardAvatarInicial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.logomo)
                    .error(R.drawable.logomo)
                    .into(ivAvatar);
        } else {
            if (cardAvatarFoto    != null) cardAvatarFoto.setVisibility(View.GONE);
            if (cardAvatarInicial != null) cardAvatarInicial.setVisibility(View.VISIBLE);
            if (tvInicialAvatar != null && nombre != null && !nombre.isEmpty())
                tvInicialAvatar.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }
    }

    // =========================================================================
    //  CALIFICACIONES  (sin cambios)
    // =========================================================================

    private void cargarCalificaciones() {
        int idUsuario = session.getIdUsuario();
        if (idUsuario <= 0) return;

        rankingPosicion      = -1;
        rankingTotal         = 0;
        promedioGlobal       = 0;
        totalGlobal          = 0;
        calificacionesGlobal = null;

        int[] pendientes = {3};

        Runnable intentarRenderizar = () -> {
            pendientes[0]--;
            if (pendientes[0] <= 0) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    renderizarSeccionCalificaciones(
                            promedioGlobal, totalGlobal, calificacionesGlobal);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                });
            }
        };

        ConexionApi.getInstance(this).getObjectNoCache(
                Constantes.calificacionPromedio((long) idUsuario),
                promedioObj -> {
                    JSONObject anidado = promedioObj.optJSONObject("promedio");
                    if (anidado != null) {
                        promedioGlobal = anidado.optDouble("promedio", 0);
                        totalGlobal    = anidado.optInt("total", 0);
                    } else {
                        promedioGlobal = promedioObj.optDouble("promedio",
                                promedioObj.optDouble("average",
                                        promedioObj.optDouble("calificacionPromedio", 0)));
                        totalGlobal = promedioObj.optInt("total",
                                promedioObj.optInt("count",
                                        promedioObj.optInt("totalCalificaciones", 0)));
                    }
                    intentarRenderizar.run();
                },
                err -> intentarRenderizar.run()
        );

        ConexionApi.getInstance(this).getArrayNoCache(
                Constantes.calificacionesPorUsuario((long) idUsuario),
                lista -> {
                    calificacionesGlobal = lista;
                    intentarRenderizar.run();
                },
                err -> intentarRenderizar.run()
        );

        String urlTop = session.isConductor()
                ? Constantes.CALIFICACIONES_TOP_CONDUCTORES
                : Constantes.CALIFICACIONES_TOP_VIAJEROS;

        ConexionApi.getInstance(this).getArrayNoCache(
                urlTop,
                topArray -> {
                    rankingTotal = topArray.length();
                    android.util.Log.d("RANKING_DEBUG",
                            "Top array length: " + rankingTotal
                                    + " | idUsuario buscado: " + idUsuario
                                    + " | primer item: " + (topArray.length() > 0
                                    ? topArray.optJSONObject(0) : "vacío"));

                    for (int i = 0; i < topArray.length(); i++) {
                        JSONObject item = topArray.optJSONObject(i);
                        if (item == null) continue;

                        int idItem = item.optInt("idUsuarios",
                                item.optInt("idUsuario",
                                        item.optInt("id",
                                                item.optInt("userId", -1))));

                        if (idItem == -1) {
                            JSONObject u = item.optJSONObject("usuario");
                            if (u != null) idItem = u.optInt("idUsuarios",
                                    u.optInt("idUsuario",
                                            u.optInt("id", -1)));
                        }

                        android.util.Log.d("RANKING_DEBUG",
                                "pos=" + (i+1) + " idItem=" + idItem
                                        + " | keys=" + item.toString()
                                        .substring(0, Math.min(120, item.toString().length())));

                        if (idItem == idUsuario) {
                            rankingPosicion = i + 1;
                            break;
                        }
                    }
                    intentarRenderizar.run();
                },
                err -> intentarRenderizar.run()
        );
    }

    // =========================================================================
    //  RENDERIZAR CALIFICACIONES  (sin cambios)
    // =========================================================================

    private void renderizarSeccionCalificaciones(double promedio, int total,
                                                 JSONArray calificaciones) {
        LinearLayout container = layoutCalificacionesContainer;
        if (container == null) {
            LinearLayout root = findViewById(R.id.layout_perfil_root);
            if (root == null) return;
            container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setId(R.id.layout_calificaciones_container);
            root.addView(container, root.indexOfChild(
                    findViewById(R.id.btn_editar_perfil)) - 1);
            layoutCalificacionesContainer = container;
        }

        container.removeAllViews();
        float d   = getResources().getDisplayMetrics().density;
        int p16   = (int)(16*d), p12 = (int)(12*d),
                p8    = (int)(8*d),  p6  = (int)(6*d),
                p4    = (int)(4*d);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, p16);
        card.setLayoutParams(lpCard);
        card.setRadius(20 * d);
        card.setCardElevation(3 * d);
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeWidth((int)(1.5f * d));
        card.setStrokeColor(Color.parseColor("#E0F2F1"));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p16, p16, p16);

        LinearLayout encabezado = new LinearLayout(this);
        encabezado.setOrientation(LinearLayout.HORIZONTAL);
        encabezado.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpEnc = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEnc.bottomMargin = p12;
        encabezado.setLayoutParams(lpEnc);

        View accentBar = new View(this);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(
                (int)(4*d), (int)(20*d));
        lpBar.rightMargin = (int)(10*d);
        accentBar.setLayoutParams(lpBar);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setShape(GradientDrawable.RECTANGLE);
        barBg.setCornerRadius(4 * d);
        barBg.setColors(new int[]{
                Color.parseColor("#00BFA0"),
                Color.parseColor("#00897B")});
        barBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        accentBar.setBackground(barBg);
        encabezado.addView(accentBar);

        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("MIS CALIFICACIONES");
        tvTitulo.setTextSize(11f);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#00897B"));
        tvTitulo.setLetterSpacing(0.12f);
        tvTitulo.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        encabezado.addView(tvTitulo);

        TextView tvIcono = new TextView(this);
        tvIcono.setText(" ");
        tvIcono.setTextSize(20f);
        encabezado.addView(tvIcono);
        inner.addView(encabezado);

        boolean hayDatos = promedio > 0
                || (calificaciones != null && calificaciones.length() > 0);

        if (hayDatos) {
            boolean mostrarRanking = rankingPosicion > 0;

            LinearLayout filaTop = new LinearLayout(this);
            filaTop.setOrientation(LinearLayout.HORIZONTAL);
            filaTop.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFT = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFT.bottomMargin = p12;
            filaTop.setLayoutParams(lpFT);

            LinearLayout bloquePromedio = new LinearLayout(this);
            bloquePromedio.setOrientation(LinearLayout.VERTICAL);
            bloquePromedio.setGravity(Gravity.CENTER_HORIZONTAL);
            bloquePromedio.setPadding(p16, p12, p16, p12);
            bloquePromedio.setLayoutParams(new LinearLayout.LayoutParams(
                    mostrarRanking ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    mostrarRanking ? 1f : 0f));

            GradientDrawable promBg = new GradientDrawable();
            promBg.setShape(GradientDrawable.RECTANGLE);
            promBg.setCornerRadius(16 * d);
            promBg.setColor(Color.parseColor("#E0F7FA"));
            promBg.setStroke((int)(1.5f*d), Color.parseColor("#80DEEA"));
            bloquePromedio.setBackground(promBg);

            TextView tvNum = new TextView(this);
            tvNum.setText(promedio > 0 ? String.format("%.1f", promedio) : "—");
            tvNum.setTextSize(48f);
            tvNum.setTypeface(null, Typeface.BOLD);
            tvNum.setTextColor(Color.parseColor("#004D40"));
            tvNum.setGravity(Gravity.CENTER);
            bloquePromedio.addView(tvNum);

            LinearLayout filaEstrellas = new LinearLayout(this);
            filaEstrellas.setOrientation(LinearLayout.HORIZONTAL);
            filaEstrellas.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpFE = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFE.topMargin = (int)(4*d);
            filaEstrellas.setLayoutParams(lpFE);

            int llenas = (int) promedio;
            boolean mediaEstrella = (promedio - llenas) >= 0.25
                    && (promedio - llenas) < 0.75;
            int totalLlenas = (promedio - llenas) >= 0.75 ? llenas + 1 : llenas;

            for (int i = 1; i <= 5; i++) {
                LinearLayout.LayoutParams lpStar = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lpStar.setMargins((int)(1*d), 0, (int)(1*d), 0);

                if (i == totalLlenas + 1 && mediaEstrella) {
                    android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
                    frame.setLayoutParams(lpStar);
                    TextView tvGris = new TextView(this);
                    tvGris.setText("★");
                    tvGris.setTextSize(24f);
                    tvGris.setTextColor(Color.parseColor("#CFD8DC"));
                    frame.addView(tvGris);
                    TextView tvDorada = new TextView(this);
                    tvDorada.setText("★");
                    tvDorada.setTextSize(24f);
                    tvDorada.setTextColor(Color.parseColor("#FFC107"));
                    tvDorada.getViewTreeObserver().addOnPreDrawListener(
                            new android.view.ViewTreeObserver.OnPreDrawListener() {
                                @Override public boolean onPreDraw() {
                                    tvDorada.getViewTreeObserver().removeOnPreDrawListener(this);
                                    int w = tvDorada.getWidth(), h = tvDorada.getHeight();
                                    if (w > 0) {
                                        android.graphics.Bitmap bmp =
                                                android.graphics.Bitmap.createBitmap(w, h,
                                                        android.graphics.Bitmap.Config.ARGB_8888);
                                        android.graphics.Canvas canvas =
                                                new android.graphics.Canvas(bmp);
                                        tvDorada.draw(canvas);
                                        android.graphics.Paint clear =
                                                new android.graphics.Paint();
                                        clear.setXfermode(new android.graphics.PorterDuffXfermode(
                                                android.graphics.PorterDuff.Mode.CLEAR));
                                        canvas.drawRect(w / 2f, 0, w, h, clear);
                                        tvDorada.setBackground(
                                                new android.graphics.drawable.BitmapDrawable(
                                                        getResources(), bmp));
                                        tvDorada.setTextColor(Color.TRANSPARENT);
                                    }
                                    return true;
                                }
                            });
                    frame.addView(tvDorada);
                    filaEstrellas.addView(frame);
                } else {
                    TextView tvStar = new TextView(this);
                    tvStar.setText("★");
                    tvStar.setTextSize(24f);
                    tvStar.setTextColor(i <= totalLlenas
                            ? Color.parseColor("#FFC107")
                            : Color.parseColor("#CFD8DC"));
                    tvStar.setLayoutParams(lpStar);
                    filaEstrellas.addView(tvStar);
                }
            }
            bloquePromedio.addView(filaEstrellas);

            int totalMostrar = total > 0 ? total
                    : (calificaciones != null ? calificaciones.length() : 0);
            TextView tvTotalCal = new TextView(this);
            tvTotalCal.setText(totalMostrar + " calificaci"
                    + (totalMostrar == 1 ? "ón" : "ones"));
            tvTotalCal.setTextSize(11f);
            tvTotalCal.setTextColor(Color.parseColor("#546E7A"));
            tvTotalCal.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpTc = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpTc.topMargin = (int)(3*d);
            tvTotalCal.setLayoutParams(lpTc);
            bloquePromedio.addView(tvTotalCal);
            filaTop.addView(bloquePromedio);

            if (mostrarRanking) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams((int)(12*d), 0));
                filaTop.addView(spacer);

                LinearLayout bloqueRanking = new LinearLayout(this);
                bloqueRanking.setOrientation(LinearLayout.VERTICAL);
                bloqueRanking.setGravity(Gravity.CENTER_HORIZONTAL);
                bloqueRanking.setPadding(p12, p12, p12, p12);
                bloqueRanking.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                String rankBgColor, rankStrokeColor, rankTextColor;
                if (rankingPosicion == 1) {
                    rankBgColor = "#FFF8E1"; rankStrokeColor = "#FFD54F"; rankTextColor = "#E65100";
                } else if (rankingPosicion <= 3) {
                    rankBgColor = "#F3E5F5"; rankStrokeColor = "#CE93D8"; rankTextColor = "#6A1B9A";
                } else if (rankingPosicion <= 10) {
                    rankBgColor = "#E8F5E9"; rankStrokeColor = "#A5D6A7"; rankTextColor = "#1B5E20";
                } else {
                    rankBgColor = "#F5F5F5"; rankStrokeColor = "#BDBDBD"; rankTextColor = "#546E7A";
                }

                GradientDrawable rankBg = new GradientDrawable();
                rankBg.setShape(GradientDrawable.RECTANGLE);
                rankBg.setCornerRadius(16 * d);
                rankBg.setColor(Color.parseColor(rankBgColor));
                rankBg.setStroke((int)(1.5f*d), Color.parseColor(rankStrokeColor));
                bloqueRanking.setBackground(rankBg);

                TextView tvTrofeo = new TextView(this);
                tvTrofeo.setText(rankingPosicion == 1 ? "🥇"
                        : rankingPosicion == 2 ? "🥈"
                        : rankingPosicion == 3 ? "🥉" : "🏅");
                tvTrofeo.setTextSize(32f);
                tvTrofeo.setGravity(Gravity.CENTER);
                bloqueRanking.addView(tvTrofeo);

                TextView tvPosicion = new TextView(this);
                tvPosicion.setText("#" + rankingPosicion);
                tvPosicion.setTextSize(28f);
                tvPosicion.setTypeface(null, Typeface.BOLD);
                tvPosicion.setTextColor(Color.parseColor(rankTextColor));
                tvPosicion.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lpPos = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lpPos.topMargin = (int)(2*d);
                tvPosicion.setLayoutParams(lpPos);
                bloqueRanking.addView(tvPosicion);

                TextView tvDeRanking = new TextView(this);
                String rolLabel = session.isConductor() ? "conductores" : "viajeros";
                tvDeRanking.setText(rankingTotal > 0
                        ? "de " + rankingTotal + "\n" + rolLabel
                        : "en el ranking\nde " + rolLabel);
                tvDeRanking.setTextSize(11f);
                tvDeRanking.setTextColor(Color.parseColor("#546E7A"));
                tvDeRanking.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lpDr = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lpDr.topMargin = (int)(2*d);
                tvDeRanking.setLayoutParams(lpDr);
                bloqueRanking.addView(tvDeRanking);
                filaTop.addView(bloqueRanking);
            }

            inner.addView(filaTop);
        }

        if (calificaciones != null && calificaciones.length() > 0) {
            View sep = new View(this);
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(1*d));
            lpSep.bottomMargin = p12;
            sep.setLayoutParams(lpSep);
            sep.setBackgroundColor(Color.parseColor("#E0F2F1"));
            inner.addView(sep);

            int mostrar = Math.min(calificaciones.length(), 5);
            for (int i = 0; i < mostrar; i++) {
                JSONObject cal = calificaciones.optJSONObject(i);
                if (cal == null) continue;
                inner.addView(crearFilaCalificacion(cal, d, p8, p6, i < mostrar - 1));
            }

            if (calificaciones.length() > 5) {
                MaterialButton btnVerTodas = new MaterialButton(this);
                btnVerTodas.setText("Ver todas (" + calificaciones.length() + ")");
                btnVerTodas.setTextSize(13f);
                btnVerTodas.setTextColor(Color.parseColor("#00897B"));
                btnVerTodas.setBackgroundColor(Color.TRANSPARENT);
                btnVerTodas.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#00897B")));
                btnVerTodas.setStrokeWidth((int)(1.5f*d));
                btnVerTodas.setCornerRadius((int)(12*d));
                LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int)(44*d));
                lpBtn.topMargin = p8;
                btnVerTodas.setLayoutParams(lpBtn);
                final JSONArray fCals = calificaciones;
                final double    fProm = promedio;
                btnVerTodas.setOnClickListener(v -> mostrarTodasCalificaciones(fCals, fProm));
                inner.addView(btnVerTodas);
            }

        } else if (promedio <= 0) {
            LinearLayout estadoVacio = new LinearLayout(this);
            estadoVacio.setOrientation(LinearLayout.VERTICAL);
            estadoVacio.setGravity(Gravity.CENTER_HORIZONTAL);
            estadoVacio.setPadding(p16, p12, p16, p8);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText("");
            tvEmoji.setTextSize(36f);
            tvEmoji.setGravity(Gravity.CENTER);
            estadoVacio.addView(tvEmoji);

            TextView tvSin = new TextView(this);
            tvSin.setText("Aún no tienes calificaciones");
            tvSin.setTextSize(14f);
            tvSin.setTypeface(null, Typeface.BOLD);
            tvSin.setTextColor(Color.parseColor("#455A64"));
            tvSin.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpSin = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpSin.topMargin = p8;
            tvSin.setLayoutParams(lpSin);
            estadoVacio.addView(tvSin);

            TextView tvSub = new TextView(this);
            tvSub.setText("Completa viajes para empezar a recibirlas");
            tvSub.setTextSize(12f);
            tvSub.setTextColor(Color.parseColor("#90A4AE"));
            tvSub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpSub.topMargin = (int)(4*d);
            tvSub.setLayoutParams(lpSub);
            estadoVacio.addView(tvSub);

            inner.addView(estadoVacio);
        }

        card.addView(inner);
        container.addView(card);
    }

    // =========================================================================
    //  FILA INDIVIDUAL DE CALIFICACIÓN  (sin cambios)
    // =========================================================================

    private View crearFilaCalificacion(JSONObject cal, float d,
                                       int p8, int p6, boolean conSeparador) {
        int p4 = (int)(4*d);
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.VERTICAL);
        fila.setPadding(0, p6, 0, p6);

        LinearLayout filaTop = new LinearLayout(this);
        filaTop.setOrientation(LinearLayout.HORIZONTAL);
        filaTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpTop = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTop.bottomMargin = p4;
        filaTop.setLayoutParams(lpTop);

        String nombreCalificador = extraerNombreCalificador(cal);
        int puntuacion = cal.optInt("puntuacion",
                cal.optInt("calificacion",
                        cal.optInt("rating",
                                cal.optInt("estrellas", 0))));

        TextView tvAvatar = new TextView(this);
        tvAvatar.setTextSize(14f);
        tvAvatar.setTypeface(null, Typeface.BOLD);
        tvAvatar.setTextColor(Color.WHITE);
        tvAvatar.setGravity(Gravity.CENTER);
        tvAvatar.setText(nombreCalificador.isEmpty() ? "U"
                : nombreCalificador.substring(0, 1).toUpperCase());
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams(
                (int)(36*d), (int)(36*d));
        lpAv.rightMargin = p8;
        tvAvatar.setLayoutParams(lpAv);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(puntuacion >= 4 ? Color.parseColor("#00897B")
                : puntuacion >= 3     ? Color.parseColor("#F57F17")
                :                       Color.parseColor("#EF5350"));
        tvAvatar.setBackground(avBg);
        filaTop.addView(tvAvatar);

        LinearLayout colNombre = new LinearLayout(this);
        colNombre.setOrientation(LinearLayout.VERTICAL);
        colNombre.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNombreCal = new TextView(this);
        tvNombreCal.setText(nombreCalificador.isEmpty() ? "Usuario" : nombreCalificador);
        tvNombreCal.setTextSize(13.5f);
        tvNombreCal.setTypeface(null, Typeface.BOLD);
        tvNombreCal.setTextColor(Color.parseColor("#1A2035"));
        tvNombreCal.setMaxLines(1);
        tvNombreCal.setEllipsize(android.text.TextUtils.TruncateAt.END);
        colNombre.addView(tvNombreCal);

        String fecha = cal.optString("fecha",
                cal.optString("fechaCalificacion",
                        cal.optString("createdAt", "")));
        if (!fecha.isEmpty() && fecha.length() >= 10) fecha = fecha.substring(0, 10);
        if (!fecha.isEmpty()) {
            TextView tvFecha = new TextView(this);
            tvFecha.setText(fecha);
            tvFecha.setTextSize(11f);
            tvFecha.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpF.topMargin = (int)(2*d);
            tvFecha.setLayoutParams(lpF);
            colNombre.addView(tvFecha);
        }
        filaTop.addView(colNombre);

        LinearLayout filaStars = new LinearLayout(this);
        filaStars.setOrientation(LinearLayout.HORIZONTAL);
        filaStars.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 1; i <= 5; i++) {
            TextView tvS = new TextView(this);
            tvS.setText("★");
            tvS.setTextSize(13f);
            tvS.setTextColor(i <= puntuacion
                    ? Color.parseColor("#FFC107")
                    : Color.parseColor("#CFD8DC"));
            filaStars.addView(tvS);
        }
        filaTop.addView(filaStars);
        fila.addView(filaTop);

        String comentario = cal.optString("comentario",
                cal.optString("comment",
                        cal.optString("descripcion", "")));
        if (!comentario.isEmpty() && !comentario.equals("null")) {
            TextView tvComentario = new TextView(this);
            tvComentario.setText("\"" + comentario + "\"");
            tvComentario.setTextSize(12.5f);
            tvComentario.setTextColor(Color.parseColor("#546E7A"));
            tvComentario.setTypeface(null, Typeface.ITALIC);
            LinearLayout.LayoutParams lpCom = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCom.leftMargin   = (int)(44*d);
            lpCom.bottomMargin = p4;
            tvComentario.setLayoutParams(lpCom);
            fila.addView(tvComentario);
        }

        if (conSeparador) {
            View sep = new View(this);
            LinearLayout.LayoutParams lpSep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(0.8f*d));
            lpSep.topMargin = p4;
            sep.setLayoutParams(lpSep);
            sep.setBackgroundColor(Color.parseColor("#F0F4F8"));
            fila.addView(sep);
        }

        return fila;
    }

    // =========================================================================
    //  DIÁLOGO TODAS LAS CALIFICACIONES  (sin cambios)
    // =========================================================================

    private void mostrarTodasCalificaciones(JSONArray calificaciones, double promedio) {
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this);
        builder.setTitle("Todas mis calificaciones  ⭐ "
                + String.format("%.1f", promedio));

        float d   = getResources().getDisplayMetrics().density;
        int p16   = (int)(16*d), p8 = (int)(8*d), p6 = (int)(6*d);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(p16, p8, p16, p8);

        for (int i = 0; i < calificaciones.length(); i++) {
            JSONObject cal = calificaciones.optJSONObject(i);
            if (cal == null) continue;
            lista.addView(crearFilaCalificacion(
                    cal, d, p8, p6, i < calificaciones.length() - 1));
        }

        sv.addView(lista);
        builder.setView(sv);
        builder.setPositiveButton("Cerrar", null);
        builder.show();
    }

    // =========================================================================
    //  HELPERS  (sin cambios)
    // =========================================================================

    private String extraerNombreCalificador(JSONObject cal) {
        for (String k : new String[]{"calificador", "usuario", "user", "pasajero", "conductor"}) {
            JSONObject obj = cal.optJSONObject(k);
            if (obj != null) {
                for (String nk : new String[]{"nombre", "nombreCompleto", "name"}) {
                    String n = obj.optString(nk, "");
                    if (!n.isEmpty() && !n.equals("null")) return n;
                }
            }
        }
        for (String k : new String[]{"nombreCalificador", "nombreUsuario", "nombre"}) {
            String n = cal.optString(k, "");
            if (!n.isEmpty() && !n.equals("null")) return n;
        }
        return "";
    }

    // =========================================================================
    //  VEHÍCULOS  (sin cambios)
    // =========================================================================

    private void cargarVehiculosDesdeApi() {
        if (layoutVehiculoRoot != null) layoutVehiculoRoot.setVisibility(View.VISIBLE);
        precargarDesdeCache();
        if (!listaVehiculos.isEmpty()) { mostrarCargando(false); actualizarInterfazVehiculos(); }
        else                           { mostrarCargando(true); }

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_VEHICULOS,
                response -> {
                    listaVehiculos.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject v = response.optJSONObject(i);
                        if (v != null) listaVehiculos.add(v);
                    }
                    persistirVehiculosEnCache();
                    mostrarCargando(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    vehiculoSeleccionadoIndex = 0;
                    runOnUiThread(this::actualizarInterfazVehiculos);
                },
                error -> {
                    mostrarCargando(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (!listaVehiculos.isEmpty()) runOnUiThread(this::actualizarInterfazVehiculos);
                    else runOnUiThread(() -> Toast.makeText(this,
                            "Sin conexión y sin datos guardados", Toast.LENGTH_SHORT).show());
                }
        );
    }

    private void precargarDesdeCache() {
        listaVehiculos.clear();
        if (!session.tieneVehiculo()) return;
        try {
            JSONObject cache = new JSONObject();
            cache.put("idVehiculos", session.getIdVehiculo());
            cache.put("marca",       session.getVModelo());
            cache.put("modelo",      "");
            cache.put("placa",       session.getVPlaca());
            cache.put("capacidad",   session.getVCapacidad());
            listaVehiculos.add(cache);
        } catch (Exception ignored) {}
    }

    private void persistirVehiculosEnCache() {
        if (listaVehiculos.isEmpty()) return;
        JSONObject v  = listaVehiculos.get(0);
        String marca  = v.optString("marca",     "");
        String modelo = v.optString("modelo",    "");
        String placa  = v.optString("placa",     "—");
        String cap    = v.optString("capacidad", "—");
        int    idV    = v.optInt("idVehiculos",  -1);
        String nombre = (marca + " " + modelo).trim();
        if (nombre.isEmpty()) nombre = "Vehículo";
        session.saveVehiculo(idV, nombre, placa, cap);
    }

    private void mostrarCargando(boolean cargando) {
        if (progressVehiculo != null)
            progressVehiculo.setVisibility(cargando ? View.VISIBLE : View.GONE);
        if (cargando) {
            if (cardVehiculo           != null) cardVehiculo.setVisibility(View.GONE);
            if (layoutSinVehiculo      != null) layoutSinVehiculo.setVisibility(View.GONE);
            if (layoutSelectorVehiculo != null) layoutSelectorVehiculo.setVisibility(View.GONE);
        }
    }

    private void actualizarInterfazVehiculos() {
        if (listaVehiculos.isEmpty()) {
            if (cardVehiculo           != null) cardVehiculo.setVisibility(View.GONE);
            if (layoutSelectorVehiculo != null) layoutSelectorVehiculo.setVisibility(View.GONE);
            if (layoutSinVehiculo      != null) {
                layoutSinVehiculo.setVisibility(View.VISIBLE);
                if (btnRegistrarVWeb != null)
                    animateButton(btnRegistrarVWeb, () -> abrirWeb(WEB_URL));
            }
            return;
        }
        if (layoutSinVehiculo != null) layoutSinVehiculo.setVisibility(View.GONE);
        if (cardVehiculo      != null) cardVehiculo.setVisibility(View.VISIBLE);
        mostrarVehiculo(vehiculoSeleccionadoIndex);
        if (layoutSelectorVehiculo != null) {
            if (listaVehiculos.size() > 1) {
                layoutSelectorVehiculo.setVisibility(View.VISIBLE);
                actualizarTextoSelector();
                if (tvCambiarVehiculo != null)
                    tvCambiarVehiculo.setOnClickListener(v -> mostrarDialogoSelectorVehiculo());
            } else {
                layoutSelectorVehiculo.setVisibility(View.GONE);
            }
        }
    }

    private void mostrarVehiculo(int index) {
        if (index < 0 || index >= listaVehiculos.size()) return;
        JSONObject v     = listaVehiculos.get(index);
        String marca     = v.optString("marca",     "");
        String modelo    = v.optString("modelo",    "");
        String placa     = v.optString("placa",     "—");
        String capacidad = v.optString("capacidad", "—");
        String nombre    = modelo.isEmpty() ? marca : (marca + " " + modelo).trim();
        if (nombre.isEmpty()) nombre = "Vehículo sin nombre";
        if (tvModelo   != null) tvModelo.setText(nombre);
        if (tvPlaca    != null) tvPlaca.setText("Placa: " + placa);
        if (tvAsientos != null) tvAsientos.setText("Capacidad: " + capacidad + " asientos");
        try { session.saveVehiculo(v.optInt("idVehiculos", -1), nombre, placa, capacidad); }
        catch (Exception ignored) {}
    }

    private void actualizarTextoSelector() {
        if (tvVehiculoActual == null) return;
        tvVehiculoActual.setText("Vehículo "
                + (vehiculoSeleccionadoIndex + 1) + " de " + listaVehiculos.size());
    }

    private void mostrarDialogoSelectorVehiculo() {
        String[] opciones = new String[listaVehiculos.size()];
        for (int i = 0; i < listaVehiculos.size(); i++) {
            JSONObject v  = listaVehiculos.get(i);
            String marca  = v.optString("marca",  "");
            String modelo = v.optString("modelo", "");
            String placa  = v.optString("placa",  "—");
            String nombre = modelo.isEmpty() ? marca : (marca + " " + modelo).trim();
            opciones[i]   = nombre + "  ·  " + placa;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Seleccionar vehículo")
                .setSingleChoiceItems(opciones, vehiculoSeleccionadoIndex, (d, which) -> {
                    vehiculoSeleccionadoIndex = which;
                    mostrarVehiculo(which);
                    actualizarTextoSelector();
                    d.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // =========================================================================
    //  BOTONES  (sin cambios)
    // =========================================================================

    private void configurarBotones() {
        if (btnEditarPerfil != null)
            animateButton(btnEditarPerfil, this::mostrarDialogoEditarWeb);
        if (btnCerrarSesion != null)
            animateButton(btnCerrarSesion, this::confirmarCerrarSesion);
    }

    private void abrirWeb(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void mostrarDialogoEditarWeb() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Editar perfil")
                .setMessage("La edición de tu perfil está disponible únicamente desde nuestra web.\n\n"
                        + "Visita el siguiente enlace para actualizar tu nombre, teléfono y contraseña:\n\n"
                        + WEB_URL)
                .setPositiveButton("🌐 Ir a la web", (d, w) -> abrirWeb(WEB_URL))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarCerrarSesion() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que quieres cerrar tu sesión?")
                .setPositiveButton("Cerrar sesión", (d, w) -> {
                    d.dismiss();
                    cerrarSesionYRedirigir();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void cerrarSesionYRedirigir() {
        session.logout();
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // =========================================================================
    //  BOTTOM NAV  (sin cambios)
    // =========================================================================

    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav == null) return;
        nav.setSelectedItemId(R.id.nav_perfil);
        boolean esConductor = session.isConductor();
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_perfil) return true;
            if (esConductor) {
                if      (id == R.id.nav_inicio)     goTo(HomeConductor.class, Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,  Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,          Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,      Transition.NONE);
            } else {
                if      (id == R.id.nav_inicio)     goTo(HomePasajero.class,        Transition.NONE);
                else if (id == R.id.nav_mis_viajes) goTo(MisReservasActivity.class, Transition.NONE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,                Transition.NONE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,            Transition.NONE);
            }
            finish();
            return true;
        });
    }
}