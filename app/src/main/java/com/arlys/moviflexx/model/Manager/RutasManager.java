package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Rutas;

public class RutasManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public RutasManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdWr() {
        db = conexionBd.getWritableDatabase();
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    };

    public void closeBd() {
        db.close();
    }

    /**
     * Inserta un nuevo registro en la tabla rutas.
     */
    public long insertData(Rutas ruta) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("punto_subida", ruta.getPuntoSubida());
        values.put("punto_bajada", ruta.getPuntoBajada());

        long id = db.insert("rutas", null, values);
        closeBd();
        return id;
    }
}