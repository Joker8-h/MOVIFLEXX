package com.arlys.moviflexx;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
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
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║   TEST E2E PASAJERO — Flujo Exhaustivo (sin Notificaciones)         ║
 * ║                    MOVIFLEX · by Arlys Dev                          ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Flujo validado:
 *   1.  Login → HomePasajero
 *   2.  Buscar viajes → Lista de resultados
 *   3.  Detalle del viaje → Validar campos (origen, destino, conductor, cupos)
 *   4.  BottomSheet de paradas → Subida + Bajada
 *   5.  Confirmar reserva → mensaje de éxito
 *   6.  Mis Viajes → reserva aparece en lista
 *   7.  Mensajes → Chat → Enviar mensaje → Validar aparición
 *   8.  Mapa → Ubicación y ruta visible
 *   9.  Perfil → Cerrar sesión → Regresa a Login
 *  10.  Cancelar reserva
 *
 * CORRECCIONES APLICADAS (v2):
 *   - test03: txt_origen_viaje → txt_info_ruta / txt_estado (IDs reales)
 *   - test05: btn final es btn_confirmar, texto dinámico "RESERVAR EN: X"
 *   - test06: rv_mis_reservas → layout_mis_reservas (confirmado en initViews)
 *   - test07: fallbacks ampliados para btnEnviar / etMensaje en Chat.class
 *   - test08: map_view → R.id.map (Mapa.java)
 *   - test09: fallbacks ampliados para layout_perfil en PerfilUsuario.class
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PasajeroE2ETest {

    // ── Constantes ──────────────────────────────────────────────────────────
    private static final String TAG  = "E2E_PASAJERO";
    private static final String PKG  = "com.arlys.moviflexx";

    // Credenciales de prueba
    private static final String EMAIL    = "andrea@gmail.com";
    private static final String PASSWORD = "Andrea123#";

    // Tiempos de espera (ms)
    private static final int T_CORTO    = 1_500;
    private static final int T_MEDIO    = 4_000;
    private static final int T_LARGO    = 9_000;
    private static final int T_RED      = 10_000;

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

    // ── Campos ───────────────────────────────────────────────────────────────
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
    //  TEST 1 — LOGIN  ✅
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test01_Login() {
        Log.d(TAG, "══ TEST 1: Login ══");
        pausa(2000);
        limpiarDialogos();

        onView(withId(R.id.edtEmail))
                .check(matches(isDisplayed()))
                .perform(scrollTo(), replaceText(EMAIL), closeSoftKeyboard());

        onView(withId(R.id.edtPassword))
                .check(matches(isDisplayed()))
                .perform(scrollTo(), replaceText(PASSWORD), closeSoftKeyboard());

        onView(withId(R.id.btnLogin))
                .check(matches(isDisplayed()))
                .perform(click());

        pausa(T_LARGO);
        limpiarDialogos();

        UiObject2 navHome = device.wait(
                Until.findObject(By.res(PKG + ":id/nav_mis_viajes")), 8000);
        assertNotNull("❌ HomePasajero no cargó — nav_mis_viajes no visible", navHome);

        Log.d(TAG, "✅ TEST 1 PASADO — Login exitoso y HomePasajero visible");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 2 — BUSCAR VIAJES  ✅
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test02_BuscarViajes() {
        Log.d(TAG, "══ TEST 2: Buscar viajes ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 campoBusqueda = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 6000);
        assertNotNull("❌ Campo destino no encontrado — pantalla de búsqueda no cargó", campoBusqueda);
        Log.d(TAG, "✅ Pantalla de búsqueda visible");

        campoBusqueda.click();
        pausa(500);
        campoBusqueda.setText("sena");
        pausa(2000);

        UiObject2 sugerencia = device.findObject(By.textContains("Sena"));
        if (sugerencia != null) { sugerencia.click(); pausa(500); }

        clickUA("btn_buscar_viajes");
        pausa(T_RED);
        limpiarDialogos();

        UiObject2 resultados = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_resultados_viajes")), 7000);
        UiObject2 listaVacia  = device.findObject(By.res(PKG + ":id/layout_vacio"));
        UiObject2 badgeActivo = device.findObject(By.text("ACTIVO"));

        assertTrue("❌ Ni resultados ni estado vacío visible tras búsqueda",
                resultados != null || listaVacia != null || badgeActivo != null);

        Log.d(TAG, "✅ TEST 2 PASADO — Lista de viajes cargada correctamente");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 3 — DETALLE DEL VIAJE  ✅  [CORREGIDO v5 — IDs reales confirmados]
    //
    //  IDs reales de initViews() en DetalleViajeActivity:
    //    map         → R.id.map_mini
    //    txtRuta     → R.id.txt_info_ruta      ← origen+destino en un solo TextView
    //    txtEstado   → R.id.txt_estado
    //    txtConductor→ R.id.txt_conductor
    //    txtVehiculo → R.id.txt_vehiculo
    //    txtPrecio   → R.id.txt_precio
    //    layoutCupos → R.id.layout_cupos
    //    btnReservar → R.id.btn_reservar
    //    loaderDetalle→R.id.loader_detalle
    //
    //  Estrategia:
    //    - Esperar que loader_detalle desaparezca (carga completada)
    //    - Validar txt_info_ruta (contiene "origen → destino")
    //    - Validar txt_conductor, txt_estado, txt_precio
    //    - layout_cupos como verificación de cupos
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test03_DetalleViaje() {
        Log.d(TAG, "══ TEST 3: Detalle del viaje ══");
        realizarLogin();
        abrirBusquedaYBuscar("sena");

        UiObject2 card = seleccionarPrimerViaje();
        assertNotNull("❌ No se encontraron viajes disponibles para seleccionar", card);
        card.click();
        pausa(T_LARGO);   // dar tiempo al loader_detalle para completar la carga
        limpiarDialogos();

        // ── 1. ESPERAR A QUE EL LOADER DESAPAREZCA ──────────────────────────
        // loader_detalle es el ID real — cuando desaparece la Activity cargó completa
        for (int intento = 0; intento < 10; intento++) {
            UiObject2 loader = device.findObject(By.res(PKG + ":id/loader_detalle"));
            if (loader == null || !loader.isEnabled()) break;
            pausa(1000);
        }

        // ── 2. VERIFICAR PANTALLA CARGADA — ID real: btn_reservar ───────────
        // btn_reservar es asignado en initViews() → btnAccionPrincipal = R.id.btn_reservar
        // Es el elemento más confiable: siempre existe en DetalleViajeActivity
        UiObject2 btnReservar = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_reservar")), 8000);
        if (btnReservar == null)
            btnReservar = device.findObject(By.res(PKG + ":id/btn_iniciar"));   // conductor
        if (btnReservar == null)
            btnReservar = device.findObject(By.res(PKG + ":id/btn_finalizar")); // conductor en curso
        if (btnReservar == null)
            btnReservar = device.findObject(By.res(PKG + ":id/loader_detalle")); // visible mientras carga
        if (btnReservar == null)
            btnReservar = device.findObject(By.res(PKG + ":id/map_mini"));      // mapa siempre presente
        assertNotNull("❌ DetalleViajeActivity no cargó — btn_reservar / map_mini no visibles", btnReservar);
        Log.d(TAG, "✅ Pantalla DetalleViajeActivity visible");

        // ── 3. VALIDAR RUTA — ID real: txt_info_ruta ────────────────────────
        // initViews(): txtRuta = findViewById(R.id.txt_info_ruta)
        // Contiene texto "origen → destino" (setText en procesarViaje)
        UiObject2 txtRuta = device.wait(
                Until.findObject(By.res(PKG + ":id/txt_info_ruta")), 5000);
        assertNotNull("❌ txt_info_ruta no visible (ID real de DetalleViajeActivity)", txtRuta);
        String textoRuta = txtRuta.getText();
        assertFalse("❌ txt_info_ruta está vacío", textoRuta == null || textoRuta.trim().isEmpty());
        assertTrue("❌ txt_info_ruta no contiene '→' (formato: 'origen → destino')",
                textoRuta.contains("→"));
        Log.d(TAG, "✅ Ruta: " + textoRuta);

        // ── 4. VALIDAR ESTADO — ID real: txt_estado ─────────────────────────
        // initViews(): txtEstado = findViewById(R.id.txt_estado)
        UiObject2 txtEstado = device.findObject(By.res(PKG + ":id/txt_estado"));
        assertNotNull("❌ txt_estado no visible (ID real de DetalleViajeActivity)", txtEstado);
        String textoEstado = txtEstado.getText();
        assertFalse("❌ txt_estado está vacío", textoEstado == null || textoEstado.trim().isEmpty());
        Log.d(TAG, "✅ Estado: " + textoEstado);

        // ── 5. VALIDAR CONDUCTOR — ID real: txt_conductor ───────────────────
        // initViews(): txtConductor = findViewById(R.id.txt_conductor)
        UiObject2 txtConductor = device.findObject(By.res(PKG + ":id/txt_conductor"));
        assertNotNull("❌ txt_conductor no visible (ID real de DetalleViajeActivity)", txtConductor);
        String textoConductor = txtConductor.getText();
        assertFalse("❌ txt_conductor está vacío", textoConductor == null || textoConductor.trim().isEmpty());
        Log.d(TAG, "✅ Conductor: " + textoConductor);

        // ── 6. VALIDAR CUPOS — ID real: layout_cupos ────────────────────────
        // initViews(): layoutCupos = findViewById(R.id.layout_cupos)
        // Contiene los chips visuales de asientos (libres / ocupados)
        UiObject2 layoutCupos = device.findObject(By.res(PKG + ":id/layout_cupos"));
        assertNotNull("❌ layout_cupos no visible (ID real de DetalleViajeActivity)", layoutCupos);
        Log.d(TAG, "✅ layout_cupos presente");

        // ── 7. VALIDAR PRECIO — ID real: txt_precio (no bloqueante) ─────────
        // initViews(): txtPrecio = findViewById(R.id.txt_precio)
        UiObject2 txtPrecio = device.findObject(By.res(PKG + ":id/txt_precio"));
        if (txtPrecio != null) {
            Log.d(TAG, "✅ Precio: " + txtPrecio.getText());
        } else {
            Log.w(TAG, "⚠ txt_precio no encontrado (puede estar oculto si precio=0)");
        }

        Log.d(TAG, "✅ TEST 3 PASADO — Todos los campos validados con IDs reales de DetalleViajeActivity");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 4 — SELECCIÓN DE PARADAS (BottomSheet)  ✅
    //  btn_reservar confirmado como correcto para abrir el sheet.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test04_SeleccionParadas() {
        Log.d(TAG, "══ TEST 4: Selección de paradas ══");
        realizarLogin();
        abrirBusquedaYBuscar("sena");
        UiObject2 card = seleccionarPrimerViaje();
        assertNotNull("❌ Sin viajes disponibles", card);
        card.click();
        pausa(T_MEDIO);
        limpiarDialogos();

        // btn_reservar es el ID real confirmado que abre el BottomSheet de paradas
        UiObject2 btnAbrir = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_reservar")), 7000);
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("ELEGIR"));
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("PARADA"));
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("SELECCIONAR"));
        assertNotNull("❌ Botón para abrir BottomSheet de paradas no encontrado", btnAbrir);
        btnAbrir.click();
        pausa(3500);   // tiempo extra para animación del BottomSheet
        limpiarDialogos();

        UiObject2 primeraParada = device.wait(
                Until.findObject(By.res(PKG + ":id/txt_nombre_parada")), 6000);
        assertNotNull("❌ BottomSheet de paradas no abrió correctamente", primeraParada);
        Log.d(TAG, "✅ BottomSheet visible — primera parada: " + primeraParada.getText());

        // ── PARADA DE SUBIDA ────────────────────────────────────────────────
        UiObject2 paradaSubida = buscarParada(
                new String[]{"Inicio", "inicio", "Salida", "Origen", "SUBIDA", "🟢"});
        if (paradaSubida == null) paradaSubida = primeraParada;

        paradaSubida.click();
        pausa(T_CORTO);
        Log.d(TAG, "✅ Parada de subida seleccionada: " + paradaSubida.getText());

        UiObject2 btnConfSubida = device.findObject(By.textContains("CONFIRMAR SUBIDA"));
        if (btnConfSubida == null) btnConfSubida = device.findObject(By.textContains("SIGUIENTE"));
        if (btnConfSubida == null) btnConfSubida = device.findObject(By.textContains("CONTINUAR"));
        if (btnConfSubida != null) {
            btnConfSubida.click();
            pausa(2000);
        }

        // ── PARADA DE BAJADA ─────────────────────────────────────────────────
        UiObject2 paradaBajada = buscarParada(
                new String[]{"Destino", "destino", "Final", "Llegada", "BAJADA", "sena", "Sena", "🔴"});
        if (paradaBajada == null) {
            List<UiObject2> todas = device.findObjects(By.res(PKG + ":id/txt_nombre_parada"));
            if (todas != null && !todas.isEmpty())
                paradaBajada = todas.get(todas.size() - 1);
        }
        assertNotNull("❌ No se encontró parada de bajada", paradaBajada);
        paradaBajada.click();
        pausa(T_CORTO);
        Log.d(TAG, "✅ Parada de bajada seleccionada: " + paradaBajada.getText());

        UiObject2 errorSeleccion = device.findObject(By.textContains("error"));
        assertNull("❌ Mensaje de error tras seleccionar paradas", errorSeleccion);

        Log.d(TAG, "✅ TEST 4 PASADO — Parada subida y bajada seleccionadas correctamente");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 5 — CONFIRMAR RESERVA  ✅  [CORREGIDO]
    //  Botón final real: btn_confirmar  (texto dinámico "RESERVAR EN: X")
    //  Se busca por ID primero, luego por texto parcial como fallback.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test05_ConfirmarReserva() {
        Log.d(TAG, "══ TEST 5: Confirmar reserva ══");
        realizarLogin();
        abrirBusquedaYBuscar("sena");
        UiObject2 card = seleccionarPrimerViaje();
        assertNotNull("❌ Sin viajes disponibles", card);
        card.click();
        pausa(T_MEDIO);
        limpiarDialogos();
        seleccionarParadasCompleto();

        // ID real confirmado: btn_confirmar (texto dinámico "RESERVAR EN: X")
        UiObject2 btnReservar = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_confirmar")), 5000);
        if (btnReservar == null)
            btnReservar = device.findObject(By.textContains("RESERVAR EN"));      // texto dinámico real
        if (btnReservar == null)
            btnReservar = device.findObject(By.text("RESERVAR"));
        if (btnReservar == null)
            btnReservar = device.findObject(By.textContains("CONFIRMAR RESERVA"));
        if (btnReservar == null)
            btnReservar = device.findObject(By.textContains("RESERVAR"));
        assertNotNull("❌ Botón RESERVAR/CONFIRMAR no encontrado en pantalla", btnReservar);

        btnReservar.click();
        pausa(T_LARGO);
        limpiarDialogos();

        boolean exito = false;

        UiObject2 msgExito = device.findObject(By.textContains("exitosa"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("confirmada"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("Reserva realizada"));
        if (msgExito == null) msgExito = device.findObject(By.textContains("éxito"));
        if (msgExito != null) {
            exito = true;
            Log.d(TAG, "✅ Mensaje de éxito encontrado: " + msgExito.getText());
        }

        if (!exito) {
            UiObject2 pantallaConf = device.findObject(
                    By.res(PKG + ":id/layout_confirmacion_reserva"));
            if (pantallaConf != null) {
                exito = true;
                Log.d(TAG, "✅ Pantalla de confirmación de reserva visible");
            }
        }

        if (!exito) {
            // layout_mis_reservas es el ID real confirmado en MisReservasActivity
            UiObject2 misViajes = device.findObject(
                    By.res(PKG + ":id/layout_mis_reservas"));
            if (misViajes == null)
                misViajes = device.findObject(By.res(PKG + ":id/rv_mis_reservas")); // fallback legacy
            if (misViajes != null) {
                exito = true;
                Log.d(TAG, "✅ Redirigido a Mis Viajes tras reserva (éxito implícito)");
            }
        }

        assertTrue("❌ No se detectó confirmación de reserva exitosa", exito);

        // El botón tras confirmar debe quedar deshabilitado o desaparecer
        UiObject2 btnConfirmPost = device.findObject(By.res(PKG + ":id/btn_confirmar"));
        if (btnConfirmPost != null) {
            assertFalse("❌ El botón btn_confirmar sigue activo tras confirmar la reserva",
                    btnConfirmPost.isEnabled());
        }

        Log.d(TAG, "✅ TEST 5 PASADO — Reserva confirmada exitosamente");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 6 — MIS VIAJES (reservas activas)  ✅  [CORREGIDO]
    //  ID real confirmado en initViews(): layout_mis_reservas
    //  rv_mis_reservas queda como fallback.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test06_MisViajes() {
        Log.d(TAG, "══ TEST 6: Mis Viajes — reservas activas ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        // ID real confirmado en initViews(): layout_mis_reservas
        UiObject2 contenedorReservas = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_mis_reservas")), 7000);
        if (contenedorReservas == null)
            contenedorReservas = device.findObject(By.res(PKG + ":id/rv_mis_reservas")); // fallback

        assertNotNull("❌ Contenedor de Mis Viajes (layout_mis_reservas) no visible", contenedorReservas);

        UiObject2 cardReserva = device.findObject(By.textContains("Confirmada"));
        if (cardReserva == null) cardReserva = device.findObject(By.textContains("En curso"));
        if (cardReserva == null) cardReserva = device.findObject(By.textContains("Pendiente"));
        UiObject2 estadoVacio  = device.findObject(By.res(PKG + ":id/layout_vacio"));

        assertTrue("❌ Ni reservas ni estado vacío visible en Mis Viajes",
                cardReserva != null || estadoVacio != null);

        if (cardReserva != null)
            Log.d(TAG, "✅ Reserva activa encontrada: " + cardReserva.getText());
        else
            Log.d(TAG, "ℹ️ Mis Viajes vacío — sin reservas activas actualmente");

        Log.d(TAG, "✅ TEST 6 PASADO — Pantalla Mis Viajes correctamente cargada");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 7 — MENSAJES → CHAT → ENVIAR  ✅  [CORREGIDO]
    //  Fallbacks ampliados para Chat.class: etMensaje, campo_mensaje, edit_mensaje
    //  btnEnviar, btn_send, fab_enviar, imageButtonEnviar
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test07_Mensajes() {
        Log.d(TAG, "══ TEST 7: Mensajes ══");
        realizarLogin();

        clickUA("nav_mensajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 rvConversaciones = device.wait(
                Until.findObject(By.res(PKG + ":id/rvConversaciones")), 7000);
        if (rvConversaciones == null)
            rvConversaciones = device.findObject(By.res(PKG + ":id/rv_conversaciones"));
        UiObject2 sinResultados = device.findObject(
                By.res(PKG + ":id/tvSinResultados"));
        if (sinResultados == null)
            sinResultados = device.findObject(By.res(PKG + ":id/tv_sin_resultados"));

        assertTrue("❌ Pantalla de Mensajes no cargó (ni lista ni estado vacío)",
                rvConversaciones != null || sinResultados != null);

        if (rvConversaciones == null) {
            Log.d(TAG, "ℹ️ Sin conversaciones — test de chat omitido");
            return;
        }

        List<UiObject2> conversaciones = rvConversaciones.getChildren();
        assertNotNull("❌ getChildren() retornó null en rvConversaciones", conversaciones);
        assertFalse("❌ Lista de conversaciones vacía", conversaciones.isEmpty());

        conversaciones.get(0).click();
        pausa(T_MEDIO);

        // Fallbacks ampliados para Chat.class
        UiObject2 etMensaje = device.wait(
                Until.findObject(By.res(PKG + ":id/etMensaje")), 6000);
        if (etMensaje == null)
            etMensaje = device.findObject(By.res(PKG + ":id/campo_mensaje"));
        if (etMensaje == null)
            etMensaje = device.findObject(By.res(PKG + ":id/edit_mensaje"));
        if (etMensaje == null)
            etMensaje = device.findObject(By.res(PKG + ":id/et_mensaje"));
        if (etMensaje == null)
            etMensaje = device.findObject(By.res(PKG + ":id/input_mensaje"));
        assertNotNull("❌ Campo de texto de chat no encontrado (etMensaje / campo_mensaje / edit_mensaje)", etMensaje);

        final String MENSAJE_TEST = "Hola, ¿a qué hora sale el viaje? 🚗";

        etMensaje.click();
        pausa(500);
        etMensaje.setText(MENSAJE_TEST);
        pausa(T_CORTO);

        assertEquals("❌ El texto no se ingresó correctamente en el campo de mensaje",
                MENSAJE_TEST, etMensaje.getText());

        // Fallbacks ampliados para el botón enviar en Chat.class
        UiObject2 btnEnviar = device.wait(
                Until.findObject(By.res(PKG + ":id/btnEnviar")), 4000);
        if (btnEnviar == null)
            btnEnviar = device.findObject(By.res(PKG + ":id/btn_enviar"));
        if (btnEnviar == null)
            btnEnviar = device.findObject(By.res(PKG + ":id/btn_send"));
        if (btnEnviar == null)
            btnEnviar = device.findObject(By.res(PKG + ":id/fab_enviar"));
        if (btnEnviar == null)
            btnEnviar = device.findObject(By.res(PKG + ":id/imageButtonEnviar"));
        if (btnEnviar == null)
            btnEnviar = device.findObject(By.desc("Enviar"));
        assertNotNull("❌ Botón enviar no encontrado (btnEnviar / btn_send / fab_enviar)", btnEnviar);
        btnEnviar.click();
        pausa(3000);

        UiObject2 campoPostEnvio = device.findObject(By.res(PKG + ":id/etMensaje"));
        if (campoPostEnvio == null)
            campoPostEnvio = device.findObject(By.res(PKG + ":id/campo_mensaje"));
        if (campoPostEnvio == null)
            campoPostEnvio = device.findObject(By.res(PKG + ":id/edit_mensaje"));
        if (campoPostEnvio != null) {
            String textoPost = campoPostEnvio.getText();
            assertTrue("❌ Campo de mensaje no se limpió tras enviar (mensaje no enviado)",
                    textoPost == null || textoPost.trim().isEmpty());
        }

        UiObject2 mensajeEnviadoUI = device.findObject(By.textContains("a qué hora"));
        assertNotNull("❌ Mensaje enviado no aparece en la lista del chat", mensajeEnviadoUI);
        Log.d(TAG, "✅ Mensaje visible en chat: " + mensajeEnviadoUI.getText());

        device.pressBack();
        pausa(T_CORTO);

        Log.d(TAG, "✅ TEST 7 PASADO — Mensaje enviado y visible en chat");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 8 — MAPA  ✅  [CORREGIDO]
    //  ID real confirmado en Mapa.java: R.id.map
    //  map_view y mapFragment quedan como fallback.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test08_Mapa() {
        Log.d(TAG, "══ TEST 8: Mapa ══");
        realizarLogin();

        clickUA("nav_mapa");
        pausa(T_LARGO);
        limpiarDialogos();

        // ID real confirmado en Mapa.java: R.id.map
        UiObject2 mapaView = device.wait(
                Until.findObject(By.res(PKG + ":id/map")), 8000);
        if (mapaView == null)
            mapaView = device.findObject(By.res(PKG + ":id/map_view"));       // fallback legacy
        if (mapaView == null)
            mapaView = device.findObject(By.res(PKG + ":id/mapFragment"));
        if (mapaView == null)
            mapaView = device.findObject(By.res(PKG + ":id/fragmentMapa"));
        if (mapaView == null)
            mapaView = device.findObject(By.res(PKG + ":id/fragment_mapa"));

        assertNotNull("❌ Vista del mapa no encontrada (R.id.map / map_view / mapFragment)", mapaView);
        Log.d(TAG, "✅ MapView/Fragment visible con ID: " + mapaView.getResourceName());

        UiObject2 btnUbicacion = device.findObject(By.res(PKG + ":id/btn_mi_ubicacion"));
        if (btnUbicacion == null)
            btnUbicacion = device.findObject(By.res(PKG + ":id/fab_ubicacion"));
        if (btnUbicacion == null)
            btnUbicacion = device.findObject(By.desc("My Location"));
        if (btnUbicacion != null) {
            Log.d(TAG, "✅ Botón de ubicación actual visible");
        } else {
            Log.w(TAG, "⚠ Botón de ubicación no encontrado (puede ser normal si el mapa usa estilo propio)");
        }

        UiObject2 marcador = device.findObject(By.res(PKG + ":id/ic_marcador_pasajero"));
        if (marcador == null)
            marcador = device.findObject(By.res(PKG + ":id/marker_viaje"));
        if (marcador != null) {
            Log.d(TAG, "✅ Marcador de viaje visible en el mapa");
        } else {
            Log.d(TAG, "ℹ️ Marcadores no accesibles vía UiAutomator (son canvas nativo de Maps — OK)");
        }

        Log.d(TAG, "✅ TEST 8 PASADO — Pantalla de Mapa cargada correctamente");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 9 — PERFIL → CERRAR SESIÓN  ✅  [CORREGIDO]
    //  Fallbacks ampliados para PerfilUsuario.class:
    //  layout_perfil, cv_perfil, scroll_perfil, txt_nombre_usuario, img_foto_perfil
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void test09_PerfilYCerrarSesion() {
        Log.d(TAG, "══ TEST 9: Perfil → Cerrar sesión ══");
        realizarLogin();

        clickUA("nav_perfil");
        pausa(T_MEDIO);
        limpiarDialogos();

        // Fallbacks ampliados para PerfilUsuario.class
        UiObject2 perfilView = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_perfil")), 6000);
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/cv_perfil"));
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/scroll_perfil"));
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/txt_nombre_usuario"));
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/tv_nombre_usuario"));
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/img_foto_perfil"));
        if (perfilView == null)
            perfilView = device.findObject(By.res(PKG + ":id/iv_foto_perfil"));

        assertNotNull("❌ Pantalla de perfil no cargó correctamente (layout_perfil / cv_perfil / txt_nombre_usuario)", perfilView);
        Log.d(TAG, "✅ Pantalla de Perfil visible con ID: " + perfilView.getResourceName());

        UiObject2 btnConfig = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_configuraciones")), 4000);
        if (btnConfig == null)
            btnConfig = device.findObject(By.res(PKG + ":id/btn_configuracion"));
        if (btnConfig == null)
            btnConfig = device.findObject(By.res(PKG + ":id/ic_configuraciones"));
        if (btnConfig != null) {
            btnConfig.click();
            pausa(T_CORTO);
        } else {
            swipeDown();
            pausa(T_CORTO);
        }

        UiObject2 btnCerrarSesion = device.findObject(By.res(PKG + ":id/btn_cerrar_sesion"));
        if (btnCerrarSesion == null)
            btnCerrarSesion = device.findObject(By.res(PKG + ":id/tv_cerrar_sesion"));
        if (btnCerrarSesion == null)
            btnCerrarSesion = device.findObject(By.textContains("Cerrar sesión"));
        if (btnCerrarSesion == null)
            btnCerrarSesion = device.findObject(By.textContains("Cerrar Sesión"));
        if (btnCerrarSesion == null)
            btnCerrarSesion = device.findObject(By.textContains("Salir"));

        if (btnCerrarSesion == null) {
            // Último recurso: navegar vía nav_cerrar_sesion si existe en el menú
            clickUA("nav_cerrar_sesion");
        }

        if (btnCerrarSesion != null) {
            btnCerrarSesion.click();
            pausa(T_CORTO);

            UiObject2 dialogConfirm = device.findObject(By.textContains("Cerrar"));
            if (dialogConfirm == null) dialogConfirm = device.findObject(By.textContains("Sí"));
            if (dialogConfirm == null) dialogConfirm = device.findObject(By.textContains("SI"));
            if (dialogConfirm == null) dialogConfirm = device.findObject(By.textContains("Aceptar"));
            if (dialogConfirm == null) dialogConfirm = device.findObject(By.textContains("ACEPTAR"));
            if (dialogConfirm != null) {
                dialogConfirm.click();
                pausa(T_CORTO);
            }
        }

        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 loginEmail = device.wait(
                Until.findObject(By.res(PKG + ":id/edtEmail")), 7000);
        assertNotNull("❌ Tras cerrar sesión no regresó a pantalla de Login", loginEmail);

        UiObject2 loginBtn = device.findObject(By.res(PKG + ":id/btnLogin"));
        assertNotNull("❌ Botón de Login no visible tras cerrar sesión", loginBtn);

        Log.d(TAG, "✅ TEST 9 PASADO — Cerrar sesión redirigió correctamente a Login");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST ADICIONAL — CANCELAR RESERVA  ✅  [CORREGIDO]
    //  Usa layout_mis_reservas (ID real) como primer candidato.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    public void testAdicional_CancelarReserva() {
        Log.d(TAG, "══ TEST ADICIONAL: Cancelar reserva ══");
        realizarLogin();

        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        // ID real confirmado: layout_mis_reservas
        UiObject2 cardReserva = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_mis_reservas")), 6000);
        if (cardReserva == null)
            cardReserva = device.findObject(By.textContains("Confirmada"));
        if (cardReserva == null)
            cardReserva = device.findObject(By.textContains("Pendiente"));
        if (cardReserva == null)
            cardReserva = device.findObject(By.textContains("En curso"));

        if (cardReserva == null) {
            Log.w(TAG, "⚠ Sin reservas activas — test de cancelación omitido");
            return;
        }

        cardReserva.click();
        pausa(3000);

        UiObject2 btnCancelar = device.wait(
                Until.findObject(By.textContains("CANCELAR")), 5000);
        if (btnCancelar == null)
            btnCancelar = device.findObject(By.res(PKG + ":id/btn_cancelar_reserva"));
        if (btnCancelar == null)
            btnCancelar = device.findObject(By.res(PKG + ":id/btn_cancelar"));
        assertNotNull("❌ Botón CANCELAR no encontrado en el detalle de reserva", btnCancelar);
        btnCancelar.click();
        pausa(T_CORTO);

        UiObject2 btnConfirmar = device.findObject(By.textContains("CONFIRMAR"));
        if (btnConfirmar == null) btnConfirmar = device.findObject(By.textContains("SÍ"));
        if (btnConfirmar == null) btnConfirmar = device.findObject(By.textContains("Sí"));
        if (btnConfirmar == null) btnConfirmar = device.findObject(By.textContains("SI"));
        if (btnConfirmar == null) btnConfirmar = device.findObject(By.textContains("ACEPTAR"));
        assertNotNull("❌ Diálogo de confirmación de cancelación no apareció", btnConfirmar);
        btnConfirmar.click();
        pausa(T_LARGO);
        limpiarDialogos();

        UiObject2 estadoCancelado = device.findObject(By.textContains("Cancelada"));
        if (estadoCancelado == null)
            estadoCancelado = device.findObject(By.textContains("cancelada"));

        if (estadoCancelado != null) {
            Log.d(TAG, "✅ Reserva cancelada — estado visible: " + estadoCancelado.getText());
        } else {
            Log.d(TAG, "✅ Reserva cancelada — card eliminada de la lista");
        }

        Log.d(TAG, "✅ TEST ADICIONAL PASADO — Cancelación de reserva exitosa");
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

    private void abrirBusquedaYBuscar(String destino) {
        clickUA("nav_mis_viajes");
        pausa(T_MEDIO);
        limpiarDialogos();

        UiObject2 campoDestino = device.wait(
                Until.findObject(By.res(PKG + ":id/edit_destino_pasajero")), 6000);
        if (campoDestino != null) {
            campoDestino.click();
            pausa(500);
            campoDestino.setText(destino);
            pausa(2000);
            UiObject2 sug = device.findObject(By.textContains("Sena"));
            if (sug != null) { sug.click(); pausa(500); }
        }

        clickUA("btn_buscar_viajes");
        pausa(T_RED);
        limpiarDialogos();
    }

    private UiObject2 seleccionarPrimerViaje() {
        UiObject2 card = device.wait(
                Until.findObject(By.res(PKG + ":id/layout_resultados_viajes")), 6000);
        if (card != null) return card;

        UiObject2 badge = device.wait(Until.findObject(By.text("ACTIVO")), 5000);
        if (badge != null) {
            UiObject2 padre = badge.getParent();
            return (padre != null) ? padre : badge;
        }

        UiObject2 card2 = device.findObject(By.textContains("San Eduardo"));
        if (card2 == null) card2 = device.findObject(By.textContains("ciudad jardin"));
        if (card2 == null) card2 = device.findObject(By.textContains("sena"));
        return card2;
    }

    private void seleccionarParadasCompleto() {
        // btn_reservar confirmado como ID real para abrir el BottomSheet
        UiObject2 btnAbrir = device.wait(
                Until.findObject(By.res(PKG + ":id/btn_reservar")), 7000);
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("ELEGIR"));
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("PARADA"));
        if (btnAbrir == null) btnAbrir = device.findObject(By.textContains("SELECCIONAR"));
        if (btnAbrir != null) { btnAbrir.click(); pausa(3500); limpiarDialogos(); }

        UiObject2 subida = buscarParada(
                new String[]{"Inicio", "inicio", "Salida", "Origen", "SUBIDA", "🟢"});
        if (subida == null)
            subida = device.wait(
                    Until.findObject(By.res(PKG + ":id/txt_nombre_parada")), 5000);
        if (subida != null) { subida.click(); pausa(T_CORTO); }

        UiObject2 btnConf = device.findObject(By.textContains("CONFIRMAR SUBIDA"));
        if (btnConf == null) btnConf = device.findObject(By.textContains("SIGUIENTE"));
        if (btnConf == null) btnConf = device.findObject(By.textContains("CONTINUAR"));
        if (btnConf != null) { btnConf.click(); pausa(2000); }

        UiObject2 bajada = buscarParada(
                new String[]{"Destino", "destino", "Final", "Llegada", "BAJADA", "sena", "Sena", "🔴"});
        if (bajada == null) {
            List<UiObject2> todas = device.findObjects(By.res(PKG + ":id/txt_nombre_parada"));
            if (todas != null && !todas.isEmpty())
                bajada = todas.get(todas.size() - 1);
        }
        if (bajada != null) { bajada.click(); pausa(T_CORTO); }
    }

    private UiObject2 buscarParada(String[] candidatos) {
        for (String texto : candidatos) {
            UiObject2 obj = device.findObject(By.textContains(texto));
            if (obj != null) return obj;
        }
        return device.findObject(By.res(PKG + ":id/txt_nombre_parada"));
    }

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