package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.utils.SuccessAnimationHelper;

public class Success extends AppCompatActivity {

    private ImageButton btnClose;
    private ImageView ivSuccessIcon;
    private TextView tvSuccessTitle, tvSuccessMessage, tvDidntReceive;

    private static final int REDIRECT_DELAY = 2600;
    private String type = "register";   // Valor por defecto
    private String role = "cliente";    // Valor por defecto

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Obtener los valores enviados
        type = getIntent().getStringExtra("type");
        role = getIntent().getStringExtra("userRole");

        // Evitar nulos
        if (type == null) type = "register";
        if (role == null) role = "cliente";

        // Normalizar texto
        type = type.trim().toLowerCase();
        role = role.trim().toLowerCase();

        // Log para depurar
        Log.e("SUCCESS_DEBUG", "TYPE RECIBIDO = " + type);
        Log.e("SUCCESS_DEBUG", "ROLE RECIBIDO = " + role);

        initViews();
        setupSuccessType();
        applyAnimations();
        autoRedirect();

        btnClose.setOnClickListener(v -> manualRedirect());
    }

    private void initViews() {
        btnClose = findViewById(R.id.btnClose);
        ivSuccessIcon = findViewById(R.id.ivSuccessIcon);
        tvSuccessTitle = findViewById(R.id.tvSuccessTitle);
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage);
        tvDidntReceive = findViewById(R.id.tvDidntReceive);
    }

    private void setupSuccessType() {
        switch (type) {

            case "login":
                ivSuccessIcon.setImageResource(R.drawable.ic_success_blue);
                tvSuccessTitle.setText("¡Bienvenido!");
                tvSuccessMessage.setText("Usuario logueado correctamente.");
                tvDidntReceive.setText("");
                break;

            case "register":
            default:
                ivSuccessIcon.setImageResource(R.drawable.ic_success_green);
                tvSuccessTitle.setText(getString(R.string.success_title));
                tvSuccessMessage.setText(getString(R.string.success_message));
                tvDidntReceive.setText(getString(R.string.didnt_receive));
                break;
        }
    }

    private void applyAnimations() {
        SuccessAnimationHelper.animateBounceIcon(ivSuccessIcon);
        SuccessAnimationHelper.animateSlideFade(tvSuccessTitle, 150);
        SuccessAnimationHelper.animateSlideFade(tvSuccessMessage, 300);

        if (!tvDidntReceive.getText().toString().isEmpty())
            SuccessAnimationHelper.animateSlideFade(tvDidntReceive, 450);

        SuccessAnimationHelper.animateRotateButton(btnClose);
    }

    private void autoRedirect() {
        new Handler(Looper.getMainLooper()).postDelayed(this::manualRedirect, REDIRECT_DELAY);
    }

    private void manualRedirect() {
        Intent intent;

        // SI EL TIPO ES LOGIN → IR A PROFILE
        if (type.equals("login")) {

            Log.e("SUCCESS_DEBUG", "REDIRIGIENDO A PROFILE...");

            intent = new Intent(Success.this, Profile.class);
            intent.putExtra("userRole", role);

        } else {
            // SI ES REGISTRO → IR A LOGIN
            Log.e("SUCCESS_DEBUG", "REDIRIGIENDO A LOGIN...");
            intent = new Intent(Success.this, Login.class);
        }

        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
