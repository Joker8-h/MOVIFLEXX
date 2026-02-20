package com.arlys.moviflexx.model.pojo;

import com.google.gson.annotations.SerializedName;

/**
 * Mapea un objeto "route" de la respuesta del endpoint POST /route-options
 * del backend FastAPI.
 * <p>
 * JSON que devuelve el backend:
 * {
 * "id": "r1",
 * "distance_km": 1.4,
 * "duration_min": 3.0,
 * "fuel_liters": 0.105,
 * "fuel_cost_cop": 1592.0,   ← ESTE es el precio que debe llegar a PublicarViaje
 * "score": 3.0,
 * "geojson": { ... }
 * }
 */
public class RouteOption {

    @SerializedName("id")
    public String id;

    @SerializedName("distance_km")
    public double distanceKm;       // kilómetros

    @SerializedName("duration_min")
    public double durationMin;      // minutos

    @SerializedName("fuel_liters")
    public double fuelLiters;       // litros consumidos

    @SerializedName("fuel_cost_cop")
    public double fuelCostCop;      // ← costo en COP — este va al Intent

    @SerializedName("score")
    public double score;

    @SerializedName("geojson")
    public java.util.Map<String, Object> geojson;
}