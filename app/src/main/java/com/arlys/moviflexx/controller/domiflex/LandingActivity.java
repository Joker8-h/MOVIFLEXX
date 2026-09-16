package com.arlys.moviflexx.controller.domiflex;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class LandingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);
        Button btnExplorar = findViewById(R.id.btnExplorar);
        Button btnLogin = findViewById(R.id.btnLogin);
        btnExplorar.setOnClickListener(v -> startActivity(new Intent(this, RestaurantesActivity.class)));
        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
    }
}
