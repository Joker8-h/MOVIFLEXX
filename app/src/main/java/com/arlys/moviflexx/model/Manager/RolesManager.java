package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Roles;

public class RolesManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public RolesManager(Context context) {
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

    public long insertData(Roles rol) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("nombreRol", rol.getNombreRol());

        long id = db.insert("roles", null, values);
        closeBd();
        return id;
    }
}