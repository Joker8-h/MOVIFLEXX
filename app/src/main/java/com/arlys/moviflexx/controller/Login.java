package com.arlys.moviflexx.controller;

import android.Manifest;
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
import android.widget.Toast;

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
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.SesionUsuario;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
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

    // ── Cloudinary ─────────────────────────────────────────────────────────────
    private static final String CLOUDINARY_CLOUD_NAME = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";

    private volatile Bitmap ultimoFrameBitmap = null;

    private static final String CLOUDINARY_UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    // ── URLs del backend ───────────────────────────────────────────────────────
    // AMBOS usan la misma ruta, el backend diferencia por el contenido:
    // - Si recibe {email, password} → login tradicional
    // - Si recibe {faceImageUrl} → login facial

    // ── Step 1 — Login normal ─────────────────────────────────────────────────
    private View layoutLogin;
    private TextInputEditText edtEmail, edtPassword;

    // ── Step 2 — Face login (SOLO 1 FOTO) ─────────────────────────────────────
    private ConstraintLayout layoutFace;
    private PreviewView previewView;
    private TextView txtEstado;
    private ExecutorService cameraExecutor;

    // ── Google / Firebase ─────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    // ── Sesión ────────────────────────────────────────────────────────────────
    private SessionManager sessionManager;

    // ── Control: UNA SOLA FOTO ────────────────────────────────────────────────
    private final AtomicBoolean yaCapturado = new AtomicBoolean(false);

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
                            Toast.makeText(this,
                                    "Error Google Sign-In", Toast.LENGTH_SHORT).show();
                        }
                    });

    // ══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Step 1 — formulario
        layoutLogin = findViewById(R.id.layoutLogin);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);

        // Step 2 — cámara facial
        layoutFace = findViewById(R.id.layoutFace);
        previewView = findViewById(R.id.previewView);
        txtEstado = findViewById(R.id.txtEstado);

        layoutFace.setVisibility(View.GONE);

        mAuth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        configurarGoogle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN ENTRE PASOS
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarFaceLogin() {
        layoutLogin.setVisibility(View.GONE);
        layoutFace.setVisibility(View.VISIBLE);
        yaCapturado.set(false); // Reset para nueva sesión
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
    //  OPCIÓN 1 — LOGIN EMAIL + PASSWORD (SIN FOTO)
    // ══════════════════════════════════════════════════════════════════════════

    public void login(View view) {
        String email = edtEmail.getText() != null
                ? edtEmail.getText().toString().trim() : "";
        String password = edtPassword.getText() != null
                ? edtPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
            // NO se incluye faceImageUrl - es login tradicional
        } catch (JSONException e) {
            return;
        }

        Log.d(TAG, "📧 Login email/password → " + Constantes.LOGIN);
        final String emailFinal = email;

        client.newCall(new Request.Builder()
                .url(Constantes.LOGIN) // Ruta tradicional de login
                .post(RequestBody.create(json.toString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build()
        ).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "❌ Fallo conexión: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(Login.this,
                        "Sin conexión: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "HTTP " + response.code() + " | " + body);

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        procesarRespuestaLogin(body, emailFinal);
                    } else {
                        String msg;
                        switch (response.code()) {
                            case 401:
                                msg = "Email o contraseña incorrectos";
                                break;
                            case 404:
                                msg = "Usuario no encontrado";
                                break;
                            case 500:
                                msg = "Error del servidor";
                                break;
                            default:
                                msg = "Error " + response.code() + ": " + body;
                        }
                        Toast.makeText(Login.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void procesarRespuestaLogin(String responseBody, String emailFallback) {
        try {
            JSONObject resp = new JSONObject(responseBody);
            String token = null;
            int idUsuario = -1;
            int idRol = -1;
            String nombre = "";
            String email = "";
            String telefono = "";

            if (resp.has("token"))
                token = resp.getString("token");
            else if (resp.has("accessToken"))
                token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre = u.optString("nombre", "Usuario");
                email = u.optString("email", emailFallback);
                telefono = u.optString("telefono", "Sin teléfono");

                if (u.has("idRol"))
                    idRol = u.getInt("idRol");
                else if (u.has("rol"))
                    idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            if (token == null || idUsuario == -1 || idRol == -1) {
                Toast.makeText(this, "Error de credenciales", Toast.LENGTH_LONG).show();
                return;
            }

            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            Toast.makeText(this, "Error en respuesta del servidor", Toast.LENGTH_SHORT).show();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  OPCIÓN 2 — BOTÓN PARA ABRIR LOGIN FACIAL
    // ══════════════════════════════════════════════════════════════════════════

    public void irFaceLogin(View view) {
        mostrarFaceLogin();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GOOGLE
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.btnGoogle).setOnClickListener(v ->
                googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sessionManager.setLoggedIn(true);
                        startActivity(new Intent(Login.this, HomePasajero.class));
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Error autenticando con Google", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  IR A REGISTRO
    // ══════════════════════════════════════════════════════════════════════════

    public void irRegister(View view) {
        startActivity(new Intent(this, Register.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERMISOS CÁMARA
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
                Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_LONG).show();
                mostrarLoginNormal();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CÁMARA — CAPTURA 1 SOLA FOTO
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                bindPreview(future.get());
            } catch (Exception e) {
                Log.e(TAG, "Error iniciando cámara: " + e.getMessage());
                Toast.makeText(this, "Error iniciando cámara", Toast.LENGTH_SHORT).show();
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
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);

            runOnUiThread(() ->
                    txtEstado.setText("📷 Coloca tu rostro frente a la cámara..."));
        } catch (Exception e) {
            Log.e(TAG, "Error vinculando cámara: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURA 1 SOLA FOTO CON DELAY DE 3 SEGUNDOS
    // ══════════════════════════════════════════════════════════════════════════

    private final AtomicBoolean cuentaRegresivaIniciada = new AtomicBoolean(false);

    private void capturarUnicaFoto(@NonNull ImageProxy image) {

        if (yaCapturado.get()) {
            image.close();
            return;
        }

        Bitmap bitmap = imageProxyToBitmap(image);
        image.close();

        if (bitmap != null) {
            ultimoFrameBitmap = bitmap;
        }

        if (!cuentaRegresivaIniciada.get()) {
            if (cuentaRegresivaIniciada.compareAndSet(false, true)) {
                runOnUiThread(() -> {
                    iniciarCuentaRegresiva(() -> {
                        yaCapturado.set(true);

                        if (ultimoFrameBitmap != null) {
                            txtEstado.setText("☁️ Subiendo imagen...");
                            subirACloudinary(ultimoFrameBitmap);
                        } else {
                            txtEstado.setText("❌ Error capturando imagen");
                            yaCapturado.set(false);
                            cuentaRegresivaIniciada.set(false);
                        }
                    });
                });
            }
        }
    }


    private void iniciarCuentaRegresiva(Runnable onComplete) {
        final int[] countdown = {3}; // 3 segundos

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    txtEstado.setText("📸 Preparando... " + countdown[0]);
                    countdown[0]--;
                    handler.postDelayed(this, 1000); // 1 segundo
                } else {
                    onComplete.run();
                }
            }
        };

        handler.post(countdownRunnable);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            // 🔥 ROTAR CORRECTAMENTE
            int rotationDegrees = image.getImageInfo().getRotationDegrees();

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotationDegrees);

            return Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo imagen: " + e.getMessage());
            return null;
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  PASO A — Subir a Cloudinary
    // ══════════════════════════════════════════════════════════════════════════

    private void subirACloudinary(Bitmap bitmap) {
        runOnUiThread(() -> txtEstado.setText("☁️ Subiendo imagen..."));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] imageBytes = baos.toByteArray();

            Log.d(TAG, "☁️ Subiendo a Cloudinary: " + imageBytes.length + " bytes");

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

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "❌ Cloudinary error: " + e.getMessage());
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Error de conexión");
                        Toast.makeText(Login.this,
                                "No se pudo subir la imagen", Toast.LENGTH_SHORT).show();
                        mostrarBotonReintentar();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            String faceImageUrl = new JSONObject(body).getString("secure_url");
                            Log.d(TAG, "✅ Cloudinary OK: " + faceImageUrl);
                            verificarConBackend(faceImageUrl);
                        } catch (JSONException e) {
                            Log.e(TAG, "❌ Error JSON: " + e.getMessage());
                            runOnUiThread(() -> {
                                txtEstado.setText("❌ Error procesando");
                                mostrarBotonReintentar();
                            });
                        }
                    } else {
                        Log.e(TAG, "❌ Cloudinary HTTP " + response.code());
                        runOnUiThread(() -> {
                            txtEstado.setText("❌ Error subiendo imagen");
                            mostrarBotonReintentar();
                        });
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
            runOnUiThread(() -> {
                txtEstado.setText("❌ Error");
                mostrarBotonReintentar();
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASO B — Verificar con backend (SOLO FOTO, SIN EMAIL)
    // ══════════════════════════════════════════════════════════════════════════

    private void verificarConBackend(String faceImageUrl) {
        runOnUiThread(() -> txtEstado.setText("🔐 Verificando identidad..."));

        try {
            JSONObject json = new JSONObject();
            json.put("faceImageUrl", faceImageUrl);
            // NO se incluye email ni nombre - reconocimiento 100% facial
            // El backend debe comparar esta foto con TODAS las fotos del registro

            Log.d(TAG, "🔐 Login FACIAL (sin email) → " + Constantes.LOGIN);
            Log.d(TAG, "Body: " + json.toString());

            client.newCall(new Request.Builder()
                    .url(Constantes.LOGIN) // Misma ruta que login tradicional
                    .post(RequestBody.create(json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "❌ Backend error: " + e.getMessage());
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Sin conexión");
                        Toast.makeText(Login.this,
                                "Error de red: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        mostrarBotonReintentar();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody =
                            response.body() != null ? response.body().string() : "";

                    Log.d(TAG, "Backend → " + response.code() + " | " + responseBody);

                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "✅ Login facial exitoso");
                            procesarLoginFacialExitoso(responseBody);
                        } else {
                            String errorMsg = "Rostro no reconocido";

                            try {
                                JSONObject err = new JSONObject(responseBody);
                                if (err.has("message")) {
                                    errorMsg = err.getString("message");
                                } else if (err.has("error")) {
                                    errorMsg = err.getString("error");
                                }
                            } catch (Exception ignored) {
                            }

                            Log.e(TAG, "❌ " + errorMsg);
                            txtEstado.setText("😕 " + errorMsg);

                            String toast = errorMsg;
                            if (response.code() == 404) {
                                toast += "\n\n¿Ya registraste tu rostro?";
                            }

                            Toast.makeText(Login.this, toast, Toast.LENGTH_LONG).show();
                            mostrarBotonReintentar();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
            runOnUiThread(() -> {
                txtEstado.setText("❌ Error interno");
                mostrarBotonReintentar();
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASO C — Éxito
    // ══════════════════════════════════════════════════════════════════════════

    private void procesarLoginFacialExitoso(String responseBody) {
        try {
            JSONObject resp = new JSONObject(responseBody);
            String token = null;
            int idUsuario = -1;
            int idRol = -1;
            String nombre = "Usuario";
            String email = "";
            String telefono = "";

            if (resp.has("token"))
                token = resp.getString("token");
            else if (resp.has("accessToken"))
                token = resp.getString("accessToken");

            if (resp.has("usuario")) {
                JSONObject u = resp.getJSONObject("usuario");
                idUsuario = u.optInt("idUsuarios", u.optInt("id", -1));
                nombre = u.optString("nombre", "Usuario");
                email = u.optString("email", "");
                telefono = u.optString("telefono", "");

                if (u.has("idRol"))
                    idRol = u.getInt("idRol");
                else if (u.has("rol"))
                    idRol = u.getJSONObject("rol").optInt("idRol", -1);
            }

            Log.d(TAG, "✅ Usuario: " + nombre);
            txtEstado.setText("✅ ¡Bienvenido " + nombre + "!");
            Toast.makeText(this, "¡Bienvenido " + nombre + "!", Toast.LENGTH_SHORT).show();

            guardarSesionYNavegar(token, nombre, email, telefono, idRol, idUsuario);

        } catch (JSONException e) {
            Log.e(TAG, "Error parseando: " + e.getMessage());
            txtEstado.setText("✅ Login exitoso");
            Toast.makeText(this, "Login exitoso", Toast.LENGTH_SHORT).show();
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
            params.bottomToTop = txtEstado.getId();
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomMargin = 24;
            btnReintentar.setLayoutParams(params);
            layoutFace.addView(btnReintentar);
        }

        final android.widget.Button boton = btnReintentar;
        boton.setVisibility(View.VISIBLE);
        boton.setOnClickListener(v -> {
            boton.setVisibility(View.GONE);
            yaCapturado.set(false); // Reset para nueva captura
            cuentaRegresivaIniciada.set(false);
            txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GUARDAR SESIÓN Y NAVEGAR
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

        Toast.makeText(this, "Bienvenido " + nombre, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this,
                idRol == 2 ? HomeConductor.class : HomePasajero.class));
        finish();
    }
}