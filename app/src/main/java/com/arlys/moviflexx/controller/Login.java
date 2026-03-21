package com.arlys.moviflexx.controller;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.AnimUtils;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.FieldTooltip;
import com.arlys.moviflexx.model.MoviAlert;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Login extends BaseActivity {

    private static final String TAG = "LOGIN_DEBUG";
    private static final int REQUEST_CAMERA_PERM = 100;

    private static final String CLOUDINARY_CLOUD_NAME    = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";
    private static final String CLOUDINARY_UPLOAD_URL    =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    private volatile Bitmap ultimoFrameBitmap = null;

    // ── Vistas del formulario (IDs exactos del XML) ───────────────────────────
    private View              layoutLogin;       // ScrollView  @+id/layoutLogin
    private TextInputEditText edtEmail, edtPassword;
    private TextInputLayout   tilEmail, tilPassword;
    private View              btnLogin;          // @+id/btnLogin
    private View              btnGoogle;         // @+id/btnGoogle
    private View              btnFaceLogin;      // @+id/btnFaceLogin
    private View              btnQrLogin;        // @+id/btnQrLogin
    private View              tvRegister;        // @+id/tvRegister

    // ── Vistas de la cámara ───────────────────────────────────────────────────
    private ConstraintLayout layoutFace;         // @+id/layoutFace
    private PreviewView      previewView;        // @+id/previewView
    private TextView         txtEstado;          // @+id/txtEstado

    private ExecutorService  cameraExecutor;
    private FirebaseAuth       mAuth;
    private GoogleSignInClient googleSignInClient;
    private SessionManager     sessionManager;
    private Dialog             loadingDialog = null;

    private final AtomicBoolean yaCapturado             = new AtomicBoolean(false);
    private final AtomicBoolean cuentaRegresivaIniciada = new AtomicBoolean(false);

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() == null) return;
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        if (task.isSuccessful()) {
                            firebaseAuthWithGoogle(task.getResult().getIdToken());
                        } else {
                            MoviAlert.error(this, "Error con Google",
                                    "No se pudo completar el inicio de sesión con Google.");
                        }
                    });

    // ══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void iniciarAsistenteVozSiPermite() {
        // No iniciar el asistente de voz en Login
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Forzar status bar teal igual que Home ──────────────
        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_login);

        // ── Enlazar vistas con IDs reales del XML ─────────────────────────────
        layoutLogin  = findViewById(R.id.layoutLogin);
        tilEmail     = findViewById(R.id.til_email);
        tilPassword  = findViewById(R.id.til_password);
        edtEmail     = findViewById(R.id.edtEmail);
        edtPassword  = findViewById(R.id.edtPassword);
        layoutFace   = findViewById(R.id.layoutFace);
        previewView  = findViewById(R.id.previewView);
        txtEstado    = findViewById(R.id.txtEstado);

        // Botones — IDs exactos del XML
        btnLogin     = findViewById(R.id.btnLogin);
        btnGoogle    = findViewById(R.id.btnGoogle);
        btnFaceLogin = findViewById(R.id.btnFaceLogin);
        btnQrLogin   = findViewById(R.id.btnQrLogin);
        tvRegister   = findViewById(R.id.tvRegister);

        layoutFace.setVisibility(View.GONE);

        mAuth          = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        FieldTooltip.attach(tilEmail,    "Correo electrónico");
        FieldTooltip.attach(tilPassword, "Contraseña");

        configurarValidacionesEnTiempoReal();
        configurarGoogle();

        // ══════════════════════════════════════════════════════════════════════
        //  ANIMACIONES DE ENTRADA — siempre al final de onCreate
        // ══════════════════════════════════════════════════════════════════════
        animarEntradaLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        dismissLoading();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ANIMACIONES DE ENTRADA
    //  Cascada: ilustración → logo → campos → botones → registro
    //  Delay acumulado de 80ms por elemento para efecto stagger natural
    // ══════════════════════════════════════════════════════════════════════════

    private void animarEntradaLogin() {
        // Ilustración del van (ImageView dentro del ScrollView, no tiene ID)
        // La animamos via el contenedor del scroll con un delay mínimo
        AnimUtils.fadeSlideIn(layoutLogin, 0);

        // Campos de texto — entran ligeramente después
        AnimUtils.fadeSlideIn(tilEmail,    180);
        AnimUtils.fadeSlideIn(tilPassword, 260);

        // Botón principal — el más importante, entra con ligero bounce
        if (btnLogin != null)     AnimUtils.bounceIn(btnLogin,     340);

        // Fila QR + Facial
        if (btnQrLogin != null)   AnimUtils.fadeSlideIn(btnQrLogin,   420);
        if (btnFaceLogin != null) AnimUtils.fadeSlideIn(btnFaceLogin, 420);

        // Google y registro — últimos
        if (btnGoogle != null)    AnimUtils.fadeSlideIn(btnGoogle,    500);
        if (tvRegister != null)   AnimUtils.fadeSlideIn(tvRegister,   560);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDACIONES EN TIEMPO REAL
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarValidacionesEnTiempoReal() {
        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validarEmail(edtEmail.getText().toString());
        });
        edtPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() > 0) validarPassword(s.toString());
                else { tilPassword.setError(null); tilPassword.setErrorEnabled(false); }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDADORES — shake cuando hay error
    // ══════════════════════════════════════════════════════════════════════════

    private boolean validarEmail(String valor) {
        valor = valor.trim();
        if (valor.isEmpty()) {
            tilEmail.setError("El correo es obligatorio");
            AnimUtils.shake(tilEmail);
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(valor).matches()) {
            tilEmail.setError("Formato inválido — debe ser: usuario@correo.com");
            AnimUtils.shake(tilEmail);
            return false;
        }
        tilEmail.setError(null);
        tilEmail.setErrorEnabled(false);
        return true;
    }

    private boolean validarPassword(String valor) {
        if (valor.isEmpty()) {
            tilPassword.setError("La contraseña es obligatoria");
            AnimUtils.shake(tilPassword);
            return false;
        }
        StringBuilder f = new StringBuilder();
        if (valor.length() < 8)
            f.append("• Mínimo 8 caracteres\n");
        if (!valor.matches(".*[A-Z].*"))
            f.append("• Al menos una mayúscula (A-Z)\n");
        if (!valor.matches(".*[a-z].*"))
            f.append("• Al menos una minúscula (a-z)\n");
        if (!valor.matches(".*[0-9].*"))
            f.append("• Al menos un número (0-9)\n");
        if (!valor.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"))
            f.append("• Al menos un carácter especial (!@#$%...)\n");
        if (f.length() > 0) {
            tilPassword.setError("Faltan requisitos:\n" + f.toString().trim());
            AnimUtils.shake(tilPassword);
            return false;
        }
        tilPassword.setError(null);
        tilPassword.setErrorEnabled(false);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN — fade suave entre formulario y cámara
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarFaceLogin() {
        // Fade out del formulario completo → mostrar cámara con fade in
        AnimUtils.fadeOut(layoutLogin, () -> {
            layoutLogin.setVisibility(View.GONE);
            layoutFace.setVisibility(View.VISIBLE);
            AnimUtils.fadeSlideIn(layoutFace, 0);
        });

        yaCapturado.set(false);
        cuentaRegresivaIniciada.set(false);
        txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
        if (verificarPermisoCamara()) iniciarCamara();
        else solicitarPermisoCamara();
    }

    private void mostrarLoginNormal() {
        // Fade out de la cámara → re-animar los elementos del formulario
        AnimUtils.fadeOut(layoutFace, () -> {
            layoutFace.setVisibility(View.GONE);
            layoutLogin.setVisibility(View.VISIBLE);

            // Re-animar en cascada al volver (delays más cortos)
            AnimUtils.fadeSlideIn(tilEmail,    0);
            AnimUtils.fadeSlideIn(tilPassword, 80);
            if (btnLogin     != null) AnimUtils.bounceIn(btnLogin,     160);
            if (btnQrLogin   != null) AnimUtils.fadeSlideIn(btnQrLogin,   220);
            if (btnFaceLogin != null) AnimUtils.fadeSlideIn(btnFaceLogin, 220);
            if (btnGoogle    != null) AnimUtils.fadeSlideIn(btnGoogle,    280);
        });

        yaCapturado.set(false);
        cuentaRegresivaIniciada.set(false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOGIN EMAIL + PASSWORD
    // ══════════════════════════════════════════════════════════════════════════

    public void login(View view) {
        String email    = edtEmail.getText() != null
                ? edtEmail.getText().toString().trim() : "";
        String password = edtPassword.getText() != null
                ? edtPassword.getText().toString().trim() : "";

        boolean emailOk    = validarEmail(email);
        boolean passwordOk = validarPassword(password);

        if (!emailOk || !passwordOk) {
            MoviAlert.toast(this, "Revisa los campos marcados en rojo", MoviAlert.WARNING);
            return;
        }

        loadingDialog = MoviAlert.loading(this, "Iniciando sesión...");

        JSONObject json = new JSONObject();
        try {
            json.put("email",    email);
            json.put("password", password);
        } catch (JSONException e) {
            dismissLoading();
            return;
        }

        final String emailFinal = email;

        client.newCall(new Request.Builder()
                .url(Constantes.LOGIN)
                .post(RequestBody.create(json.toString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build()
        ).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    dismissLoading();
                    MoviAlert.error(Login.this, "Sin conexión",
                            "No se pudo conectar al servidor.\nVerifica tu internet e intenta de nuevo.");
                });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    dismissLoading();
                    if (response.isSuccessful()) {
                        procesarRespuestaLogin(body, emailFinal);
                    } else {
                        String msg;
                        switch (response.code()) {
                            case 401: msg = "El correo o la contraseña son incorrectos."; break;
                            case 404: msg = "No encontramos una cuenta con ese correo."; break;
                            case 429: msg = "Demasiados intentos. Espera unos minutos."; break;
                            case 500: msg = "Error interno del servidor. Inténtalo más tarde."; break;
                            default:  msg = "Error inesperado (código " + response.code() + ").";
                        }
                        MoviAlert.error(Login.this, "Error al iniciar sesión", msg);
                    }
                });
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PROCESAR RESPUESTA LOGIN
    // ══════════════════════════════════════════════════════════════════════════

    private void procesarRespuestaLogin(String responseBody, String emailFallback) {
        try {
            JSONObject resp = new JSONObject(responseBody);
            String token  = null;
            int idUsuario = -1, idRol = -1;
            String nombre = "", email = "", telefono = "", fotoPerfil = "";

            if (resp.has("token"))            token = resp.getString("token");
            else if (resp.has("accessToken")) token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario  = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre     = u.optString("nombre",      "Usuario");
                email      = u.optString("email",       emailFallback);
                telefono   = u.optString("telefono",    "Sin teléfono");
                fotoPerfil = u.optString("fotoPerfil",
                        u.optString("fotoPerfi",
                                u.optString("foto",
                                        u.optString("photoUrl",
                                                u.optString("profilePicture", "")))));
                if (u.has("idRol"))    idRol = u.getInt("idRol");
                else if (u.has("rol")) idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            if (token == null || idUsuario == -1 || idRol == -1) {
                MoviAlert.error(this, "Error de credenciales",
                        "La respuesta del servidor no es válida. Contacta al soporte.");
                return;
            }

            if (!fotoPerfil.isEmpty() && !fotoPerfil.equals("null")) {
                sessionManager.saveFotoPerfil(fotoPerfil);
                Log.d(TAG, "Foto de perfil guardada: " + fotoPerfil);
            }

            sessionManager.setPendingWelcome(true);
            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);
            mostrarNotificacionLocal(nombre);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            MoviAlert.error(this, "Error inesperado",
                    "Hubo un problema procesando la respuesta del servidor.");
        }
    }



    // ══════════════════════════════════════════════════════════════════════════
    //  FACE LOGIN
    // ══════════════════════════════════════════════════════════════════════════

    public void irFaceLogin(View view) { mostrarFaceLogin(); }

    public void irQrScanner(View view) {
        startActivity(new Intent(this, QrScanner.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GOOGLE
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail().build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // tapPulse en el botón de Google antes de lanzar el intent
        findViewById(R.id.btnGoogle).setOnClickListener(v -> {
            AnimUtils.tapPulse(v);
            v.postDelayed(() ->
                    googleSignInClient.signOut().addOnCompleteListener(task ->
                            googleLauncher.launch(googleSignInClient.getSignInIntent())
                    ), 150);
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        loadingDialog = MoviAlert.loading(this, "Autenticando con Google...");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        loginGoogleEnBackend(idToken);
                    } else {
                        dismissLoading();
                        MoviAlert.error(this, "Error con Google",
                                "No se pudo autenticar con Google.\nIntenta de nuevo.");
                    }
                });
    }

    private void loginGoogleEnBackend(String idToken) {
        try {
            JSONObject json = new JSONObject();
            json.put("idToken", idToken);

            client.newCall(new Request.Builder()
                    .url(Constantes.LOGIN_GOOGLE)
                    .post(RequestBody.create(json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        MoviAlert.error(Login.this, "Sin conexión",
                                "No se pudo conectar al servidor.\nVerifica tu internet.");
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        dismissLoading();
                        if (response.isSuccessful()) {
                            String emailFallback = mAuth.getCurrentUser() != null
                                    ? mAuth.getCurrentUser().getEmail() : "";
                            procesarRespuestaLogin(body, emailFallback);
                        } else {
                            String msg = "Error inesperado (código " + response.code() + ").";
                            try {
                                JSONObject err = new JSONObject(body);
                                if (err.has("message"))    msg = err.getString("message");
                                else if (err.has("error")) msg = err.getString("error");
                            } catch (Exception ignored) {}
                            MoviAlert.error(Login.this, "Error con Google", msg);
                        }
                    });
                }
            });
        } catch (JSONException e) {
            dismissLoading();
            MoviAlert.error(this, "Error interno", "No se pudo preparar la solicitud.");
        }
    }

    private void mostrarNotificacionLocal(String nombre) {
        String channelId = "moviflexx_login";
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Canal obligatorio Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    channelId,
                    "Moviflexx Login",
                    NotificationManager.IMPORTANCE_HIGH);
            canal.enableVibration(true);
            manager.createNotificationChannel(canal);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle("¡Bienvenido a Moviflexx! 👋")
                        .setContentText("Hola " + nombre + ", has iniciado sesión correctamente.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify(1001, builder.build());
    }

    public void irRegister(View view) {
        // slide desde abajo al ir al registro — sensación de "abrir" nueva pantalla
        startActivity(new Intent(this, Register.class));
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERMISOS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean verificarPermisoCamara() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisoCamara() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERM) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara();
            } else {
                MoviAlert.error(this, "Permiso requerido",
                        "Necesitamos acceso a la cámara para el reconocimiento facial.",
                        this::mostrarLoginNormal);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CÁMARA
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try { bindPreview(future.get()); }
            catch (Exception e) {
                Log.e(TAG, "Error iniciando cámara: " + e.getMessage());
                runOnUiThread(() -> MoviAlert.error(this, "Error de cámara",
                        "No se pudo iniciar la cámara. Intenta de nuevo."));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::capturarUnicaFoto);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis);
            runOnUiThread(() ->
                    txtEstado.setText("📷 Coloca tu rostro frente a la cámara..."));
        } catch (Exception e) {
            Log.e(TAG, "Error vinculando cámara: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURA
    // ══════════════════════════════════════════════════════════════════════════

    private void capturarUnicaFoto(@NonNull ImageProxy image) {
        if (yaCapturado.get()) { image.close(); return; }
        Bitmap bitmap = imageProxyToBitmap(image);
        image.close();
        if (bitmap != null) ultimoFrameBitmap = bitmap;

        if (!cuentaRegresivaIniciada.get()) {
            if (cuentaRegresivaIniciada.compareAndSet(false, true)) {
                runOnUiThread(() -> iniciarCuentaRegresiva(() -> {
                    yaCapturado.set(true);
                    if (ultimoFrameBitmap != null) {
                        runOnUiThread(() -> {
                            txtEstado.setText("☁️ Subiendo imagen...");
                            loadingDialog = MoviAlert.loading(this, "Verificando identidad...");
                        });
                        subirACloudinary(ultimoFrameBitmap);
                    } else {
                        runOnUiThread(() -> {
                            txtEstado.setText("❌ Error capturando imagen");
                            MoviAlert.error(this, "Error de captura",
                                    "No se pudo capturar tu rostro. Intenta de nuevo.",
                                    () -> {
                                        yaCapturado.set(false);
                                        cuentaRegresivaIniciada.set(false);
                                        txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
                                    });
                        });
                    }
                }));
            }
        }
    }

    private void iniciarCuentaRegresiva(Runnable onComplete) {
        final int[] countdown = {3};
        final android.os.Handler handler =
                new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable r = new Runnable() {
            @Override public void run() {
                if (countdown[0] > 0) {
                    txtEstado.setText("📸 Preparando... " + countdown[0]);
                    AnimUtils.bounceIn(txtEstado, 0);   // bounce en cada número
                    countdown[0]--;
                    handler.postDelayed(this, 1000);
                } else {
                    onComplete.run();
                }
            }
        };
        handler.post(r);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining(),
                    uSize = uBuffer.remaining(),
                    vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
            byte[] imageBytes = out.toByteArray();
            Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(
                    bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo imagen: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLOUDINARY
    // ══════════════════════════════════════════════════════════════════════════

    private void subirACloudinary(Bitmap bitmap) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] imageBytes = baos.toByteArray();

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("file", "rostro_login.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                    .build();

            client.newCall(new Request.Builder()
                    .url(CLOUDINARY_UPLOAD_URL)
                    .post(requestBody)
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        txtEstado.setText("❌ Error de conexión");
                        MoviAlert.error(Login.this, "Sin conexión",
                                "No se pudo subir la imagen. Verifica tu internet.",
                                () -> mostrarBotonReintentar());
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            String imageUrl = new JSONObject(body).getString("secure_url");
                            runOnUiThread(() -> txtEstado.setText("🔐 Verificando identidad..."));
                            verificarConBackend(imageUrl);
                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                dismissLoading();
                                txtEstado.setText("❌ Error procesando");
                                MoviAlert.error(Login.this, "Error inesperado",
                                        "Hubo un problema al procesar la imagen.",
                                        () -> mostrarBotonReintentar());
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            dismissLoading();
                            txtEstado.setText("❌ Error subiendo imagen");
                            MoviAlert.error(Login.this, "Error al subir imagen",
                                    "El servidor rechazó la imagen (código " + response.code() + ").",
                                    () -> mostrarBotonReintentar());
                        });
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                dismissLoading();
                txtEstado.setText("❌ Error");
                MoviAlert.error(this, "Error interno",
                        "Ocurrió un error inesperado: " + e.getMessage(),
                        this::mostrarBotonReintentar);
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BACKEND FACIAL
    // ══════════════════════════════════════════════════════════════════════════

    private void verificarConBackend(String faceImageUrl) {
        try {
            JSONObject json = new JSONObject();
            json.put("faceImageUrl", faceImageUrl);

            client.newCall(new Request.Builder()
                    .url(Constantes.LOGIN)
                    .post(RequestBody.create(json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        txtEstado.setText("❌ Sin conexión");
                        MoviAlert.error(Login.this, "Sin conexión",
                                "No se pudo conectar al servidor.\nVerifica tu internet.",
                                () -> mostrarBotonReintentar());
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        dismissLoading();
                        if (response.isSuccessful()) {
                            procesarLoginFacialExitoso(responseBody);
                        } else {
                            String errorMsg;
                            switch (response.code()) {
                                case 404: errorMsg = "Rostro no reconocido.\n¿Registraste tu cara al crear la cuenta?"; break;
                                case 401: errorMsg = "No tienes permiso para acceder."; break;
                                case 403: errorMsg = "Tu cuenta está inactiva o suspendida."; break;
                                case 500: errorMsg = "Error interno del servidor. Intenta más tarde."; break;
                                default:  errorMsg = "Error inesperado (código " + response.code() + ").";
                            }
                            try {
                                JSONObject err = new JSONObject(responseBody);
                                if (err.has("message"))    errorMsg = err.getString("message");
                                else if (err.has("error")) errorMsg = err.getString("error");
                            } catch (Exception ignored) {}

                            txtEstado.setText("😕 No reconocido");
                            final String mensajeFinal = errorMsg;
                            MoviAlert.error(Login.this, "Rostro no reconocido",
                                    mensajeFinal, () -> mostrarBotonReintentar());
                        }
                    });
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                dismissLoading();
                txtEstado.setText("❌ Error interno");
                MoviAlert.error(this, "Error interno",
                        "Ocurrió un error inesperado: " + e.getMessage(),
                        this::mostrarBotonReintentar);
            });
        }
    }

    private void procesarLoginFacialExitoso(String responseBody) {
        try {
            JSONObject resp = new JSONObject(responseBody);
            String token  = null;
            int idUsuario = -1, idRol = -1;
            String nombre = "Usuario", email = "", telefono = "", fotoPerfil = "";

            if (resp.has("token"))            token = resp.getString("token");
            else if (resp.has("accessToken")) token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario  = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre     = u.optString("nombre",   "Usuario");
                email      = u.optString("email",    "");
                telefono   = u.optString("telefono", "");
                fotoPerfil = u.optString("fotoPerfil",
                        u.optString("fotoPerfi",
                                u.optString("foto",
                                        u.optString("photoUrl",
                                                u.optString("profilePicture", "")))));
                if (u.has("idRol"))    idRol = u.getInt("idRol");
                else if (u.has("rol")) idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            if (!fotoPerfil.isEmpty() && !fotoPerfil.equals("null")) {
                sessionManager.saveFotoPerfil(fotoPerfil);
                Log.d(TAG, "Foto de perfil (facial) guardada: " + fotoPerfil);
            }

            txtEstado.setText("✅ ¡Bienvenido " + nombre + "!");
            AnimUtils.bounceIn(txtEstado, 0);   // bounce en el mensaje de éxito

            MoviAlert.toast(this, "¡Bienvenido, " + nombre + "!", MoviAlert.SUCCESS);
            sessionManager.setPendingWelcome(true);
            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando respuesta facial: " + e.getMessage());
            txtEstado.setText("✅ Login exitoso");
            MoviAlert.toast(this, "¡Login exitoso!", MoviAlert.SUCCESS);
            startActivity(new Intent(this, HomePasajero.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REINTENTAR
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarBotonReintentar() {
        android.widget.Button btnReintentar =
                layoutFace.findViewWithTag("btn_reintentar");
        if (btnReintentar == null) {
            btnReintentar = new android.widget.Button(this);
            btnReintentar.setTag("btn_reintentar");
            btnReintentar.setText("🔄  Intentar de nuevo");
            btnReintentar.setTextColor(0xFFFFFFFF);
            btnReintentar.setBackgroundColor(0xFF1ABC9C);
            btnReintentar.setPadding(40, 24, 40, 24);

            ConstraintLayout.LayoutParams params =
                    new ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.WRAP_CONTENT,
                            ConstraintLayout.LayoutParams.WRAP_CONTENT);
            params.bottomToTop  = txtEstado.getId();
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomMargin = 24;
            btnReintentar.setLayoutParams(params);
            layoutFace.addView(btnReintentar);
        }

        final android.widget.Button boton = btnReintentar;
        boton.setVisibility(View.VISIBLE);
        AnimUtils.bounceIn(boton, 0);   // bounce al aparecer

        boton.setOnClickListener(v -> {
            AnimUtils.tapPulse(v);
            v.postDelayed(() -> {
                boton.setVisibility(View.GONE);
                yaCapturado.set(false);
                cuentaRegresivaIniciada.set(false);
                txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
            }, 150);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GUARDAR SESIÓN Y NAVEGAR
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    //  GUARDAR SESIÓN Y NAVEGAR
    //  ⚠️ Reemplaza el método guardarSesionYNavegar existente en Login.java
    // ══════════════════════════════════════════════════════════════════════════

    private void guardarSesionYNavegar(String token, String nombre, String email,
                                       String telefono, int idRol, int idUsuario) {
        if (token != null && idUsuario != -1 && idRol != -1) {
            sessionManager.saveToken(token);
            SesionUsuario.setToken(token);
            sessionManager.saveUser(nombre, email, telefono, idRol, idUsuario);
            SesionUsuario.setIdUsuario(idUsuario);
            SesionUsuario.setIdRol(idRol);
            sessionManager.setLoggedIn(true);
            sessionManager.loadSessionToMemory();

            // 🔔 Enviar token FCM al backend para poder recibir notificaciones push
            final int idUsuarioFinal = idUsuario;
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(fcmToken -> {
                        try {
                            org.json.JSONObject body = new org.json.JSONObject();
                            body.put("token",     fcmToken);
                            body.put("idUsuario", idUsuarioFinal);

                            com.arlys.moviflexx.model.ConexionApi.getInstance(this).post(
                                    com.arlys.moviflexx.model.Constantes.BASE_URL
                                            + "/api/usuarios/" + idUsuarioFinal + "/fcm-token",
                                    body,
                                    response -> android.util.Log.d("FCM", "Token guardado OK"),
                                    error    -> android.util.Log.w("FCM", "Error guardando token FCM")
                            );
                        } catch (Exception e) {
                            android.util.Log.e("FCM", "Error preparando token: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e ->
                            android.util.Log.w("FCM", "No se pudo obtener token FCM: " + e.getMessage())
                    );
        }

        startActivity(new Intent(this,
                idRol == 2 ? HomeConductor.class : HomePasajero.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    // ─── SCREEN DESCRIPTOR ────────────────────────────────────────────────────
    @Override public String getNombrePantalla() { return "Inicio de Sesión"; }
    @Override public String getDescripcionPantalla() {
        return "Estás en la pantalla de inicio de sesión. "
             + "Hay campos para correo y contraseña, botón de login, "
             + "login con Google y reconocimiento facial.";
    }
    @Override public String getOpcionesPantalla() {
        return "Puedes iniciar sesión con correo y contraseña, "
             + "con Google, o con reconocimiento facial. "
             + "También puedes ir a registrarte si no tienes cuenta.";
    }
}