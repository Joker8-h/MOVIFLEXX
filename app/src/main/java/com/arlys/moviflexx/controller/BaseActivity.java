package com.arlys.moviflexx.controller;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.AnimUtils;
import com.arlys.moviflexx.model.SessionManager;
import com.arlys.moviflexx.model.VoiceAssistantManager;
import com.google.android.material.navigation.NavigationView;

public abstract class BaseActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public enum Transition { SLIDE, FADE, NONE }

    protected VoiceAssistantManager voiceAssistant;
    protected static final int REQ_AUDIO_GLOBAL = 2299;

    protected DrawerLayout   drawerLayout;
    protected NavigationView navView;

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        voiceAssistant = VoiceAssistantManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        iniciarAsistenteVozSiPermite();
        AnimUtils.resetScrollReveal();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (voiceAssistant != null) voiceAssistant.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO_GLOBAL) iniciarAsistenteVozSiPermite();
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
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (voiceAssistant != null) voiceAssistant.saludarConDatoCurioso(nombre);
            }, 1000);
        }
    }

    // =========================================================================
    //  NAVEGACIÓN CON ANIMACIÓN
    // =========================================================================

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
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
        applyTransition(Transition.SLIDE, true);
    }

    protected void applyTransition(Transition type, boolean isBack) {
        switch (type) {
            case SLIDE:
                if (isBack) overridePendingTransition(R.anim.slide_in_left,  R.anim.slide_out_right);
                else        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                break;
            case FADE:
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                break;
            case NONE:
                overridePendingTransition(0, 0);
                break;
        }
    }

    // =========================================================================
    //  BOTTOM NAV — fade suave entre tabs
    //  Uso: setupNavBar(findViewById(R.id.bottom_navigation), R.id.nav_inicio);
    // =========================================================================

    protected void setupNavBar(
            com.google.android.material.bottomnavigation.BottomNavigationView navBar,
            int currentItemId) {
        if (navBar == null) return;
        navBar.setSelectedItemId(currentItemId);
        SessionManager session = new SessionManager(this);
        boolean esConductor = session.isConductor();

        navBar.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentItemId) return true;

            if (esConductor) {
                if      (id == R.id.nav_inicio)     goTo(HomeConductor.class,     Transition.FADE);
                else if (id == R.id.nav_mis_viajes) goTo(PublicarRuta.class,      Transition.FADE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,              Transition.FADE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,          Transition.FADE);
                else if (id == R.id.nav_perfil)     goTo(PerfilUsuario.class,     Transition.FADE);
            } else {
                if      (id == R.id.nav_inicio)     goTo(HomePasajero.class,        Transition.FADE);
                else if (id == R.id.nav_mis_viajes) goTo(MisReservasActivity.class, Transition.FADE);
                else if (id == R.id.nav_mapa)       goTo(Mapa.class,               Transition.FADE);
                else if (id == R.id.nav_mensajes)   goTo(Mensajes.class,            Transition.FADE);
                else if (id == R.id.nav_perfil)     goTo(PerfilUsuario.class,       Transition.FADE);
            }
            finish();
            return true;
        });
    }

    // =========================================================================
    //  MICROINTERACCIÓN BOTÓN
    // =========================================================================

    protected void animateButton(View view, Runnable accion) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f)
                            .setDuration(80)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1.02f).scaleY(1.02f)
                            .setDuration(100)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() ->
                                    v.animate().scaleX(1.0f).scaleY(1.0f)
                                            .setDuration(80)
                                            .setInterpolator(new OvershootInterpolator(1.5f))
                                            .withEndAction(accion).start()
                            ).start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    // =========================================================================
    //  ANIMACIÓN DE ENTRADA  (delega en AnimUtils)
    // =========================================================================

    protected void animateViewEntrance(View view, int delayMs) {
        AnimUtils.fadeSlideIn(view, delayMs);
    }

    // =========================================================================
    //  NAVIGATION DRAWER
    // =========================================================================

    protected void setupDrawer(int activeItemId) {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView      = findViewById(R.id.nav_view);
        if (drawerLayout == null || navView == null) return;
        navView.setNavigationItemSelectedListener(this);
        navView.setCheckedItem(activeItemId);
    }

    protected void abrirDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
        SessionManager session = new SessionManager(this);
        int id = item.getItemId();

        if (id == R.id.nav_inicio) {
            if (session.isConductor()) goTo(HomeConductor.class, Transition.FADE);

            else                       goTo(HomePasajero.class,  Transition.FADE);

        } else if (id == R.id.nav_viajes) {
            goTo(MisViajesActivity.class, Transition.FADE);

        } else if (id == R.id.nav_reservas) {
            goTo(MisReservasActivity.class, Transition.FADE);

        } else if (id == R.id.nav_mapa) {
            goTo(Mapa.class, Transition.FADE);

        } else if (id == R.id.nav_mensajes) {
            goTo(Mensajes.class, Transition.FADE);

        } else if (id == R.id.nav_publicar_viaje) {
            goTo(PublicarViaje.class, Transition.SLIDE);

        } else if (id == R.id.nav_mis_rutas) {
            goTo(MisRutasActivity.class, Transition.SLIDE);

        } else if (id == R.id.nav_mis_vehiculos) {
            goTo(MisVehiculosActivity.class, Transition.SLIDE);

        } else if (id == R.id.nav_notificaciones) {
            goTo(Notificaciones.class, Transition.SLIDE);

        } else if (id == R.id.nav_perfil) {
            if (!this.getClass().equals(PerfilUsuario.class))
                goTo(PerfilUsuario.class, Transition.FADE);

        } else if (id == R.id.nav_ayuda) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://moviflexconreact-production.up.railway.app/")));

        } else if (id == R.id.nav_cerrar_sesion) {
            session.logout();
            Intent intent = new Intent(this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }
        return true;
    }
}