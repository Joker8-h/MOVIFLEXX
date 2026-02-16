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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;

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

public class Register extends AppCompatActivity {

    private static final String TAG = "REGISTER_DEBUG";
    private static final int REQUEST_CAMERA_PERM = 200;

    // ── Cloudinary ─────────────────────────────────────────────────────────────
    private static final String CLOUDINARY_CLOUD_NAME = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";
    private static final String CLOUDINARY_UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    // ── Último frame capturado ────────────────────────────────────────────────
    private volatile Bitmap ultimoFrameBitmap = null;

    // ── Step 1 — Formulario ────────────────────────────────────────────────────
    private TextInputEditText edtNombre, edtEmail, edtTelefono, edtPassword;
    private RadioGroup rgRol;
    private MaterialButton btnRegistrar;
    private View layoutFormulario;

    // ── Step 2 — Escaneo facial (SOLO 1 FOTO) ─────────────────────────────────
    private ConstraintLayout layoutFace;
    private PreviewView previewView;
    private TextView txtEstado;
    private ExecutorService cameraExecutor;

    // ── Datos del formulario guardados en memoria ─────────────────────────────
    private String datosNombre;
    private String datosEmail;
    private String datosTelefono;
    private String datosPassword;
    private String datosRol;

    // ── Control: UNA SOLA FOTO ────────────────────────────────────────────────
    private final AtomicBoolean yaCapturado = new AtomicBoolean(false);
    private final AtomicBoolean cuentaRegresivaIniciada = new AtomicBoolean(false);

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // ══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Step 1 — vistas del formulario
        layoutFormulario = findViewById(R.id.layoutFormulario);
        edtNombre = findViewById(R.id.edt_nombre);
        edtEmail = findViewById(R.id.edt_email);
        edtTelefono = findViewById(R.id.edt_telefono);
        edtPassword = findViewById(R.id.edt_password);
        rgRol = findViewById(R.id.rgRol);
        btnRegistrar = findViewById(R.id.btn_register);

        // Step 2 — vistas de la cámara
        layoutFace = findViewById(R.id.layoutFace);
        previewView = findViewById(R.id.previewView);
        txtEstado = findViewById(R.id.txtEstado);

        layoutFace.setVisibility(View.GONE);

