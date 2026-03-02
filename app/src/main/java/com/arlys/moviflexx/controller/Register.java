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
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.textfield.TextInputLayout;
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

    private static final String CLOUDINARY_CLOUD_NAME = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";
    private static final String CLOUDINARY_UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    private volatile Bitmap ultimoFrameBitmap = null;

    private TextInputEditText edtNombre, edtEmail, edtTelefono, edtPassword;
    private TextInputLayout   tilNombre, tilEmail, tilTelefono, tilPassword;
    private MaterialButton btnRegistrar;
    private View layoutFormulario;
    private CheckBox cbTerminos;

    private ConstraintLayout layoutFace;
    private PreviewView previewView;
    private TextView txtEstado;
    private ExecutorService cameraExecutor;

    private String datosNombre, datosEmail, datosTelefono, datosPassword, datosRol;

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

        layoutFormulario = findViewById(R.id.layoutFormulario);

        tilNombre   = findViewById(R.id.til_nombre);
        tilEmail    = findViewById(R.id.til_email);
        tilTelefono = findViewById(R.id.til_telefono);
        tilPassword = findViewById(R.id.til_password);

        edtNombre   = findViewById(R.id.edt_nombre);
        edtEmail    = findViewById(R.id.edt_email);
        edtTelefono = findViewById(R.id.edt_telefono);
        edtPassword = findViewById(R.id.edt_password);

        btnRegistrar = findViewById(R.id.btn_register);

        layoutFace  = findViewById(R.id.layoutFace);
        previewView = findViewById(R.id.previewView);
        txtEstado   = findViewById(R.id.txtEstado);

        // ── Términos y Condiciones ──
        cbTerminos = findViewById(R.id.cbTerminos);
        TextView tvLinkTerminos = findViewById(R.id.tvLinkTerminos);
        tvLinkTerminos.setOnClickListener(v -> mostrarTerminosYCondiciones());

        layoutFace.setVisibility(View.GONE);

        configurarValidacionesEnTiempoReal();

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
    //  TÉRMINOS Y CONDICIONES
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarTerminosYCondiciones() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_terminos);

        android.widget.ScrollView scrollView = dialog.findViewById(R.id.scrollTerminos);
        Button btnCerrarTop = dialog.findViewById(R.id.btnCerrarTerminosTop);
        Button btnCerrar    = dialog.findViewById(R.id.btnCerrarTerminos);
        Button btnAceptar   = dialog.findViewById(R.id.btnAceptarTerminos);
        TextView tvProgreso = dialog.findViewById(R.id.tvProgreso);

        // El botón Acepto arranca deshabilitado (gris)
        btnAceptar.setEnabled(false);

        // Detectar cuando el usuario llegó al final del scroll
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            android.widget.ScrollView sv = scrollView;
            int totalScrollHeight = sv.getChildAt(0).getHeight() - sv.getHeight();
            int currentScroll     = sv.getScrollY();

            // Si está a 50px o menos del fondo → habilitar
            if (totalScrollHeight - currentScroll <= 50) {
                if (!btnAceptar.isEnabled()) {
                    btnAceptar.setEnabled(true);
                    btnAceptar.getBackground().setTint(0xFF2EC4B6); // teal
                    tvProgreso.setText("✅  Ya puedes aceptar los Términos y Condiciones");
                    tvProgreso.setTextColor(0xFF2E7D32); // verde
                }
            }
        });

        btnCerrarTop.setOnClickListener(v -> dialog.dismiss());
        btnCerrar.setOnClickListener(v -> dialog.dismiss());

        btnAceptar.setOnClickListener(v -> {
            cbTerminos.setChecked(true);
            dialog.dismiss();
        });

        dialog.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDACIONES EN TIEMPO REAL
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarValidacionesEnTiempoReal() {

        edtNombre.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validarNombre(edtNombre.getText().toString());
        });

        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validarEmail(edtEmail.getText().toString());
        });

        edtTelefono.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validarTelefono(edtTelefono.getText().toString());
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

    private boolean validarNombre(String valor) {
        valor = valor.trim();

        if (valor.isEmpty()) {
            tilNombre.setError("El nombre es obligatorio");
            return false;
        }

        StringBuilder faltantes = new StringBuilder();
        if (valor.length() < 3)
            faltantes.append("• Mínimo 3 caracteres\n");
        if (!Character.isUpperCase(valor.charAt(0)))
            faltantes.append("• Debe comenzar con mayúscula\n");
        if (!valor.matches("[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+"))
            faltantes.append("• Solo se permiten letras, sin números ni símbolos\n");

        if (faltantes.length() > 0) {
            tilNombre.setError(faltantes.toString().trim());
            return false;
        }
        tilNombre.setError(null);
        tilNombre.setErrorEnabled(false);
        return true;
    }

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

    private boolean validarTelefono(String valor) {
        valor = valor.trim();

        if (valor.isEmpty()) {
            tilTelefono.setError("El teléfono es obligatorio");
            return false;
        }

        StringBuilder faltantes = new StringBuilder();
        if (!valor.matches("[0-9]+"))
            faltantes.append("• Solo se permiten números, sin espacios ni guiones\n");
        else if (valor.length() < 7)
            faltantes.append("• Mínimo 7 dígitos\n");
        else if (valor.length() > 15)
            faltantes.append("• Máximo 15 dígitos\n");

        if (faltantes.length() > 0) {
            tilTelefono.setError(faltantes.toString().trim());
            return false;
        }
        tilTelefono.setError(null);
        tilTelefono.setErrorEnabled(false);
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

    private boolean validarTodosLosCampos(String nombre, String email,
                                          String telefono, String password) {
        boolean ok = true;
        if (!validarNombre(nombre))     ok = false;
        if (!validarEmail(email))       ok = false;
        if (!validarTelefono(telefono)) ok = false;
        if (!validarPassword(password)) ok = false;
        return ok;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STEP 1 — Validar y pasar al escaneo
    // ══════════════════════════════════════════════════════════════════════════

    private void validarFormularioYEscanear() {
        String nombre   = edtNombre.getText().toString().trim();
        String email    = edtEmail.getText().toString().trim();
        String telefono = edtTelefono.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (!validarTodosLosCampos(nombre, email, telefono, password)) return;

        // ── Validación de Términos y Condiciones ──
        if (!cbTerminos.isChecked()) {
            mostrarAlerta(
                    "Términos y Condiciones requeridos",
                    "Para registrarte debes leer y aceptar los Términos y Condiciones.\n\nToca el enlace «Términos y Condiciones» para leerlos."
            );
            return;
        }

        datosNombre   = nombre;
        datosEmail    = email;
        datosTelefono = telefono;
        datosPassword = password;
        datosRol      = "PASAJERO";

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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara();
            } else {
                mostrarAlerta("Permiso requerido", "Se necesita acceso a la cámara");
                mostrarFormulario();
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
            try {
                bindPreview(future.get());
            } catch (Exception e) {
                Log.e(TAG, "Error iniciando cámara: " + e.getMessage());
                mostrarAlerta("Error", "Error al iniciar la cámara");
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
            runOnUiThread(() -> txtEstado.setText("📷 Coloca tu rostro dentro del óvalo..."));
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
                        txtEstado.setText("☁️ Subiendo imagen...");
                        subirACloudinary(ultimoFrameBitmap);
                    } else {
                        txtEstado.setText("❌ Error capturando imagen");
                        yaCapturado.set(false);
                        cuentaRegresivaIniciada.set(false);
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
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo imagen: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLOUDINARY
    // ══════════════════════════════════════════════════════════════════════════

    private void subirACloudinary(Bitmap bitmap) {
        runOnUiThread(() -> txtEstado.setText("☁️ Subiendo imagen..."));
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] imageBytes = baos.toByteArray();

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("file", "rostro_registro.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                    .build();

            client.newCall(new Request.Builder().url(CLOUDINARY_UPLOAD_URL).post(requestBody).build())
                    .enqueue(new Callback() {
                        @Override public void onFailure(Call call, IOException e) {
                            runOnUiThread(() -> {
                                txtEstado.setText("❌ Sin internet");
                                mostrarAlerta("Error de conexión", "No se pudo subir la imagen");
                                mostrarBotonReintentar();
                            });
                        }
                        @Override public void onResponse(Call call, Response response) throws IOException {
                            String body = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                try {
                                    registrarEnBackend(new JSONObject(body).getString("secure_url"));
                                } catch (JSONException e) {
                                    runOnUiThread(() -> {
                                        txtEstado.setText("❌ Error procesando");
                                        mostrarAlerta("Error", "Error procesando imagen");
                                        mostrarBotonReintentar();
                                    });
                                }
                            } else {
                                runOnUiThread(() -> {
                                    txtEstado.setText("❌ Error subiendo imagen");
                                    mostrarAlerta("Error", "Error al subir imagen");
                                    mostrarBotonReintentar();
                                });
                            }
                        }
                    });
        } catch (Exception e) {
            runOnUiThread(() -> {
                txtEstado.setText("❌ Error");
                mostrarAlerta("Error", "Error: " + e.getMessage());
                mostrarBotonReintentar();
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BACKEND
    // ══════════════════════════════════════════════════════════════════════════

    private void registrarEnBackend(String faceImageUrl) {
        runOnUiThread(() -> txtEstado.setText("💾 Creando cuenta..."));
        try {
            JSONObject json = new JSONObject();
            json.put("nombre",       datosNombre);
            json.put("email",        datosEmail);
            json.put("telefono",     datosTelefono);
            json.put("password",     datosPassword);
            json.put("rol",          datosRol);
            json.put("faceImageUrl", faceImageUrl);

            client.newCall(new Request.Builder()
                    .url(Constantes.REGISTER)
                    .post(RequestBody.create(json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        txtEstado.setText("❌ Sin conexión");
                        mostrarAlerta("Error de conexión", "No se puede conectar al servidor");
                        mostrarBotonReintentar();
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            txtEstado.setText("✅ ¡Cuenta creada!");
                            mostrarAlerta("✅ ¡Éxito!", "Registro exitoso. Ahora inicia sesión.", () -> {
                                startActivity(new Intent(Register.this, Login.class));
                                finish();
                            });
                        } else {
                            String errorMsg = "Error en el registro";
                            switch (response.code()) {
                                case 400: errorMsg = "Datos inválidos";                          break;
                                case 409: errorMsg = "El email ya está registrado";              break;
                                case 429: errorMsg = "Demasiados intentos. Espera 5 minutos.";  break;
                                case 500: errorMsg = "Error del servidor";                       break;
                            }
                            try {
                                JSONObject err = new JSONObject(responseBody);
                                if (err.has("message"))    errorMsg = err.getString("message");
                                else if (err.has("error")) errorMsg = err.getString("error");
                            } catch (Exception ignored) {}

                            txtEstado.setText("❌ " + errorMsg);
                            mostrarAlerta("Error", errorMsg);

                            if (response.code() != 429) {
                                mostrarBotonReintentar();
                            } else {
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(() -> mostrarFormulario(), 3000);
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                txtEstado.setText("❌ Error interno");
                mostrarAlerta("Error", "Error interno: " + e.getMessage());
                mostrarBotonReintentar();
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALERTAS
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarAlerta(String titulo, String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle(titulo).setMessage(mensaje)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .setCancelable(false).show();
    }

    private void mostrarAlerta(String titulo, String mensaje, Runnable onOk) {
        new AlertDialog.Builder(this)
                .setTitle(titulo).setMessage(mensaje)
                .setPositiveButton("OK", (d, w) -> { d.dismiss(); onOk.run(); })
                .setCancelable(false).show();
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