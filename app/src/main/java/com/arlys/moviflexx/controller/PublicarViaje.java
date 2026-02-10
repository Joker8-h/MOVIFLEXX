package com.arlys.moviflexx.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PublicarViaje extends AppCompatActivity {

    private TextInputEditText editFechaHora, editPrecio;
    private Spinner spinnerCupos;
    private ProgressBar loader;
    private View overlayBackground;
    private CardView loaderContainer;
    private CardView mainCard, metadataCard, statsCard;
    private MaterialButton btnPublicar;
    private View headerCard;

    private int rutaId;
    private int vehiculoId;

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicar_viaje);

        session = new SessionManager(this);

        // 🔥 ID RUTA
        rutaId = getIntent().getIntExtra("ID_RUTA_CREADA", 0);
        if (rutaId == 0) {
            Toast.makeText(this, "Error: no se recibió la ruta", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔥 ID VEHÍCULO
        vehiculoId = session.getVehiculoId();
        if (vehiculoId == -1) {
            Toast.makeText(this, "Debes registrar un vehículo primero", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, RegistrarVehiculo.class));
            finish();
            return;
        }

        initViews();
        configurarSpinnerCupos();
        colocarFechaHoraActual();
        configurarBottomNav();

        // ✨ INICIAR ANIMACIONES PROFESIONALES
        animarEntradaProfesional();

        btnPublicar.setOnClickListener(v -> {
            animarBotonProfesional(v);
            publicarViaje();
        });
    }

    private void initViews() {
        editFechaHora = findViewById(R.id.edit_fecha_hora);
        editPrecio = findViewById(R.id.edit_precio);
        spinnerCupos = findViewById(R.id.spinnerCupo);
        loader = findViewById(R.id.loader_viaje);
        overlayBackground = findViewById(R.id.overlay_background);
        loaderContainer = findViewById(R.id.loader_container);
        mainCard = findViewById(R.id.main_card);
        metadataCard = findViewById(R.id.metadata_card);
        statsCard = findViewById(R.id.stats_card);
        btnPublicar = findViewById(R.id.btn_publicar_viaje);
        headerCard = findViewById(R.id.header_card);
    }

    // ✨ ================= ANIMACIONES PROFESIONALES =================

    private void animarEntradaProfesional() {
        // Animar header con slide down profesional
        if (headerCard != null) {
            headerCard.setTranslationY(-200f);
            headerCard.setAlpha(0f);
            headerCard.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(600)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }

        // Animar card principal con fade y escala
        mainCard.postDelayed(() -> {
            AnimatorSet cardSet = new AnimatorSet();
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mainCard, "alpha", 0f, 1f);
            ObjectAnimator translateY = ObjectAnimator.ofFloat(mainCard, "translationY", 50f, 0f);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(mainCard, "scaleX", 0.95f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(mainCard, "scaleY", 0.95f, 1f);

            cardSet.playTogether(fadeIn, translateY, scaleX, scaleY);
            cardSet.setDuration(700);
            cardSet.setInterpolator(new DecelerateInterpolator(1.2f));
            cardSet.start();
        }, 300);

        // Animar metadata card
        metadataCard.postDelayed(() -> {
            AnimatorSet metadataSet = new AnimatorSet();
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(metadataCard, "alpha", 0f, 1f);
            ObjectAnimator translateY = ObjectAnimator.ofFloat(metadataCard, "translationY", 30f, 0f);

            metadataSet.playTogether(fadeIn, translateY);
            metadataSet.setDuration(600);
            metadataSet.setInterpolator(new DecelerateInterpolator());
            metadataSet.start();
        }, 500);

        // Animar stats card
        statsCard.postDelayed(() -> {
            AnimatorSet statsSet = new AnimatorSet();
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(statsCard, "alpha", 0f, 1f);
            ObjectAnimator translateY = ObjectAnimator.ofFloat(statsCard, "translationY", 30f, 0f);

            statsSet.playTogether(fadeIn, translateY);
            statsSet.setDuration(600);
            statsSet.setInterpolator(new DecelerateInterpolator());
            statsSet.start();
        }, 650);
    }

    private void animarBotonProfesional(View boton) {
        // Efecto de pulso profesional
        AnimatorSet pulseSet = new AnimatorSet();

        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(boton, "scaleX", 1f, 0.96f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(boton, "scaleY", 1f, 0.96f);

        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        pulseSet.playTogether(scaleDownX, scaleDownY);
        pulseSet.setInterpolator(new AccelerateDecelerateInterpolator());

        pulseSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AnimatorSet scaleUpSet = new AnimatorSet();
                ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(boton, "scaleX", 0.96f, 1f);
                ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(boton, "scaleY", 0.96f, 1f);

                scaleUpSet.playTogether(scaleUpX, scaleUpY);
                scaleUpSet.setDuration(150);
                scaleUpSet.setInterpolator(new OvershootInterpolator(2f));
                scaleUpSet.start();
            }
        });

        pulseSet.start();
    }

    private void mostrarLoaderProfesional() {
        overlayBackground.setVisibility(View.VISIBLE);
        loaderContainer.setVisibility(View.VISIBLE);

        overlayBackground.setAlpha(0f);
        loaderContainer.setAlpha(0f);
        loaderContainer.setScaleX(0.7f);
        loaderContainer.setScaleY(0.7f);

        // Animar overlay
        overlayBackground.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        // Animar loader con efecto profesional
        AnimatorSet loaderSet = new AnimatorSet();
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(loaderContainer, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(loaderContainer, "scaleX", 0.7f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(loaderContainer, "scaleY", 0.7f, 1f);

        loaderSet.playTogether(fadeIn, scaleX, scaleY);
        loaderSet.setDuration(350);
        loaderSet.setInterpolator(new OvershootInterpolator(1.2f));
        loaderSet.start();

        // Animación de rotación sutil del card loader
        ObjectAnimator rotation = ObjectAnimator.ofFloat(loaderContainer, "rotation", 0f, 2f, -2f, 0f);
        rotation.setDuration(2000);
        rotation.setRepeatCount(ValueAnimator.INFINITE);
        rotation.setInterpolator(new AccelerateDecelerateInterpolator());
        rotation.start();
    }

    private void ocultarLoaderProfesional() {
        AnimatorSet hideSet = new AnimatorSet();

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(loaderContainer, "alpha", 1f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(loaderContainer, "scaleX", 1f, 0.7f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(loaderContainer, "scaleY", 1f, 0.7f);

        hideSet.playTogether(fadeOut, scaleX, scaleY);
        hideSet.setDuration(250);
        hideSet.setInterpolator(new AccelerateDecelerateInterpolator());

        hideSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                loaderContainer.setVisibility(View.GONE);
                overlayBackground.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> overlayBackground.setVisibility(View.GONE))
                        .start();
            }
        });

        hideSet.start();
    }

    private void animarErrorProfesional(View view) {
        // Shake horizontal profesional
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX",
                0, -15, 15, -15, 15, -10, 10, -5, 5, 0);
        shake.setDuration(600);
        shake.setInterpolator(new DecelerateInterpolator());
        shake.start();

        // Efecto de parpadeo rojo sutil
        final int originalColor = ((CardView) view).getCardBackgroundColor().getDefaultColor();
        ValueAnimator colorAnim = ValueAnimator.ofArgb(originalColor, 0xFFFFEEEE, originalColor);
        colorAnim.setDuration(600);
        colorAnim.addUpdateListener(animator ->
                ((CardView) view).setCardBackgroundColor((int) animator.getAnimatedValue())
        );
        colorAnim.start();
    }

    private void animarExitoProfesional(Runnable onComplete) {
        // Animación de éxito con escala y fade
        AnimatorSet successSet = new AnimatorSet();

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mainCard, "scaleX", 1f, 0.92f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mainCard, "scaleY", 1f, 0.92f);
        ObjectAnimator translateY = ObjectAnimator.ofFloat(mainCard, "translationY", 0f, 20f);

        successSet.playTogether(fadeOut, scaleX, scaleY, translateY);
        successSet.setDuration(400);
        successSet.setInterpolator(new AccelerateDecelerateInterpolator());

        successSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onComplete.run();
            }
        });

        successSet.start();
    }

    // ================= SPINNER =================
    private void configurarSpinnerCupos() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.cupos,
                R.drawable.spinner_item
        );
        adapter.setDropDownViewResource(R.drawable.spinner_dropdown_item);
        spinnerCupos.setAdapter(adapter);
    }

    // ================= FECHA =================
    private void colocarFechaHoraActual() {
        String ahora = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        editFechaHora.setText(ahora);
    }

    // ================= PUBLICAR =================
    private void publicarViaje() {

        String precioTxt = editPrecio.getText().toString().trim();
        if (precioTxt.isEmpty()) {
            editPrecio.setError("Ingrese el precio del servicio");
            animarErrorProfesional(mainCard);
            return;
        }

        int cupos = Integer.parseInt(
                spinnerCupos.getSelectedItem().toString()
        );

        mostrarLoaderProfesional();

        try {
            JSONObject body = new JSONObject();

            body.put("idRuta", rutaId);
            body.put("idVehiculos", vehiculoId);
            body.put("fechaHoraSalida", editFechaHora.getText().toString());
            body.put("cuposTotales", cupos);
            body.put("cuposDisponibles", cupos);
            body.put("precio", Double.parseDouble(precioTxt));

            ConexionApi.getInstance(this).post(
                    Constantes.VIAJES,
                    body,
                    response -> {
                        ocultarLoaderProfesional();

                        // Animación de éxito profesional
                        animarExitoProfesional(() -> {
                            Toast.makeText(this, "Servicio publicado exitosamente", Toast.LENGTH_LONG).show();

                            Intent intent = new Intent(this, HomeConductor.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    },
                    error -> {
                        ocultarLoaderProfesional();
                        animarErrorProfesional(mainCard);
                        Toast.makeText(this, "Error al publicar el servicio", Toast.LENGTH_LONG).show();
                    }
            );

        } catch (Exception e) {
            ocultarLoaderProfesional();
            e.printStackTrace();
        }
    }

    // ================= NAV =================
    private void configurarBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_mis_viajes);

        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) {
                startActivity(new Intent(this, HomeConductor.class));
                finish();
            }
            return true;
        });
    }
}