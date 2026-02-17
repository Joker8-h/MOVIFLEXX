package com.arlys.moviflexx.model.pojo;

import com.google.gson.annotations.SerializedName;

public class RouteRequest {

    @SerializedName("origin")
    public LatLng origin;

    @SerializedName("destination")
    public LatLng destination;

    @SerializedName("preference")
    public String preference;

    @SerializedName("k")
    public int k;

    @SerializedName("fuel_price_per_liter")
    public int fuelPricePerLiter;

    /**
     * @param originLat   Latitud del origen
     * @param originLng   Longitud del origen
     * @param destLat     Latitud del destino
     * @param destLng     Longitud del destino
     * @param preference  "FASTEST" | "CHEAPEST" | "LOW_FUEL" | "SHORT_DISTANCE"
     */
    public RouteRequest(double originLat, double originLng,
                        double destLat, double destLng,
                        String preference) {
        this.origin = new LatLng(originLat, originLng);
        this.destination = new LatLng(destLat, destLng);
        this.preference = preference;
        this.k = 3;                    // hasta 3 rutas alternativas
        this.fuelPricePerLiter = 15000; // precio combustible en COP
    }

    public static class LatLng {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lng")
        public double lng;

        public LatLng(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }
}