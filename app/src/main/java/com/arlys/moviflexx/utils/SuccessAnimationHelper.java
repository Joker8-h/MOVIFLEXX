package com.arlys.moviflexx.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

/**
 * 🎮 ULTRA PREMIUM SUCCESS ANIMATION HELPER
 * Animaciones de nivel AAA para pantalla de éxito
 * Inspirado en juegos modernos y motion design profesional
 */
public class SuccessAnimationHelper {

    private static final Random random = new Random();

    // ═══════════════════════════════════════════════════════════
    // 🎯 SECUENCIA MAESTRA DE ANIMACIÓN
    // ═══════════════════════════════════════════════════════════

    /**
     * Ejecuta toda la secuencia de animaciones en orden perfecto
     */
    public static void executeFullSequence(View rootView) {
        // FASE 1: Flash inicial + ondas expansivas (0-300ms)
        animateScreenFlash(rootView.findViewById(getId(rootView, "screen_flash")));
        animateRippleWaves(rootView);

        // FASE 2: Explosión radial + rayos de luz (300-800ms)
        postDelayed(() -> {
            animateRadialBurst(rootView.findViewById(getId(rootView, "radial_glow")));
            animateLightRays(rootView.findViewById(getId(rootView, "light_rays")));
        }, 300);

        // FASE 3: Aparecer card principal (500-1200ms)
        postDelayed(() -> animateCardEntrance(rootView.findViewById(getId(rootView, "main_success_card"))), 500);

        // FASE 4: Anillos orbitales (700-1500ms)
        postDelayed(() -> animateOrbitalRings(rootView), 700);

        // FASE 5: Ícono central con explosión (900-2000ms)
        postDelayed(() -> animateIconExplosion(rootView), 900);

        // FASE 6: Partículas de explosión (1100ms)
        postDelayed(() -> animateBurstParticles(rootView), 1100);

        // FASE 7: Confeti celebration (1200-3000ms)
        postDelayed(() -> animateConfetti(rootView), 1200);

        // FASE 8: Textos con efecto de escritura (1400-2200ms)
        postDelayed(() -> animateTextSequence(rootView), 1400);

        // FASE 9: Estadísticas con contadores (2000-2800ms)
        postDelayed(() -> animateStats(rootView), 2000);

        // FASE 10: Botones con efectos (2400-3000ms)
        postDelayed(() -> animateButtons(rootView), 2400);

        // FASE 11: Achievement badge (2800ms)
        postDelayed(() -> animateAchievementBadge(rootView.findViewById(getId(rootView, "achievement_badge"))), 2800);

        // FASE 12: Animaciones infinitas de mantenimiento
        postDelayed(() -> startInfiniteAnimations(rootView), 3000);
    }

    // ═══════════════════════════════════════════════════════════
    // ⚡ FASE 1: EFECTOS DE IMPACTO INICIAL
    // ═══════════════════════════════════════════════════════════

    /**
     * Flash blanco dramático tipo juego de acción
     */
    private static void animateScreenFlash(View flash) {
        if (flash == null) return;

        ObjectAnimator alpha = ObjectAnimator.ofFloat(flash, "alpha", 0f, 0.9f, 0f);
        alpha.setDuration(400);
        alpha.setInterpolator(new DecelerateInterpolator(2f));
        alpha.start();
    }

    /**
     * Ondas expansivas múltiples tipo impacto
     */
    private static void animateRippleWaves(View rootView) {
        animateSingleRipple(rootView.findViewById(getId(rootView, "ripple_wave_1")), 0, 800, 1.5f);
        animateSingleRipple(rootView.findViewById(getId(rootView, "ripple_wave_2")), 150, 1000, 2f);
        animateSingleRipple(rootView.findViewById(getId(rootView, "ripple_wave_3")), 300, 1200, 2.5f);
    }

