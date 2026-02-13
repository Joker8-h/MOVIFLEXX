package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SesionUsuario;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class HomePasajero extends AppCompatActivity {

    // ================= VISTAS =================
    private TextView tvNombreUsuario;
    private TextView tvVerTodasPromociones, tvVerMapa;
    private CardView cardPromo1, cardPromo2, cardPromo3;
    private CardView cardDestinoAeropuerto, cardDestinoCentro, cardDestinoUniversidad, cardDestinoTerminal;
    private CardView cardNoticia1, cardNoticia2, cardNoticia3;

    private MaterialButton btnBuscarViaje;
    private BottomNavigationView bottomNavigation;

    // ================= DATOS DEL USUARIO =================
    private String nombreUsuario = "Usuario";
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_pasajero);

        // Inicializar SessionManager
        sessionManager = new SessionManager(this);

        // Inicializar vistas
        inicializarVistas();

        // Cargar datos del usuario
        cargarDatosUsuario();

        // Configurar listeners
        configurarListeners();
    }

    // ================= INICIALIZAR VISTAS =================
    private void inicializarVistas() {
        // Header
        tvNombreUsuario = findViewById(R.id.tv_nombre_usuario);

        // Links de secciones
        tvVerTodasPromociones = findViewById(R.id.tv_ver_todas_promociones);
        tvVerMapa = findViewById(R.id.tv_ver_mapa);

        // Cards de promociones
        cardPromo1 = findViewById(R.id.card_promo_1);
        cardPromo2 = findViewById(R.id.card_promo_2);
        cardPromo3 = findViewById(R.id.card_promo_3);

        // Cards de destinos
        cardDestinoAeropuerto = findViewById(R.id.card_destino_aeropuerto);
        cardDestinoCentro = findViewById(R.id.card_destino_centro);
        cardDestinoUniversidad = findViewById(R.id.card_destino_universidad);
        cardDestinoTerminal = findViewById(R.id.card_destino_terminal);

        // Cards de noticias
        cardNoticia1 = findViewById(R.id.card_noticia_1);
        cardNoticia2 = findViewById(R.id.card_noticia_2);
        cardNoticia3 = findViewById(R.id.card_noticia_3);

        // Botón principal
        btnBuscarViaje = findViewById(R.id.btn_buscar_viaje);

        // Bottom Navigation
        bottomNavigation = findViewById(R.id.bottom_navigation);
    }

    // ================= CARGAR DATOS DEL USUARIO =================
    private void cargarDatosUsuario() {
        // Obtener datos de SessionManager
        Map<String, String> userData = sessionManager.getUserData();

        if (userData.containsKey("nombre")) {
            nombreUsuario = userData.get("nombre");
            tvNombreUsuario.setText(nombreUsuario);
        } else {
            // Si no hay datos guardados, cargar desde API
            cargarDatosDesdeAPI();
        }
    }

    // ================= CARGAR DATOS DESDE API =================
    private void cargarDatosDesdeAPI() {
        int idUsuario = SesionUsuario.getIdUsuario();
        String token = SesionUsuario.getToken();

        if (idUsuario <= 0 || token == null) {
            tvNombreUsuario.setText("Usuario");
            return;
        }

        String url = Constantes.usuarioPorId((long) idUsuario);

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        String nombre = response.optString("nombre", "Usuario");
                        String email = response.optString("email", "");
                        String telefono = response.optString("telefono", "");
                        int idRol = response.optInt("idRol", 1);

                        // Actualizar UI
                        tvNombreUsuario.setText(nombre);
                        nombreUsuario = nombre;

                        // Guardar en SessionManager
                        sessionManager.saveUser(nombre, email, telefono, idRol, idUsuario);

                    } catch (Exception e) {
                        tvNombreUsuario.setText("Usuario");
                    }
                },
                error -> {
                    Toast.makeText(this, "Error al cargar perfil", Toast.LENGTH_SHORT).show();
                    tvNombreUsuario.setText("Usuario");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        queue.add(request);
    }

    // ================= CONFIGURAR LISTENERS =================
    private void configurarListeners() {
        // ========== LINKS DE TEXTO ==========
        tvVerTodasPromociones.setOnClickListener(v ->
                abrirURLExterna("https://www.moviflex.com/promociones")
        );

        tvVerMapa.setOnClickListener(v ->
                abrirGoogleMaps()
        );

        // ========== PROMOCIONES ==========
        cardPromo1.setOnClickListener(v -> {
            Toast.makeText(this, "Promoción: 20% OFF - Código copiado", Toast.LENGTH_SHORT).show();
            // Copiar código al portapapeles
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Código", "BIENVENIDO");
            clipboard.setPrimaryClip(clip);
        });

        cardPromo2.setOnClickListener(v -> {
            Toast.makeText(this, "Promoción: Viaje gratis al aeropuerto", Toast.LENGTH_SHORT).show();
            abrirURLExterna("https://www.moviflex.com/promo-aeropuerto");
        });

        cardPromo3.setOnClickListener(v -> {
            Toast.makeText(this, "Promoción: 2x1 en viajes compartidos", Toast.LENGTH_SHORT).show();
            abrirURLExterna("https://www.moviflex.com/promo-2x1");
        });

        // ========== DESTINOS ==========
        cardDestinoAeropuerto.setOnClickListener(v ->
                buscarViajeDestino("Aeropuerto")
        );

        cardDestinoCentro.setOnClickListener(v ->
                buscarViajeDestino("Centro")
        );

        cardDestinoUniversidad.setOnClickListener(v ->
                buscarViajeDestino("Universidad")
        );

        cardDestinoTerminal.setOnClickListener(v ->
                buscarViajeDestino("Terminal")
        );

        // ========== NOTICIAS ==========
        cardNoticia1.setOnClickListener(v ->
                abrirURLExterna("https://www.moviflex.com/consejos-seguridad")
        );

        cardNoticia2.setOnClickListener(v ->
                abrirURLExterna("https://www.moviflex.com/viajes-eco-friendly")
        );

        cardNoticia3.setOnClickListener(v ->
                abrirURLExterna("https://www.moviflex.com/programa-puntos")
        );

        // ========== BOTÓN BUSCAR VIAJE ==========
        btnBuscarViaje.setOnClickListener(v ->
                irABuscarViajes()
        );

        // ========== BOTTOM NAVIGATION ==========
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_inicio) {
                // Ya estamos en inicio
                return true;
            } else if (itemId == R.id.nav_mis_viajes) {
                irAMisViajes();
                return true;
            } else if (itemId == R.id.nav_mapa) {
                irAMapa();
                return true;
            } else if (itemId == R.id.nav_mensajes) {
                irAMensajes();
                return true;
            } else if (itemId == R.id.nav_perfil) {
                irAPerfil();
                return true;
            }

            return false;
        });

        // Marcar inicio como seleccionado
        bottomNavigation.setSelectedItemId(R.id.nav_inicio);
    }

    // ================= MÉTODOS DE NAVEGACIÓN =================

    private void buscarViajeDestino(String destino) {
        Toast.makeText(this, "Buscando viajes a: " + destino, Toast.LENGTH_SHORT).show();

        // Aquí puedes abrir una activity de búsqueda pasando el destino
        Intent intent = new Intent(this, DetalleViajeActivity.class);
        intent.putExtra("destino", destino);
        startActivity(intent);
    }

    private void irABuscarViajes() {
        // Navegar a la pantalla de búsqueda de viajes
        Intent intent = new Intent(this, RutasFrecuentes.class);
        startActivity(intent);
    }

    private void irAMisViajes() {
        // Navegar a mis viajes/reservas
        Intent intent = new Intent(this, RutasFrecuentes.class);
        startActivity(intent);
    }

    private void irAMapa() {
        // Navegar al mapa
        Intent intent = new Intent(this, Mapa.class);
        startActivity(intent);
    }

    private void irAMensajes() {
        // Navegar a mensajes/chat
        Intent intent = new Intent(this, Chat.class);
        startActivity(intent);
    }

    private void irAPerfil() {
        // Navegar a perfil
        Intent intent = new Intent(this, PerfilUsuario.class);
        startActivity(intent);
    }

    // ================= ABRIR LINKS EXTERNOS =================

    private void abrirURLExterna(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se puede abrir el enlace", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirGoogleMaps() {
        try {
            // Abrir Google Maps con ubicación de Popayán, Colombia
            String uri = "geo:2.4448,76.6147?z=13&q=Popayán,Colombia";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Si no tiene Google Maps, abrir en navegador
                abrirURLExterna("https://www.google.com/maps/place/Popayán,+Cauca");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir mapa", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= COMPARTIR PROMOCIÓN =================

    private void compartirPromocion(String titulo, String codigo) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, titulo);
            intent.putExtra(Intent.EXTRA_TEXT,
                    "¡Aprovecha esta promoción en MoviFlex! " + titulo +
                            "\nCódigo: " + codigo +
                            "\n\nDescarga la app: https://moviflex.com/app");
            startActivity(Intent.createChooser(intent, "Compartir promoción"));
        } catch (Exception e) {
            Toast.makeText(this, "Error al compartir", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar datos al volver a la pantalla
        cargarDatosUsuario();
    }

}

