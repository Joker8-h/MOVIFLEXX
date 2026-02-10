package com.arlys.moviflexx.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetalleViajeActivity extends AppCompatActivity {

    private static final String TAG = "DetalleViaje";
    private static final int REQ_LOCATION = 1002;

    // UI Components
    private MapView map;
    private TextView txtRuta, txtEstado, txtConductor, txtVehiculo;
    private Button btnIniciar, btnFinalizar, btnAgregarParada, btnSeleccionarParada;

    // Data
    private int viajeId;
    private int rutaId;
    private boolean esConductor;
    private String origenActual = "Popayán Centro";
    private String destinoActual = "Universidad del Cauca";
    private ArrayList<JSONObject> paradasDisponibles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar OSMDroid ANTES de setContentView
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_detalle_viaje2);

        // Obtener datos del Intent
        viajeId = getIntent().getIntExtra("ID_VIAJE", 0);
        esConductor = new SessionManager(this).isConductor();

        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "    DETALLE VIAJE - INICIO");
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "ViajeID: " + viajeId);
        Log.d(TAG, "Es Conductor: " + esConductor);
        Log.d(TAG, "═══════════════════════════════════════");

        if (viajeId == 0) {
            Toast.makeText(this, "⚠️ ID de viaje inválido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        configurarMapa();
        verificarPermisos();
        cargarDetalleViaje();
    }

    private void initViews() {
        try {
            map = findViewById(R.id.map_mini);
            txtRuta = findViewById(R.id.txt_info_ruta);
            txtEstado = findViewById(R.id.txt_estado);
            txtConductor = findViewById(R.id.txt_conductor);
            txtVehiculo = findViewById(R.id.txt_vehiculo);

            btnIniciar = findViewById(R.id.btn_iniciar);
            btnFinalizar = findViewById(R.id.btn_finalizar);
            btnAgregarParada = findViewById(R.id.btn_parada);
            btnSeleccionarParada = findViewById(R.id.btn_seleccionar_parada);

            // Configurar listeners
            btnIniciar.setOnClickListener(v -> cambiarEstado("iniciar"));
            btnFinalizar.setOnClickListener(v -> cambiarEstado("finalizar"));
            btnAgregarParada.setOnClickListener(v -> mostrarDialogoAgregarParada());
            btnSeleccionarParada.setOnClickListener(v -> mostrarDialogoSeleccionarParada());

            Log.d(TAG, "✅ Vistas inicializadas correctamente");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando vistas", e);
            Toast.makeText(this, "Error inicializando interfaz", Toast.LENGTH_SHORT).show();
        }
    }

    private void configurarMapa() {
        try {
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setMultiTouchControls(true);
            map.getController().setZoom(13.5);

            // Centrar en Popayán por defecto (Coordenadas de Popayán, Cauca, Colombia)
            GeoPoint popayan = new GeoPoint(2.4419, -76.6063);
            map.getController().setCenter(popayan);

            Log.d(TAG, "✅ Mapa configurado - Centro: Popayán");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error configurando mapa", e);
        }
    }

    private void verificarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION
            );
            Log.d(TAG, "📍 Solicitando permisos de ubicación...");
        } else {
            Log.d(TAG, "✅ Permisos de ubicación ya concedidos");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_LOCATION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Permisos de ubicación concedidos");
            cargarDetalleViaje();
        } else {
            Log.w(TAG, "⚠️ Permisos de ubicación denegados");
        }
    }

    private void cargarDetalleViaje() {
        Log.d(TAG, "");
        Log.d(TAG, "▶▶▶ CARGANDO DETALLE DEL VIAJE ◀◀◀");

        String endpoint = Constantes.VIAJE_POR_ID + viajeId;
        Log.d(TAG, "📡 Endpoint: " + endpoint);

        ConexionApi.getInstance(this).getObject(
                endpoint,
                response -> {
                    try {
                        Log.d(TAG, "");
                        Log.d(TAG, "✅ RESPUESTA DEL SERVIDOR RECIBIDA");
                        Log.d(TAG, "─────────────────────────────────────");
                        Log.d(TAG, response.toString(2));
                        Log.d(TAG, "─────────────────────────────────────");

                        procesarRespuestaViaje(response);

                    } catch (Exception e) {
                        Log.e(TAG, "");
                        Log.e(TAG, "❌ EXCEPCIÓN AL PROCESAR RESPUESTA");
                        Log.e(TAG, "Error: " + e.getMessage());
                        e.printStackTrace();
                        Toast.makeText(this, "Error procesando datos del viaje", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    Log.e(TAG, "");
                    Log.e(TAG, "❌ ERROR EN PETICIÓN API");
                    Log.e(TAG, "─────────────────────────────────────");

                    if (error != null && error.networkResponse != null) {
                        Log.e(TAG, "Código HTTP: " + error.networkResponse.statusCode);
                        if (error.networkResponse.data != null) {
                            String errorBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Respuesta: " + errorBody);
                        }
                    }

                    if (error != null && error.getMessage() != null) {
                        Log.e(TAG, "Mensaje: " + error.getMessage());
                    }

                    Log.e(TAG, "─────────────────────────────────────");
                    Toast.makeText(this, "Error cargando viaje. Revisa tu conexión.", Toast.LENGTH_LONG).show();
                }
        );
    }

    private void procesarRespuestaViaje(JSONObject response) {
        try {
            // ═══════════════════════════════════════
            // EXTRAER ESTADO
            // ═══════════════════════════════════════
            String estado = response.optString("estado", "DESCONOCIDO").trim().toUpperCase();
            Log.d(TAG, "");
            Log.d(TAG, "📌 ESTADO: " + estado);

            // ═══════════════════════════════════════
            // EXTRAER RESERVAS
            // ═══════════════════════════════════════
            int reservas = response.optInt("totalReservas", 0);
            Log.d(TAG, "👥 RESERVAS: " + reservas);

            // ═══════════════════════════════════════
            // EXTRAER DATOS DE RUTA
            // ═══════════════════════════════════════
            extraerDatosRuta(response);

            // ═══════════════════════════════════════
            // ACTUALIZAR INTERFAZ
            // ═══════════════════════════════════════
            actualizarInterfaz(response, estado);

            // ═══════════════════════════════════════
            // CONFIGURAR BOTONES
            // ═══════════════════════════════════════
            configurarBotones(estado, reservas);

            // ═══════════════════════════════════════
            // DIBUJAR MAPA SEGÚN ESTADO
            // ═══════════════════════════════════════
            if (estado.equals("INICIADO")) {
                Log.d(TAG, "");
                Log.d(TAG, "🚀 VIAJE INICIADO - Cargando ruta completa con paradas");
                cargarYDibujarRutaCompleta();
            } else {
                Log.d(TAG, "");
                Log.d(TAG, "📍 Dibujando ruta básica (Estado: " + estado + ")");
                dibujarRutaBasica(origenActual, destinoActual);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error procesando respuesta del viaje", e);
            throw e;
        }
    }

    private void extraerDatosRuta(JSONObject response) {
        Log.d(TAG, "");
        Log.d(TAG, "🗺️ EXTRAYENDO DATOS DE RUTA");

        JSONObject ruta = response.optJSONObject("ruta");

        if (ruta == null) {
            Log.w(TAG, "⚠️ Objeto 'ruta' no encontrado en la respuesta");

            // Intentar obtener idRuta directamente del viaje
            rutaId = response.optInt("idRuta", 0);

            if (rutaId == 0) {
                Log.e(TAG, "❌ ERROR: No se encontró 'ruta' ni 'idRuta'");
                Log.e(TAG, "📋 Usando valores por defecto para evitar crash");
                rutaId = 1; // Valor por defecto
            } else {
                Log.d(TAG, "✅ Se obtuvo idRuta del objeto principal: " + rutaId);
            }

            // Intentar obtener origen y destino del nivel principal
            origenActual = response.optString("origen", origenActual);
            destinoActual = response.optString("destino", destinoActual);

        } else {
            // Ruta encontrada normalmente
            rutaId = ruta.optInt("idRuta", 1);
            origenActual = ruta.optString("origen", "Origen desconocido");
            destinoActual = ruta.optString("destino", "Destino desconocido");

            Log.d(TAG, "✅ Ruta extraída correctamente");
        }

        Log.d(TAG, "   • ID Ruta: " + rutaId);
        Log.d(TAG, "   • Origen: " + origenActual);
        Log.d(TAG, "   • Destino: " + destinoActual);
    }

    private void actualizarInterfaz(JSONObject response, String estado) {
        Log.d(TAG, "");
        Log.d(TAG, "🎨 ACTUALIZANDO INTERFAZ");

        // ═══════════════════════════════════════
        // RUTA
        // ═══════════════════════════════════════
        txtRuta.setText("📍 " + origenActual + " → " + destinoActual);
        txtEstado.setText("📌 Estado: " + estado);

        // ═══════════════════════════════════════
        // CONDUCTOR
        // ═══════════════════════════════════════
        JSONObject conductor = response.optJSONObject("conductor");
        if (conductor != null) {
            String nombreConductor = conductor.optString("nombre", "Conductor");
            txtConductor.setText("👤 " + nombreConductor);
            Log.d(TAG, "   • Conductor: " + nombreConductor);
        } else {
            txtConductor.setText("👤 Conductor no disponible");
            Log.w(TAG, "   ⚠️ Objeto conductor no encontrado");
        }

        // ═══════════════════════════════════════
        // VEHÍCULO
        // ═══════════════════════════════════════
        JSONObject vehiculo = response.optJSONObject("vehiculo");
        if (vehiculo != null) {
            String marca = vehiculo.optString("marca", "");
            String modelo = vehiculo.optString("modelo", "");
            String placa = vehiculo.optString("placa", "");

            String textoVehiculo = marca + " " + modelo;
            if (!placa.isEmpty()) {
                textoVehiculo += " • " + placa;
            }

            txtVehiculo.setText("🚘 " + textoVehiculo);
            Log.d(TAG, "   • Vehículo: " + textoVehiculo);
        } else {
            txtVehiculo.setText("🚘 Vehículo no disponible");
            Log.w(TAG, "   ⚠️ Objeto vehículo no encontrado");
        }

        Log.d(TAG, "✅ Interfaz actualizada");
    }

    private void configurarBotones(String estado, int reservas) {
        Log.d(TAG, "");
        Log.d(TAG, "🔘 CONFIGURANDO BOTONES");
        Log.d(TAG, "   • Rol: " + (esConductor ? "CONDUCTOR" : "PASAJERO"));
        Log.d(TAG, "   • Estado: " + estado);
        Log.d(TAG, "   • Reservas: " + reservas);

        // Ocultar todos por defecto
        btnIniciar.setVisibility(View.GONE);
        btnFinalizar.setVisibility(View.GONE);
        btnAgregarParada.setVisibility(View.GONE);
        btnSeleccionarParada.setVisibility(View.GONE);

        // ═══════════════════════════════════════
        // LÓGICA PARA CONDUCTOR
        // ═══════════════════════════════════════
        if (esConductor) {
            // Puede iniciar si hay reservas y estado es CREADO/PROGRAMADO
            if ((estado.equals("CREADO") || estado.equals("PROGRAMADO")) && reservas > 0) {
                btnIniciar.setVisibility(View.VISIBLE);
                Log.d(TAG, "   ✅ Botón INICIAR visible");
            }

            // Si está iniciado, mostrar FINALIZAR y AGREGAR PARADA
            if (estado.equals("INICIADO")) {
                btnFinalizar.setVisibility(View.VISIBLE);
                btnAgregarParada.setVisibility(View.VISIBLE);
                Log.d(TAG, "   ✅ Botones FINALIZAR y AGREGAR PARADA visibles");
            }
        }

        // ═══════════════════════════════════════
        // LÓGICA PARA PASAJERO
        // ═══════════════════════════════════════
        else {
            // Si está iniciado, mostrar SELECCIONAR PARADA
            if (estado.equals("INICIADO")) {
                btnSeleccionarParada.setVisibility(View.VISIBLE);
                Log.d(TAG, "   ✅ Botón SELECCIONAR PARADA visible");
            }
        }

        Log.d(TAG, "✅ Botones configurados");
    }

    private void cambiarEstado(String accion) {
        Log.d(TAG, "");
        Log.d(TAG, "🔄 CAMBIANDO ESTADO DEL VIAJE");
        Log.d(TAG, "   • Acción: " + accion.toUpperCase());

        String endpoint = Constantes.VIAJE_POR_ID + viajeId + "/" + accion;
        Log.d(TAG, "   • Endpoint: " + endpoint);

        ConexionApi.getInstance(this).post(
                endpoint,
                null,
                response -> {
                    Log.d(TAG, "");
                    Log.d(TAG, "✅ ESTADO CAMBIADO EXITOSAMENTE");
                    Log.d(TAG, "Respuesta: " + response.toString());

                    Toast.makeText(this, "✅ Viaje " + accion + "do correctamente", Toast.LENGTH_SHORT).show();

                    // Recargar detalles después de 500ms
                    map.postDelayed(() -> {
                        Log.d(TAG, "🔄 Recargando detalles del viaje...");
                        cargarDetalleViaje();
                    }, 500);
                },
                error -> {
                    Log.e(TAG, "");
                    Log.e(TAG, "❌ ERROR CAMBIANDO ESTADO");

                    if (error != null && error.networkResponse != null) {
                        Log.e(TAG, "Código: " + error.networkResponse.statusCode);
                        if (error.networkResponse.data != null) {
                            Log.e(TAG, "Datos: " + new String(error.networkResponse.data));
                        }
                    }

                    String msg = "Error al " + accion + " el viaje";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONDUCTOR: AGREGAR PARADA
    // ═══════════════════════════════════════════════════════════════

    private void mostrarDialogoAgregarParada() {
        Log.d(TAG, "📍 Mostrando diálogo para agregar parada");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("➕ Agregar Parada");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre (ej: Terminal de buses)");
        layout.addView(inputNombre);

        final EditText inputDireccion = new EditText(this);
        inputDireccion.setHint("Dirección (ej: Calle 5 #10-20, Popayán)");
        layout.addView(inputDireccion);

        builder.setView(layout);

        builder.setPositiveButton("Agregar", (dialog, which) -> {
            String nombre = inputNombre.getText().toString().trim();
            String direccion = inputDireccion.getText().toString().trim();

            if (nombre.isEmpty() || direccion.isEmpty()) {
                Toast.makeText(this, "⚠️ Complete todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            agregarParada(nombre, direccion);
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Log.d(TAG, "❌ Diálogo de parada cancelado");
        });

        builder.show();
    }

    private void agregarParada(String nombre, String direccion) {
        Toast.makeText(this, "🔍 Buscando ubicación de: " + direccion, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "");
        Log.d(TAG, "📍 AGREGANDO NUEVA PARADA");
        Log.d(TAG, "   • Nombre: " + nombre);
        Log.d(TAG, "   • Dirección: " + direccion);

        new Thread(() -> {
            try {
                GeoPoint punto = geocodificar(direccion);
                Log.d(TAG, "   ✅ Geocodificado: " + punto.getLatitude() + ", " + punto.getLongitude());

                runOnUiThread(() -> enviarParadaAlServidor(nombre, punto));

            } catch (Exception e) {
                Log.e(TAG, "   ❌ Error geocodificando", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "❌ No se encontró la dirección: " + direccion, Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void enviarParadaAlServidor(String nombre, GeoPoint punto) {
        try {
            JSONObject body = new JSONObject();
            body.put("nombre", nombre);
            body.put("latitud", punto.getLatitude());
            body.put("longitud", punto.getLongitude());

            String endpoint = Constantes.RUTA_PARADAS + rutaId + "/paradas";

            Log.d(TAG, "");
            Log.d(TAG, "📤 ENVIANDO PARADA AL SERVIDOR");
            Log.d(TAG, "   • Endpoint: " + endpoint);
            Log.d(TAG, "   • Body: " + body.toString());

            ConexionApi.getInstance(this).post(
                    endpoint,
                    body,
                    r -> {
                        Log.d(TAG, "");
                        Log.d(TAG, "✅ PARADA AGREGADA EXITOSAMENTE");
                        Log.d(TAG, "Respuesta: " + r.toString());

                        Toast.makeText(this, "✅ Parada '" + nombre + "' agregada", Toast.LENGTH_SHORT).show();

                        // Recargar ruta completa
                        cargarYDibujarRutaCompleta();
                    },
                    e -> {
                        Log.e(TAG, "");
                        Log.e(TAG, "❌ ERROR AGREGANDO PARADA");

                        if (e != null && e.networkResponse != null) {
                            Log.e(TAG, "Código: " + e.networkResponse.statusCode);
                            if (e.networkResponse.data != null) {
                                Log.e(TAG, "Respuesta: " + new String(e.networkResponse.data));
                            }
                        }

                        Toast.makeText(this, "❌ Error agregando parada", Toast.LENGTH_LONG).show();
                    }
            );

        } catch (Exception e) {
            Log.e(TAG, "❌ Error creando JSON para parada", e);
            Toast.makeText(this, "Error interno al crear parada", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PASAJERO: SELECCIONAR PARADA
    // ═══════════════════════════════════════════════════════════════

    private void mostrarDialogoSeleccionarParada() {
        Log.d(TAG, "📍 Mostrando diálogo de selección de parada");

        if (paradasDisponibles.isEmpty()) {
            Toast.makeText(this, "ℹ️ No hay paradas en esta ruta", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "⚠️ No hay paradas disponibles");
            return;
        }

        String[] nombres = new String[paradasDisponibles.size()];
        for (int i = 0; i < paradasDisponibles.size(); i++) {
            nombres[i] = paradasDisponibles.get(i).optString("nombre", "Parada " + (i + 1));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📍 Seleccionar Parada de Bajada");
        builder.setItems(nombres, (dialog, which) -> {
            marcarParadaSeleccionada(paradasDisponibles.get(which));
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void marcarParadaSeleccionada(JSONObject parada) {
        try {
            String nombre = parada.getString("nombre");
            double lat = parada.getDouble("latitud");
            double lon = parada.getDouble("longitud");

            Log.d(TAG, "");
            Log.d(TAG, "✅ PARADA SELECCIONADA POR PASAJERO");
            Log.d(TAG, "   • Nombre: " + nombre);
            Log.d(TAG, "   • Ubicación: " + lat + ", " + lon);

            // Marcar en el mapa
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(lat, lon));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle("✅ Mi parada: " + nombre);
            map.getOverlays().add(marker);
            map.getController().animateTo(new GeoPoint(lat, lon));
            map.invalidate();

            Toast.makeText(this, "✅ Parada seleccionada: " + nombre, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error seleccionando parada", e);
            Toast.makeText(this, "Error seleccionando parada", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DIBUJAR RUTA EN MAPA
    // ═══════════════════════════════════════════════════════════════

    private void cargarYDibujarRutaCompleta() {
        String endpoint = Constantes.PARADAS_POR_RUTA + rutaId;

        Log.d(TAG, "");
        Log.d(TAG, "🗺️ CARGANDO PARADAS DE LA RUTA");
        Log.d(TAG, "   • Endpoint: " + endpoint);

        ConexionApi.getInstance(this).getObject(
                endpoint,
                response -> {
                    try {
                        Log.d(TAG, "✅ Respuesta paradas: " + response.toString());

                        JSONArray paradas = response.optJSONArray("paradas");
                        if (paradas == null) {
                            paradas = new JSONArray();
                            Log.w(TAG, "⚠️ No se encontró array 'paradas', usando array vacío");
                        }

                        Log.d(TAG, "📍 Paradas encontradas: " + paradas.length());

                        paradasDisponibles.clear();
                        for (int i = 0; i < paradas.length(); i++) {
                            paradasDisponibles.add(paradas.getJSONObject(i));
                            Log.d(TAG, "   " + (i+1) + ". " + paradas.getJSONObject(i).optString("nombre"));
                        }

                        dibujarRutaCompleta(origenActual, destinoActual, paradas);

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error procesando paradas", e);
                        Toast.makeText(this, "Error cargando paradas", Toast.LENGTH_SHORT).show();
                        dibujarRutaBasica(origenActual, destinoActual);
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Error cargando paradas", error);
                    Toast.makeText(this, "Error obteniendo paradas", Toast.LENGTH_SHORT).show();
                    dibujarRutaBasica(origenActual, destinoActual);
                }
        );
    }

    private void dibujarRutaBasica(String origenTxt, String destinoTxt) {
        Log.d(TAG, "");
        Log.d(TAG, "🗺️ DIBUJANDO RUTA BÁSICA (Sin paradas)");
        Log.d(TAG, "   • Origen: " + origenTxt);
        Log.d(TAG, "   • Destino: " + destinoTxt);

        new Thread(() -> {
            try {
                GeoPoint origen = geocodificar(origenTxt);
                GeoPoint destino = geocodificar(destinoTxt);

                // Construir URL para OSRM
                String coords = origen.getLongitude() + "," + origen.getLatitude() + ";" +
                        destino.getLongitude() + "," + destino.getLatitude();

                String url = "https://router.project-osrm.org/route/v1/driving/" + coords +
                        "?overview=full&geometries=geojson";

                Log.d(TAG, "   🌐 Consultando OSRM...");

                JSONObject res = new JSONObject(peticionHttp(url));
                JSONArray coordsArray = res.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                ArrayList<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coordsArray.length(); i++) {
                    JSONArray c = coordsArray.getJSONArray(i);
                    puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                Log.d(TAG, "   ✅ Ruta obtenida: " + puntos.size() + " puntos");

                runOnUiThread(() -> dibujarEnMapa(puntos, origen, destino, null));

            } catch (Exception e) {
                Log.e(TAG, "❌ Error dibujando ruta básica", e);
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error generando ruta en mapa", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void dibujarRutaCompleta(String origenTxt, String destinoTxt, JSONArray paradas) {
        Log.d(TAG, "");
        Log.d(TAG, "🗺️ DIBUJANDO RUTA COMPLETA (Con " + paradas.length() + " paradas)");

        new Thread(() -> {
            try {
                GeoPoint origen = geocodificar(origenTxt);
                GeoPoint destino = geocodificar(destinoTxt);

                // Waypoints: Origen + Paradas + Destino
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(origen);

                ArrayList<GeoPoint> puntosParada = new ArrayList<>();
                ArrayList<String> nombresParada = new ArrayList<>();

                // Agregar paradas
                for (int i = 0; i < paradas.length(); i++) {
                    JSONObject p = paradas.getJSONObject(i);
                    GeoPoint punto = new GeoPoint(
                            p.getDouble("latitud"),
                            p.getDouble("longitud")
                    );
                    waypoints.add(punto);
                    puntosParada.add(punto);
                    nombresParada.add(p.optString("nombre", "Parada " + (i + 1)));
                }

                waypoints.add(destino);

                // Construir coordenadas para OSRM
                StringBuilder coordsStr = new StringBuilder();
                for (int i = 0; i < waypoints.size(); i++) {
                    GeoPoint p = waypoints.get(i);
                    if (i > 0) coordsStr.append(";");
                    coordsStr.append(p.getLongitude()).append(",").append(p.getLatitude());
                }

                String url = "https://router.project-osrm.org/route/v1/driving/" +
                        coordsStr.toString() + "?overview=full&geometries=geojson";

                Log.d(TAG, "   🌐 Consultando OSRM con paradas...");

                JSONObject res = new JSONObject(peticionHttp(url));
                JSONArray coords = res.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                ArrayList<GeoPoint> puntosRuta = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray c = coords.getJSONArray(i);
                    puntosRuta.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
                }

                final ArrayList<GeoPoint> paradasFinal = puntosParada;
                final ArrayList<String> nombresFinal = nombresParada;

                Log.d(TAG, "   ✅ Ruta completa obtenida: " + puntosRuta.size() + " puntos");

                runOnUiThread(() -> dibujarEnMapa(puntosRuta, origen, destino, paradasFinal, nombresFinal));

            } catch (Exception e) {
                Log.e(TAG, "❌ Error dibujando ruta completa", e);
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error generando ruta completa", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void dibujarEnMapa(ArrayList<GeoPoint> rutaPuntos, GeoPoint origen, GeoPoint destino,
                               ArrayList<GeoPoint> paradas) {
        dibujarEnMapa(rutaPuntos, origen, destino, paradas, null);
    }

    private void dibujarEnMapa(ArrayList<GeoPoint> rutaPuntos, GeoPoint origen, GeoPoint destino,
                               ArrayList<GeoPoint> paradas, ArrayList<String> nombres) {

        Log.d(TAG, "");
        Log.d(TAG, "🎨 DIBUJANDO EN MAPA");

        // Limpiar mapa
        map.getOverlays().clear();

        // ═══════════════════════════════════════
        // LÍNEA DE RUTA
        // ═══════════════════════════════════════
        Polyline linea = new Polyline();
        linea.setPoints(rutaPuntos);
        linea.setColor(Color.parseColor("#6C3BFF"));
        linea.setWidth(12f);
        map.getOverlays().add(linea);
        Log.d(TAG, "   ✅ Línea de ruta dibujada");

        // ═══════════════════════════════════════
        // MARCADOR DE ORIGEN
        // ═══════════════════════════════════════
        Marker markerOrigen = new Marker(map);
        markerOrigen.setPosition(origen);
        markerOrigen.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        markerOrigen.setTitle("🟢 Origen: " + origenActual);
        map.getOverlays().add(markerOrigen);
        Log.d(TAG, "   ✅ Marcador de origen colocado");

        // ═══════════════════════════════════════
        // MARCADORES DE PARADAS
        // ═══════════════════════════════════════
        if (paradas != null && !paradas.isEmpty()) {
            Log.d(TAG, "   📍 Dibujando " + paradas.size() + " paradas");
            for (int i = 0; i < paradas.size(); i++) {
                Marker m = new Marker(map);
                m.setPosition(paradas.get(i));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                String nombre = (nombres != null && i < nombres.size())
                        ? nombres.get(i)
                        : ("Parada " + (i + 1));

                m.setTitle("🔵 " + nombre);
                map.getOverlays().add(m);
            }
            Log.d(TAG, "   ✅ Paradas dibujadas");
        }

        // ═══════════════════════════════════════
        // MARCADOR DE DESTINO
        // ═══════════════════════════════════════
        Marker markerDestino = new Marker(map);
        markerDestino.setPosition(destino);
        markerDestino.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        markerDestino.setTitle("🔴 Destino: " + destinoActual);
        map.getOverlays().add(markerDestino);
        Log.d(TAG, "   ✅ Marcador de destino colocado");

        // ═══════════════════════════════════════
        // AJUSTAR ZOOM
        // ═══════════════════════════════════════
        try {
            map.zoomToBoundingBox(linea.getBounds(), true, 150);
            Log.d(TAG, "   ✅ Zoom ajustado automáticamente");
        } catch (Exception e) {
            Log.w(TAG, "   ⚠️ No se pudo ajustar zoom automático", e);
        }

        map.invalidate();

        Log.d(TAG, "✅ MAPA DIBUJADO CORRECTAMENTE");
        Log.d(TAG, "═══════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Geocodifica una dirección a coordenadas
     */
    private GeoPoint geocodificar(String direccion) throws Exception {
        Log.d(TAG, "🔍 Geocodificando: " + direccion);

        // ═══════════════════════════════════════
        // MÉTODO 1: Geocoder de Android
        // ═══════════════════════════════════════
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(direccion + ", Popayán, Colombia", 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                double lat = addr.getLatitude();
                double lon = addr.getLongitude();

                Log.d(TAG, "   ✅ Geocoder Android exitoso: " + lat + ", " + lon);
                return new GeoPoint(lat, lon);
            }
        } catch (Exception e) {
            Log.w(TAG, "   ⚠️ Geocoder Android falló: " + e.getMessage());
        }

        // ═══════════════════════════════════════
        // MÉTODO 2: Nominatim (Fallback)
        // ═══════════════════════════════════════
        String url = "https://nominatim.openstreetmap.org/search?q=" +
                direccion.replace(" ", "+") + ",Popayan,Colombia&format=json&limit=1";

        Log.d(TAG, "   🌐 Usando Nominatim como fallback");

        JSONArray arr = new JSONArray(peticionHttp(url));
        if (arr.length() == 0) {
            throw new Exception("Ubicación no encontrada: " + direccion);
        }

        JSONObject obj = arr.getJSONObject(0);
        double lat = obj.getDouble("lat");
        double lon = obj.getDouble("lon");

        Log.d(TAG, "   ✅ Nominatim exitoso: " + lat + ", " + lon);
        return new GeoPoint(lat, lon);
    }

    /**
     * Realiza una petición HTTP GET y retorna el resultado como String
     */
    private String peticionHttp(String urlString) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Moviflexx-App/1.0");
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP Error: " + responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return builder.toString();

        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CICLO DE VIDA
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "    ACTIVITY DESTRUIDA");
        Log.d(TAG, "═══════════════════════════════════════");
    }
}