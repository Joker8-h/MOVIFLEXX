package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
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
                if (response.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, "Login OK", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, RestaurantesActivity.class));
                    finish();
                } else Toast.makeText(LoginActivity.this, "Credenciales inválidas", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error: "+t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
