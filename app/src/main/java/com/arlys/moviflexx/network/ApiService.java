package com.arlys.moviflexx.network;

import com.arlys.moviflexx.model.pojo.Negocios;
import com.arlys.moviflexx.model.pojo.Pedidos;
import com.arlys.moviflexx.model.pojo.Producto;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import com.google.gson.JsonArray;

import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // Auth
    @POST("auth/login")
    Call<JsonObject> login(@Body JsonObject body);

    @POST("auth/register")
    Call<JsonObject> register(@Body JsonObject body);

    @POST("auth/fcm-token")
    Call<JsonObject> guardarFcmToken(@Header("Authorization") String token, @Body JsonObject body);

    // Negocios - Fase 3
    @GET("negocios")
    Call<List<Negocios>> getNegocios(@Query("tipo") String tipo, @Query("categoriaId") Integer categoriaId);

    @GET("negocios/{id}")
    Call<Negocios> getNegocioById(@Path("id") int id);

    @GET("negocios/{id}/productos")
    Call<List<Producto>> getProductosByNegocio(@Path("id") int id);

    @GET("negocios/tipo/{tipo}")
    Call<List<Negocios>> getNegociosPorTipo(@Path("tipo") String tipo);

    // Productos
    @GET("productos/negocio/{negocioId}")
    Call<List<Producto>> getProductos(@Path("negocioId") int negocioId);

    @GET("productos/{id}")
    Call<Producto> getProductoById(@Path("id") int id);

    // Pedidos - Fase 2
    @POST("pedidos")
    Call<Pedidos> crearPedido(@Header("Authorization") String token, @Body JsonObject body);

    @GET("pedidos/mis-pedidos")
    Call<List<Pedidos>> getMisPedidos(@Header("Authorization") String token);

    @GET("pedidos/repartidor")
    Call<List<Pedidos>> getPedidosRepartidor(@Header("Authorization") String token);

    @GET("pedidos/{id}")
    Call<Pedidos> getPedidoById(@Header("Authorization") String token, @Path("id") int id);

    @GET("pedidos/{id}")
    Call<JsonObject> getPedidoJson(@Header("Authorization") String token, @Path("id") int id);

    @GET("pedidos/buscar")
    Call<JsonArray> buscarPedidos(@Header("Authorization") String token);

    @POST("pedidos/{id}/asignar")
    Call<JsonObject> asignarPedido(@Header("Authorization") String token, @Path("id") int id, @Body JsonObject body);

    @POST("pedidos/{id}/estado")
    Call<JsonObject> cambiarEstado(@Header("Authorization") String token, @Path("id") int id, @Body JsonObject body);

    @POST("pedidos/{id}/ubicacion")
    Call<JsonObject> publicarUbicacion(@Header("Authorization") String token, @Path("id") int id, @Body JsonObject body);

    @GET("pagos/pedido/{id}")
    Call<JsonElement> pagoDelPedido(@Header("Authorization") String token, @Path("id") int id);

    @PUT("pagos/confirmarRepartidor/{id}")
    Call<JsonObject> confirmarRepartidor(@Header("Authorization") String token, @Path("id") int id);

    // Pricing - estimación
    @POST("pedidos/estimar")
    Call<JsonObject> estimarPrecio(@Body JsonObject body);
}
