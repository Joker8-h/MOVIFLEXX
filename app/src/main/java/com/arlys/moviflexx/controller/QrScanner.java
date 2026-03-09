package com.arlys.moviflexx.controller;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class QrScanner extends AppCompatActivity {

    private static final String TAG = "QR_SCANNER";
    private static final int REQUEST_CAMERA_PERM = 300;

    // ── Vistas ──
    private PreviewView      previewView;
    private View             scanLine;
    private TextView         txtEstadoQr;
    private MaterialCardView cardEstado;
    private MaterialButton   btnVolverLogin;   // header (arrow back)
    private MaterialButton   btnVolverLoginBottom; // botón inferior
    private View             overlayExito;
    private TextView         txtNombreQr;
    private TextView         txtRolQr;

    // ── Lógica ──
    private ExecutorService     cameraExecutor;
    private SessionManager      sessionManager;
    private ValueAnimator       scanAnimator;
    private final AtomicBoolean yaLeido = new AtomicBoolean(false);

    // ══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        sessionManager = new SessionManager(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        bindViews();

        // Botón header (flecha atrás) — puede ser null si el layout no lo tiene
        if (btnVolverLogin != null)
            btnVolverLogin.setOnClickListener(v -> volverAlLogin());

        // Botón inferior — ID real en el XML es btnQrLogin
        if (btnVolverLoginBottom != null)
            btnVolverLoginBottom.setOnClickListener(v -> volverAlLogin());

        if (tieneCamara()) iniciarCamara();
        else               pedirPermisoCamara();
    }

    // Necesario porque el XML tiene android:onClick="login" en btnQrLogin
    // Si no existe este método, la app crasha al tocar el botón
    public void login(View view) {
        volverAlLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanAnimator != null && !scanAnimator.isRunning()) scanAnimator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scanAnimator != null) scanAnimator.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (scanAnimator   != null) scanAnimator.cancel();
    }

    @Override
    public void onBackPressed() { volverAlLogin(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  BIND VIEWS
    // ══════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        previewView          = findViewById(R.id.qr_preview);
        scanLine             = findViewById(R.id.qr_scan_line);
        txtEstadoQr          = findViewById(R.id.txt_estado_qr);
        cardEstado           = findViewById(R.id.card_estado_qr);
        btnVolverLogin       = findViewById(R.id.btn_volver_login_qr);   // header arrow — puede ser null
        btnVolverLoginBottom = findViewById(R.id.btnQrLogin);            // botón inferior — ID real del XML
        overlayExito         = findViewById(R.id.overlay_exito_qr);
        txtNombreQr          = findViewById(R.id.txt_nombre_qr);
        txtRolQr             = findViewById(R.id.txt_rol_qr);

        Log.d(TAG, "bindViews → btnVolverLogin=" + btnVolverLogin
                + " | btnVolverLoginBottom=" + btnVolverLoginBottom
                + " | cardEstado=" + cardEstado
                + " | overlayExito=" + overlayExito);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CÁMARA
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analizarFrame);

                provider.unbindAll();
                provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

                runOnUiThread(this::iniciarAnimacionLinea);

            } catch (Exception e) {
                Log.e(TAG, "Error iniciando cámara: " + e.getMessage());
                runOnUiThread(() -> mostrarEstado("❌ Error al iniciar la cámara", false));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ANIMACIÓN LÍNEA
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarAnimacionLinea() {
        if (scanLine == null) return;
        scanLine.post(() -> {
            View marco = findViewById(R.id.qr_marco);
            if (marco == null) return;
            scanAnimator = ValueAnimator.ofFloat(
                    marco.getTop(),
                    marco.getTop() + marco.getHeight() - scanLine.getHeight());
            scanAnimator.setDuration(1800);
            scanAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scanAnimator.setRepeatMode(ValueAnimator.REVERSE);
            scanAnimator.setInterpolator(new LinearInterpolator());
            scanAnimator.addUpdateListener(a -> scanLine.setY((float) a.getAnimatedValue()));
            scanAnimator.start();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ANÁLISIS DE FRAMES
    // ══════════════════════════════════════════════════════════════════════════

    private void analizarFrame(@NonNull ImageProxy image) {
        if (yaLeido.get()) { image.close(); return; }
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    bytes, image.getWidth(), image.getHeight(),
                    0, 0, image.getWidth(), image.getHeight(), false);

            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(source)));

            if (yaLeido.compareAndSet(false, true)) {
                String contenido = result.getText();
                Log.d(TAG, "QR leído: " + contenido);
                runOnUiThread(() -> procesarQr(contenido));
            }
        } catch (com.google.zxing.NotFoundException ignored) {
            // Frame sin QR — normal, seguir escaneando
        } catch (Exception e) {
            Log.w(TAG, "Error analizando frame: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PROCESAR QR
    // ══════════════════════════════════════════════════════════════════════════

    private void procesarQr(String contenido) {
        if (scanAnimator != null) scanAnimator.pause();
        mostrarEstado("🔐 Verificando código QR...", true);

        try {
            // ── 1. Separar TOKEN|nombre ──
            String token  = contenido;
            String nombre = "Usuario";

            if (contenido.contains("|")) {
                String[] partes = contenido.split("\\|", 2);
                token  = partes[0].trim();
                if (partes.length > 1 && !partes[1].trim().isEmpty())
                    nombre = partes[1].trim();
            }

            if (token.isEmpty() || !token.contains(".")) {
                mostrarEstado("❌ QR inválido — no es un token válido", false);
                reiniciarScanner(2500);
                return;
            }

            // ── 2. Decodificar payload del JWT ──
            String[] jwtPartes = token.split("\\.");
            if (jwtPartes.length < 2) {
                mostrarEstado("❌ Formato de token incorrecto", false);
                reiniciarScanner(2500);
                return;
            }

            String payloadBase64 = jwtPartes[1];
            int padding = payloadBase64.length() % 4;
            if (padding == 2) payloadBase64 += "==";
            else if (padding == 3) payloadBase64 += "=";

            byte[]     decoded     = Base64.decode(payloadBase64, Base64.URL_SAFE);
            String     payloadJson = new String(decoded, StandardCharsets.UTF_8);
            JSONObject payload     = new JSONObject(payloadJson);

            // ── 3. Log diagnóstico completo ──
            Log.d(TAG, "=== QR LOGIN ===");
            Log.d(TAG, "Payload completo: " + payloadJson);

            // ── 4. Extraer campos del payload ──
            int    idUsuario = payload.optInt("id",    -1);
            String email     = payload.optString("email", "");
            int    idRol     = payload.optInt("idRol", -1);
            long   exp       = payload.optLong("exp",   0);

            // Soporte para "rol" como objeto { idRol, nombre } o número directo
            if (idRol == -1 && payload.has("rol")) {
                try {
                    JSONObject rolObj = payload.optJSONObject("rol");
                    if (rolObj != null) {
                        idRol = rolObj.optInt("idRol", rolObj.optInt("id", -1));
                    } else {
                        idRol = payload.optInt("rol", -1);
                    }
                } catch (Exception ignored) {}
            }

            Log.d(TAG, "idUsuario=" + idUsuario + " | idRol=" + idRol
                    + " | email=" + email + " | nombre=" + nombre);

            // ── 5. Verificar expiración ──
            long ahoraSegundos = System.currentTimeMillis() / 1000L;
            if (exp > 0 && ahoraSegundos > exp) {
                mostrarEstado("⏰ Este QR ya expiró\nGenera uno nuevo desde tu perfil", false);
                reiniciarScanner(4000);
                return;
            }

            // ── 6. Validar campos mínimos ──
            if (idUsuario == -1 || idRol == -1) {
                Log.e(TAG, "❌ QR sin datos válidos — idUsuario=" + idUsuario + " idRol=" + idRol);
                mostrarEstado("❌ QR sin datos de usuario válidos", false);
                reiniciarScanner(2500);
                return;
            }

            // ── 7. Limpiar memoria estática de sesión anterior ──
            SesionUsuario.setIdUsuario(-1);
            SesionUsuario.setIdRol(-1);
            SesionUsuario.setToken(null);

            // ── 8. Guardar nueva sesión ──
            final String tokenFinal  = token;
            final String nombreFinal = nombre;
            final String emailFinal  = email;
            final int    idRolFinal  = idRol;
            final int    idUsFinal   = idUsuario;

            sessionManager.saveToken(tokenFinal);
            SesionUsuario.setToken(tokenFinal);
            sessionManager.saveUser(nombreFinal, emailFinal, "", idRolFinal, idUsFinal);
            SesionUsuario.setIdUsuario(idUsFinal);
            SesionUsuario.setIdRol(idRolFinal);
            sessionManager.setLoggedIn(true);
            sessionManager.loadSessionToMemory();

            Log.d(TAG, "✅ Sesión guardada — idUsuario=" + idUsFinal + " | idRol=" + idRolFinal);

            // ── 9. Mostrar éxito y navegar ──
            mostrarOverlayExito(nombreFinal, idRolFinal);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Toast.makeText(this, "¡Bienvenido " + nombreFinal + "! 👋",
                        Toast.LENGTH_SHORT).show();
                Class<?> destino = idRolFinal == 2 ? HomeConductor.class : HomePasajero.class;
                Log.d(TAG, "Navegando a: " + destino.getSimpleName());
                startActivity(new Intent(QrScanner.this, destino));
                finish();
            }, 1500);

        } catch (Exception e) {
            Log.e(TAG, "Error procesando QR: " + e.getMessage(), e);
            mostrarEstado("❌ Error leyendo el QR, intenta de nuevo", false);
            reiniciarScanner(2500);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarEstado(String mensaje, boolean cargando) {
        if (txtEstadoQr == null || cardEstado == null) return;
        txtEstadoQr.setText(mensaje);
        cardEstado.setVisibility(View.VISIBLE);

        if (mensaje.startsWith("❌") || mensaje.startsWith("⏰")) {
            cardEstado.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
            txtEstadoQr.setTextColor(Color.parseColor("#C62828"));
        } else if (mensaje.startsWith("✅") || mensaje.startsWith("🎉")) {
            cardEstado.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            txtEstadoQr.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            cardEstado.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            txtEstadoQr.setTextColor(Color.parseColor("#1565C0"));
        }
    }

    private void mostrarOverlayExito(String nombre, int idRol) {
        if (overlayExito == null) return;
        if (txtNombreQr != null) txtNombreQr.setText("¡Hola, " + nombre + "!");
        if (txtRolQr    != null) txtRolQr.setText(idRol == 2 ? "🚗 Conductor" : "🧳 Pasajero");
        overlayExito.setVisibility(View.VISIBLE);
        overlayExito.setAlpha(0f);
        overlayExito.animate().alpha(1f).setDuration(300).start();
    }

    private void reiniciarScanner(long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            yaLeido.set(false);
            if (cardEstado != null) cardEstado.setVisibility(View.GONE);
            if (scanAnimator != null) scanAnimator.resume();
        }, delayMs);
    }

    private void volverAlLogin() {
        startActivity(new Intent(this, Login.class));
        finish();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERMISOS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean tieneCamara() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermisoCamara() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara();
            } else {
                mostrarEstado("❌ Se necesita permiso de cámara", false);
                new Handler(Looper.getMainLooper()).postDelayed(this::volverAlLogin, 2000);
            }
        }
    }
}