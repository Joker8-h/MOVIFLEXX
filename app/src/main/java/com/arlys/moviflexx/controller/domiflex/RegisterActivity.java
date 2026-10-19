package com.arlys.moviflexx.controller.domiflex;

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

public class RegisterActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        EditText etNombre = findViewById(R.id.etNombre);
        EditText etCorreo = findViewById(R.id.etCorreo);
        EditText etPass = findViewById(R.id.etPass);
        Button btn = findViewById(R.id.btnRegister);
        android.view.View tvLogin = findViewById(R.id.tvLogin);
        if (tvLogin != null) {
            tvLogin.setOnClickListener(v -> finish());
        }
        btn.setOnClickListener(v -> {
            JsonObject body = new JsonObject();
            body.addProperty("nombre", etNombre.getText().toString().trim());
            body.addProperty("email", etCorreo.getText().toString().trim());
            body.addProperty("password", etPass.getText().toString());
            body.addProperty("rol", "CLIENTE");
            body.addProperty("skipOtp", true);
            RetrofitClient.getApiService().register(body).enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> r) {
                    Toast.makeText(RegisterActivity.this, r.isSuccessful()?"Registro OK":"Error en registro", Toast.LENGTH_SHORT).show();
                    if (r.isSuccessful()) finish();
                }
                @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(RegisterActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
