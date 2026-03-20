package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

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

public class VerificarOtp extends BaseActivity {

    private TextInputLayout   tilOtp;
    private TextInputEditText edtOtp;
    private MaterialButton    btnVerificar;
    private MaterialButton    btnReenviar;
    private TextView          tvEmailMostrado;
    private TextView          tvTimer;

    private String emailVerificado;
    private CountDownTimer countDownTimer;
    private static final long TIMER_MS = 10 * 60 * 1000L;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verificar_otp);

        emailVerificado = getIntent().getStringExtra("email");
        if (emailVerificado == null || emailVerificado.isEmpty()) {
            finish();
            return;
        }

        tilOtp          = findViewById(R.id.til_otp);
        edtOtp          = findViewById(R.id.edt_otp);
        btnVerificar    = findViewById(R.id.btn_verificar_otp);
        btnReenviar     = findViewById(R.id.btn_reenviar_otp);
        tvEmailMostrado = findViewById(R.id.tv_email_mostrado);
        tvTimer         = findViewById(R.id.tv_timer_otp);

        tvEmailMostrado.setText(enmascararEmail(emailVerificado));

        edtOtp.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                tilOtp.setError(null);
                if (s.length() == 6) verificarOtp();
            }
        });

        btnVerificar.setOnClickListener(v -> verificarOtp());
        btnReenviar.setOnClickListener(v -> reenviarOtp());
        iniciarTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void iniciarTimer() {
        btnReenviar.setEnabled(false);
        btnReenviar.setAlpha(0.4f);
        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(TIMER_MS, 1000) {
            @Override public void onTick(long ms) {
                long min = ms / 60000, seg = (ms % 60000) / 1000;
                tvTimer.setVisibility(View.VISIBLE);
                tvTimer.setText(String.format("⏱ El código expira en %02d:%02d", min, seg));
            }
            @Override public void onFinish() {
                tvTimer.setText("⚠️ El código ha expirado. Solicita uno nuevo.");
                tvTimer.setTextColor(0xFFE74C3C);
                btnReenviar.setEnabled(true);
                btnReenviar.setAlpha(1.0f);
                btnVerificar.setEnabled(false);
                btnVerificar.setAlpha(0.4f);
            }
        }.start();
    }

    private void verificarOtp() {
        String codigo = edtOtp.getText() != null
                ? edtOtp.getText().toString().trim() : "";
        if (codigo.length() != 6) {
            tilOtp.setError("El código debe tener 6 dígitos");
            return;
        }

        btnVerificar.setEnabled(false);
        btnVerificar.setAlpha(0.6f);
        btnVerificar.setText("Verificando...");

        try {
            JSONObject json = new JSONObject();
            json.put("email", emailVerificado);
            json.put("otp",   codigo);

            Request request = new Request.Builder()
                    .url(Constantes.BASE_URL + "/api/auth/verify-pre-otp") // ✅ ya estaba correcto
                    .post(RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resetBotonVerificar();
                        MoviAlert.error(VerificarOtp.this,
                                "Sin conexión", "No se pudo verificar. Revisa tu internet.", null);
                    });
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            if (countDownTimer != null) countDownTimer.cancel();
                            MoviAlert.success(VerificarOtp.this,
                                    "¡Correo verificado!",
                                    "Tu correo fue verificado correctamente.\nCompleta tu registro.",
                                    () -> {
                                        Intent result = new Intent();
                                        result.putExtra("emailVerificado", emailVerificado);
                                        setResult(RESULT_OK, result);
                                        finish();
                                    });
                        } else {
                            resetBotonVerificar();
                            String errorMsg = "Código incorrecto o expirado.";
                            try {
                                JSONObject err = new JSONObject(body);
                                if (err.has("message"))    errorMsg = err.getString("message");
                                else if (err.has("error")) errorMsg = err.getString("error");
                            } catch (Exception ignored) {}

                            if (response.code() == 400 || response.code() == 401) {
                                tilOtp.setError(errorMsg);
                                edtOtp.setText("");
                            } else if (response.code() == 410) {
                                tilOtp.setError("El código expiró. Solicita uno nuevo.");
                                btnReenviar.setEnabled(true);
                                btnReenviar.setAlpha(1.0f);
                                if (countDownTimer != null) countDownTimer.cancel();
                                tvTimer.setText("⚠️ Código expirado.");
                                tvTimer.setTextColor(0xFFE74C3C);
                            } else {
                                MoviAlert.error(VerificarOtp.this,
                                        "Error al verificar", errorMsg, null);
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            resetBotonVerificar();
            MoviAlert.error(this, "Error interno",
                    "Ocurrió un error inesperado: " + e.getMessage(), null);
        }
    }

    private void reenviarOtp() {
        btnReenviar.setEnabled(false);
        btnReenviar.setAlpha(0.4f);
        btnReenviar.setText("Enviando...");
        btnVerificar.setEnabled(true);
        btnVerificar.setAlpha(1.0f);
        edtOtp.setText("");
        tvTimer.setTextColor(0xFF4ACFBD);

        try {
            JSONObject json = new JSONObject();
            json.put("email", emailVerificado);

            Request request = new Request.Builder()
                    .url(Constantes.BASE_URL + "/api/auth/request-pre-otp") // ✅ CORREGIDO
                    .post(RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        btnReenviar.setText("Reenviar código");
                        btnReenviar.setEnabled(true);
                        btnReenviar.setAlpha(1.0f);
                        MoviAlert.error(VerificarOtp.this,
                                "Sin conexión", "No se pudo reenviar. Verifica tu internet.", null);
                    });
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        btnReenviar.setText("Reenviar código");
                        if (response.isSuccessful()) {
                            MoviAlert.toast(VerificarOtp.this,
                                    "Nuevo código enviado a tu correo 📧", MoviAlert.SUCCESS);
                            iniciarTimer();
                        } else {
                            btnReenviar.setEnabled(true);
                            btnReenviar.setAlpha(1.0f);
                            MoviAlert.error(VerificarOtp.this, "Error",
                                    "No se pudo reenviar el código. Intenta más tarde.", null);
                        }
                    });
                }
            });
        } catch (Exception e) {
            btnReenviar.setText("Reenviar código");
            btnReenviar.setEnabled(true);
            btnReenviar.setAlpha(1.0f);
        }
    }

    private void resetBotonVerificar() {
        btnVerificar.setEnabled(true);
        btnVerificar.setAlpha(1.0f);
        btnVerificar.setText("Verificar código");
    }

    private String enmascararEmail(String email) {
        try {
            int at = email.indexOf('@');
            if (at <= 2) return email;
            return email.substring(0, 2) + "***" + email.substring(at);
        } catch (Exception e) { return email; }
    }
}