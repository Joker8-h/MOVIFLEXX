package com.arlys.moviflexx.network;

import com.arlys.moviflexx.model.Constantes;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

// OSRM Colombia - mapas para domicilios
public interface OsrmService {
    @GET("/route/v1/driving/{coords}")
    Call<String> getRoute(@Path("coords") String coords, @Query("overview") String overview);

    @GET("/table/v1/driving/{coords}")
    Call<String> getTable(@Path("coords") String coords);
}
