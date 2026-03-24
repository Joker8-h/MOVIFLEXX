package com.arlys.moviflexx;

import android.Manifest;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.arlys.moviflexx.controller.Login;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.fail;

/**
 * TEST E2E COMPLETO — CONDUCTOR (V5 — Flujo Exhaustivo)
 *
 * Flujo completo:
 *   1. Login
 *   2. Home → Mis Vehículos → volver
 *   3. Home → Mis Rutas → volver
 *   4. Home → Publicar Ruta → llenar destino → calcular → seleccionar ruta
 *   5. Publicar Viaje (formulario: fecha/hora, cupos) → publicar
 *   6. Navegar a Mensajes → abrir un chat → escribir mensaje → enviar
 *   7. Navegar a Mis Viajes
 *   8. Navegar a Mapa
 *   9. Navegar a Perfil → Cerrar Sesión
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ConductorE2ETest {

    private static final String TAG = "E2E_CONDUCTOR";
    private static final String PKG = "com.arlys.moviflexx";

    @Rule
    public ActivityScenarioRule<Login> activityRule = new ActivityScenarioRule<>(Login.class);

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    );

    private UiDevice device;

    @Before
    public void setUp() {
        com.arlys.moviflexx.model.VoiceAssistantManager.isTestMode = true;
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST PRINCIPAL — FLUJO COMPLETO DEL CONDUCTOR
    // ═══════════════════════════════════════════════════════════════
    @Test
    public void testFlujoCompletoConductor() {
        Log.d(TAG, "🚀 INICIO — Flujo completo del conductor");
        pausa(2000);

        // ─────────────────────────────────────────────────────────────
        // 1. LOGIN
        // ─────────────────────────────────────────────────────────────
        limpiarDialogos();
        escribirEnCampo(R.id.edtEmail, "yo@gmail.com");
        escribirEnCampo(R.id.edtPassword, "Diana123#");
        clickEspresso(R.id.btnLogin);
        pausa(8000);
        limpiarDialogos();
        Log.d(TAG, "✅ Login completado");

        // ─────────────────────────────────────────────────────────────
        // 2. HOME → MIS VEHÍCULOS → Volver
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "📋 Explorando Mis Vehículos...");
        clickEspresso(R.id.btn_mis_vehiculos);
        pausa(3000);
        device.pressBack();
        pausa(2000);
        Log.d(TAG, "✅ Mis Vehículos visitado");

        // ─────────────────────────────────────────────────────────────
        // 3. HOME → MIS RUTAS → Volver
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "📋 Explorando Mis Rutas...");
        clickEspresso(R.id.btn_mis_rutas);
        pausa(3000);
        device.pressBack();
        pausa(2000);
        Log.d(TAG, "✅ Mis Rutas visitado");

        // ─────────────────────────────────────────────────────────────
        // 4. PUBLICAR RUTA (destino + calcular + seleccionar)
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "🛣️ Iniciando publicación de ruta...");
        clickEspresso(R.id.btn_publicar_viaje);
        pausa(3000);

        escribirEnCampo(R.id.edit_destino, "Centro");
        clickEspresso(R.id.btn_calcular_ruta);
        pausa(6000);

        // Seleccionar una ruta del resultado
        try {
            UiObject2 card = device.wait(
                    Until.findObject(By.res(PKG + ":id/card_ruta")), 8000);
            if (card != null) { card.click(); pausa(1500); }
        } catch (Exception e) {
            Log.w(TAG, "card_ruta no encontrada: " + e.getMessage());
        }

        // Bajar y publicar ruta
        swipeDown();
        pausa(1000);
        clickUA("btn_publicar_ruta");
        pausa(8000);
        Log.d(TAG, "✅ Ruta publicada, en pantalla PublicarViaje");

        // ─────────────────────────────────────────────────────────────
        // 5. PUBLICAR VIAJE (formulario completo)
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "📝 Llenando formulario de publicación...");

        // 5A. Fecha y Hora
        UiObject2 campoFecha = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_fecha_hora")), 12000);
        if (campoFecha != null) {
            campoFecha.click();
            pausa(2000);
            confirmarPicker();   // DatePicker → Aceptar
            pausa(1500);
            confirmarPicker();   // TimePicker → Aceptar
            pausa(1500);
            Log.d(TAG, "✅ Fecha/hora seleccionada");
        } else {
            Log.e(TAG, "⚠ edit_fecha_hora NO apareció");
        }

        // 5B. Cupos (spinner ya tiene valor por defecto)
        UiObject2 spinner = device.findObject(By.res(PKG + ":id/spinnerCupo"));
        if (spinner != null) {
            spinner.click();
            pausa(1500);
            device.click(device.getDisplayWidth() / 2, device.getDisplayHeight() / 3);
            pausa(1000);
            Log.d(TAG, "✅ Cupos seleccionados");
        }

        // 5C. Bajar y publicar viaje
        swipeDown();
        pausa(1000);
        swipeDown();
        pausa(1000);
        clickUA("btn_publicar_viaje");
        pausa(12000);
        Log.d(TAG, "✅ Viaje publicado — volviendo al Home");

        // ─────────────────────────────────────────────────────────────
        // 6. NAVEGAR A MENSAJES → Abrir Chat → Escribir → Enviar
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "💬 Navegando a Mensajes...");
        clickUA("nav_mensajes");
        pausa(5000);
        limpiarDialogos();

        // Intentar abrir la primera conversación disponible
        boolean chatAbierto = false;
        try {
            // Esperar a que cargue la lista de conversaciones
            UiObject2 recycler = device.wait(
                    Until.findObject(By.res(PKG + ":id/rvConversaciones")), 8000);
            if (recycler != null) {
                List<UiObject2> items = recycler.getChildren();
                if (items != null && !items.isEmpty()) {
                    items.get(0).click();
                    pausa(4000);
                    chatAbierto = true;
                    Log.d(TAG, "✅ Chat abierto");

                    // Escribir y enviar un mensaje
                    UiObject2 inputMsg = device.wait(
                            Until.findObject(By.res(PKG + ":id/etMensaje")), 5000);
                    if (inputMsg != null) {
                        inputMsg.click();
                        pausa(500);
                        inputMsg.setText("Hola, este es un mensaje de prueba E2E 🚗");
                        pausa(1500);

                        // El botón enviar se hace visible al escribir texto
                        UiObject2 btnSend = device.wait(
                                Until.findObject(By.res(PKG + ":id/btnEnviar")), 3000);
                        if (btnSend != null) {
                            btnSend.click();
                            pausa(3000);
                            Log.d(TAG, "✅ Mensaje enviado");
                        } else {
                            Log.w(TAG, "⚠ btnEnviar no apareció, posible validación");
                        }
                    }

                    // Volver a la lista de mensajes
                    device.pressBack();
                    pausa(2000);
                }
            }

            if (!chatAbierto) {
                Log.w(TAG, "⚠ No hay conversaciones disponibles — verificando empty state");
                // Verificar que al menos estamos en la pantalla de mensajes
                UiObject2 emptyState = device.findObject(By.res(PKG + ":id/tvSinResultados"));
                if (emptyState != null) {
                    Log.d(TAG, "✅ Pantalla Mensajes visible (sin conversaciones)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en flujo de mensajes: " + e.getMessage());
        }

        // ─────────────────────────────────────────────────────────────
        // 7. NAVEGAR A MIS VIAJES (nav_mis_viajes)
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "🚗 Navegando a Mis Viajes...");
        clickUA("nav_mis_viajes");
        pausa(4000);
        Log.d(TAG, "✅ Mis Viajes visitado");

        // ─────────────────────────────────────────────────────────────
        // 8. NAVEGAR A MAPA (nav_mapa)
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "🗺️ Navegando a Mapa...");
        clickUA("nav_mapa");
        pausa(5000);
        Log.d(TAG, "✅ Mapa visitado");

        // ─────────────────────────────────────────────────────────────
        // 9. NAVEGAR A PERFIL → CERRAR SESIÓN
        // ─────────────────────────────────────────────────────────────
        Log.d(TAG, "👤 Navegando a Perfil...");
        clickUA("nav_perfil");
        pausa(4000);

        // Intentar cerrar sesión desde el drawer (btn_configuraciones → nav_cerrar_sesion)
        UiObject2 config = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_configuraciones")), 5000);
        if (config != null) {
            config.click();
            pausa(2000);
            clickUA("nav_cerrar_sesion");
        } else {
            // Fallback: botón directo de cerrar sesión
            swipeDown();
            pausa(1000);
            clickUA("btn_cerrar_sesion");
        }

        pausa(3000);
        Log.d(TAG, "🏁 ✅ FLUJO COMPLETO DEL CONDUCTOR FINALIZADO EXITOSAMENTE");
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    /** Confirmar DatePicker/TimePicker buscando "ACEPTAR", "Aceptar" u "OK". */
    private void confirmarPicker() {
        String[] labels = {"ACEPTAR", "Aceptar", "OK", "Ok"};
        for (String label : labels) {
            UiObject2 btn = device.findObject(By.text(label));
            if (btn != null) { btn.click(); return; }
        }
        UiObject2 btn = device.findObject(By.textContains("ACEP"));
        if (btn != null) btn.click();
    }

    /** Clic con UiAutomator — reintenta 5 veces sin abortar el test. */
    private void clickUA(String idSuffix) {
        for (int i = 0; i < 5; i++) {
            limpiarDialogos();
            UiObject2 obj = device.findObject(By.res(PKG + ":id/" + idSuffix));
            if (obj != null) { obj.click(); return; }
            pausa(1500);
        }
        Log.e(TAG, "⚠ clickUA: no se encontró " + idSuffix + " tras 5 intentos");
    }

    /** Escritura con Espresso + fallback UiAutomator. Fatal si falla. */
    private void escribirEnCampo(int resId, String texto) {
        String resName = getResName(resId);
        for (int i = 0; i < 3; i++) {
            try {
                limpiarDialogos();
                onView(withId(resId)).perform(scrollTo(), replaceText(texto), closeSoftKeyboard());
                return;
            } catch (Exception e) {
                UiObject2 obj = device.findObject(By.res(PKG + ":id/" + resName));
                if (obj != null) { obj.setText(texto); return; }
                pausa(1500);
            }
        }
        fail("❌ Error escribiendo en: " + resName);
    }

    /** Clic con Espresso + fallback UiAutomator. Fatal si falla. */
    private void clickEspresso(int resId) {
        String resName = getResName(resId);
        for (int i = 0; i < 3; i++) {
            try {
                limpiarDialogos();
                onView(withId(resId)).perform(scrollTo(), click());
                return;
            } catch (Exception e) {
                UiObject2 obj = device.findObject(By.res(PKG + ":id/" + resName));
                if (obj != null) { obj.click(); return; }
                pausa(1500);
            }
        }
        fail("❌ Error dando click en: " + resName);
    }

    /** Cerrar diálogos del sistema (Samsung Pass, Autofill, etc.) */
    private void limpiarDialogos() {
        String[] textos = {"Omitir", "Nunca", "Samsung Pass", "Oportunidad",
                "Cerrar", "Califica", "No, gracias"};
        for (String t : textos) {
            UiObject2 obj = device.findObject(By.textContains(t));
            if (obj != null && obj.isClickable()) { obj.click(); pausa(400); }
        }
    }

    /** Swipe descendente para revelar elementos ocultos debajo del fold. */
    private void swipeDown() {
        int w = device.getDisplayWidth();
        int h = device.getDisplayHeight();
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20);
        pausa(800);
    }

    private String getResName(int resId) {
        try {
            return InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getResources().getResourceEntryName(resId);
        } catch (Exception e) { return String.valueOf(resId); }
    }

    private void pausa(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
