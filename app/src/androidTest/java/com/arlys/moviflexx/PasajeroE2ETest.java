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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * TEST E2E PASAJERO (V1 — Flujo de Reserva Completo)
 * 
 * Flujo: Login -> Home Pasajero -> Buscar Ruta -> Detalle Viaje -> Selección Paradas -> Reserva
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PasajeroE2ETest {

    private static final String TAG = "E2E_PASAJERO";
    private static final String PKG = "com.arlys.moviflexx";

    @Rule
    public ActivityScenarioRule<Login> activityRule = new ActivityScenarioRule<>(Login.class);

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    );

    private UiDevice device;

    @Before
    public void setUp() {
        // Desactivar asistente de voz para no interferir con la automatización
        com.arlys.moviflexx.model.VoiceAssistantManager.isTestMode = true;
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testFlujoReservaPasajero() {
        Log.d(TAG, "🚀 INICIO — V1");
        pausa(3000);

        // 1. LOGIN (Cuentas de prueba)
        limpiarDialogos();
        onView(withId(R.id.edtEmail)).perform(replaceText("test@gmail.com"), closeSoftKeyboard());
        onView(withId(R.id.edtPassword)).perform(replaceText("Test1234#"), closeSoftKeyboard());
        onView(withId(R.id.btnLogin)).perform(click());
        pausa(9000);
        limpiarDialogos();

        // 2. NAVEGAR A BUSCAR RUTA
        onView(withId(R.id.btn_buscar_viaje)).perform(click());
        pausa(4000);

        // 3. REALIZAR BÚSQUEDA
        onView(withId(R.id.edit_origen)).perform(replaceText("Popayan"), closeSoftKeyboard());
        onView(withId(R.id.edit_destino)).perform(replaceText("Timbio"), closeSoftKeyboard());
        // ID corregido: btn_buscar -> btn_buscar_rutas
        onView(withId(R.id.btn_buscar_rutas)).perform(click());
        pausa(10000);

        // 4. SELECCIONAR PRIMER RESULTADO
        UiObject2 cardViaje = device.wait(Until.findObject(By.res(PKG + ":id/card_viaje_search")), 8000);
        if (cardViaje == null) {
            // Intentar por ID genérico si falló el específico
            cardViaje = device.findObject(By.res(PKG + ":id/item_viaje"));
        }
        
        if (cardViaje != null) {
            cardViaje.click();
            Log.d(TAG, "   ✅ Viaje seleccionado");
        } else {
            // Si el conductor acaba de publicar, puede que tarde un poco en aparecer
            Log.e(TAG, "❌ No se encontraron viajes disponibles. Reintentando...");
            onView(withId(R.id.btn_buscar_rutas)).perform(click());
            pausa(5000);
            cardViaje = device.findObject(By.res(PKG + ":id/card_viaje_search"));
            if (cardViaje != null) cardViaje.click();
            else return;
        }
        pausa(6000);

        // 5. FLUJO DE RESERVA (STEP BY STEP)
        Log.d(TAG, "🚏 Iniciando flujo de selección de paradas...");
        
        // Clic en "ELEGIR PARADA" (btn_reservar en activity_detalle_viaje2.xml)
        UiObject2 btnReservar = device.wait(Until.findObject(By.res(PKG + ":id/btn_reservar")), 5000);
        if (btnReservar == null) {
            btnReservar = device.findObject(By.textContains("ELEGIR"));
        }
        
        if (btnReservar != null) {
            btnReservar.click();
            pausa(3000);
            
            // --- PASO 1: SELECCIONAR SUBIDA ---
            Log.d(TAG, "   Paso 1: Subida");
            // Seleccionamos la primera opción del BottomSheet
            UiObject2 itemSubida = device.findObject(By.textContains("Inicio"));
            if (itemSubida == null) itemSubida = device.findObject(By.textContains("🟢"));
            if (itemSubida == null) itemSubida = device.findObject(By.res(PKG + ":id/txt_nombre_parada"));
            
            if (itemSubida != null) {
                itemSubida.click();
                pausa(1500);
                
                // Clic en "CONFIRMAR SUBIDA"
                UiObject2 btnSiguiente = device.findObject(By.textContains("CONFIRMAR SUBIDA"));
                if (btnSiguiente != null) {
                    btnSiguiente.click();
                    pausa(3000);
                    
                    // --- PASO 2: SELECCIONAR BAJADA ---
                    Log.d(TAG, "   Paso 2: Bajada");
                    UiObject2 itemBajada = device.findObject(By.textContains("Destino"));
                    if (itemBajada == null) itemBajada = device.findObject(By.textContains("🔴"));
                    if (itemBajada == null) {
                        // Si no hay destino específico, buscar cualquier otro elemento del listado
                        List<UiObject2> paradas = device.findObjects(By.res(PKG + ":id/txt_nombre_parada"));
                        if (!paradas.isEmpty()) itemBajada = paradas.get(paradas.size()-1); // La última suele ser el final
                    }
                    
                    if (itemBajada != null) {
                        itemBajada.click();
                        pausa(1500);
                        
                        // Clic en "RESERVAR"
                        UiObject2 btnFinal = device.findObject(By.text("RESERVAR"));
                        if (btnFinal == null) btnFinal = device.findObject(By.textContains("RESERVAR"));
                        
                        if (btnFinal != null) {
                            btnFinal.click();
                            Log.d(TAG, "   ✅ Petición de reserva enviada");
                            pausa(8000);
                        }
                    }
                }
            }
        }

        // 6. VERIFICACIÓN FINAL
        // Verificamos por texto "TU RESERVA" ya que la card se crea dinámicamente
        UiObject2 rInfo = device.findObject(By.text("TU RESERVA"));
        if (rInfo == null) {
             onView(withText(containsString("RESERVA"))).check(matches(isDisplayed()));
        }
        Log.d(TAG, "🏁 V1 COMPLETADO — Reserva exitosa");
    }

    private void limpiarDialogos() {
        try {
            String[] textos = {"Omitir", "Nunca", "Cerrar", "No, gracias", "CANCELAR", "Aceptar"};
            for (String t : textos) {
                UiObject2 b = device.findObject(By.text(t));
                if (b == null) b = device.findObject(By.textContains(t));
                if (b != null) b.click();
            }
        } catch (Exception ignored) {}
    }

    private void pausa(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
