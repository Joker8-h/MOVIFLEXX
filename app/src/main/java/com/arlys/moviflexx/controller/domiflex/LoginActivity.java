package com.arlys.moviflexx.controller.domiflex;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.network.RetrofitClient;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    EditText etCorreo, etPass;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        etCorreo = findViewById(R.id.etCorreo);
        etPass = findViewById(R.id.etPass);
        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> doLogin());
        findViewById(R.id.tvRegister).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }
    private void doLogin() {
        JsonObject body = new JsonObject();
        body.addProperty("email", etCorreo.getText().toString());
        body.addProperty("password", etPass.getText().toString());
        RetrofitClient.getApiService().login(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null && response.body().has("token")) {
                    String token = response.body().get("token").getAsString();
                    getSharedPreferences("domiflex", MODE_PRIVATE).edit().putString("token", token).apply();
                    String rol = "";
                    if (response.body().has("usuario") && response.body().get("usuario").isJsonObject()) {
                        JsonObject usuario = response.body().getAsJsonObject("usuario");
                        if (usuario.has("rol") && !usuario.get("rol").isJsonNull()) {
                            rol = usuario.get("rol").getAsString();
                        }
                    }
                    FcmRegistro.enviar(LoginActivity.this, token);
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
                    }
                    Toast.makeText(LoginActivity.this, "Login OK", Toast.LENGTH_SHORT).show();
                    Class<?> destino = "REPARTIDOR".equalsIgnoreCase(rol)
                            ? DriverHomeActivity.class
                            : RestaurantesActivity.class;
                    startActivity(new Intent(LoginActivity.this, destino));
                    finish();
                } else Toast.makeText(LoginActivity.this, "Credenciales inválidas", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
