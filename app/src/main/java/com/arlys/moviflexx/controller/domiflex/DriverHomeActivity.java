package com.arlys.moviflexx.controller.domiflex;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.network.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DriverHomeActivity extends AppCompatActivity implements LocationListener {
    private TextView tvEstado;
    private TextView tvLista;
    private Button btnTomar;
    private Button btnEstado;
    private Button btnEfectivo;
    private JsonObject activo;
    private JsonObject disponible;
    private int idPago;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);
        tvEstado = findViewById(R.id.tvEstado);
        tvLista = findViewById(R.id.tvLista);
        btnTomar = findViewById(R.id.btnTomar);
        btnEstado = findViewById(R.id.btnEstado);
        btnEfectivo = findViewById(R.id.btnEfectivo);
        findViewById(R.id.btnRefrescar).setOnClickListener(v -> cargar());
        btnTomar.setOnClickListener(v -> tomar());
        btnEstado.setOnClickListener(v -> avanzar());
        btnEfectivo.setOnClickListener(v -> confirmarEfectivo());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 41);
        }
        cargar();
    }

    private String token() {
        String jwt = getSharedPreferences("domiflex", MODE_PRIVATE).getString("token", "");
        return "Bearer " + jwt;
    }

    private void cargar() {
        RetrofitClient.getApiService().getMisPedidos(token()).enqueue(new Callback<java.util.List<com.arlys.moviflexx.model.pojo.Pedidos>>() {
            @Override public void onResponse(Call<java.util.List<com.arlys.moviflexx.model.pojo.Pedidos>> call, Response<java.util.List<com.arlys.moviflexx.model.pojo.Pedidos>> response) {
                activo = null;
                com.arlys.moviflexx.model.pojo.Pedidos entregado = null;
                if (response.isSuccessful() && response.body() != null) {
                    for (com.arlys.moviflexx.model.pojo.Pedidos pedido : response.body()) {
                        String estado = pedido.getEstado() == null ? "" : pedido.getEstado();
                        if ("ASIGNADO".equals(estado) || "RECOGIENDO".equals(estado) || "EN_CAMINO".equals(estado)) {
                            activo = pedidoActivo(pedido, estado);
                            break;
                        }
                        if ("ENTREGADO".equals(estado) && entregado == null) entregado = pedido;
                    }
                }
                if (activo == null && entregado != null) activo = pedidoActivo(entregado, "ENTREGADO");
                if (activo == null) cargarDisponibles();
                else mostrarActivo();
            }
            @Override public void onFailure(Call<java.util.List<com.arlys.moviflexx.model.pojo.Pedidos>> call, Throwable t) {
                Toast.makeText(DriverHomeActivity.this, "No se pudieron cargar los pedidos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarDisponibles() {
        detenerGps();
        btnTomar.setEnabled(false);
        btnEstado.setVisibility(Button.GONE);
        btnEfectivo.setVisibility(Button.GONE);
        tvEstado.setText("Pedidos listos para tomar");
        RetrofitClient.getApiService().buscarPedidos(token()).enqueue(new Callback<JsonArray>() {
            @Override public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                disponible = null;
                if (!response.isSuccessful() || response.body() == null || response.body().size() == 0) {
                    tvLista.setText("No hay pedidos en espera.");
                    return;
                }
                StringBuilder lista = new StringBuilder();
                for (JsonElement item : response.body()) {
                    JsonObject pedido = item.getAsJsonObject();
                    if (disponible == null) disponible = pedido;
                    String negocio = pedido.has("negocio") && pedido.get("negocio").isJsonObject()
                            ? pedido.getAsJsonObject("negocio").has("nombre") ? pedido.getAsJsonObject("negocio").get("nombre").getAsString() : "Negocio"
                            : "Envío";
                    lista.append("#").append(pedido.get("idPedido").getAsInt())
                            .append(" · ").append(negocio)
                            .append("\n").append(texto(pedido, "dirEntrega"))
                            .append("\n\n");
                }
                tvLista.setText(lista.toString().trim());
                btnTomar.setEnabled(disponible != null);
                btnTomar.setVisibility(Button.VISIBLE);
            }
            @Override public void onFailure(Call<JsonArray> call, Throwable t) {
                tvLista.setText("No se pudo consultar la lista.");
            }
        });
    }

    private void mostrarActivo() {
        disponible = null;
        btnTomar.setVisibility(Button.GONE);
        String estado = texto(activo, "estado");
        tvEstado.setText("Pedido #" + activo.get("idPedido").getAsInt() + " · " + estado);
        tvLista.setText(texto(activo, "dirEntrega"));
        String siguiente = siguienteEstado(estado);
        if (siguiente == null) {
            btnEstado.setVisibility(Button.GONE);
            detenerGps();
            cargarPago();
        } else {
            btnEstado.setVisibility(Button.VISIBLE);
            btnEstado.setText(etiqueta(siguiente));
            btnEfectivo.setVisibility(Button.GONE);
            iniciarGps();
        }
    }

    private void cargarPago() {
        int id = activo.get("idPedido").getAsInt();
        RetrofitClient.getApiService().pagoDelPedido(token(), id).enqueue(new Callback<JsonElement>() {
            @Override public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                btnEfectivo.setVisibility(Button.VISIBLE);
                JsonObject pago = primerPago(response.body());
                if (!response.isSuccessful() || pago == null || pago.has("error")) {
                    btnEfectivo.setEnabled(false);
                    return;
                }
                idPago = pago.has("idPago") ? pago.get("idPago").getAsInt() : 0;
                boolean cliente = pago.has("confirmacionCliente") && pago.get("confirmacionCliente").getAsBoolean();
                boolean repartidor = pago.has("confirmacionRepartidor") && pago.get("confirmacionRepartidor").getAsBoolean();
                if (repartidor || "COMPLETADO".equals(texto(pago, "estado"))) {
                    activo = null;
                    cargarDisponibles();
                    return;
                }
                btnEfectivo.setEnabled(cliente && idPago > 0);
                if (!cliente) tvLista.setText("Espera a que el cliente confirme el efectivo.");
                else if (repartidor) tvLista.setText("Efectivo confirmado.");
            }
            @Override public void onFailure(Call<JsonElement> call, Throwable t) {
                btnEfectivo.setEnabled(false);
            }
        });
    }

    private void tomar() {
        if (disponible == null) return;
        int id = disponible.get("idPedido").getAsInt();
        RetrofitClient.getApiService().asignarPedido(token(), id, new JsonObject()).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.body() != null && response.body().has("error")) {
                    Toast.makeText(DriverHomeActivity.this, response.body().get("error").getAsString(), Toast.LENGTH_SHORT).show();
                    return;
                }
                cargar();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(DriverHomeActivity.this, "No se pudo tomar el pedido", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void avanzar() {
        if (activo == null) return;
        String siguiente = siguienteEstado(texto(activo, "estado"));
        if (siguiente == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("estado", siguiente);
        RetrofitClient.getApiService().cambiarEstado(token(), activo.get("idPedido").getAsInt(), body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.body() != null && response.body().has("error")) {
                    Toast.makeText(DriverHomeActivity.this, response.body().get("error").getAsString(), Toast.LENGTH_SHORT).show();
                    return;
                }
                cargar();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(DriverHomeActivity.this, "No se pudo cambiar el estado", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmarEfectivo() {
        if (idPago <= 0) return;
        RetrofitClient.getApiService().confirmarRepartidor(token(), idPago).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                Toast.makeText(DriverHomeActivity.this, "Efectivo registrado", Toast.LENGTH_SHORT).show();
                cargar();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(DriverHomeActivity.this, "No se pudo confirmar el pago", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void iniciarGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (locationManager != null) return;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;
        String proveedor = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER
                : LocationManager.NETWORK_PROVIDER;
        locationManager.requestLocationUpdates(proveedor, 15000, 10, this);
    }

    private void detenerGps() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            locationManager = null;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (activo == null) return;
        String estado = texto(activo, "estado");
        if (!"ASIGNADO".equals(estado) && !"RECOGIENDO".equals(estado) && !"EN_CAMINO".equals(estado)) return;
        JsonObject body = new JsonObject();
        body.addProperty("lat", location.getLatitude());
        body.addProperty("lng", location.getLongitude());
        RetrofitClient.getApiService().publicarUbicacion(token(), activo.get("idPedido").getAsInt(), body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {}
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    @Override protected void onDestroy() {
        detenerGps();
        super.onDestroy();
    }

    private static JsonObject primerPago(JsonElement body) {
        if (body == null || body.isJsonNull()) return null;
        if (body.isJsonObject()) return body.getAsJsonObject();
        if (!body.isJsonArray() || body.getAsJsonArray().size() == 0) return null;
        JsonObject primero = null;
        for (JsonElement item : body.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            JsonObject pago = item.getAsJsonObject();
            if (primero == null) primero = pago;
            if (pago.has("tipoPago") && "PEDIDO".equals(pago.get("tipoPago").getAsString())) return pago;
        }
        return primero;
    }

    private static JsonObject pedidoActivo(com.arlys.moviflexx.model.pojo.Pedidos pedido, String estado) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("idPedido", pedido.getIdPedido());
        objeto.addProperty("estado", estado);
        objeto.addProperty("dirEntrega", pedido.getDirEntrega() == null ? "" : pedido.getDirEntrega());
        return objeto;
    }

    private static String siguienteEstado(String estado) {
        if ("ASIGNADO".equals(estado)) return "RECOGIENDO";
        if ("RECOGIENDO".equals(estado)) return "EN_CAMINO";
        if ("EN_CAMINO".equals(estado)) return "ENTREGADO";
        return null;
    }

    private static String etiqueta(String estado) {
        if ("RECOGIENDO".equals(estado)) return "Ya recogí el pedido";
        if ("EN_CAMINO".equals(estado)) return "Voy en camino";
        if ("ENTREGADO".equals(estado)) return "Ya lo entregué";
        return estado;
    }

    private static String texto(JsonObject objeto, String campo) {
        if (objeto == null || !objeto.has(campo) || objeto.get(campo).isJsonNull()) return "";
        return objeto.get(campo).getAsString();
    }
}
