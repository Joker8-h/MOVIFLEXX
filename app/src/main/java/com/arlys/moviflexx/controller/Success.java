package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.utils.SuccessAnimationHelper;

public class Success extends AppCompatActivity {


    private ImageView ivSuccessIcon;
    private TextView tvSuccessTitle, tvSuccessMessage, tvDidntReceive;

    private static final int REDIRECT_DELAY = 2600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setupSuccessUI();
        applyAnimations();
        autoRedirect();


    }

    private void initViews() {
        ivSuccessIcon = findViewById(R.id.ivSuccessIcon);
        tvSuccessTitle = findViewById(R.id.tvSuccessTitle);
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage);
        tvDidntReceive = findViewById(R.id.tvDidntReceive);
    }

    private void setupSuccessUI() {
        ivSuccessIcon.setImageResource(R.drawable.ic_success_check);
        tvSuccessTitle.setText(getString(R.string.success_title));
        tvSuccessMessage.setText(getString(R.string.success_message));
        tvDidntReceive.setText("");
    }

    private void applyAnimations() {
        SuccessAnimationHelper.animateBounceIcon(ivSuccessIcon);
        SuccessAnimationHelper.animateSlideFade(tvSuccessTitle, 150);
        SuccessAnimationHelper.animateSlideFade(tvSuccessMessage, 300);

    }

    private void autoRedirect() {
        new Handler(Looper.getMainLooper())
                .postDelayed(this::redirectToHome, REDIRECT_DELAY);
    }

    private void redirectToHome() {
        Intent intent = new Intent(Success.this, HomeConductor.class);

        // 🔒 Limpia el stack (no volver a login ni success)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);

        // 🎬 Animación de transición
        overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
        );
    }
}
