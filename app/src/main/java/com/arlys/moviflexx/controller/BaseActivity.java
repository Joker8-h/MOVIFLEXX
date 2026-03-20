package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.VoiceAssistantManager;

import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.view.GravityCompat;
import android.os.Handler;
import android.os.Looper;

/**
 * BaseActivity — Clase base para todas las Activities.
 * Proporciona transiciones, animaciones de botones y entrada de vistas.
 */
public abstract class BaseActivity extends AppCompatActivity {

    // ─── SCREEN DESCRIPTOR (Default implementations to avoid build breaks) ─────
    public String getNombrePantalla() { return "Pantalla de MoviFlexx"; }
    public String getDescripcionPantalla() { return "Estás en una pantalla de la aplicación."; }
    public String getOpcionesPantalla() { return "Puedes navegar por la aplicación o pedir ayuda."; }

    public enum Transition { SLIDE, FADE, NONE }

    protected VoiceAssistantManager voiceAssistant;
    protected static final int REQ_AUDIO_GLOBAL = 2299;

    // ─── NAVIGATION & DRAWER ──────────────────────────────────────────────────
    protected DrawerLayout drawerLayout;
    protected NavigationView navView;

    protected void setupDrawer(int itemId) {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        if (navView != null) {
            navView.setCheckedItem(itemId);
            navView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == itemId) {
                    if (drawerLayout != null) drawerLayout.closeDrawers();
                    return true;
                }
                
                // Navegación común
                if (id == R.id.nav_inicio) {
                    SessionManager s = new SessionManager(this);
                    if (s.isConductor()) goTo(HomeConductor.class);
                    else goTo(HomePasajero.class);
                } else if (id == R.id.nav_viajes || id == R.id.nav_mis_viajes) {
                    goTo(MisViajesActivity.class);
                } else if (id == R.id.nav_perfil) {
                    goTo(PerfilUsuario.class);
                } else if (id == R.id.nav_mensajes) {
                    goTo(Mensajes.class);
                } else if (id == R.id.nav_cerrar_sesion) {
                    if (voiceAssistant != null) voiceAssistant.hablar("Cerrando sesión.");
                    new SessionManager(this).logout();
                    goTo(Login.class, Transition.FADE, true);
                }
                
                if (drawerLayout != null) drawerLayout.closeDrawers();
                return true;
            });
        }
    }

    protected void abrirDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    protected void setupNavBar(BottomNavigationView nav, int itemId) {
        if (nav == null) return;
        nav.setSelectedItemId(itemId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == itemId) return true;

            if (id == R.id.nav_inicio) {
                SessionManager s = new SessionManager(this);
                if (s.isConductor()) goTo(HomeConductor.class, Transition.NONE);
                else goTo(HomePasajero.class, Transition.NONE);
                return true;
            } else if (id == R.id.nav_mis_viajes) {
                goTo(MisViajesActivity.class, Transition.NONE);
                return true;
            } else if (id == R.id.nav_mensajes) {
                goTo(Mensajes.class, Transition.NONE);
                return true;
            } else if (id == R.id.nav_perfil) {
                goTo(PerfilUsuario.class, Transition.NONE);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        voiceAssistant = VoiceAssistantManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        iniciarAsistenteVozSiPermite();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (voiceAssistant != null) voiceAssistant.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO_GLOBAL) {
            iniciarAsistenteVozSiPermite();
        }
    }

    protected void iniciarAsistenteVozSiPermite() {
        if (voiceAssistant == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_GLOBAL);
            return;
        }
        voiceAssistant.escuchar();
        verificarSaludoPendiente();
    }

    private void verificarSaludoPendiente() {
        SessionManager session = new SessionManager(this);
        if (session.isPendingWelcome()) {
            session.setPendingWelcome(false);
            String nombre = session.getNombre();
            // Pequeño delay para asegurar que el TTS está listo
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (voiceAssistant != null) {
                    voiceAssistant.saludarConDatoCurioso(nombre);
                }
            }, 1000);
        }
    }

    // ─── NAVEGACIÓN CON ANIMACIÓN ─────────────────────────────────────────────

    protected void goTo(Class<?> destino) {
        goTo(destino, Transition.SLIDE, false);
    }

    protected void goTo(Class<?> destino, Transition transition) {
        goTo(destino, transition, false);
    }

    protected void goTo(Class<?> destino, Transition transition, boolean finishCurrent) {
        startActivity(new Intent(this, destino));
        applyTransition(transition, false);
        if (finishCurrent) finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        applyTransition(Transition.SLIDE, true);
    }

    private void applyTransition(Transition type, boolean isBack) {
        switch (type) {
            case SLIDE:
                if (isBack) {
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                } else {
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
                break;
            case FADE:
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                break;
            case NONE:
                overridePendingTransition(0, 0);
                break;
        }
    }

    // ─── MICROINTERACCIÓN BOTÓN (escala 0.95) ────────────────────────────────

    protected void animateButton(View view, Runnable accion) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.95f).scaleY(0.95f)
                            .setDuration(80)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                    v.animate()
                            .scaleX(1.02f).scaleY(1.02f)
                            .setDuration(100)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() ->
                                    v.animate()
                                            .scaleX(1.0f).scaleY(1.0f)
                                            .setDuration(80)
                                            .setInterpolator(new OvershootInterpolator(1.5f))
                                            .withEndAction(accion)
                                            .start()
                            ).start();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(100)
                            .start();
                    break;
            }
            return false;
        });
    }

    // ─── ANIMACIÓN DE ENTRADA (fade + slide desde abajo) ─────────────────────

    protected void animateViewEntrance(View view, int delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}