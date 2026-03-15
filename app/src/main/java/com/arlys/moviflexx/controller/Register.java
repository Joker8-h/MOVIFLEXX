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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.FieldTooltip;
import com.arlys.moviflexx.model.MoviAlert;
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

    private static final String TAG               = "REGISTER_DEBUG";
    private static final int    REQUEST_CAMERA_PERM = 200;

    private static final String CLOUDINARY_CLOUD_NAME    = "davda0bon";
    private static final String CLOUDINARY_UPLOAD_PRESET = "ml_default";
    private static final String CLOUDINARY_UPLOAD_URL    =
            "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

    // ══ Pasos ══
    private View stepEmail;
    private View stepOtp;
    private View layoutFormulario;
    private View scrollFormulario;

    // ══ Paso 1 ══
    private TextInputEditText edtEmailPaso1;
    private MaterialButton    btnEnviarCodigo;
    private View              progressPaso1;

    // ══ Paso 2 ══
    private TextView          tvEmailDestino;
    private TextInputEditText otp1, otp2, otp3, otp4, otp5, otp6;
    private MaterialButton    btnVerificarOtp;
    private TextView          tvCambiarCorreo;
    private TextView          tvReenviarCodigo;
    private View              progressPaso2;

    // ══ Paso 3 — Formulario ══
    private TextInputEditText edtNombre, edtEmail, edtTelefono, edtPassword;
    private TextInputLayout   tilNombre, tilEmail, tilTelefono, tilPassword;
    private MaterialButton    btnRegistrar;
    private View              layoutEmailVerificado;
    private View              layoutEmailNoVerificado;
    private View              progressPaso3;
    private CheckBox          cbTerminos;
    private boolean           terminosLeidos = false;

    // ══ Paso 4 — Cámara ══
    private ConstraintLayout layoutFace;
    private PreviewView      previewView;
    private TextView         txtEstado;
    private ExecutorService  cameraExecutor;

    private volatile Bitmap ultimoFrameBitmap = null;
    private String  datosNombre, datosEmail, datosTelefono, datosPassword;
    private Dialog  loadingDialog = null;

    private final AtomicBoolean yaCapturado             = new AtomicBoolean(false);
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

        stepEmail        = findViewById(R.id.stepEmail);
        stepOtp          = findViewById(R.id.stepOtp);
        layoutFormulario = findViewById(R.id.layoutFormulario);
        scrollFormulario = findViewById(R.id.scrollFormulario);

        edtEmailPaso1   = findViewById(R.id.edt_email_paso1);
        btnEnviarCodigo = findViewById(R.id.btn_enviar_codigo);
        progressPaso1   = findViewById(R.id.progress_paso1);

        tvEmailDestino   = findViewById(R.id.tv_email_destino);
        otp1 = findViewById(R.id.otp1); otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3); otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5); otp6 = findViewById(R.id.otp6);
        btnVerificarOtp  = findViewById(R.id.btn_verificar_otp);
        tvCambiarCorreo  = findViewById(R.id.tv_cambiar_correo);
        tvReenviarCodigo = findViewById(R.id.tv_reenviar_codigo);
        progressPaso2    = findViewById(R.id.progress_paso2);

        tilNombre    = findViewById(R.id.til_nombre);
        tilEmail     = findViewById(R.id.til_email);
        tilTelefono  = findViewById(R.id.til_telefono);
        tilPassword  = findViewById(R.id.til_password);
        edtNombre    = findViewById(R.id.edt_nombre);
        edtEmail     = findViewById(R.id.edt_email);
        edtTelefono  = findViewById(R.id.edt_telefono);
        edtPassword  = findViewById(R.id.edt_password);
        btnRegistrar            = findViewById(R.id.btn_register);
        layoutEmailVerificado   = findViewById(R.id.layoutEmailVerificado);
        layoutEmailNoVerificado = findViewById(R.id.layoutEmailNoVerificado);
        cbTerminos              = findViewById(R.id.cbTerminos);
        TextView tvLinkTerminos = findViewById(R.id.tvLinkTerminos);
        progressPaso3 = findViewById(R.id.progress_paso3);

        layoutFace  = findViewById(R.id.layoutFace);
        previewView = findViewById(R.id.previewView);
        txtEstado   = findViewById(R.id.txtEstado);

        mostrarPaso1();

        // ── Listeners paso 1 ──
        btnEnviarCodigo.setOnClickListener(v -> enviarCodigo());

        // ── Listeners paso 2 ──
        configurarOtp();
        btnVerificarOtp.setOnClickListener(v -> verificarCodigo());
        tvCambiarCorreo.setOnClickListener(v -> mostrarPaso1());
        tvReenviarCodigo.setOnClickListener(v -> enviarCodigo());

        // ── Listeners paso 3 ──
        cbTerminos.setEnabled(false);
        cbTerminos.setAlpha(0.4f);
        cbTerminos.setOnClickListener(v -> {
            if (!terminosLeidos) {
                cbTerminos.setChecked(false);
                MoviAlert.toast(this, "Primero debes leer los Términos y Condiciones", MoviAlert.WARNING);
            }
        });
        tvLinkTerminos.setOnClickListener(v -> mostrarTerminosYCondiciones());
        btnRegistrar.setOnClickListener(v -> validarFormularioYEscanear());

        configurarValidacionesEnTiempoReal();

        FieldTooltip.attach(tilNombre,   "Nombre completo");
        FieldTooltip.attach(tilEmail,    "Correo electrónico");
        FieldTooltip.attach(tilTelefono, "Teléfono");
        FieldTooltip.attach(tilPassword, "Contraseña");

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        dismissLoading();
    }

    @Override
    public void onBackPressed() {
        if (layoutFace.getVisibility() == View.VISIBLE) {
            mostrarPaso3();
        } else if (scrollFormulario.getVisibility() == View.VISIBLE) {
            mostrarPaso2();
        } else if (stepOtp.getVisibility() == View.VISIBLE) {
            mostrarPaso1();
        } else {
            super.onBackPressed();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarPaso1() {
        stepEmail.setVisibility(View.VISIBLE);
        stepOtp.setVisibility(View.GONE);
        layoutFormulario.setVisibility(View.GONE);
        scrollFormulario.setVisibility(View.GONE);
        layoutFace.setVisibility(View.GONE);
        stepEmail.post(() -> animarBarra(progressPaso1, 0.33f));
    }

    private void mostrarPaso2(String email) {
        tvEmailDestino.setText(email);
        stepEmail.setVisibility(View.GONE);
        stepOtp.setVisibility(View.VISIBLE);
        layoutFormulario.setVisibility(View.GONE);
        scrollFormulario.setVisibility(View.GONE);
        layoutFace.setVisibility(View.GONE);
        limpiarOtp();
        stepOtp.post(() -> animarBarra(progressPaso2, 0.66f));
        otp1.requestFocus();
    }

    private void mostrarPaso2() {
        mostrarPaso2(edtEmailPaso1.getText() != null
                ? edtEmailPaso1.getText().toString().trim() : "");
    }

    private void mostrarPaso3() {
        stepEmail.setVisibility(View.GONE);
        stepOtp.setVisibility(View.GONE);
        layoutFormulario.setVisibility(View.VISIBLE);
        scrollFormulario.setVisibility(View.VISIBLE);
        layoutFace.setVisibility(View.GONE);
        scrollFormulario.post(() -> animarBarra(progressPaso3, 1.0f));
        String emailVerificado = edtEmailPaso1.getText() != null
                ? edtEmailPaso1.getText().toString().trim() : "";
        edtEmail.setText(emailVerificado);
        edtEmail.setEnabled(false);
        edtEmail.setAlpha(0.85f);
        if (layoutEmailVerificado != null)   layoutEmailVerificado.setVisibility(View.VISIBLE);
        if (layoutEmailNoVerificado != null) layoutEmailNoVerificado.setVisibility(View.GONE);
        if (tilEmail != null) tilEmail.setError(null);
    }

    private void mostrarEscaneoFacial() {
        layoutFormulario.setVisibility(View.GONE);
        scrollFormulario.setVisibility(View.GONE);
        layoutFace.setVisibility(View.VISIBLE);
        yaCapturado.set(false);
        cuentaRegresivaIniciada.set(false);
        txtEstado.setText("📷 Coloca tu rostro dentro del óvalo...");
        if (verificarPermisoCamara()) iniciarCamara();
        else solicitarPermisoCamara();
    }

    private void animarBarra(View barra, float fraccion) {
        if (barra == null || barra.getParent() == null) return;
        View contenedor = (View) barra.getParent();
        int ancho = (int) (contenedor.getWidth() * fraccion);
        android.view.ViewGroup.LayoutParams p = barra.getLayoutParams();
        p.width = ancho;
        barra.setLayoutParams(p);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASO 1 — ENVIAR CÓDIGO (usa el backend igual que EmailVerificacion.java)
    // ══════════════════════════════════════════════════════════════════════════

    private void enviarCodigo() {
        String email = edtEmailPaso1.getText() != null
                ? edtEmailPaso1.getText().toString().trim() : "";
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            MoviAlert.toast(this, "Ingresa un correo válido", MoviAlert.WARNING);
            return;
        }

        loadingDialog = MoviAlert.loading(this, "Enviando código...");

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);

            client.newCall(new Request.Builder()
                    .url(Constantes.BASE_URL + "/api/auth/request-pre-otp")
                    .post(RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        MoviAlert.error(Register.this, "Sin conexión",
                                "No se pudo enviar el código.\nVerifica tu internet.");
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "request-pre-otp: " + response.code() + " — " + body);
                    runOnUiThread(() -> {
                        dismissLoading();
                        if (response.isSuccessful()) {
                            mostrarPaso2(email);
                        } else if (response.code() == 409) {
                            MoviAlert.warning(Register.this, "Correo ya registrado",
                                    "Este correo ya tiene una cuenta en MOVIFLEX.\n¿Ya tienes cuenta? Inicia sesión.");
                        } else {
                            String msg = "No se pudo enviar el código.";
                            try {
                                JSONObject err = new JSONObject(body);
                                if (err.has("message"))    msg = err.getString("message");
                                else if (err.has("error")) msg = err.getString("error");
                            } catch (Exception ignored) {}
                            MoviAlert.error(Register.this, "Error al enviar código", msg);
                        }
                    });
                }
            });
        } catch (Exception e) {
            dismissLoading();
            Log.e(TAG, "Error enviarCodigo: " + e.getMessage());
            MoviAlert.error(this, "Error inesperado", "No se pudo enviar el código.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASO 2 — OTP
    // ══════════════════════════════════════════════════════════════════════════

    private void configurarOtp() {
        TextInputEditText[] campos = {otp1, otp2, otp3, otp4, otp5, otp6};
        for (int i = 0; i < campos.length; i++) {
            final int idx = i;
            campos[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && idx < campos.length - 1)
                        campos[idx + 1].requestFocus();
                    actualizarBotonOtp(campos);
                }
            });
            campos[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && campos[idx].getText() != null
                        && campos[idx].getText().toString().isEmpty()
                        && idx > 0) {
                    campos[idx - 1].requestFocus();
                    campos[idx - 1].setText("");
                    return true;
                }
                return false;
            });
        }
    }

    private void actualizarBotonOtp(TextInputEditText[] campos) {
        boolean llenos = true;
        for (TextInputEditText c : campos) {
            if (c.getText() == null || c.getText().toString().isEmpty()) {
                llenos = false; break;
            }
        }
        btnVerificarOtp.setEnabled(llenos);
        btnVerificarOtp.setAlpha(llenos ? 1.0f : 0.5f);
    }

    private void limpiarOtp() {
        for (TextInputEditText c : new TextInputEditText[]{otp1,otp2,otp3,otp4,otp5,otp6})
            c.setText("");
        btnVerificarOtp.setEnabled(false);
        btnVerificarOtp.setAlpha(0.5f);
    }

    private String obtenerCodigoIngresado() {
        return safeText(otp1) + safeText(otp2) + safeText(otp3)
                + safeText(otp4) + safeText(otp5) + safeText(otp6);
    }

    private String safeText(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString() : "";
    }

    private void verificarCodigo() {
        String codigo = obtenerCodigoIngresado();
        if (codigo.length() != 6) return;

        btnVerificarOtp.setEnabled(false);
        btnVerificarOtp.setAlpha(0.6f);
        loadingDialog = MoviAlert.loading(this, "Verificando...");

        try {
            JSONObject json = new JSONObject();
            json.put("email", edtEmailPaso1.getText() != null
                    ? edtEmailPaso1.getText().toString().trim() : "");
            json.put("otp", codigo);

            client.newCall(new Request.Builder()
                    .url(Constantes.BASE_URL + "/api/auth/verify-pre-otp")
                    .post(RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        btnVerificarOtp.setEnabled(true);
                        btnVerificarOtp.setAlpha(1.0f);
                        MoviAlert.error(Register.this, "Sin conexión",
                                "No se pudo verificar. Revisa tu internet.");
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "verify-pre-otp: " + response.code() + " — " + body);
                    runOnUiThread(() -> {
                        dismissLoading();
                        btnVerificarOtp.setEnabled(true);
                        btnVerificarOtp.setAlpha(1.0f);
                        if (response.isSuccessful()) {
                            MoviAlert.toast(Register.this,
                                    "✅ Correo verificado correctamente", MoviAlert.SUCCESS);
                            mostrarPaso3();
                        } else {
                            limpiarOtp();
                            String msg = "Código incorrecto o expirado.";
                            try {
                                JSONObject err = new JSONObject(body);
                                if (err.has("message"))    msg = err.getString("message");
                                else if (err.has("error")) msg = err.getString("error");
                            } catch (Exception ignored) {}
                            MoviAlert.error(Register.this, "Código incorrecto", msg);
                            otp1.requestFocus();
                        }
                    });
                }
            });
        } catch (Exception e) {
            dismissLoading();
            btnVerificarOtp.setEnabled(true);
            btnVerificarOtp.setAlpha(1.0f);
            Log.e(TAG, "Error verificarCodigo: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASO 3 — FORMULARIO
    // ══════════════════════════════════════════════════════════════════════════

    private void validarFormularioYEscanear() {
        String nombre   = edtNombre.getText()   != null ? edtNombre.getText().toString().trim()   : "";
        String email    = edtEmail.getText()    != null ? edtEmail.getText().toString().trim()    : "";
        String telefono = edtTelefono.getText() != null ? edtTelefono.getText().toString().trim() : "";
        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";

        if (!validarTodosLosCampos(nombre, email, telefono, password)) {
            MoviAlert.toast(this, "Revisa los campos marcados en rojo", MoviAlert.WARNING);
            return;
        }
        if (!terminosLeidos || !cbTerminos.isChecked()) {
            MoviAlert.warning(this, "Términos requeridos",
                    "Debes leer y aceptar los Términos y Condiciones para continuar.");
            return;
        }
        datosNombre   = nombre;
        datosEmail    = email;
        datosTelefono = telefono;
        datosPassword = password;
        mostrarEscaneoFacial();
    }

    private void configurarValidacionesEnTiempoReal() {
        edtNombre.setOnFocusChangeListener((v, f) -> {
            if (!f) validarNombre(edtNombre.getText().toString());
        });
        edtTelefono.setOnFocusChangeListener((v, f) -> {
            if (!f) validarTelefono(edtTelefono.getText().toString());
        });
        edtPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) validarPassword(s.toString());
                else { tilPassword.setError(null); tilPassword.setErrorEnabled(false); }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VALIDADORES
    // ══════════════════════════════════════════════════════════════════════════

    private boolean validarNombre(String v) {
        v = v.trim();
        if (v.isEmpty()) { tilNombre.setError("El nombre es obligatorio"); return false; }
        if (v.length() < 3) { tilNombre.setError("Mínimo 3 caracteres"); return false; }
        if (!Character.isUpperCase(v.charAt(0))) { tilNombre.setError("Debe comenzar con mayúscula"); return false; }
        if (!v.matches("[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+")) { tilNombre.setError("Solo letras, sin números ni símbolos"); return false; }
        tilNombre.setError(null); tilNombre.setErrorEnabled(false); return true;
    }

    private boolean validarEmail(String v) {
        v = v.trim();
        if (v.isEmpty()) { tilEmail.setError("El correo es obligatorio"); return false; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(v).matches()) {
            tilEmail.setError("Formato inválido — debe ser: usuario@correo.com"); return false;
        }
        tilEmail.setError(null); tilEmail.setErrorEnabled(false); return true;
    }

    private boolean validarTelefono(String v) {
        v = v.trim();
        if (v.isEmpty()) { tilTelefono.setError("El teléfono es obligatorio"); return false; }
        if (!v.matches("[0-9]+")) { tilTelefono.setError("Solo números, sin espacios ni guiones"); return false; }
        if (v.length() < 7)  { tilTelefono.setError("Mínimo 7 dígitos"); return false; }
        if (v.length() > 15) { tilTelefono.setError("Máximo 15 dígitos"); return false; }
        tilTelefono.setError(null); tilTelefono.setErrorEnabled(false); return true;
    }

    private boolean validarPassword(String v) {
        if (v.isEmpty()) { tilPassword.setError("La contraseña es obligatoria"); return false; }
        StringBuilder f = new StringBuilder();
        if (v.length() < 8)                    f.append("• Mínimo 8 caracteres\n");
        if (!v.matches(".*[A-Z].*"))            f.append("• Al menos una mayúscula (A-Z)\n");
        if (!v.matches(".*[a-z].*"))            f.append("• Al menos una minúscula (a-z)\n");
        if (!v.matches(".*[0-9].*"))            f.append("• Al menos un número (0-9)\n");
        if (!v.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"))
            f.append("• Al menos un carácter especial (!@#$...)\n");
        if (f.length() > 0) { tilPassword.setError("Faltan requisitos:\n" + f.toString().trim()); return false; }
        tilPassword.setError(null); tilPassword.setErrorEnabled(false); return true;
    }

    private boolean validarTodosLosCampos(String nombre, String email, String telefono, String password) {
        boolean ok = true;
        if (!validarNombre(nombre))     ok = false;
        if (!validarEmail(email))       ok = false;
        if (!validarTelefono(telefono)) ok = false;
        if (!validarPassword(password)) ok = false;
        return ok;
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

        btnAceptar.setEnabled(false);
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int total   = scrollView.getChildAt(0).getHeight() - scrollView.getHeight();
            int current = scrollView.getScrollY();
            if (total - current <= 50 && !btnAceptar.isEnabled()) {
                btnAceptar.setEnabled(true);
                btnAceptar.getBackground().setTint(0xFF2EC4B6);
                tvProgreso.setText("✅  Ya puedes aceptar los Términos y Condiciones");
                tvProgreso.setTextColor(0xFF2E7D32);
            }
        });
        btnCerrarTop.setOnClickListener(v -> dialog.dismiss());
        btnCerrar.setOnClickListener(v -> dialog.dismiss());
        btnAceptar.setOnClickListener(v -> {
            terminosLeidos = true;
            cbTerminos.setEnabled(true);
            cbTerminos.setAlpha(1.0f);
            cbTerminos.setChecked(true);
            dialog.dismiss();
            MoviAlert.toast(this, "Términos aceptados. ¡Ya puedes registrarte!", MoviAlert.SUCCESS);
        });
        dialog.show();
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                iniciarCamara();
            else
                MoviAlert.error(this, "Permiso requerido",
                        "Necesitamos acceso a la cámara para el reconocimiento facial.",
                        this::mostrarPaso3);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CÁMARA
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try { bindPreview(future.get()); }
            catch (Exception e) {
                Log.e(TAG, "Error cámara: " + e.getMessage());
                runOnUiThread(() -> MoviAlert.error(this, "Error de cámara",
                        "No se pudo iniciar la cámara. Intenta de nuevo."));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(cameraExecutor, this::capturarUnicaFoto);
        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            runOnUiThread(() -> txtEstado.setText("📷 Coloca tu rostro dentro del óvalo..."));
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
                            loadingDialog = MoviAlert.loading(this, "Subiendo imagen...");
                        });
                        subirACloudinary(ultimoFrameBitmap);
                    } else {
                        runOnUiThread(() -> {
                            txtEstado.setText("❌ Error capturando");
                            MoviAlert.error(this, "Error", "No se pudo capturar. Intenta de nuevo.");
                            yaCapturado.set(false);
                            cuentaRegresivaIniciada.set(false);
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
            ByteBuffer yBuf = planes[0].getBuffer();
            ByteBuffer uBuf = planes[1].getBuffer();
            ByteBuffer vBuf = planes[2].getBuffer();
            int ySize = yBuf.remaining(), uSize = uBuf.remaining(), vSize = vBuf.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuf.get(nv21, 0, ySize);
            vBuf.get(nv21, ySize, vSize);
            uBuf.get(nv21, ySize + vSize, uSize);
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
            byte[] bytes = out.toByteArray();
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        } catch (Exception e) { Log.e(TAG, "Error bitmap: " + e.getMessage()); return null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLOUDINARY
    // ══════════════════════════════════════════════════════════════════════════

    private void subirACloudinary(Bitmap bitmap) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] bytes = baos.toByteArray();
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("file", "rostro_registro.jpg",
                            RequestBody.create(bytes, MediaType.parse("image/jpeg")))
                    .build();
            client.newCall(new Request.Builder().url(CLOUDINARY_UPLOAD_URL).post(body).build())
                    .enqueue(new Callback() {
                        @Override public void onFailure(Call call, IOException e) {
                            runOnUiThread(() -> {
                                dismissLoading();
                                txtEstado.setText("❌ Sin internet");
                                MoviAlert.error(Register.this, "Sin conexión",
                                        "No se pudo subir la imagen.", () -> mostrarBotonReintentar());
                            });
                        }
                        @Override public void onResponse(Call call, Response response) throws IOException {
                            String resp = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                try {
                                    String url = new JSONObject(resp).getString("secure_url");
                                    runOnUiThread(() -> {
                                        dismissLoading();
                                        loadingDialog = MoviAlert.loading(Register.this, "Creando tu cuenta...");
                                    });
                                    registrarEnBackend(url);
                                } catch (JSONException e) {
                                    runOnUiThread(() -> { dismissLoading(); mostrarBotonReintentar(); });
                                }
                            } else {
                                runOnUiThread(() -> {
                                    dismissLoading();
                                    MoviAlert.error(Register.this, "Error al subir imagen",
                                            "El servidor rechazó la imagen (código " + response.code() + ").",
                                            () -> mostrarBotonReintentar());
                                });
                            }
                        }
                    });
        } catch (Exception e) {
            runOnUiThread(() -> { dismissLoading(); mostrarBotonReintentar(); });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BACKEND — REGISTRO
    // ══════════════════════════════════════════════════════════════════════════

    private void registrarEnBackend(String faceImageUrl) {
        try {
            JSONObject json = new JSONObject();
            json.put("nombre",       datosNombre);
            json.put("email",        datosEmail);
            json.put("telefono",     datosTelefono);
            json.put("password",     datosPassword);
            json.put("rol",          "PASAJERO");
            json.put("faceImageUrl", faceImageUrl);

            client.newCall(new Request.Builder()
                    .url(Constantes.REGISTER)
                    .post(RequestBody.create(json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build()
            ).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        dismissLoading();
                        MoviAlert.error(Register.this, "Sin conexión",
                                "No se pudo conectar.", () -> mostrarBotonReintentar());
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        dismissLoading();
                        if (response.isSuccessful()) {
                            txtEstado.setText("✅ ¡Cuenta creada!");
                            MoviAlert.success(Register.this, "¡Registro exitoso!",
                                    "Tu cuenta fue creada correctamente.\nYa puedes iniciar sesión.",
                                    () -> { startActivity(new Intent(Register.this, Login.class)); finish(); });
                        } else {
                            String msg;
                            switch (response.code()) {
                                case 400: msg = "Los datos enviados son inválidos."; break;
                                case 409: msg = "Este correo ya está registrado en la plataforma."; break;
                                case 429: msg = "Demasiados intentos. Espera 5 minutos."; break;
                                case 500: msg = "Error interno del servidor. Inténtalo más tarde."; break;
                                default:  msg = "Error desconocido (código " + response.code() + ").";
                            }
                            try {
                                JSONObject err = new JSONObject(respBody);
                                if (err.has("message"))    msg = err.getString("message");
                                else if (err.has("error")) msg = err.getString("error");
                            } catch (Exception ignored) {}
                            final String mensajeFinal = msg;
                            txtEstado.setText("❌ " + mensajeFinal);
                            if (response.code() == 409) {
                                MoviAlert.warning(Register.this, "Correo ya registrado",
                                        "Este correo ya tiene una cuenta.\n¿Ya tienes cuenta? Inicia sesión.");
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(Register.this::mostrarPaso1, 2500);
                            } else if (response.code() == 429) {
                                MoviAlert.warning(Register.this, "Límite de intentos", mensajeFinal);
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(Register.this::mostrarPaso3, 3000);
                            } else {
                                MoviAlert.error(Register.this, "Error en el registro", mensajeFinal,
                                        () -> mostrarBotonReintentar());
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> { dismissLoading(); mostrarBotonReintentar(); });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REINTENTAR
    // ══════════════════════════════════════════════════════════════════════════

    private void mostrarBotonReintentar() {
        android.widget.Button btn = layoutFace.findViewWithTag("btn_reintentar");
        if (btn == null) {
            btn = new android.widget.Button(this);
            btn.setTag("btn_reintentar");
            btn.setText("🔄  Intentar de nuevo");
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF2EC4B6);
            btn.setPadding(40, 24, 40, 24);
            ConstraintLayout.LayoutParams p = new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT);
            p.bottomToTop  = txtEstado.getId();
            p.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            p.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID;
            p.bottomMargin = 24;
            btn.setLayoutParams(p);
            layoutFace.addView(btn);
        }
        final android.widget.Button boton = btn;
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

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    public void irLoginView(View view) {
        startActivity(new Intent(this, Login.class));
        finish();
    }
}