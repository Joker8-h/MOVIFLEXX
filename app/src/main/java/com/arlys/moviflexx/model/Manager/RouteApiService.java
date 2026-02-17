package com.arlys.moviflexx.model.Manager;

import com.arlys.moviflexx.model.pojo.RouteOptionsResponse;
import com.arlys.moviflexx.model.pojo.RouteRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface RouteApiService {

    @POST("route-options")
    Call<RouteOptionsResponse> getRouteOptions(@Body RouteRequest request);
}