package com.arlys.moviflexx.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * PublicarViaje — Configuración final del viaje antes de publicarlo.
 *
 * RECIBE de PublicarRuta (via Intent):
 *   - ID_RUTA_CREADA     : int    — idRuta persistido en BD
 *   - ORIGEN_RUTA        : String
 *   - DESTINO_RUTA       : String
 *   - TIPO_TRANSPORTE    : String — "driving" | "motorcycle"
 *   - DISTANCIA_KM       : double
 *   - DURACION_MIN       : double
 *   - FUEL_LITROS        : double
 *   - COSTO_COMBUSTIBLE  : double — precio sugerido en COP
 *
 * PUBLICA al backend:
 *   POST /api/viajes  (§6 del doc arquitectura)
 *   Estado inicial: PROGRAMADO
 *
 * VALIDACIONES aplicadas (§7.1):
 *   - Sesión conductor activa y con vehículo registrado
 *   - Fecha de salida: no puede ser pasada ni más de 30 días adelante
 *   - Precio > 0
 *   - Cupos: mínimo 1, máximo según capacidad del vehículo
 */
public class PublicarViaje extends AppCompatActivity {

    private static final String TAG = "PublicarViaje";

    // Límites de validación (§7.1 del doc)
    private static final int MAX_DIAS_ADELANTE = 30;
    private static final double PRECIO_MINIMO  = 1.0;
    private static final int    CUPOS_MAXIMO   = 8;

    // ─── VISTAS ───────────────────────────────────────────────────────────────
    private TextInputEditText    editFechaHora;
    private TextInputEditText    editPrecio;
    private Spinner              spinnerCupos;
    private CardView             mainCard;
    private CardView             loaderContainer;
    private View                 overlayBackground;
    private MaterialButton       btnPublicar;
    private BottomNavigationView bottomNavigation;

    private TextView txtDestinoInfo;
    private TextView txtOrigenInfo;
    private TextView txtVehiculoInfo;
    private TextView txtConductorInfo;
    private TextView txtPrecioSugerido;

    // ─── SESIÓN ───────────────────────────────────────────────────────────────
    private SessionManager session;

    // ─── DATOS DEL INTENT ────────────────────────────────────────────────────
    private int    rutaId            = 0;
    private int    vehiculoIdInt     = -1;
    private int    conductorIdInt    = -1;
    private String destinoRuta       = "";
    private String origenRuta        = "";
    private double distanciaKm       = 0;
    private double duracionMin       = 0;
    private double fuelLitros        = 0;
    private double costoCombustible  = 0;
    private String tipoTransporte    = "driving";

    // ─── FECHA ────────────────────────────────────────────────────────────────
    private final Calendar calendarioSalida = Calendar.getInstance();
    private boolean fechaSeleccionada = false;

