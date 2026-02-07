package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;

public class Home extends AppCompatActivity {

    private ImageView logoHome, moviflexx;
    private TextView txtTitulo, txtSubtitulo;
    private Button btnComenzar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        setupAnimations();
    }

    private void initViews() {
        logoHome = findViewById(R.id.logoHome);
        moviflexx = findViewById(R.id.Moviflex);
        txtTitulo = findViewById(R.id.txtTitulo);
        txtSubtitulo = findViewById(R.id.txtSubtitulo);
        btnComenzar = findViewById(R.id.btnComenzar);
    }

    private void setupAnimations() {
        // Animación de entrada para el logo
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(1000);
        logoHome.startAnimation(fadeIn);

        // Animación para el título
        Animation slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slideUp.setDuration(800);
        slideUp.setStartOffset(300);
        txtTitulo.startAnimation(slideUp);

        // Animación para el logo Moviflexx
        Animation fadeInMoviflexx = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeInMoviflexx.setDuration(800);
        fadeInMoviflexx.setStartOffset(500);
        moviflexx.startAnimation(fadeInMoviflexx);

        // Animación para el subtítulo
        Animation fadeInSubtitle = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeInSubtitle.setDuration(800);
        fadeInSubtitle.setStartOffset(700);
        txtSubtitulo.startAnimation(fadeInSubtitle);

        // Animación para el botón
        Animation fadeInButton = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        fadeInButton.setDuration(600);
        fadeInButton.setStartOffset(900);
        btnComenzar.startAnimation(fadeInButton);
    }

    public void irLogin(View view) {
        Intent intent = new Intent(this, Login.class);
        startActivity(intent);

        // Transición suave entre actividades
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}