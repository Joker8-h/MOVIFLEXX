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

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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

public class Login extends AppCompatActivity {

    private static final String TAG = "LOGIN_DEBUG";
    private static final int REQUEST_CAMERA_PERM = 100;

    private static final String CLOUDINARY_CLOUD_NAME  = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";
    private static final String CLOUDINARY_UPLOAD_URL  =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    private volatile Bitmap ultimoFrameBitmap = null;

    private View layoutLogin;
    private TextInputEditText edtEmail, edtPassword;
    private TextInputLayout   tilEmail, tilPassword;

    private ConstraintLayout layoutFace;
    private PreviewView      previewView;
    private TextView         txtEstado;
    private ExecutorService  cameraExecutor;

    private FirebaseAuth     mAuth;
    private GoogleSignInClient googleSignInClient;
    private SessionManager   sessionManager;

    // Loading dialog (para mostrarlo/cerrarlo)
    private Dialog loadingDialog = null;

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
                            MoviAlert.error(this,
                                    "Error con Google",
                                    "No se pudo completar el inicio de sesión con Google.");
                        }
                    });

    // ══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        layoutLogin = findViewById(R.id.layoutLogin);

        tilEmail    = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        edtEmail    = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);

        layoutFace  = findViewById(R.id.layoutFace);
        previewView = findViewById(R.id.previewView);
        txtEstado   = findViewById(R.id.txtEstado);

        layoutFace.setVisibility(View.GONE);

        mAuth          = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // ── Tooltips al tocar el ícono de error de cada campo ──
        FieldTooltip.attach(tilEmail,    "Correo electrónico");
        FieldTooltip.attach(tilPassword, "Contraseña");

        configurarValidacionesEnTiempoReal();
        configurarGoogle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDACIONES EN TIEMPO REAL
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarValidacionesEnTiempoReal() {

        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validarEmail(edtEmail.getText().toString());
        });

        edtPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() > 0) {
                    validarPassword(s.toString());
                } else {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDADORES
    // ══════════════════════════════════════════════════════════════════════════

    private boolean validarEmail(String valor) {
        valor = valor.trim();
        if (valor.isEmpty()) {
            tilEmail.setError("El correo es obligatorio");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(valor).matches()) {
            tilEmail.setError("Formato inválido — debe ser: usuario@correo.com");
            return false;
        }
        tilEmail.setError(null);
        tilEmail.setErrorEnabled(false);
        return true;
    }

    private boolean validarPassword(String valor) {
        if (valor.isEmpty()) {
            tilPassword.setError("La contraseña es obligatoria");
            return false;
        }
        StringBuilder faltantes = new StringBuilder();
        if (valor.length() < 8)
            faltantes.append("• Mínimo 8 caracteres\n");
        if (!valor.matches(".*[A-Z].*"))
            faltantes.append("• Al menos una letra mayúscula (A-Z)\n");
        if (!valor.matches(".*[a-z].*"))
            faltantes.append("• Al menos una letra minúscula (a-z)\n");
        if (!valor.matches(".*[0-9].*"))
            faltantes.append("• Al menos un número (0-9)\n");
        if (!valor.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"))
            faltantes.append("• Al menos un carácter especial (!@#$%...)\n");
        if (faltantes.length() > 0) {
            tilPassword.setError("Faltan requisitos:\n" + faltantes.toString().trim());
            return false;
        }
        tilPassword.setError(null);
        tilPassword.setErrorEnabled(false);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN ENTRE PASOS
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarFaceLogin() {
        layoutLogin.setVisibility(View.GONE);
        layoutFace.setVisibility(View.VISIBLE);
        yaCapturado.set(false);
        cuentaRegresivaIniciada.set(false);
        txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");

        if (verificarPermisoCamara()) {
            iniciarCamara();
        } else {
            solicitarPermisoCamara();
        }
    }

    private void mostrarLoginNormal() {
        layoutFace.setVisibility(View.GONE);
        layoutLogin.setVisibility(View.VISIBLE);
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

        // Mostrar loading
        loadingDialog = MoviAlert.loading(this, "Iniciando sesión...");

        JSONObject json = new JSONObject();
        try {
            json.put("email",    email);
            json.put("password", password);
        } catch (JSONException e) {
            dismissLoading();
            return;
        }

        Log.d(TAG, "📧 Login email/password → " + Constantes.LOGIN);
        final String emailFinal = email;

        client.newCall(new Request.Builder()
                .url(Constantes.LOGIN)
                .post(RequestBody.create(json.toString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build()
        ).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "❌ Fallo conexión: " + e.getMessage());
                runOnUiThread(() -> {
                    dismissLoading();
                    MoviAlert.error(Login.this,
                            "Sin conexión",
                            "No se pudo conectar al servidor.\nVerifica tu internet e intenta de nuevo.");
                });
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "HTTP " + response.code() + " | " + body);
                runOnUiThread(() -> {
                    dismissLoading();
                    if (response.isSuccessful()) {
                        procesarRespuestaLogin(body, emailFinal);
                    } else {
                        String msg;
                        switch (response.code()) {
                            case 401: msg = "El correo o la contraseña son incorrectos.\nVerifica tus datos e intenta de nuevo."; break;
                            case 404: msg = "No encontramos una cuenta con ese correo.\n¿Ya tienes cuenta? Regístrate."; break;
                            case 429: msg = "Demasiados intentos fallidos.\nEspera unos minutos antes de volver a intentarlo."; break;
                            case 500: msg = "Error interno del servidor.\nInténtalo más tarde."; break;
                            default:  msg = "Error inesperado (código " + response.code() + ").\n" + body;
                        }
                        if (response.code() == 401 || response.code() == 404) {
                            MoviAlert.error(Login.this, "Credenciales incorrectas", msg);
                        } else if (response.code() == 429) {
                            MoviAlert.warning(Login.this, "Límite de intentos", msg);
                        } else {
                            MoviAlert.error(Login.this, "Error al iniciar sesión", msg);
                        }
                    }
                });
            }
        });
    }

    private void procesarRespuestaLogin(String responseBody, String emailFallback) {
        try {
            JSONObject resp = new JSONObject(responseBody);
            String token   = null;
            int idUsuario  = -1, idRol = -1;
            String nombre  = "", email = "", telefono = "";

            if (resp.has("token"))            token = resp.getString("token");
            else if (resp.has("accessToken")) token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre    = u.optString("nombre",   "Usuario");
                email     = u.optString("email",    emailFallback);
                telefono  = u.optString("telefono", "Sin teléfono");

                if (u.has("idRol"))      idRol = u.getInt("idRol");
                else if (u.has("rol"))   idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            if (token == null || idUsuario == -1 || idRol == -1) {
                MoviAlert.error(this,
                        "Error de credenciales",
                        "La respuesta del servidor no es válida. Contacta al soporte.");
                return;
            }
            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            MoviAlert.error(this,
                    "Error inesperado",
                    "Hubo un problema procesando la respuesta del servidor.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FACE LOGIN
    // ══════════════════════════════════════════════════════════════════════════

    public void irFaceLoagin(View view) { mostrarFaceLogin(); }

    public void irQrScanner(View view) {
        startActivity(new Intent(this, QrScanner.class));
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
        findViewById(R.id.btnGoogle).setOnClickListener(v ->
                googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        loadingDialog = MoviAlert.loading(this, "Autenticando con Google...");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    dismissLoading();
                    if (task.isSuccessful()) {
                        sessionManager.setLoggedIn(true);
                        MoviAlert.toast(this, "¡Bienvenido con Google!", MoviAlert.SUCCESS);
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> {
                                    startActivity(new Intent(Login.this, HomePasajero.class));
                                    finish();
                                }, 800);
                    } else {
                        MoviAlert.error(this,
                                "Error con Google",
                                "No se pudo autenticar con Google.\nIntenta de nuevo.");
                    }
                });
    }

    public void irRegister(View view) {
        startActivity(new Intent(this, Register.class));
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara();
            } else {
                MoviAlert.error(this,
                        "Permiso requerido",
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
                runOnUiThread(() -> MoviAlert.error(this,
                        "Error de cámara",
                        "No se pudo iniciar la cámara. Intenta de nuevo."));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::capturarUnicaFoto);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
            runOnUiThread(() -> txtEstado.setText("📷 Coloca tu rostro frente a la cámara..."));
        } catch (Exception e) { Log.e(TAG, "Error vinculando cámara: " + e.getMessage()); }
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
                            MoviAlert.error(this,
                                    "Error de captura",
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
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable r = new Runnable() {
            @Override public void run() {
                if (countdown[0] > 0) {
                    txtEstado.setText("📸 Preparando... " + countdown[0]);
                    countdown[0]--;
                    handler.postDelayed(this, 1000);
                } else { onComplete.run(); }
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

            int ySize = yBuffer.remaining(), uSize = uBuffer.remaining(), vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
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

            client.newCall(new Request.Builder().url(CLOUDINARY_UPLOAD_URL).post(requestBody).build())
                    .enqueue(new Callback() {
                        @Override public void onFailure(Call call, IOException e) {
                            runOnUiThread(() -> {
                                dismissLoading();
                                txtEstado.setText("❌ Error de conexión");
                                MoviAlert.error(Login.this,
                                        "Sin conexión",
                                        "No se pudo subir la imagen. Verifica tu internet.",
                                        () -> mostrarBotonReintentar());
                            });
                        }

                        @Override public void onResponse(Call call, Response response) throws IOException {
                            String body = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                try {
                                    verificarConBackend(new JSONObject(body).getString("secure_url"));
                                } catch (JSONException e) {
                                    runOnUiThread(() -> {
                                        dismissLoading();
                                        txtEstado.setText("❌ Error procesando");
                                        MoviAlert.error(Login.this,
                                                "Error inesperado",
                                                "Hubo un problema al procesar la imagen.",
                                                () -> mostrarBotonReintentar());
                                    });
                                }
                            } else {
                                runOnUiThread(() -> {
                                    dismissLoading();
                                    txtEstado.setText("❌ Error subiendo imagen");
                                    MoviAlert.error(Login.this,
                                            "Error al subir imagen",
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
        runOnUiThread(() -> txtEstado.setText("🔐 Verificando identidad..."));
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
                        MoviAlert.error(Login.this,
                                "Sin conexión",
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
                                case 404: errorMsg = "Rostro no reconocido.\n¿Ya registraste tu cara al crear la cuenta?"; break;
                                case 401: errorMsg = "No tienes permiso para acceder."; break;
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
                            MoviAlert.error(Login.this,
                                    "Rostro no reconocido",
                                    mensajeFinal,
                                    () -> mostrarBotonReintentar());
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
            String token   = null;
            int idUsuario  = -1, idRol = -1;
            String nombre  = "Usuario", email = "", telefono = "";

            if (resp.has("token"))            token = resp.getString("token");
            else if (resp.has("accessToken")) token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre    = u.optString("nombre",   "Usuario");
                email     = u.optString("email",    "");
                telefono  = u.optString("telefono", "");

                if (u.has("idRol"))    idRol = u.getInt("idRol");
                else if (u.has("rol")) idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            txtEstado.setText("✅ ¡Bienvenido " + nombre + "!");
            MoviAlert.toast(this, "¡Bienvenido, " + nombre + "!", MoviAlert.SUCCESS);
            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            txtEstado.setText("✅ Login exitoso");
            MoviAlert.toast(this, "¡Login exitoso!", MoviAlert.SUCCESS);
            startActivity(new Intent(this, HomePasajero.class));
            finish();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REINTENTAR
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarBotonReintentar() {
        android.widget.Button btnReintentar = layoutFace.findViewWithTag("btn_reintentar");
        if (btnReintentar == null) {
            btnReintentar = new android.widget.Button(this);
            btnReintentar.setTag("btn_reintentar");
            btnReintentar.setText("🔄  Intentar de nuevo");
            btnReintentar.setTextColor(0xFFFFFFFF);
            btnReintentar.setBackgroundColor(0xFF2EC4B6);
            btnReintentar.setPadding(40, 24, 40, 24);

            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
            params.bottomToTop  = txtEstado.getId();
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd     = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomMargin = 24;
            btnReintentar.setLayoutParams(params);
            layoutFace.addView(btnReintentar);
        }
        final android.widget.Button boton = btnReintentar;
        boton.setVisibility(View.VISIBLE);
        boton.setOnClickListener(v -> {
            boton.setVisibility(View.GONE);
            yaCapturado.set(false);
            cuentaRegresivaIniciada.set(false);
            txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SESIÓN
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
        }
        startActivity(new Intent(this, idRol == 2 ? HomeConductor.class : HomePasajero.class));
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
}