    /* ─── LIFECYCLE ─────────────────────────────────────────────────────────── */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicar_viaje);

        session = new SessionManager(this);

        validarSesion();
        obtenerDatosIntent();
        enlazarVistas();
        configurarUI();
    }

    /* ─── VALIDAR SESIÓN (§9 del doc — seguridad JWT) ────────────────────── */

    private void validarSesion() {
        SesionUsuario.init(this);

        if (!session.isLoggedIn()) {
            irAlLogin();
            return;
        }

        conductorIdInt = session.getIdUsuario();
        vehiculoIdInt  = session.getVehiculoId();

        if (conductorIdInt <= 0 || vehiculoIdInt <= 0) {
            Toast.makeText(this,
                    "⚠️ Debes registrar un vehículo antes de publicar viajes.",
                    Toast.LENGTH_LONG).show();
            irAlLogin();
        }
    }

    /* ─── DATOS DEL INTENT ──────────────────────────────────────────────── */

    private void obtenerDatosIntent() {
        Intent i = getIntent();
        rutaId           = i.getIntExtra("ID_RUTA_CREADA", 0);
        destinoRuta      = i.getStringExtra("DESTINO_RUTA");
        origenRuta       = i.getStringExtra("ORIGEN_RUTA");
        distanciaKm      = i.getDoubleExtra("DISTANCIA_KM", 0);
        duracionMin      = i.getDoubleExtra("DURACION_MIN", 0);
        fuelLitros       = i.getDoubleExtra("FUEL_LITROS", 0);
        costoCombustible = i.getDoubleExtra("COSTO_COMBUSTIBLE", 0);
        String tipoIntent = i.getStringExtra("TIPO_TRANSPORTE");
        tipoTransporte = (tipoIntent != null && !tipoIntent.isEmpty()) ? tipoIntent : "driving";

        destinoRuta = limpiarTexto(destinoRuta, "Destino");
        origenRuta  = limpiarTexto(origenRuta,  "Origen");
        destinoRuta = capitalizarTexto(destinoRuta);
        origenRuta  = capitalizarTexto(origenRuta);

        Log.d(TAG, "Intent → rutaId=" + rutaId + " | origen=" + origenRuta
                + " | destino=" + destinoRuta + " | km=" + distanciaKm
                + " | combustible=$" + costoCombustible);

        if (rutaId == 0) {
            Toast.makeText(this, "⚠️ Ruta inválida. Vuelve a calcular.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /* ─── ENLAZAR VISTAS ─────────────────────────────────────────────────── */

    private void enlazarVistas() {
        editFechaHora     = findViewById(R.id.edit_fecha_hora);
        editPrecio        = findViewById(R.id.edit_precio);
        spinnerCupos      = findViewById(R.id.spinnerCupo);
        mainCard          = findViewById(R.id.main_card);
        loaderContainer   = findViewById(R.id.loader_container);
        overlayBackground = findViewById(R.id.overlay_background);
        btnPublicar       = findViewById(R.id.btn_publicar_viaje);
        bottomNavigation  = findViewById(R.id.bottom_navigation);
        txtDestinoInfo    = findViewById(R.id.txt_destino_info);
        txtOrigenInfo     = findViewById(R.id.txt_origen_info);
        txtVehiculoInfo   = findViewById(R.id.txt_vehiculo_info);
        txtConductorInfo  = findViewById(R.id.txt_conductor_info);
        txtPrecioSugerido = findViewById(R.id.txt_precio_sugerido);
    }

    /* ─── CONFIGURAR UI ──────────────────────────────────────────────────── */

    private void configurarUI() {
        txtDestinoInfo.setText("🏁 " + destinoRuta);
        txtOrigenInfo.setText("📍 " + origenRuta);

        String vehiculoNombre = limpiarTexto(session.getVehiculoNombre(), "Vehículo");
        String vehiculoPlaca  = limpiarTexto(session.getVehiculoPlaca(),  "Sin placa");
        txtVehiculoInfo.setText("🚘 " + vehiculoNombre + " • " + vehiculoPlaca);

        String nombreConductor = limpiarTexto(session.getNombre(), "Conductor");
        txtConductorInfo.setText("🚗 Conductor: " + nombreConductor);

        Log.d(TAG, "Session → nombre=" + session.getNombre()
                + " | vehiculo=" + session.getVehiculoNombre()
                + " | placa=" + session.getVehiculoPlaca()
                + " | capacidad=" + session.getVCapacidad());

        configurarSpinnerConCapacidad();
        colocarPrecioAutomatico();
        configurarSelectorFechaHora();
        configurarBotonPublicar();
        configurarBottomNavigation();
        animarEntradaMainCard();
    }

    /* ─── SPINNER CUPOS (§7.1: mínimo 1, máximo capacidad vehículo) ─────── */

    private void configurarSpinnerConCapacidad() {
        String capacidadStr = session.getVCapacidad();
        int capacidad = 4;
        if (capacidadStr != null && !capacidadStr.trim().isEmpty()) {
            try { capacidad = Integer.parseInt(capacidadStr.trim()); }
            catch (NumberFormatException e) { Log.w(TAG, "Capacidad inválida: " + capacidadStr); }
        }
        int maxCupos = Math.min(Math.max(capacidad, 1), CUPOS_MAXIMO);
        String[] opciones = new String[maxCupos];
        for (int i = 0; i < maxCupos; i++) opciones[i] = String.valueOf(i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, opciones);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCupos.setAdapter(adapter);
        spinnerCupos.setSelection(maxCupos - 1);
        Log.d(TAG, "Spinner cupos: 1–" + maxCupos);
    }

    /* ─── PRECIO AUTOMÁTICO ─────────────────────────────────────────────── */

    private void colocarPrecioAutomatico() {
        double precioFinal;
        String origenPrecio;

        if (costoCombustible > 0) {
            // Precio basado en costo real de combustible del backend FastAPI
            precioFinal  = costoCombustible;
            origenPrecio = "ruta";
        } else if (distanciaKm > 0) {
            // Estimado: $700/km (tarifa local Popayán)
            precioFinal  = distanciaKm * 700.0;
            origenPrecio = "km";
        } else {
            // Precio base mínimo
            precioFinal  = 5000.0;
            origenPrecio = "base";
        }

        // Redondear al centenar superior (§7.1: precio > 0)
        precioFinal = Math.ceil(precioFinal / 100.0) * 100.0;
        precioFinal = Math.max(precioFinal, PRECIO_MINIMO);

        editPrecio.setText(String.format(Locale.getDefault(), "%.0f", precioFinal));

        if (txtPrecioSugerido != null) {
            StringBuilder detalle = new StringBuilder();
            switch (origenPrecio) {
                case "ruta":
                    detalle.append(String.format(Locale.getDefault(),
                            "💡 Precio de la ruta  ·  ⛽ $%,.0f COP combustible", costoCombustible));
                    break;
                case "km":
                    detalle.append("💡 Estimado: $700/km");
                    break;
                default:
                    detalle.append("💡 Precio mínimo base");
            }
            if (distanciaKm > 0)
                detalle.append(String.format(Locale.getDefault(), "  ·  📏 %.1f km", distanciaKm));
            if (duracionMin > 0)
                detalle.append(String.format(Locale.getDefault(), "  ·  ⏱ %.0f min", duracionMin));
            detalle.append("  ·  editable ✏️");
            txtPrecioSugerido.setText(detalle.toString());
            txtPrecioSugerido.setVisibility(View.VISIBLE);
        }
        Log.d(TAG, "Precio sugerido: $" + precioFinal + " COP (origen=" + origenPrecio + ")");
    }

    /* ─── SELECTOR DE FECHA Y HORA ───────────────────────────────────────── */

    private void configurarSelectorFechaHora() {
        actualizarCampoFechaHora();
        editFechaHora.setFocusable(false);
        editFechaHora.setClickable(true);
        editFechaHora.setOnClickListener(v -> mostrarDialogoFecha());
    }

    private void mostrarDialogoFecha() {
        Calendar hoy = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    calendarioSalida.set(Calendar.YEAR,         year);
                    calendarioSalida.set(Calendar.MONTH,        month);
                    calendarioSalida.set(Calendar.DAY_OF_MONTH, day);
                    mostrarDialogoHora();
                },
                hoy.get(Calendar.YEAR),
                hoy.get(Calendar.MONTH),
                hoy.get(Calendar.DAY_OF_MONTH)
        ) {{
            // Rango permitido: hoy hasta 30 días adelante (§7.1)
            getDatePicker().setMinDate(hoy.getTimeInMillis());
            Calendar maxFecha = Calendar.getInstance();
            maxFecha.add(Calendar.DAY_OF_YEAR, MAX_DIAS_ADELANTE);
            getDatePicker().setMaxDate(maxFecha.getTimeInMillis());
        }}.show();
    }

    private void mostrarDialogoHora() {
        Calendar hoy = Calendar.getInstance();
        new TimePickerDialog(
                this,
                (view, hora, minuto) -> {
                    calendarioSalida.set(Calendar.HOUR_OF_DAY, hora);
                    calendarioSalida.set(Calendar.MINUTE,      minuto);
                    calendarioSalida.set(Calendar.SECOND,      0);
                    fechaSeleccionada = true;
                    actualizarCampoFechaHora();
                    editFechaHora.setError(null);
                },
                hoy.get(Calendar.HOUR_OF_DAY),
                hoy.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void actualizarCampoFechaHora() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        editFechaHora.setText(sdf.format(calendarioSalida.getTime()));
    }

    /* ─── BOTÓN PUBLICAR ─────────────────────────────────────────────────── */

    private void configurarBotonPublicar() {
        btnPublicar.setOnClickListener(v -> {
            animarBoton(btnPublicar);
            if (validarFormulario()) publicarViaje();
        });
    }

    /* ─── VALIDACIONES (§7.1 y §7.2) ────────────────────────────────────── */

    private boolean validarFormulario() {
        // Fecha: requerida y futura (§7.1)
        if (!fechaSeleccionada) {
            editFechaHora.setError("Selecciona fecha y hora de salida");
            animarError(mainCard);
            Toast.makeText(this, "📅 Selecciona la fecha y hora de salida", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (calendarioSalida.getTimeInMillis() <= System.currentTimeMillis()) {
            editFechaHora.setError("La hora de salida debe ser futura");
            animarError(mainCard);
            Toast.makeText(this, "⏰ La hora de salida debe ser futura", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Validar que no es más de 30 días adelante (§7.1)
        Calendar maxFecha = Calendar.getInstance();
        maxFecha.add(Calendar.DAY_OF_YEAR, MAX_DIAS_ADELANTE);
        if (calendarioSalida.after(maxFecha)) {
            editFechaHora.setError("La salida no puede ser en más de " + MAX_DIAS_ADELANTE + " días");
            animarError(mainCard);
            Toast.makeText(this,
                    "📅 La fecha no puede estar a más de " + MAX_DIAS_ADELANTE + " días",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // Precio: requerido y > 0 (§7.1)
        String precioTxt = editPrecio.getText() != null
                ? editPrecio.getText().toString().trim() : "";
        if (precioTxt.isEmpty()) {
            editPrecio.setError("Ingresa el precio por pasajero");
            animarError(mainCard);
            return false;
        }
        try {
            double precio = Double.parseDouble(precioTxt);
            if (precio < PRECIO_MINIMO) {
                editPrecio.setError("El precio debe ser mayor a $0");
                animarError(mainCard);
                return false;
            }
        } catch (NumberFormatException e) {
            editPrecio.setError("Precio inválido");
            animarError(mainCard);
            return false;
        }

        return true;
    }

    /* ─── PUBLICAR VIAJE (POST /api/viajes) ──────────────────────────────── */

    private void publicarViaje() {
        String token = SesionUsuario.getToken();
        if (token == null || token.isEmpty()) { irAlLogin(); return; }

        mostrarLoader(true);

        try {
            int    cupos     = Integer.parseInt(spinnerCupos.getSelectedItem().toString());
            double precio    = Double.parseDouble(editPrecio.getText().toString().trim());
            String fechaHora = editFechaHora.getText().toString().trim();

            // Cuerpo del POST según §6.1 del doc de arquitectura
            JSONObject body = new JSONObject();
            body.put("idRuta",           rutaId);
            body.put("idVehiculos",      vehiculoIdInt);
            body.put("idConductor",      conductorIdInt);
            body.put("fechaHoraSalida",  fechaHora);
            body.put("cuposTotales",     cupos);
            body.put("cuposDisponibles", cupos);
            body.put("precio",           precio);
            body.put("estado",           "PROGRAMADO");   // estado inicial según doc

            // Datos adicionales para trazabilidad
            if (distanciaKm > 0)      body.put("distanciaKm",      distanciaKm);
            if (duracionMin > 0)      body.put("duracionMin",       duracionMin);
            if (costoCombustible > 0) body.put("costoCombustible",  costoCombustible);
            if (fuelLitros > 0)       body.put("combustibleLitros", fuelLitros);

            Log.d(TAG, "POST /api/viajes → " + body.toString());

            ConexionApi.getInstance(this).post(
                    Constantes.VIAJES,
                    body,
                    response -> {
                        mostrarLoader(false);
                        Log.d(TAG, "Viaje publicado: " + response.toString());
                        Toast.makeText(this, "✅ ¡Viaje publicado correctamente!", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, HomeConductor.class));
                        finish();
                    },
                    error -> {
                        mostrarLoader(false);
                        animarError(mainCard);

                        // Manejo de errores HTTP (§7.2 del doc)
                        String msg = "❌ Error al publicar el viaje";
                        if (error != null && error.networkResponse != null) {
                            int code = error.networkResponse.statusCode;
                            switch (code) {
                                case 400: msg = "❌ Datos inválidos (400)"; break;
                                case 401: msg = "❌ Sesión expirada. Inicia sesión de nuevo (401)"; break;
                                case 403: msg = "❌ Sin permisos para publicar viajes (403)"; break;
                                case 404: msg = "❌ Ruta no encontrada. Vuelve a crearla (404)"; break;
                                case 409: msg = "❌ Ya existe un viaje con estos datos (409)"; break;
                                case 422: msg = "❌ Validación fallida: revisa los datos (422)"; break;
                                case 429: msg = "❌ Demasiadas solicitudes. Espera un momento (429)"; break;
                                case 500: msg = "❌ Error en el servidor, intenta más tarde (500)"; break;
                            }
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Error Volley publicarViaje: " + error.toString());
                    }
            );

        } catch (Exception e) {
            mostrarLoader(false);
            Log.e(TAG, "Error construyendo JSON del viaje: " + e.getMessage());
            Toast.makeText(this, "Error inesperado. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
        }
    }

    /* ─── LOADER ─────────────────────────────────────────────────────────── */

    private void mostrarLoader(boolean mostrar) {
        overlayBackground.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        loaderContainer.setVisibility(mostrar   ? View.VISIBLE : View.GONE);
        btnPublicar.setEnabled(!mostrar);
        btnPublicar.setText(mostrar ? "Publicando..." : "PUBLICAR VIAJE EN POPAYÁN");
    }

    /* ─── BOTTOM NAVIGATION ──────────────────────────────────────────────── */

    private void configurarBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            startActivity(new Intent(this, HomeConductor.class));
            finish();
            return true;
        });
    }

    /* ─── NAVEGACIÓN ─────────────────────────────────────────────────────── */

    private void irAlLogin() {
        SesionUsuario.cerrarSesion();
        startActivity(new Intent(this, Login.class));
        finish();
    }

    /* ─── HELPERS DE TEXTO ───────────────────────────────────────────────── */

    /**
     * Limpia valores del SessionManager o del Intent.
     * Descarta tokens JWT u otros strings técnicos largos sin espacios (> 30 chars).
     */
    private String limpiarTexto(String valor, String fallback) {
        if (valor == null || valor.trim().isEmpty()) return fallback;
        String v = valor.trim();
        if (v.length() > 30 && !v.contains(" ")) return fallback;
        return v;
    }

    /** "san eduardo primera etapa" → "San Eduardo Primera Etapa" */
    private String capitalizarTexto(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        String[] palabras = texto.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    /* ─── ANIMACIONES ─────────────────────────────────────────────────────── */

    private void animarEntradaMainCard() {
        mainCard.setAlpha(0f);
        mainCard.setTranslationY(60f);
        mainCard.animate().alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(150)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animarError(View view) {
        ObjectAnimator.ofFloat(view, "translationX",
                        0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f)
                .setDuration(450).start();
    }

    private void animarBoton(View btn) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0.94f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0.94f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
    }
}