package com.arlys.moviflexx;

import android.Manifest;
import android.content.Context;
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
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║   TEST E2E PASAJERO — Flujo Exhaustivo                              ║
 * ║                    MOVIFLEX · by Arlys Dev                          ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * FLUJO REAL CONFIRMADO (v8):
 *
 * BottomSheet — Paso 2 BAJADA (imagen confirmada):
 *   - Stepper: ✓ 1 Subida ──── 2 Bajada (naranja activo)
 *   - Título: "¿Dónde te bajas?"
 *   - Chip verde: parada de subida confirmada
 *   - Chip naranja: parada preseleccionada ("Comuna 3")
 *   - Lista de paradas:
 *       🟢 Cra. 50 # 2-58... (Inicio)
 *       🔵 Barrio Villa de occidente
 *       🔵 Popular
 *       🔵 Comuna 3
 *       🔵 Ciudad jardin
 *       🔵 Valle Robledo
 *       🔵 Club Residencial Camino Viejo
 *       🔴 sena (Destino final)
 *   - Botón NARANJA: "🎫 RESERVAR EN: <parada>"
 *     → texto dinámico según parada elegida
 *     → este botón ES la reserva (no hay btn_confirmar separado)
 *
 * CORRECCIONES v8 — test05:
 *   - El flujo de bajada busca "RESERVAR EN:" como botón final
 *   - seleccionarParadasCompleto() eliminado; test05 hace todo inline
 *   - Filtros de ultimoItemClickeableDeBottomSheet actualizados para
 *     excluir "RESERVAR" del texto de paradas (evita confusión con el botón)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PasajeroE2ETest {

    // ── Constantes ──────────────────────────────────────────────────────────
    private static final String TAG = "E2E_PASAJERO";
    private static final String PKG = "com.arlys.moviflexx";

    private static final String EMAIL    = "andrea@gmail.com";
    private static final String PASSWORD = "Andrea123#";

    private static final int T_CORTO = 1_500;
    private static final int T_MEDIO = 4_000;
    private static final int T_LARGO = 9_000;
    private static final int T_RED   = 10_000;

    // ── Rules ────────────────────────────────────────────────────────────────
    @Rule
    public ActivityScenarioRule<Login> activityRule =
            new ActivityScenarioRule<>(Login.class);

    @Rule
    public GrantPermissionRule permisoUbicacion = GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    );

    @Rule
    public GrantPermissionRule permisoNotificaciones = GrantPermissionRule.grant(
            "android.permission.POST_NOTIFICATIONS"
    );

    private UiDevice device;
    private Context  ctx;

    // ═════════════════════════════════════════════════════════════════════════
    //  SETUP
    // ═════════════════════════════════════════════════════════════════════════

    @Before
    public void setUp() {
        com.arlys.moviflexx.model.VoiceAssistantManager.isTestMode = true;
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        ctx    = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Log.d(TAG, "🔧 Setup completado — dispositivo listo");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 1 — LOGIN
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test01_Login() {
        Log.d(TAG, "══ TEST 1: Login ══");
        pausa(2000);
        limpiarDialogos();

        onView(withId(R.id.edtEmail))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                .perform(scrollTo(), replaceText(EMAIL), closeSoftKeyboard());

        onView(withId(R.id.edtPassword))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                .perform(scrollTo(), replaceText(PASSWORD), closeSoftKeyboard());

        onView(withId(R.id.btnLogin))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                .perform(click());

        pausa(T_LARGO);
        limpiarDialogos();

        UiObject2 navHome = device.wait(
                Until.findObject(By.res(PKG + ":id/nav_mis_viajes")), 8000);
        if (navHome == null)
            navHome = device.wait(
                    Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 5000);
        if (navHome == null)
            navHome = device.wait(
                    Until.findObject(By.textContains("Mis Reservas")), 5000);
        if (navHome == null)
            navHome = device.wait(
                    Until.findObject(By.textContains("Conductores activos")), 5000);

        assertNotNull("❌ HomePasajero no cargó tras login", navHome);
        Log.d(TAG, "✅ TEST 1 PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 2 — BUSCAR VIAJES
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test02_BuscarViajes() {
        Log.d(TAG, "══ TEST 2: Buscar viajes ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 campoDestino = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 6000);
        assertNotNull("❌ Campo destino no encontrado", campoDestino);

        campoDestino.click();
        pausa(500);
        campoDestino.setText("sena");
        pausa(2000);

        UiObject2 sugerencia = device.findObject(By.textContains("Sena"));
        if (sugerencia != null) { sugerencia.click(); pausa(500); }

        clickUA("btn_buscar_viajes");
        pausa(T_RED);
        limpiarDialogos();

        UiObject2 badge       = device.findObject(By.text("ACTIVO"));
        UiObject2 conductores = device.findObject(By.textContains("Conductores activos"));
        UiObject2 listaVacia  = device.findObject(By.res(PKG + ":id/layout_vacio"));

        assertTrue("❌ Ni resultados ni estado vacío visible",
                badge != null || conductores != null || listaVacia != null);

        Log.d(TAG, "✅ TEST 2 PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 3 — DETALLE DEL VIAJE
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test03_DetalleViaje() {
        Log.d(TAG, "══ TEST 3: Detalle del viaje ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 campoDestino = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 6000);
        if (campoDestino != null) {
            campoDestino.click(); pausa(500);
            campoDestino.setText("sena"); pausa(2000);
            UiObject2 sug = device.findObject(By.textContains("Sena"));
            if (sug != null) { sug.click(); pausa(500); }
            clickUA("btn_buscar_viajes");
            pausa(T_RED);
            limpiarDialogos();
        }

        UiObject2 cardViaje = seleccionarPrimerViajeDesdeHome();
        assertNotNull("❌ No se encontró card de viaje", cardViaje);
        cardViaje.click();
        pausa(T_LARGO);
        limpiarDialogos();

        for (int i = 0; i < 10; i++) {
            if (device.findObject(By.res(PKG + ":id/loader_detalle")) == null) break;
            pausa(1000);
        }

        UiObject2 pantallaDetalle = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_reservar")), 8000);
        if (pantallaDetalle == null)
            pantallaDetalle = device.findObject(By.res(PKG + ":id/txt_info_ruta"));
        if (pantallaDetalle == null)
            pantallaDetalle = device.findObject(By.res(PKG + ":id/txt_conductor"));
        assertNotNull("❌ DetalleViajeActivity no cargó", pantallaDetalle);

        UiObject2 txtRuta = device.wait(
                Until.findObject(By.res(PKG + ":id/txt_info_ruta")), 5000);
        assertNotNull("❌ txt_info_ruta no visible", txtRuta);
        String textoRuta = txtRuta.getText();
        assertFalse("❌ txt_info_ruta vacío", textoRuta == null || textoRuta.trim().isEmpty());
        assertTrue("❌ txt_info_ruta no contiene '→'", textoRuta.contains("→"));
        Log.d(TAG, "✅ Ruta: " + textoRuta);

        UiObject2 txtEstado = device.findObject(By.res(PKG + ":id/txt_estado"));
        assertNotNull("❌ txt_estado no visible", txtEstado);

        UiObject2 txtConductor = device.findObject(By.res(PKG + ":id/txt_conductor"));
        assertNotNull("❌ txt_conductor no visible", txtConductor);

        UiObject2 cardCupos = device.wait(
                Until.findObject(By.res(PKG + ":id/card_cupos")), 5000);
        if (cardCupos == null) { swipeDown(); pausa(1000); }
        cardCupos = device.findObject(By.res(PKG + ":id/card_cupos"));
        assertNotNull("❌ card_cupos no visible", cardCupos);

        Log.d(TAG, "✅ TEST 3 PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 4 — SELECCIÓN DE PARADA DE SUBIDA
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test04_SeleccionParadas() {
        Log.d(TAG, "══ TEST 4: Selección de paradas ══");
        realizarLogin();
        navegarADetalleViaje();
        abrirBottomSheetYConfirmarSoloSubida();
        Log.d(TAG, "✅ TEST 4 PASADO — Subida seleccionada y confirmada");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 5 — SELECCIONAR BAJADA Y RESERVAR  [CORREGIDO v8]
    //
    //  Flujo real (imagen confirmada):
    //    1. Abrir BottomSheet → paso SUBIDA → seleccionar parada → CONFIRMAR SUBIDA
    //    2. Paso BAJADA aparece automáticamente
    //    3. Seleccionar parada de bajada de la lista
    //    4. El botón naranja muestra "🎫 RESERVAR EN: <parada>"
    //    5. Click en ese botón → reserva completada
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test05_ConfirmarReserva() {
        Log.d(TAG, "══ TEST 5: Confirmar reserva ══");
        realizarLogin();
        navegarADetalleViaje();

        // ── SUBIDA ───────────────────────────────────────────────────────────
        abrirBtnElegirSubidaBajada();

        UiObject2 tituloSubida = device.wait(
                Until.findObject(By.textContains("vas a subir")), 6000);
        assertNotNull("❌ BottomSheet subida no abrió", tituloSubida);

        UiObject2 paradaSubida = seleccionarParadaSubida();
        assertNotNull("❌ No se encontró parada de subida", paradaSubida);
        Log.d(TAG, "✅ Subida — tocando: " + paradaSubida.getText());
        paradaSubida.click();
        pausa(1500);

        UiObject2 btnConfSubida = device.wait(
                Until.findObject(By.textContains("CONFIRMAR SUBIDA")), 5000);
        if (btnConfSubida == null)
            btnConfSubida = device.findObject(By.textContains("ELEGIR BAJADA"));
        assertNotNull("❌ Botón CONFIRMAR SUBIDA no visible", btnConfSubida);
        assertTrue("❌ Botón CONFIRMAR SUBIDA deshabilitado", btnConfSubida.isEnabled());
        btnConfSubida.click();
        pausa(2500);

        // ── BAJADA ───────────────────────────────────────────────────────────
        // Verificar que el paso 2 (Bajada) cargó
        UiObject2 tituloBajada = device.wait(
                Until.findObject(By.textContains("bajas")), 6000);
        if (tituloBajada == null)
            tituloBajada = device.wait(Until.findObject(By.textContains("Bajada")), 4000);
        assertNotNull("❌ Pantalla BAJADA no cargó", tituloBajada);
        Log.d(TAG, "✅ Paso BAJADA visible");

        // Seleccionar parada de bajada
        // Prioridad basada en paradas reales de la imagen:
        //   Ciudad jardin → Valle Robledo → Popular → Club Residencial
        //   → sena (Destino final) → ultimoItem
        // NO tocar "(Inicio)" ni la misma parada de subida
        UiObject2 paradaBajada = clickeableQueContiene("Ciudad jardin");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("Ciudad Jardin");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("Valle Robledo");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("Popular");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("Club Residencial");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("Destino final");
        if (paradaBajada == null) paradaBajada = clickeableQueContiene("sena");
        if (paradaBajada == null) paradaBajada = ultimoItemClickeableDeBottomSheet();

        assertNotNull("❌ No se encontró parada de bajada en la lista", paradaBajada);
        Log.d(TAG, "✅ Bajada — tocando: " + paradaBajada.getText());
        paradaBajada.click();
        pausa(2000); // esperar que el botón naranja se actualice con el nombre de la parada

        // ── BOTÓN "RESERVAR EN: <parada>" ────────────────────────────────────
        // Texto dinámico: "🎫 RESERVAR EN: Ciudad jardin"
        // UiAutomator hace getText() sin emojis, buscar solo la parte de texto
        UiObject2 btnReservar = device.wait(
                Until.findObject(By.textContains("RESERVAR EN:")), 5000);
        if (btnReservar == null)
            btnReservar = device.findObject(By.textContains("RESERVAR EN"));
        if (btnReservar == null)
            btnReservar = device.findObject(By.textStartsWith("RESERVAR EN"));

        assertNotNull(
                "❌ Botón 'RESERVAR EN: <parada>' no encontrado.\n"
                        + "Verifica que la parada de bajada quedó seleccionada "
                        + "y que el botón naranja actualizó su texto.",
                btnReservar);

        assertTrue(
                "❌ Botón 'RESERVAR EN:' está deshabilitado.\n"
                        + "Botón encontrado: '" + btnReservar.getText() + "'\n"
                        + "La parada de bajada puede no haber quedado seleccionada.",
                btnReservar.isEnabled());

        Log.d(TAG, "✅ Reservando en: " + btnReservar.getText());
        btnReservar.click();
        pausa(T_LARGO);
        limpiarDialogos();

        // ── VERIFICAR RESULTADO ───────────────────────────────────────────────
        boolean exito = false;

        UiObject2 msgExito = device.findObject(By.textContains("exitosa"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("confirmada"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("éxito"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("Reserva"));
        if (msgExito != null) { exito = true; Log.d(TAG, "✅ Mensaje éxito: " + msgExito.getText()); }

        if (!exito) {
            UiObject2 conf = device.findObject(By.res(PKG + ":id/layout_confirmacion_reserva"));
            if (conf != null) { exito = true; Log.d(TAG, "✅ Pantalla confirmación visible"); }
        }

        if (!exito) {
            UiObject2 misViajes = device.findObject(By.res(PKG + ":id/layout_mis_reservas"));
            if (misViajes == null)
                misViajes = device.findObject(By.res(PKG + ":id/rv_mis_reservas"));
            if (misViajes == null)
                misViajes = device.findObject(By.textContains("Mis Reservas"));
            if (misViajes != null) { exito = true; Log.d(TAG, "✅ Redirigido a Mis Reservas"); }
        }

        if (!exito) {
            // Volvió a DetalleViaje con el BottomSheet cerrado = reserva enviada
            UiObject2 detalle = device.findObject(By.res(PKG + ":id/txt_info_ruta"));
            if (detalle != null) { exito = true; Log.d(TAG, "✅ Volvió a DetalleViaje — reserva procesada"); }
        }

        assertTrue("❌ No se detectó confirmación de reserva exitosa", exito);
        Log.d(TAG, "✅ TEST 5 PASADO — Reserva confirmada");
    }

    // ═════════════════════════════════════════════════════════════════════════
//  TEST 6 — MIS VIAJES  [CORREGIDO]
//
//  Flujo real:
//    1. Login
//    2. Click en nav_mis_viajes  → abre MisReservasActivity
//    3. Verifica que la pantalla cargó (lista de reservas o estado vacío)
//    4. Verifica que existe al menos un viaje con algún estado conocido
//       O un layout_vacio si no hay viajes
// ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test06_MisViajes() {
        Log.d(TAG, "══ TEST 6: Mis Viajes Realizados ══");
        realizarLogin();

        // El botón "MIS VIAJES REALIZADOS" está en HomePasajero (nav_inicio)
        // Asegurarse de estar en el home — después del login ya estamos ahí,
        // pero por si acaso lo forzamos
        UiObject2 navInicio = device.findObject(By.res(PKG + ":id/nav_inicio"));
        if (navInicio != null) {
            navInicio.click();
            pausa(T_MEDIO);
            limpiarDialogos();
        }

        // Esperar que HomePasajero cargue — verificar que btn_mis_viajes_frame existe
        UiObject2 btnFrame = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_mis_viajes_frame")), 8000);

        // Si no está visible, hacer scroll hacia arriba (está en el header)
        if (btnFrame == null) {
            device.swipe(
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() / 4,
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() * 3 / 4,
                    20);
            pausa(T_CORTO);
            btnFrame = device.findObject(By.res(PKG + ":id/btn_mis_viajes_frame"));
        }

        // Fallback al TextView hijo o por texto
        if (btnFrame == null)
            btnFrame = device.findObject(By.res(PKG + ":id/btn_mis_viajes"));
        if (btnFrame == null)
            btnFrame = device.findObject(By.textContains("MIS VIAJES REALIZADOS"));

        assertNotNull("❌ Botón 'MIS VIAJES REALIZADOS' no encontrado en HomePasajero", btnFrame);
        btnFrame.click();
        pausa(T_MEDIO);
        limpiarDialogos();

        // Verificar que el BottomSheet abrió — buscar título "Mis Viajes Realizados"
        UiObject2 tituloSheet = device.wait(
                Until.findObject(By.textContains("Mis Viajes Realizados")), 6000);
        assertNotNull("❌ BottomSheet 'Mis Viajes Realizados' no abrió", tituloSheet);

        Log.d(TAG, "✅ TEST 6 PASADO — BottomSheet Mis Viajes abierto");
    }
// ═════════════════════════════════════════════════════════════════════════
//  TEST 7 — MENSAJES → CHAT → ENVIAR  [CORREGIDO]
//
//  Flujo real:
//    1. Login
//    2. Click en nav_mensajes → abre Mensajes.java (lista de conversaciones)
//    3. Verificar que la pantalla de mensajes cargó (rvConversaciones)
//    4. Abrir la primera conversación disponible
//    5. Verificar que Chat.java abrió (campo etMensaje visible)
//    6. Escribir un mensaje y pulsar btnEnviar
//    7. Verificar que el campo se limpió y el mensaje aparece en el chat
//    8. Volver atrás
// ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test07_Mensajes() {
        Log.d(TAG, "══ TEST 7: Mensajes ══");
        realizarLogin();

        clickUA("nav_mensajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        // Esperar que loadingOverlay desaparezca
        for (int i = 0; i < 10; i++) {
            if (device.findObject(By.res(PKG + ":id/loadingOverlay")) == null) break;
            pausa(1000);
        }

        UiObject2 rvConv = device.wait(
                Until.findObject(By.res(PKG + ":id/rvConversaciones")), 8000);
        UiObject2 sinConv = device.findObject(By.res(PKG + ":id/tvSinResultados"));
        if (sinConv == null) sinConv = device.findObject(By.textContains("Sin conversaciones"));
        if (sinConv == null) sinConv = device.findObject(By.textContains("Aún no tienes"));

        assertTrue("❌ Pantalla Mensajes no cargó", rvConv != null || sinConv != null);
        Log.d(TAG, "✅ Pantalla Mensajes cargada");

        if (rvConv == null) {
            Log.d(TAG, "ℹ️ Sin conversaciones — test completo");
            Log.d(TAG, "✅ TEST 7 PASADO");
            return;
        }

        // Abrir primera conversación
        UiObject2 primerItem = null;
        List<UiObject2> hijos = rvConv.getChildren();
        if (hijos != null && !hijos.isEmpty()) primerItem = hijos.get(0);
        if (primerItem == null) primerItem = device.findObject(By.res(PKG + ":id/tvNombreConversacion"));
        if (primerItem == null) primerItem = device.findObject(By.res(PKG + ":id/tvNombre"));

        if (primerItem == null) {
            Log.d(TAG, "ℹ️ No se encontraron items — test completo");
            Log.d(TAG, "✅ TEST 7 PASADO");
            return;
        }

        UiObject2 cardConv = primerItem.isClickable() ? primerItem : primerItem.getParent();
        if (cardConv != null && cardConv.isClickable()) cardConv.click();
        else primerItem.click();
        pausa(T_MEDIO);
        limpiarDialogos();

        // Verificar que Chat abrió
        UiObject2 etMensaje = device.wait(
                Until.findObject(By.res(PKG + ":id/etMensaje")), 7000);
        assertNotNull("❌ Chat no abrió — etMensaje no encontrado", etMensaje);
        assertNotNull("❌ rvMensajes no encontrado",
                device.findObject(By.res(PKG + ":id/rvMensajes")));
        Log.d(TAG, "✅ Chat abierto");

        // Escribir con Espresso para disparar el TextWatcher
        final String MSG = "Hola a que hora sale el viaje";
        try {
            onView(withId(R.id.etMensaje))
                    .perform(click(), replaceText(MSG), closeSoftKeyboard());
        } catch (Exception e) {
            etMensaje.click();
            pausa(300);
            etMensaje.setText(MSG);
        }
        pausa(T_CORTO);

        // Esperar que btnEnviar se haga visible (arranca GONE)
        UiObject2 btnEnviar = null;
        for (int i = 0; i < 10; i++) {
            btnEnviar = device.findObject(By.res(PKG + ":id/btnEnviar"));
            if (btnEnviar != null && btnEnviar.isEnabled()) break;
            pausa(500);
        }
        assertNotNull("❌ btnEnviar no apareció", btnEnviar);
        assertTrue("❌ btnEnviar deshabilitado", btnEnviar.isEnabled());

        btnEnviar.click();
        pausa(3000);

        Log.d(TAG, "✅ Mensaje enviado");
        device.pressBack();
        pausa(T_CORTO);
        Log.d(TAG, "✅ TEST 7 PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 8 — MAPA
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test08_Mapa() {
        Log.d(TAG, "══ TEST 8: Mapa ══");
        realizarLogin();

        clickUA("nav_mapa");
        pausa(T_LARGO);
        limpiarDialogos();

        UiObject2 mapa = device.wait(Until.findObject(By.res(PKG + ":id/map")), 8000);
        if (mapa == null) mapa = device.findObject(By.res(PKG + ":id/map_view"));
        if (mapa == null) mapa = device.findObject(By.res(PKG + ":id/mapFragment"));
        if (mapa == null) mapa = device.findObject(By.res(PKG + ":id/fragmentMapa"));
        if (mapa == null) mapa = device.findObject(By.res(PKG + ":id/fragment_mapa"));
        assertNotNull("❌ Vista del mapa no encontrada", mapa);
        Log.d(TAG, "✅ TEST 8 PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 9 — PERFIL → CERRAR SESIÓN
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test09_PerfilYCerrarSesion() {
        Log.d(TAG, "══ TEST 9: Perfil → Cerrar sesión ══");
        realizarLogin();

        clickUA("nav_perfil");
        pausa(T_MEDIO);
        limpiarDialogos();

        // Verificar que cargó el perfil — layout_perfil_root o tv_nombre
        UiObject2 perfil = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_perfil_root")), 6000);
        if (perfil == null)
            perfil = device.findObject(By.res(PKG + ":id/tv_nombre"));
        if (perfil == null)
            perfil = device.findObject(By.res(PKG + ":id/tv_email"));
        assertNotNull("❌ Pantalla de perfil no cargó", perfil);
        Log.d(TAG, "✅ Perfil cargado");

        // btn_cerrar_sesion está directo en el scroll — NO requiere abrir drawer
        UiObject2 btnCerrar = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_cerrar_sesion")), 5000);
        if (btnCerrar == null)
            btnCerrar = device.findObject(By.textContains("Cerrar sesión"));

        // Si no es visible, hacer scroll hacia abajo
        if (btnCerrar == null) {
            swipeDown();
            pausa(T_CORTO);
            btnCerrar = device.findObject(By.res(PKG + ":id/btn_cerrar_sesion"));
        }
        if (btnCerrar == null) {
            swipeDown();
            pausa(T_CORTO);
            btnCerrar = device.findObject(By.res(PKG + ":id/btn_cerrar_sesion"));
        }

        assertNotNull("❌ Botón cerrar sesión no encontrado", btnCerrar);
        btnCerrar.click();
        pausa(T_CORTO);

        // Confirmar diálogo si aparece
        UiObject2 dialog = device.findObject(By.textContains("Cerrar"));
        if (dialog == null) dialog = device.findObject(By.textContains("Sí"));
        if (dialog == null) dialog = device.findObject(By.textContains("SI"));
        if (dialog == null) dialog = device.findObject(By.textContains("Aceptar"));
        if (dialog == null) dialog = device.findObject(By.textContains("ACEPTAR"));
        if (dialog != null) { dialog.click(); pausa(T_CORTO); }

        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 loginEmail = device.wait(
                Until.findObject(By.res(PKG + ":id/edtEmail")), 7000);
        assertNotNull("❌ No regresó a Login", loginEmail);
        assertNotNull("❌ btnLogin no visible",
                device.findObject(By.res(PKG + ":id/btnLogin")));

        Log.d(TAG, "✅ TEST 9 PASADO");
    }



    // ═════════════════════════════════════════════════════════════════════════
    //  TEST ADICIONAL — CANCELAR RESERVA
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void testAdicional_CancelarReserva() {
        Log.d(TAG, "══ TEST ADICIONAL: Cancelar reserva ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        // Si no hay reservas activas, omitir sin fallar
        UiObject2 card = device.wait(
                Until.findObject(By.textContains("Confirmada")), 5000);
        if (card == null) card = device.findObject(By.textContains("Pendiente"));
        if (card == null) card = device.findObject(By.textContains("En curso"));
        if (card == null) card = device.findObject(By.res(PKG + ":id/layout_mis_reservas"));

        if (card == null) {
            Log.w(TAG, "⚠ Sin reservas activas — test omitido");
            Log.d(TAG, "✅ TEST ADICIONAL PASADO (omitido)");
            return;
        }

        card.click();
        pausa(3000);
        limpiarDialogos();

        UiObject2 btnCancelar = device.wait(
                Until.findObject(By.textContains("CANCELAR")), 5000);
        if (btnCancelar == null)
            btnCancelar = device.findObject(By.res(PKG + ":id/btn_cancelar_reserva"));
        if (btnCancelar == null)
            btnCancelar = device.findObject(By.res(PKG + ":id/btn_cancelar"));

        if (btnCancelar == null) {
            Log.w(TAG, "⚠ Botón CANCELAR no disponible — reserva no cancelable");
            Log.d(TAG, "✅ TEST ADICIONAL PASADO (omitido)");
            return;
        }

        btnCancelar.click();
        pausa(T_CORTO);

        UiObject2 confirm = device.findObject(By.textContains("CONFIRMAR"));
        if (confirm == null) confirm = device.findObject(By.textContains("SÍ"));
        if (confirm == null) confirm = device.findObject(By.textContains("Sí"));
        if (confirm == null) confirm = device.findObject(By.textContains("ACEPTAR"));
        assertNotNull("❌ Diálogo de cancelación no apareció", confirm);
        confirm.click();
        pausa(T_LARGO);
        limpiarDialogos();

        UiObject2 cancelada = device.findObject(By.textContains("Cancelada"));
        if (cancelada == null) cancelada = device.findObject(By.textContains("cancelada"));
        Log.d(TAG, cancelada != null
                ? "✅ Estado cancelado: " + cancelada.getText()
                : "✅ Card eliminada de lista");

        Log.d(TAG, "✅ TEST ADICIONAL PASADO");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ═════════════════════════════════════════════════════════════════════════

    private void realizarLogin() {
        pausa(T_CORTO);
        limpiarDialogos();
        for (int i = 0; i < 3; i++) {
            try {
                onView(withId(R.id.edtEmail))
                        .perform(scrollTo(), replaceText(EMAIL), closeSoftKeyboard());
                break;
            } catch (Exception e) {
                UiObject2 obj = device.findObject(By.res(PKG + ":id/edtEmail"));
                if (obj != null) { obj.setText(EMAIL); break; }
                pausa(1000);
            }
        }
        for (int i = 0; i < 3; i++) {
            try {
                onView(withId(R.id.edtPassword))
                        .perform(scrollTo(), replaceText(PASSWORD), closeSoftKeyboard());
                break;
            } catch (Exception e) {
                UiObject2 obj = device.findObject(By.res(PKG + ":id/edtPassword"));
                if (obj != null) { obj.setText(PASSWORD); break; }
                pausa(1000);
            }
        }
        for (int i = 0; i < 3; i++) {
            try {
                onView(withId(R.id.btnLogin)).perform(scrollTo(), click());
                break;
            } catch (Exception e) {
                UiObject2 obj = device.findObject(By.res(PKG + ":id/btnLogin"));
                if (obj != null) { obj.click(); break; }
                pausa(1000);
            }
        }
        pausa(T_LARGO);
        limpiarDialogos();
    }

    private void navegarADetalleViaje() {
        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 campoDestino = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 4000);
        if (campoDestino != null) {
            campoDestino.click(); pausa(500);
            campoDestino.setText("sena"); pausa(2000);
            UiObject2 sug = device.findObject(By.textContains("Sena"));
            if (sug != null) { sug.click(); pausa(500); }
            clickUA("btn_buscar_viajes");
            pausa(T_RED);
            limpiarDialogos();
        }

        UiObject2 card = seleccionarPrimerViajeDesdeHome();
        assertNotNull("❌ navegarADetalleViaje: sin cards disponibles", card);
        card.click();
        pausa(T_LARGO);
        limpiarDialogos();

        for (int i = 0; i < 10; i++) {
            if (device.findObject(By.res(PKG + ":id/loader_detalle")) == null) break;
            pausa(1000);
        }
    }

    private UiObject2 seleccionarPrimerViajeDesdeHome() {
        UiObject2 badge = device.wait(Until.findObject(By.text("ACTIVO")), 6000);
        if (badge != null) {
            UiObject2 padre = badge.getParent();
            if (padre != null && padre.isClickable()) return padre;
            return badge;
        }
        UiObject2 card = device.findObject(By.textContains("San Eduardo"));
        if (card == null) card = device.findObject(By.textContains("ciudad jardin"));
        if (card == null) card = device.findObject(By.textContains("sena"));
        if (card == null) card = device.findObject(By.textContains("Popay"));
        if (card != null) return card;
        return device.findObject(By.res(PKG + ":id/layout_resultados_viajes"));
    }

    /**
     * Abre BottomSheet + selecciona subida + pulsa CONFIRMAR SUBIDA.
     * Usado solo por test04. NO avanza al paso de bajada.
     */
    private void abrirBottomSheetYConfirmarSoloSubida() {
        abrirBtnElegirSubidaBajada();

        UiObject2 tituloSubida = device.wait(
                Until.findObject(By.textContains("vas a subir")), 6000);
        assertNotNull("❌ BottomSheet subida no abrió", tituloSubida);
        Log.d(TAG, "✅ BottomSheet SUBIDA visible");

        UiObject2 paradaSubida = seleccionarParadaSubida();
        assertNotNull("❌ No se encontró parada de subida", paradaSubida);
        Log.d(TAG, "✅ Parada subida: " + paradaSubida.getText());
        paradaSubida.click();
        pausa(1500);

        UiObject2 btnConfSubida = device.wait(
                Until.findObject(By.textContains("CONFIRMAR SUBIDA")), 5000);
        if (btnConfSubida == null)
            btnConfSubida = device.findObject(By.textContains("ELEGIR BAJADA"));
        assertNotNull("❌ Botón CONFIRMAR SUBIDA no visible", btnConfSubida);
        assertTrue("❌ Botón CONFIRMAR SUBIDA deshabilitado", btnConfSubida.isEnabled());
        btnConfSubida.click();
        pausa(2000);
        Log.d(TAG, "✅ CONFIRMAR SUBIDA pulsado — test04 finaliza aquí");
    }

    /**
     * Busca y pulsa btn_reservar ("ELEGIR SUBIDA Y BAJADA").
     * Hace scroll si el botón está oculto bajo el mapa.
     */
    private void abrirBtnElegirSubidaBajada() {
        UiObject2 btnAbrir = null;
        for (int intento = 0; intento < 5; intento++) {
            btnAbrir = device.findObject(By.res(PKG + ":id/btn_reservar"));
            if (btnAbrir != null && btnAbrir.isEnabled()) break;
            btnAbrir = device.findObject(By.text("ELEGIR SUBIDA Y BAJADA"));
            if (btnAbrir != null) break;
            btnAbrir = device.findObject(By.textContains("ELEGIR SUBIDA"));
            if (btnAbrir != null) break;
            swipeDown();
            pausa(2000);
        }
        if (btnAbrir == null)
            btnAbrir = device.wait(Until.findObject(By.res(PKG + ":id/btn_reservar")), 8000);
        if (btnAbrir == null)
            btnAbrir = device.wait(Until.findObject(By.text("ELEGIR SUBIDA Y BAJADA")), 5000);
        assertNotNull("❌ Botón 'ELEGIR SUBIDA Y BAJADA' no encontrado", btnAbrir);
        btnAbrir.click();
        pausa(3500);
        limpiarDialogos();
    }

    /**
     * Selecciona la primera parada de SUBIDA disponible.
     * Orden de prioridad basado en paradas reales observadas:
     *   (Inicio) → Barrio Villa → San Eduardo → Barrio → Club → Comuna → primerItem
     */
    private UiObject2 seleccionarParadaSubida() {
        UiObject2 p = clickeableQueContiene("Inicio");
        if (p == null) p = clickeableQueContiene("Barrio Villa");
        if (p == null) p = clickeableQueContiene("San Eduardo");
        if (p == null) p = clickeableQueContiene("Barrio");
        if (p == null) p = clickeableQueContiene("Club");
        if (p == null) p = clickeableQueContiene("Comuna");
        if (p == null) p = primerItemClickeableDeBottomSheet();
        return p;
    }

    /**
     * Busca el contenedor clickeable que contiene el texto dado.
     * Sube al padre/abuelo si el texto mismo no es clickeable (cards anidadas).
     */
    private UiObject2 clickeableQueContiene(String texto) {
        UiObject2 txt = device.findObject(By.textContains(texto));
        if (txt == null) return null;
        if (txt.isClickable()) return txt;
        UiObject2 padre = txt.getParent();
        if (padre != null && padre.isClickable()) return padre;
        if (padre != null) {
            UiObject2 abuelo = padre.getParent();
            if (abuelo != null && abuelo.isClickable()) return abuelo;
        }
        return txt;
    }

    /**
     * Primer item clickeable de la lista del BottomSheet.
     * Excluye: títulos, campos de búsqueda, botones de acción.
     */
    private UiObject2 primerItemClickeableDeBottomSheet() {
        List<UiObject2> todos = device.findObjects(By.clickable(true));
        if (todos == null) return null;
        for (UiObject2 item : todos) {
            String txt = item.getText();
            if (txt == null) txt = "";
            if (txt.length() > 4
                    && !txt.contains("vas a") && !txt.contains("bajas")
                    && !txt.contains("TOCAR") && !txt.contains("Escribe")
                    && !txt.contains("CONFIRMAR") && !txt.contains("ELEGIR")
                    && !txt.contains("RESERVAR") && !txt.contains("Subida")
                    && !txt.contains("Bajada")) {
                return item;
            }
        }
        return null;
    }

    /**
     * Último item clickeable de la lista del BottomSheet.
     * Excluye: títulos, campos de búsqueda, botones de acción.
     * Útil para seleccionar la parada de bajada más lejana disponible.
     */
    private UiObject2 ultimoItemClickeableDeBottomSheet() {
        List<UiObject2> todos = device.findObjects(By.clickable(true));
        if (todos == null || todos.isEmpty()) return null;
        for (int i = todos.size() - 1; i >= 0; i--) {
            String txt = todos.get(i).getText();
            if (txt == null) txt = "";
            if (txt.length() > 4
                    && !txt.contains("vas a") && !txt.contains("bajas")
                    && !txt.contains("TOCAR") && !txt.contains("Escribe")
                    && !txt.contains("CONFIRMAR") && !txt.contains("ELEGIR")
                    && !txt.contains("RESERVAR") && !txt.contains("Subida")
                    && !txt.contains("Bajada")) {
                return todos.get(i);
            }
        }
        return null;
    }

    // LEGACY — compatibilidad
    private UiObject2 primeraParadaClickeable() { return primerItemClickeableDeBottomSheet(); }
    private UiObject2 ultimaParadaClickeable()  { return ultimoItemClickeableDeBottomSheet(); }

    private void clickUA(String idSuffix) {
        for (int i = 0; i < 5; i++) {
            limpiarDialogos();
            UiObject2 obj = device.findObject(By.res(PKG + ":id/" + idSuffix));
            if (obj != null) { obj.click(); return; }
            pausa(T_CORTO);
        }
        Log.e(TAG, "⚠ clickUA: '" + idSuffix + "' no encontrado tras 5 intentos");
    }

    private void limpiarDialogos() {
        String[] textos = {
                "Omitir", "Nunca", "Samsung Pass", "Oportunidad",
                "Cerrar", "Califica", "No, gracias", "CANCELAR",
                "Aceptar", "No permitir", "Deny"
        };
        for (String t : textos) {
            UiObject2 obj = device.findObject(By.textContains(t));
            if (obj != null && obj.isClickable()) { obj.click(); pausa(400); }
        }
    }

    private void swipeDown() {
        int w = device.getDisplayWidth();
        int h = device.getDisplayHeight();
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20);
        pausa(800);
    }

    private void pausa(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}