    private static void animateSingleRipple(View ripple, long delay, int duration, float scale) {
        if (ripple == null) return;

        ripple.setScaleX(0f);
        ripple.setScaleY(0f);
        ripple.setAlpha(0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(ripple, "scaleX", 0f, scale),
                ObjectAnimator.ofFloat(ripple, "scaleY", 0f, scale),
                ObjectAnimator.ofFloat(ripple, "alpha", 0f, 0.7f, 0f)
        );
        set.setDuration(duration);
        set.setStartDelay(delay);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    // ═══════════════════════════════════════════════════════════
    // 💥 FASE 2: EXPLOSIÓN Y ENERGÍA
    // ═══════════════════════════════════════════════════════════

    /**
     * Explosión radial de energía desde el centro
     */
    private static void animateRadialBurst(View burst) {
        if (burst == null) return;

        burst.setScaleX(0f);
        burst.setScaleY(0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(burst, "scaleX", 0f, 2.5f),
                ObjectAnimator.ofFloat(burst, "scaleY", 0f, 2.5f),
                ObjectAnimator.ofFloat(burst, "alpha", 0f, 0.8f, 0f),
                ObjectAnimator.ofFloat(burst, "rotation", 0f, 180f)
        );
        set.setDuration(1500);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    /**
     * Rayos de luz rotativos tipo juego épico
     */
    private static void animateLightRays(View rays) {
        if (rays == null) return;

        // Aparición inicial
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(rays, "alpha", 0f, 0.4f);
        fadeIn.setDuration(800);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.start();

        // Rotación continua
        ObjectAnimator rotation = ObjectAnimator.ofFloat(rays, "rotation", 0f, 360f);
        rotation.setDuration(20000);
        rotation.setRepeatCount(ValueAnimator.INFINITE);
        rotation.setInterpolator(new LinearInterpolator());
        rotation.start();
    }

    // ═══════════════════════════════════════════════════════════
    // 🎴 FASE 3: ENTRADA DEL CARD PRINCIPAL
    // ═══════════════════════════════════════════════════════════

    /**
     * Card principal con efecto cinematográfico
     */
    private static void animateCardEntrance(View card) {
        if (card == null) return;

        card.setScaleX(0.3f);
        card.setScaleY(0.3f);
        card.setRotationX(90f);
        card.setAlpha(0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 0.3f, 1.05f, 1f),
                ObjectAnimator.ofFloat(card, "scaleY", 0.3f, 1.05f, 1f),
                ObjectAnimator.ofFloat(card, "rotationX", 90f, -5f, 0f),
                ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
        );
        set.setDuration(1000);
        set.setInterpolator(new AnticipateOvershootInterpolator(0.8f));
        set.start();

        // Pulso sutil continuo
        postDelayed(() -> animateCardPulse(card), 1000);
    }

    private static void animateCardPulse(View card) {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(card, "elevation", 24f, 32f, 24f);
        pulse.setDuration(2000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    // ═══════════════════════════════════════════════════════════
    // 🌀 FASE 4: ANILLOS ORBITALES
    // ═══════════════════════════════════════════════════════════

    /**
     * Anillos orbitales tipo sci-fi
     */
    private static void animateOrbitalRings(View rootView) {
        animateSingleOrbit(rootView.findViewById(getId(rootView, "orbit_ring_1")), 0, true);
        animateSingleOrbit(rootView.findViewById(getId(rootView, "orbit_ring_2")), 200, false);
    }

    private static void animateSingleOrbit(View ring, long delay, boolean clockwise) {
        if (ring == null) return;

        // Aparición
        ring.setScaleX(0f);
        ring.setScaleY(0f);
        ring.setAlpha(0f);

        AnimatorSet appear = new AnimatorSet();
        appear.playTogether(
                ObjectAnimator.ofFloat(ring, "scaleX", 0f, 1.2f, 1f),
                ObjectAnimator.ofFloat(ring, "scaleY", 0f, 1.2f, 1f),
                ObjectAnimator.ofFloat(ring, "alpha", 0f, 0.5f)
        );
        appear.setDuration(800);
        appear.setStartDelay(delay);
        appear.setInterpolator(new OvershootInterpolator());
        appear.start();

        // Rotación orbital continua
        postDelayed(() -> {
            float direction = clockwise ? 360f : -360f;
            ObjectAnimator rotate = ObjectAnimator.ofFloat(ring, "rotation", 0f, direction);
            rotate.setDuration(8000);
            rotate.setRepeatCount(ValueAnimator.INFINITE);
            rotate.setInterpolator(new LinearInterpolator());
            rotate.start();

            // Pulso de escala
            ObjectAnimator scalePulse = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.05f, 1f);
            ObjectAnimator scalePulse2 = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.05f, 1f);
            scalePulse.setDuration(3000);
            scalePulse2.setDuration(3000);
            scalePulse.setRepeatCount(ValueAnimator.INFINITE);
            scalePulse2.setRepeatCount(ValueAnimator.INFINITE);
            scalePulse.start();
            scalePulse2.start();
        }, delay + 800);
    }

    // ═══════════════════════════════════════════════════════════
    // ✅ FASE 5: ÍCONO CENTRAL EXPLOSIVO
    // ═══════════════════════════════════════════════════════════

    /**
     * Animación del ícono principal con máximo impacto
     */
    private static void animateIconExplosion(View rootView) {
        View iconBg = rootView.findViewById(getId(rootView, "icon_bg_card"));
        ImageView icon = rootView.findViewById(getId(rootView, "success_icon"));
        View glow = rootView.findViewById(getId(rootView, "inner_glow"));

        // Background del ícono
        if (iconBg != null) {
            iconBg.setScaleX(0f);
            iconBg.setScaleY(0f);
            iconBg.setRotation(-180f);

            AnimatorSet bgSet = new AnimatorSet();
            bgSet.playTogether(
                    ObjectAnimator.ofFloat(iconBg, "scaleX", 0f, 1.3f, 0.95f, 1f),
                    ObjectAnimator.ofFloat(iconBg, "scaleY", 0f, 1.3f, 0.95f, 1f),
                    ObjectAnimator.ofFloat(iconBg, "rotation", -180f, 20f, 0f),
                    ObjectAnimator.ofFloat(iconBg, "alpha", 0f, 1f)
            );
            bgSet.setDuration(1000);
            bgSet.setInterpolator(new AnticipateOvershootInterpolator(1.2f));
            bgSet.start();
        }

        // Ícono checkmark con dibujo animado
        if (icon != null) {
            postDelayed(() -> {
                icon.setScaleX(0f);
                icon.setScaleY(0f);
                icon.setRotation(90f);

                AnimatorSet iconSet = new AnimatorSet();
                iconSet.playTogether(
                        ObjectAnimator.ofFloat(icon, "scaleX", 0f, 1.5f, 1f),
                        ObjectAnimator.ofFloat(icon, "scaleY", 0f, 1.5f, 1f),
                        ObjectAnimator.ofFloat(icon, "rotation", 90f, -10f, 0f),
                        ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f)
                );
                iconSet.setDuration(800);
                iconSet.setInterpolator(new OvershootInterpolator(2f));
                iconSet.start();

                // Pulso continuo del ícono
                postDelayed(() -> {
                    ObjectAnimator pulse = ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.08f, 1f);
                    ObjectAnimator pulse2 = ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.08f, 1f);
                    pulse.setDuration(1500);
                    pulse2.setDuration(1500);
                    pulse.setRepeatCount(ValueAnimator.INFINITE);
                    pulse2.setRepeatCount(ValueAnimator.INFINITE);
                    pulse.start();
                    pulse2.start();
                }, 800);
            }, 200);
        }

        // Glow pulsante
        if (glow != null) {
            postDelayed(() -> {
                ObjectAnimator glowAnim = ObjectAnimator.ofFloat(glow, "alpha", 0.3f, 1f, 0.3f);
                glowAnim.setDuration(2000);
                glowAnim.setRepeatCount(ValueAnimator.INFINITE);
                glowAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                glowAnim.start();
            }, 400);
        }

        // Sparkles decorativas
        animateSparkles(rootView);
    }

    /**
     * Destellos brillantes alrededor del ícono
     */
    private static void animateSparkles(View rootView) {
        for (int i = 1; i <= 3; i++) {
            ImageView sparkle = rootView.findViewById(getId(rootView, "sparkle_" + i));
            if (sparkle != null) {
                long delay = i * 100L;
                postDelayed(() -> {
                    sparkle.setScaleX(0f);
                    sparkle.setScaleY(0f);
                    sparkle.setRotation(0f);

                    AnimatorSet sparkleSet = new AnimatorSet();
                    sparkleSet.playTogether(
                            ObjectAnimator.ofFloat(sparkle, "scaleX", 0f, 1.5f, 1f),
                            ObjectAnimator.ofFloat(sparkle, "scaleY", 0f, 1.5f, 1f),
                            ObjectAnimator.ofFloat(sparkle, "alpha", 0f, 1f),
                            ObjectAnimator.ofFloat(sparkle, "rotation", 0f, 180f)
                    );
                    sparkleSet.setDuration(600);
                    sparkleSet.setInterpolator(new OvershootInterpolator());
                    sparkleSet.start();

                    // Rotación y pulso continuo
                    postDelayed(() -> {
                        ObjectAnimator rotate = ObjectAnimator.ofFloat(sparkle, "rotation", 0f, 360f);
                        rotate.setDuration(3000);
                        rotate.setRepeatCount(ValueAnimator.INFINITE);
                        rotate.setInterpolator(new LinearInterpolator());
                        rotate.start();

                        ObjectAnimator blink = ObjectAnimator.ofFloat(sparkle, "alpha", 1f, 0.3f, 1f);
                        blink.setDuration(1500);
                        blink.setRepeatCount(ValueAnimator.INFINITE);
                        blink.start();
                    }, 600);
                }, delay);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 💫 FASE 6: PARTÍCULAS DE EXPLOSIÓN
    // ═══════════════════════════════════════════════════════════

    /**
     * Partículas que explotan desde el centro
     */
    private static void animateBurstParticles(View rootView) {
        for (int i = 1; i <= 4; i++) {
            ImageView particle = rootView.findViewById(getId(rootView, "burst_particle_" + i));
            if (particle != null) {
                // Calcular posición de explosión
                float angle = (i - 1) * 90f; // 0°, 90°, 180°, 270°
                float distance = 200f;
                float endX = (float) (distance * Math.cos(Math.toRadians(angle)));
                float endY = (float) (distance * Math.sin(Math.toRadians(angle)));

                particle.setAlpha(0f);
                particle.setScaleX(0f);
                particle.setScaleY(0f);

                long delay = i * 50L;
                postDelayed(() -> {
                    AnimatorSet burstSet = new AnimatorSet();
                    burstSet.playTogether(
                            ObjectAnimator.ofFloat(particle, "translationX", 0f, endX),
                            ObjectAnimator.ofFloat(particle, "translationY", 0f, endY),
                            ObjectAnimator.ofFloat(particle, "scaleX", 0f, 1.5f, 0f),
                            ObjectAnimator.ofFloat(particle, "scaleY", 0f, 1.5f, 0f),
                            ObjectAnimator.ofFloat(particle, "alpha", 0f, 1f, 0f),
                            ObjectAnimator.ofFloat(particle, "rotation", 0f, 720f)
                    );
                    burstSet.setDuration(1200);
                    burstSet.setInterpolator(new DecelerateInterpolator());
                    burstSet.start();
                }, delay);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🎊 FASE 7: CONFETI CELEBRATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Confeti cayendo desde arriba (requiere layout separado)
     */
    private static void animateConfetti(View rootView) {
        // Esta función asume que tienes un layout con múltiples views de confeti
        // Ver método createConfettiParticle para generar confeti dinámico
        for (int i = 0; i < 20; i++) {
            postDelayed(() -> {
                // Aquí irían las animaciones de confeti individual
                // (necesita views preexistentes en el layout)
            }, i * 100L);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 📝 FASE 8: TEXTOS CON EFECTOS
    // ═══════════════════════════════════════════════════════════

    /**
     * Secuencia de textos con múltiples efectos
     */
    private static void animateTextSequence(View rootView) {
        TextView title = rootView.findViewById(getId(rootView, "success_title"));
        View underline = rootView.findViewById(getId(rootView, "title_underline"));
        TextView subtitle = rootView.findViewById(getId(rootView, "success_subtitle"));
        View messageCard = rootView.findViewById(getId(rootView, "message_card"));

        // Título con efecto de impacto
        if (title != null) {
            title.setScaleX(0f);
            title.setScaleY(0f);
            title.setAlpha(0f);

            AnimatorSet titleSet = new AnimatorSet();
            titleSet.playTogether(
                    ObjectAnimator.ofFloat(title, "scaleX", 0f, 1.2f, 1f),
                    ObjectAnimator.ofFloat(title, "scaleY", 0f, 1.2f, 1f),
                    ObjectAnimator.ofFloat(title, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(title, "rotation", -10f, 5f, 0f)
            );
            titleSet.setDuration(800);
            titleSet.setInterpolator(new OvershootInterpolator(1.5f));
            titleSet.start();
        }

        // Línea decorativa expansiva
        if (underline != null) {
            postDelayed(() -> {
                underline.setPivotX(0);
                underline.setScaleX(0f);

                ObjectAnimator lineAnim = ObjectAnimator.ofFloat(underline, "scaleX", 0f, 1f);
                lineAnim.setDuration(800);
                lineAnim.setInterpolator(new OvershootInterpolator());
                lineAnim.start();

                ObjectAnimator alpha = ObjectAnimator.ofFloat(underline, "alpha", 0f, 1f);
                alpha.setDuration(800);
                alpha.start();
            }, 200);
        }

        // Subtítulo con slide + fade
        if (subtitle != null) {
            postDelayed(() -> {
                subtitle.setTranslationY(50f);
                subtitle.setAlpha(0f);

                AnimatorSet subtitleSet = new AnimatorSet();
                subtitleSet.playTogether(
                        ObjectAnimator.ofFloat(subtitle, "translationY", 50f, 0f),
                        ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f)
                );
                subtitleSet.setDuration(700);
                subtitleSet.setInterpolator(new DecelerateInterpolator());
                subtitleSet.start();
            }, 400);
        }

        // Card de mensaje con efecto de vidrio
        if (messageCard != null) {
            postDelayed(() -> {
                messageCard.setScaleY(0f);
                messageCard.setPivotY(0f);
                messageCard.setAlpha(0f);

                AnimatorSet cardSet = new AnimatorSet();
                cardSet.playTogether(
                        ObjectAnimator.ofFloat(messageCard, "scaleY", 0f, 1f),
                        ObjectAnimator.ofFloat(messageCard, "alpha", 0f, 1f)
                );
                cardSet.setDuration(600);
                cardSet.setInterpolator(new OvershootInterpolator());
                cardSet.start();
            }, 600);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 📊 FASE 9: ESTADÍSTICAS ANIMADAS
    // ═══════════════════════════════════════════════════════════

    /**
     * Stats container con números animados
     */
    private static void animateStats(View rootView) {
        View statsContainer = rootView.findViewById(getId(rootView, "stats_container"));

        if (statsContainer != null) {
            statsContainer.setTranslationY(60f);
            statsContainer.setAlpha(0f);

            AnimatorSet containerSet = new AnimatorSet();
            containerSet.playTogether(
                    ObjectAnimator.ofFloat(statsContainer, "translationY", 60f, 0f),
                    ObjectAnimator.ofFloat(statsContainer, "alpha", 0f, 1f)
            );
            containerSet.setDuration(800);
            containerSet.setInterpolator(new OvershootInterpolator());
            containerSet.start();

            // Animar números individuales
            postDelayed(() -> {
                TextView statSpeed = rootView.findViewById(getId(rootView, "stat_speed"));
                TextView statItems = rootView.findViewById(getId(rootView, "stat_items"));

                if (statSpeed != null) animateCounterText(statSpeed, "2.3s", 800);
                if (statItems != null) animateCounterText(statItems, "100%", 800);
            }, 200);
        }
    }

    /**
     * Anima texto como contador incremental
     */
    private static void animateCounterText(TextView textView, String finalText, long duration) {
        // Efecto de pulso mientras cuenta
        ObjectAnimator pulse = ObjectAnimator.ofFloat(textView, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator pulse2 = ObjectAnimator.ofFloat(textView, "scaleY", 1f, 1.3f, 1f);

        AnimatorSet pulseSet = new AnimatorSet();
        pulseSet.playTogether(pulse, pulse2);
        pulseSet.setDuration(duration);
        pulseSet.setInterpolator(new OvershootInterpolator());
        pulseSet.start();
    }

    // ═══════════════════════════════════════════════════════════
    // 🔘 FASE 10: BOTONES INTERACTIVOS
    // ═══════════════════════════════════════════════════════════

    /**
     * Botones con efectos premium
     */
    private static void animateButtons(View rootView) {
        View buttonsContainer = rootView.findViewById(getId(rootView, "buttons_container"));
        View primaryButton = rootView.findViewById(getId(rootView, "primary_button"));
        View buttonShine = rootView.findViewById(getId(rootView, "button_shine"));
        ImageView buttonIcon = rootView.findViewById(getId(rootView, "button_icon"));
        ImageView buttonArrow = rootView.findViewById(getId(rootView, "button_arrow"));

        // Container entrance
        if (buttonsContainer != null) {
            buttonsContainer.setTranslationY(80f);
            buttonsContainer.setAlpha(0f);

            AnimatorSet containerSet = new AnimatorSet();
            containerSet.playTogether(
                    ObjectAnimator.ofFloat(buttonsContainer, "translationY", 80f, -10f, 0f),
                    ObjectAnimator.ofFloat(buttonsContainer, "alpha", 0f, 1f)
            );
            containerSet.setDuration(800);
            containerSet.setInterpolator(new OvershootInterpolator());
            containerSet.start();
        }

        // Efecto de brillo deslizante en el botón
        if (buttonShine != null && primaryButton != null) {
            postDelayed(() -> {
                ObjectAnimator shine = ObjectAnimator.ofFloat(buttonShine, "translationX",
                        -buttonShine.getWidth(), primaryButton.getWidth());
                shine.setDuration(1500);
                shine.setRepeatCount(ValueAnimator.INFINITE);
                shine.setRepeatMode(ValueAnimator.RESTART);
                shine.setStartDelay(2000);
                shine.setInterpolator(new AccelerateDecelerateInterpolator());

                shine.addUpdateListener(animation -> {
                    buttonShine.setAlpha(0.6f);
                });

                shine.start();
            }, 400);
        }

        // Pulso del ícono del botón
        if (buttonIcon != null) {
            postDelayed(() -> {
                ObjectAnimator iconPulse = ObjectAnimator.ofFloat(buttonIcon, "scaleX", 1f, 1.15f, 1f);
                ObjectAnimator iconPulse2 = ObjectAnimator.ofFloat(buttonIcon, "scaleY", 1f, 1.15f, 1f);

                iconPulse.setDuration(1500);
                iconPulse2.setDuration(1500);
                iconPulse.setRepeatCount(ValueAnimator.INFINITE);
                iconPulse2.setRepeatCount(ValueAnimator.INFINITE);
                iconPulse.start();
                iconPulse2.start();
            }, 600);
        }

        // Flecha animada
        if (buttonArrow != null) {
            postDelayed(() -> {
                ObjectAnimator arrowMove = ObjectAnimator.ofFloat(buttonArrow, "translationX", 0f, 10f, 0f);
                arrowMove.setDuration(1200);
                arrowMove.setRepeatCount(ValueAnimator.INFINITE);
                arrowMove.setInterpolator(new AccelerateDecelerateInterpolator());
                arrowMove.start();
            }, 800);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🏆 FASE 11: ACHIEVEMENT BADGE
    // ═══════════════════════════════════════════════════════════

    /**
     * Badge de logro tipo juego móvil
     */
    private static void animateAchievementBadge(View badge) {
        if (badge == null) return;

        badge.setTranslationY(-100f);
        badge.setScaleX(0f);
        badge.setScaleY(0f);
        badge.setAlpha(0f);

        // Entrada dramática
        AnimatorSet entrance = new AnimatorSet();
        entrance.playTogether(
                ObjectAnimator.ofFloat(badge, "translationY", -100f, 80f),
                ObjectAnimator.ofFloat(badge, "scaleX", 0f, 1.2f, 1f),
                ObjectAnimator.ofFloat(badge, "scaleY", 0f, 1.2f, 1f),
                ObjectAnimator.ofFloat(badge, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(badge, "rotation", -45f, 0f)
        );
        entrance.setDuration(1000);
        entrance.setInterpolator(new OvershootInterpolator(1.5f));
        entrance.start();

        // Mantener visible por 3 segundos
        postDelayed(() -> {
            AnimatorSet exit = new AnimatorSet();
            exit.playTogether(
                    ObjectAnimator.ofFloat(badge, "translationY", 80f, -200f),
                    ObjectAnimator.ofFloat(badge, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(badge, "scaleX", 1f, 0.5f),
                    ObjectAnimator.ofFloat(badge, "scaleY", 1f, 0.5f)
            );
            exit.setDuration(800);
            exit.setInterpolator(new AccelerateInterpolator());
            exit.start();
        }, 3000);
    }

    // ═══════════════════════════════════════════════════════════
    // ♾️ ANIMACIONES INFINITAS DE MANTENIMIENTO
    // ═══════════════════════════════════════════════════════════

    /**
     * Animaciones sutiles continuas para mantener vida
     */
    private static void startInfiniteAnimations(View rootView) {
        // Ya iniciadas en otras funciones, esta es placeholder
        // para animaciones adicionales de ambiente
    }

    // ═══════════════════════════════════════════════════════════
    // 🛠️ UTILIDADES HELPER
    // ═══════════════════════════════════════════════════════════

    /**
     * Helper para obtener ID de manera segura
     */
    private static int getId(View view, String idName) {
        try {
            return view.getContext().getResources().getIdentifier(
                    idName, "id", view.getContext().getPackageName()
            );
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * PostDelayed simplificado
     */
    private static void postDelayed(Runnable runnable, long delay) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(runnable, delay);
    }

    // ═══════════════════════════════════════════════════════════
    // 🎨 EFECTOS ADICIONALES OPCIONALES
    // ═══════════════════════════════════════════════════════════

    /**
     * Vibración háptica para feedback táctil (requiere permiso)
     */
    public static void triggerHapticFeedback(View view) {
        view.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        );
    }

    /**
     * Animación de hover para botones
     */
    public static void setupButtonHover(View button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    triggerHapticFeedback(v);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }
}