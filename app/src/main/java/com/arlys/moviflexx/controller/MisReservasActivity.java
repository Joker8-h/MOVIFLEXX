package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

public class MisReservasActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private RecyclerView           rvReservas;
    private ProgressBar            progressBar;
    private LinearLayout           layoutSinReservas;
    private BottomNavigationView   bottomNavigation;

    // ── Data ──────────────────────────────────────────────────────────────────
    private SessionManager              session;
    private ReservasEmbedAdapter        adapter;
    private final List<JSONObject>      reservas = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_reservas);

        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        session.loadSessionToMemory();
        initViews();
        setupRecyclerView();
        configurarBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisReservas();
        if (bottomNavigation != null)
            bottomNavigation.setSelectedItemId(R.id.nav_mis_viajes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INIT VIEWS
    // ─────────────────────────────────────────────────────────────────────────

    private void initViews() {
        rvReservas        = findViewById(R.id.rv_reservas);
        progressBar       = findViewById(R.id.progress_bar);
        layoutSinReservas = findViewById(R.id.layout_sin_reservas);
        bottomNavigation  = findViewById(R.id.bottom_navigation);

        MaterialButton btnVolver = findViewById(R.id.btn_volver);
        if (btnVolver != null)
            btnVolver.setOnClickListener(v -> finish());

        MaterialButton btnBuscarViajes = findViewById(R.id.btn_buscar_viajes);
        if (btnBuscarViajes != null)
            btnBuscarViajes.setOnClickListener(v ->
                    startActivity(new Intent(this, RutasFrecuentes.class)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RECYCLER — usa adapter embebido, sin depender de ReservasAdapter.java
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        // 1. Crear adapter
        adapter = new ReservasEmbedAdapter(reservas,
                this::abrirDetalleViaje,
                this::mostrarDialogoCancelar);

        // 2. Asignar al RecyclerView
        rvReservas.setLayoutManager(new LinearLayoutManager(this));
        rvReservas.setAdapter(adapter);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NAVEGAR AL DETALLE
    // ─────────────────────────────────────────────────────────────────────────

    private void abrirDetalleViaje(JSONObject reserva) {
        int idViaje = reserva.optInt("idViaje", 0);
        if (idViaje == 0) {
            JSONObject viajeObj = reserva.optJSONObject("viaje");
            if (viajeObj != null)
                idViaje = viajeObj.optInt("idViaje", viajeObj.optInt("id", 0));
        }
        if (idViaje > 0) {
            Intent intent = new Intent(this, DetalleViajeActivity.class);
            intent.putExtra("ID_VIAJE", idViaje);
            startActivity(intent);
        } else {
            Toast.makeText(this, "No se pudo abrir el viaje", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API: GET /api/reservas/mis-reservas
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarMisReservas() {
        mostrarEstado(Estado.CARGANDO);

        ConexionApi.getInstance(this).getArray(
                Constantes.MIS_RESERVAS,
                response -> runOnUiThread(() -> {
                    reservas.clear();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject r = response.optJSONObject(i);
                        if (r != null) reservas.add(r);
                    }
                    adapter.notifyDataSetChanged();
                    mostrarEstado(reservas.isEmpty() ? Estado.VACIO : Estado.LISTA);
                }),
                error -> runOnUiThread(() -> {
                    mostrarEstado(Estado.VACIO);
                    Toast.makeText(this, "Error al cargar reservas",
                            Toast.LENGTH_LONG).show();
                })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ESTADOS DE UI
    // ─────────────────────────────────────────────────────────────────────────

    private enum Estado { CARGANDO, LISTA, VACIO }

    private void mostrarEstado(Estado estado) {
        progressBar.setVisibility(
                estado == Estado.CARGANDO ? View.VISIBLE : View.GONE);
        rvReservas.setVisibility(
                estado == Estado.LISTA    ? View.VISIBLE : View.GONE);
        layoutSinReservas.setVisibility(
                estado == Estado.VACIO    ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CANCELAR  →  POST /api/reservas/{id}/cancelar
    // ─────────────────────────────────────────────────────────────────────────

    private void mostrarDialogoCancelar(JSONObject reserva) {
        String estado = reserva.optString("estado", "").toUpperCase();
        if (!estado.equals("ACTIVA") && !estado.equals("CONFIRMADA")
                && !estado.equals("PENDIENTE")) {
            Toast.makeText(this,
                    "No se puede cancelar (estado: " + estado + ")",
                    Toast.LENGTH_LONG).show();
            return;
        }

        int    idReserva = reserva.optInt("idReserva", reserva.optInt("id", 0));
        String origen = "", destino = "";
        JSONObject viajeObj = reserva.optJSONObject("viaje");
        if (viajeObj != null) {
            JSONObject rutaObj = viajeObj.optJSONObject("ruta");
            if (rutaObj != null) {
                origen  = rutaObj.optString("origen",  "");
                destino = rutaObj.optString("destino", "");
            }
        }
        if (origen.isEmpty())  origen  = reserva.optString("origen",  "Origen");
        if (destino.isEmpty()) destino = reserva.optString("destino", "Destino");

        final String ruta = origen + " → " + destino;

        new AlertDialog.Builder(this)
                .setTitle("Cancelar Reserva")
                .setMessage("¿Cancelar tu reserva?\n\n📍 " + ruta)
                .setPositiveButton("Sí, cancelar",
                        (d, w) -> ejecutarCancelacion(idReserva))
                .setNegativeButton("No", null)
                .show();
    }

    private void ejecutarCancelacion(int idReserva) {
        if (idReserva <= 0) return;
        mostrarEstado(Estado.CARGANDO);
        ConexionApi.getInstance(this).post(
                Constantes.reservaCancelar((long) idReserva),
                null,
                response -> runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Reserva cancelada",
                            Toast.LENGTH_SHORT).show();
                    cargarMisReservas();
                }),
                error -> runOnUiThread(() -> {
                    mostrarEstado(Estado.LISTA);
                    String msg = "Error al cancelar";
                    if (error != null && error.networkResponse != null) {
                        int code = error.networkResponse.statusCode;
                        if      (code == 400) msg = "No se puede cancelar esta reserva";
                        else if (code == 404) msg = "Reserva no encontrada";
                        else if (code == 403) msg = "Sin permiso para cancelar";
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BOTTOM NAV
    // ─────────────────────────────────────────────────────────────────────────

    private void configurarBottomNav() {
        if (bottomNavigation == null) return;
        bottomNavigation.setSelectedItemId(R.id.nav_mis_viajes);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_inicio)     { startActivity(new Intent(this, HomePasajero.class));  finish(); return true; }
            else if (id == R.id.nav_mis_viajes) { return true; }
            else if (id == R.id.nav_mapa)       { startActivity(new Intent(this, Mapa.class));           return true; }
            else if (id == R.id.nav_mensajes)   { startActivity(new Intent(this, Mensajes.class));       return true; }
            else if (id == R.id.nav_perfil)     { startActivity(new Intent(this, PerfilUsuario.class));  return true; }
            return false;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ADAPTER EMBEBIDO — no depende de ReservasAdapter.java externo
    // ═══════════════════════════════════════════════════════════════════════

    static class ReservasEmbedAdapter
            extends RecyclerView.Adapter<ReservasEmbedAdapter.VH> {

        interface Accion { void ejecutar(JSONObject reserva); }

        private final List<JSONObject> items;
        private final Accion           onVer;
        private final Accion           onCancelar;

        ReservasEmbedAdapter(List<JSONObject> items, Accion onVer, Accion onCancelar) {
            this.items      = items;
            this.onVer      = onVer;
            this.onCancelar = onCancelar;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            float d = parent.getContext().getResources().getDisplayMetrics().density;
            lp.setMargins(0, 0, 0, (int)(12 * d));
            card.setLayoutParams(lp);
            card.setRadius((int)(16 * d));
            card.setCardElevation((int)(4 * d));
            card.setCardBackgroundColor(Color.WHITE);
            card.setUseCompatPadding(true);
            card.setClickable(true);
            card.setFocusable(true);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            JSONObject reserva = items.get(position);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            card.removeAllViews();

            float d   = card.getContext().getResources().getDisplayMetrics().density;
            int   p16 = (int)(16 * d);
            int   p8  = (int)(8  * d);
            int   p4  = (int)(4  * d);

            // ── Extraer datos del JSON ────────────────────────────────────
            JSONObject viajeObj = reserva.optJSONObject("viaje");
            JSONObject rutaObj  = viajeObj != null ? viajeObj.optJSONObject("ruta") : null;

            String origen  = rutaObj  != null ? rutaObj.optString("origen",  "") :
                    viajeObj != null ? viajeObj.optString("origen", "") : "";
            String destino = rutaObj  != null ? rutaObj.optString("destino",  "") :
                    viajeObj != null ? viajeObj.optString("destino", "") : "";
            if (origen.isEmpty())  origen  = reserva.optString("origen",  "Origen");
            if (destino.isEmpty()) destino = reserva.optString("destino", "Destino");

            String nombreParada = reserva.optString("nombreParada", destino);
            String estado       = reserva.optString("estado", "ACTIVA").toUpperCase();
            String codigo       = reserva.optString("codigoReserva",
                    reserva.optString("codigo", ""));
            int    asientos     = reserva.optInt("numeroAsientos",
                    reserva.optInt("asientos", 1));

            double precio = 0;
            if (viajeObj != null) precio = viajeObj.optDouble("precio", 0);
            if (precio == 0)      precio = reserva.optDouble("precio",  0);

            String fechaHora = viajeObj != null
                    ? viajeObj.optString("fechaHoraSalida",
                    viajeObj.optString("fechaSalida", "")) : "";
            if (fechaHora.isEmpty())
                fechaHora = reserva.optString("fechaHoraSalida", "");

            // ── Color según estado ────────────────────────────────────────
            int colorTexto, colorFondo;
            String etiqueta;
            switch (estado) {
                case "ACTIVA": case "CONFIRMADA":
                    etiqueta = "✅ Confirmada"; colorTexto = 0xFF2E7D32; colorFondo = 0xFFF1F8E9; break;
                case "PENDIENTE":
                    etiqueta = "⏳ Pendiente";  colorTexto = 0xFFE65100; colorFondo = 0xFFFFF3E0; break;
                case "EN_CURSO": case "INICIADO":
                    etiqueta = "🚗 En curso";   colorTexto = 0xFF1565C0; colorFondo = 0xFFE3F2FD; break;
                case "COMPLETADA": case "FINALIZADA": case "FINALIZADO":
                    etiqueta = "🏁 Completada"; colorTexto = 0xFF00695C; colorFondo = 0xFFE0F2F1; break;
                case "CANCELADA": case "CANCELADO":
                    etiqueta = "❌ Cancelada";  colorTexto = 0xFFC62828; colorFondo = 0xFFFFEBEE; break;
                default:
                    etiqueta = "📌 " + estado;  colorTexto = 0xFF546E7A; colorFondo = Color.WHITE;
            }
            card.setCardBackgroundColor(colorFondo);

            // ── Construir layout ──────────────────────────────────────────
            LinearLayout root = new LinearLayout(card.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(p16, p16, p16, p16);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Fila estado + código
            LinearLayout filaTop = new LinearLayout(card.getContext());
            filaTop.setOrientation(LinearLayout.HORIZONTAL);
            filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpTop = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTop.bottomMargin = p8;
            filaTop.setLayoutParams(lpTop);

            TextView tvEstado = new TextView(card.getContext());
            tvEstado.setText(etiqueta);
            tvEstado.setTextSize(13f);
            tvEstado.setTypeface(null, android.graphics.Typeface.BOLD);
            tvEstado.setTextColor(colorTexto);
            tvEstado.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            filaTop.addView(tvEstado);

            if (!codigo.isEmpty()) {
                TextView tvCodigo = new TextView(card.getContext());
                tvCodigo.setText("# " + codigo);
                tvCodigo.setTextSize(11f);
                tvCodigo.setTextColor(0xFF90A4AE);
                filaTop.addView(tvCodigo);
            }

            // Origen
            TextView tvOrigen = new TextView(card.getContext());
            tvOrigen.setText("📍 " + origen);
            tvOrigen.setTextSize(15f);
            tvOrigen.setTypeface(null, android.graphics.Typeface.BOLD);
            tvOrigen.setTextColor(0xFF004D40);

            // Flecha
            TextView tvFlecha = new TextView(card.getContext());
            tvFlecha.setText("    ↓");
            tvFlecha.setTextSize(12f);
            tvFlecha.setTextColor(0xFF90A4AE);
            LinearLayout.LayoutParams lpFlecha = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpFlecha.topMargin = p4 / 2; lpFlecha.bottomMargin = p4 / 2;
            tvFlecha.setLayoutParams(lpFlecha);

            // Destino
            TextView tvDestino = new TextView(card.getContext());
            tvDestino.setText("📍 " + destino);
            tvDestino.setTextSize(15f);
            tvDestino.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDestino.setTextColor(0xFF004D40);

            // Parada
            TextView tvParada = new TextView(card.getContext());
            tvParada.setText("🔵 Bajarás en: " + nombreParada);
            tvParada.setTextSize(12f);
            tvParada.setTextColor(0xFF00897B);
            tvParada.setTypeface(null, android.graphics.Typeface.BOLD);
            tvParada.setBackgroundColor(0xFFE0F2F1);
            tvParada.setPadding(p8, p4, p8, p4);
            LinearLayout.LayoutParams lpParada = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpParada.topMargin = p8; lpParada.bottomMargin = p8;
            tvParada.setLayoutParams(lpParada);

            root.addView(filaTop);
            root.addView(tvOrigen);
            root.addView(tvFlecha);
            root.addView(tvDestino);
            root.addView(tvParada);

            // Fila asientos + precio
            LinearLayout filaMid = new LinearLayout(card.getContext());
            filaMid.setOrientation(LinearLayout.HORIZONTAL);
            filaMid.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpMid = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpMid.bottomMargin = p4;
            filaMid.setLayoutParams(lpMid);

            TextView tvAsientos = new TextView(card.getContext());
            tvAsientos.setText("👥 " + asientos + " asiento" + (asientos == 1 ? "" : "s"));
            tvAsientos.setTextSize(12f);
            tvAsientos.setTextColor(0xFF546E7A);
            tvAsientos.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            filaMid.addView(tvAsientos);

            if (precio > 0) {
                TextView tvPrecio = new TextView(card.getContext());
                tvPrecio.setText("💵 $" + String.format("%,.0f", precio));
                tvPrecio.setTextSize(15f);
                tvPrecio.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPrecio.setTextColor(0xFFFF6F00);
                filaMid.addView(tvPrecio);
            }
            root.addView(filaMid);

            // Fecha
            if (!fechaHora.isEmpty()) {
                TextView tvFecha = new TextView(card.getContext());
                tvFecha.setText("📅 " + formatFecha(fechaHora));
                tvFecha.setTextSize(12f);
                tvFecha.setTextColor(0xFF546E7A);
                LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpF.bottomMargin = p8;
                tvFecha.setLayoutParams(lpF);
                root.addView(tvFecha);
            }

            // Botón cancelar
            boolean puedeAnular = estado.equals("ACTIVA") || estado.equals("CONFIRMADA")
                    || estado.equals("PENDIENTE");
            if (puedeAnular) {
                android.widget.Button btnCancelar = new android.widget.Button(card.getContext());
                btnCancelar.setText("Cancelar Reserva");
                btnCancelar.setTextColor(0xFFEF5350);
                btnCancelar.setBackgroundColor(0x00FFFFFF);
                btnCancelar.setAllCaps(false);
                btnCancelar.setTextSize(14f);
                android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
                border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                border.setStroke((int)(1 * d), 0xFFEF5350);
                border.setCornerRadius((int)(12 * d));
                border.setColor(0x00FFFFFF);
                btnCancelar.setBackground(border);
                LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * d));
                lpBtn.topMargin = p4;
                btnCancelar.setLayoutParams(lpBtn);
                btnCancelar.setOnClickListener(v -> onCancelar.ejecutar(reserva));
                root.addView(btnCancelar);
            }

            card.addView(root);
            card.setOnClickListener(v -> onVer.ejecutar(reserva));
        }

        private String formatFecha(String f) {
            try {
                String s = f.replace("T", " ");
                String[] p = s.split(" ");
                if (p.length >= 2) {
                    String[] fecha = p[0].split("-");
                    String hora = p[1].length() >= 5 ? p[1].substring(0, 5) : p[1];
                    if (fecha.length == 3)
                        return fecha[2] + "/" + fecha[1] + "/" + fecha[0] + "  " + hora;
                }
                return f;
            } catch (Exception e) { return f; }
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(android.view.View v) { super(v); }
        }
    }
}