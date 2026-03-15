package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.MoviAlert;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EmailVerificacion extends AppCompatActivity {

    private TextInputLayout   tilEmail;
    private TextInputEditText edtEmail;
    private MaterialButton    btnEnviarOtp;
    private boolean           modoFormulario = false;

    private ActivityResultLauncher<Intent> verificarOtpLauncher;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        verificarOtpLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String emailOk = result.getData().getStringExtra("emailVerificado");
                        if (emailOk != null && !emailOk.isEmpty()) {
                            if (modoFormulario) {
                                Intent devolver = new Intent();
                                devolver.putExtra("emailVerificado", emailOk);
                                setResult(RESULT_OK, devolver);
                                finish();
                            } else {
                                Intent intent = new Intent(EmailVerificacion.this, Register.class);
                                intent.putExtra("emailVerificado", emailOk);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                                finish();
                            }
                        }
                    }
                }
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verificacion);

        tilEmail     = findViewById(R.id.til_email_verificacion);
        edtEmail     = findViewById(R.id.edt_email_verificacion);
        btnEnviarOtp = findViewById(R.id.btn_enviar_otp);

        String emailPrecargado = getIntent().getStringExtra("emailPrecargado");
        if (emailPrecargado != null && !emailPrecargado.isEmpty()) {
            edtEmail.setText(emailPrecargado);
            modoFormulario = true;
        }

        btnEnviarOtp.setOnClickListener(v -> validarYEnviarOtp());

        findViewById(R.id.btn_ir_login).setOnClickListener(v -> {
            startActivity(new Intent(EmailVerificacion.this, Login.class));
            finish();
        });
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

    private void validarYEnviarOtp() {
        String email = edtEmail.getText() != null
                ? edtEmail.getText().toString().trim() : "";
        if (!validarEmail(email)) return;

        btnEnviarOtp.setEnabled(false);
        btnEnviarOtp.setAlpha(0.6f);
        btnEnviarOtp.setText("Enviando...");
        enviarOtpAlBackend(email);
    }

    private void enviarOtpAlBackend(String email) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);

            Request request = new Request.Builder()
                    .url(Constantes.BASE_URL + "/api/auth/request-pre-otp") // ✅ CORREGIDO
                    .post(RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resetBoton();
                        MoviAlert.error(EmailVerificacion.this,
                                "Sin conexión",
                                "No se pudo conectar al servidor. Verifica tu internet.", null);
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        resetBoton();
                        if (response.isSuccessful()) {
                            Intent intent = new Intent(EmailVerificacion.this, VerificarOtp.class);
                            intent.putExtra("email", email);
                            verificarOtpLauncher.launch(intent);
                        } else {
                            String errorMsg = "No se pudo enviar el código.";
                            try {
                                JSONObject err = new JSONObject(body);
                                if (err.has("message"))    errorMsg = err.getString("message");
                                else if (err.has("error")) errorMsg = err.getString("error");
                            } catch (Exception ignored) {}

                            if (response.code() == 409) {
                                MoviAlert.warning(EmailVerificacion.this,
                                        "Correo ya registrado",
                                        errorMsg + "\n\n¿Ya tienes cuenta? Inicia sesión.");
                            } else {
                                MoviAlert.error(EmailVerificacion.this,
                                        "Error al enviar código", errorMsg, null);
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                resetBoton();
                MoviAlert.error(EmailVerificacion.this, "Error interno",
                        "Ocurrió un error inesperado: " + e.getMessage(), null);
            });
        }
    }

    private void resetBoton() {
        btnEnviarOtp.setEnabled(true);
        btnEnviarOtp.setAlpha(1.0f);
        btnEnviarOtp.setText("Enviar código de verificación");
    }
}