        btnRegistrar.setOnClickListener(v -> validarFormularioYEscanear());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (layoutFace.getVisibility() == View.VISIBLE) {
            mostrarFormulario();
        } else {
            super.onBackPressed();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STEP 1 — Validar campos
    // ══════════════════════════════════════════════════════════════════════════

    private void validarFormularioYEscanear() {
        String nombre = edtNombre.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (nombre.isEmpty() || email.isEmpty()
                || telefono.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgRol.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Selecciona un rol", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb = findViewById(checkedId);

        // Guardar en memoria
        datosNombre = nombre;
        datosEmail = email;
        datosTelefono = telefono;
        datosPassword = password;
        datosRol = rb.getText().toString().toUpperCase();

        Log.d(TAG, "Formulario válido → escaneo facial");

        mostrarEscaneoFacial();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarEscaneoFacial() {
        layoutFormulario.setVisibility(View.GONE);
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

    private void mostrarFormulario() {
        layoutFace.setVisibility(View.GONE);
        layoutFormulario.setVisibility(View.VISIBLE);
        yaCapturado.set(false);
        cuentaRegresivaIniciada.set(false);
        resetBoton();
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
                Toast.makeText(this,
                        "Permiso de cámara requerido", Toast.LENGTH_LONG).show();
                mostrarFormulario();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STEP 2 — CÁMARA
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
                    txtEstado.setText("📷 Coloca tu rostro dentro del óvalo..."));

        } catch (Exception e) {
            Log.e(TAG, "Error vinculando cámara: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CAPTURA 1 SOLA FOTO CON DELAY DE 3 SEGUNDOS (IGUAL QUE LOGIN)
    // ══════════════════════════════════════════════════════════════════════════

    private void capturarUnicaFoto(@NonNull ImageProxy image) {
        // Si ya capturamos, cerrar y retornar
        if (yaCapturado.get()) {
            image.close();
            return;
        }

        // Convertir frame actual a bitmap
        Bitmap bitmap = imageProxyToBitmap(image);
        image.close();

        // Guardar último frame
        if (bitmap != null) {
            ultimoFrameBitmap = bitmap;
        }

        // Iniciar countdown solo una vez
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

            // 🔥 ROTAR CORRECTAMENTE (igual que Login)
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

            Log.d(TAG, "☁️ Subiendo: " + imageBytes.length + " bytes");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("file", "rostro_registro.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                    .build();

            Request request = new Request.Builder()
                    .url(CLOUDINARY_UPLOAD_URL)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "❌ Cloudinary: " + e.getMessage());
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Sin internet");
                        Toast.makeText(Register.this,
                                "Error de conexión", Toast.LENGTH_SHORT).show();
                        mostrarBotonReintentar();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        try {
                            String imageUrl = new JSONObject(body).getString("secure_url");
                            Log.d(TAG, "✅ Cloudinary: " + imageUrl);
                            registrarEnBackend(imageUrl);
                        } catch (JSONException e) {
                            Log.e(TAG, "❌ JSON error: " + e.getMessage());
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
    //  PASO B — Registrar en backend
    // ══════════════════════════════════════════════════════════════════════════

    private void registrarEnBackend(String faceImageUrl) {
        runOnUiThread(() -> txtEstado.setText("💾 Creando cuenta..."));

        try {
            JSONObject json = new JSONObject();
            json.put("nombre", datosNombre);
            json.put("email", datosEmail);
            json.put("telefono", datosTelefono);
            json.put("password", datosPassword);
            json.put("rol", datosRol);
            json.put("faceImageUrl", faceImageUrl);

            Log.d(TAG, "💾 Registrando en: " + Constantes.REGISTER);
            Log.d(TAG, "Usuario: " + datosNombre + " | " + datosEmail);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(Constantes.REGISTER)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "❌ Backend: " + e.getMessage());
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Sin conexión");
                        Toast.makeText(Register.this,
                                "Error de conexión", Toast.LENGTH_SHORT).show();
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
                            Log.d(TAG, "✅ Registro exitoso");
                            txtEstado.setText("✅ ¡Cuenta creada!");
                            Toast.makeText(Register.this,
                                    "¡Registro exitoso! Ahora inicia sesión.",
                                    Toast.LENGTH_LONG).show();

                            startActivity(new Intent(Register.this, Login.class));
                            finish();

                        } else {
                            String errorMsg = "Error en el registro";

                            switch (response.code()) {
                                case 400:
                                    errorMsg = "Datos inválidos";
                                    break;
                                case 409:
                                    errorMsg = "El email ya está registrado";
                                    break;
                                case 429:
                                    errorMsg = "Demasiados intentos. Espera 5 minutos.";
                                    break;
                                case 500:
                                    errorMsg = "Error del servidor";
                                    break;
                            }

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
                            txtEstado.setText("❌ " + errorMsg);
                            Toast.makeText(Register.this, errorMsg, Toast.LENGTH_LONG).show();

                            // Solo mostrar reintentar si NO es error 429
                            if (response.code() != 429) {
                                mostrarBotonReintentar();
                            } else {
                                // Volver al formulario después de 3 segundos
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(() -> mostrarFormulario(), 3000);
                            }
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
            yaCapturado.set(false);
            cuentaRegresivaIniciada.set(false);
            txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    private void resetBoton() {
        btnRegistrar.setEnabled(true);
        btnRegistrar.setAlpha(1.0f);
        btnRegistrar.setText(getString(R.string.register_btn));
    }

    public void irLoginView(View view) {
        startActivity(new Intent(this, Login.class));
        finish();
    }
}