package com.arlys.moviflexx.model.pojo;

import java.util.List;

/**
 * Mapea la respuesta completa de POST /route-options del backend FastAPI.
 *
 * JSON:
 * {
 *   "preference": "FASTEST",
 *   "requested": 3,
 *   "returned": 3,
 *   "routes": [ { RouteOption }, ... ]
 * }
 */
public class RouteOptionsResponse {
    public String           preference;
    public int              requested;
    public int              returned;
    public List<RouteOption> routes;
}