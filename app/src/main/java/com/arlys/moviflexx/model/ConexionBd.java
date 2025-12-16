package com.arlys.moviflexx.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ConexionBd extends SQLiteOpenHelper {

    public ConexionBd(Context context) {
        // Se mantienen los parámetros originales para la base de datos
        super(context, Constantes.NAME_BD, null, Constantes.VERSION_BD);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. CREACIÓN DE TABLAS BASE
        // 1.1 ROLES
        db.execSQL("CREATE TABLE roles (idRol INTEGER PRIMARY KEY AUTOINCREMENT, nombreRol TEXT)");

        // 1.2 USUARIOS (Corregida y combinada de las dos líneas originales)
        db.execSQL("CREATE TABLE USUARIOS (idUsuarios INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    NOMBRE TEXT,\n" +
                "    APELLIDO TEXT,\n" +
                "    CORREO TEXT UNIQUE,\n" +
                "    TELEFONO TEXT,\n" +
                "    CONTRASEÑA TEXT,\n" +
                "    ROL TEXT)");

        // 1.3 METODOS_PAGO_CATALOGO
        db.execSQL("CREATE TABLE metodos_pago_catalogo (idMetodos_pago INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT, tipo TEXT)");

        // 1.4 TARIFAS
        db.execSQL("CREATE TABLE tarifas (idTarifas INTEGER PRIMARY KEY AUTOINCREMENT," +
                " costo_base REAL, costo_por_km REAL, fecha_vigencia TEXT)");

        // 2. CREACIÓN DE TABLAS DE VEHÍCULOS

        // 2.1 VEHICULOS
        db.execSQL("CREATE TABLE vehiculos (idVehiculos INTEGER PRIMARY KEY AUTOINCREMENT," +
                " tipoVehicular TEXT, placa TEXT, modelo TEXT, color TEXT)");

        // 2.2 DOCUMENTOS_CATALOGO
        db.execSQL("CREATE TABLE documentos_catalogo (idDocumento_catalogo INTEGER PRIMARY KEY AUTOINCREMENT," +
                " nombre TEXT, aplica_e_p TEXT, requiere_vigencia INTEGER)");

        // 2.3 DOCUMENTOS_VEHIC
        db.execSQL("CREATE TABLE documentos_vehic (idRegistro INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "idDocumento_catalogo INTEGER, idVehiculos INTEGER, url_archivo TEXT, fecha_vencimiento TEXT, " +
                "estado TEXT, estado_final TEXT, fecha_subida TEXT, FOREIGN KEY (idDocumento_catalogo) " +
                "REFERENCES documentos_catalogo(idDocumento_catalogo), FOREIGN KEY (idVehiculos) " +
                "REFERENCES vehiculos(idVehiculos))");

        // 2.4 USUARIO_VEHICULO (Tabla de unión)
        db.execSQL("CREATE TABLE usuario_vehiculo (idUsuarios INTEGER, idVehiculos INTEGER," +
                " PRIMARY KEY (idUsuarios, idVehiculos), FOREIGN KEY (idUsuarios)" +
                " REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (idVehiculos) REFERENCES vehiculos(idVehiculos))");

        // 3. CREACIÓN DE TABLAS DE VIAJES Y RUTAS

        // 3.1 VIAJES
        db.execSQL("CREATE TABLE viajes (idViajes INTEGER PRIMARY KEY AUTOINCREMENT," +
                " idVehiculos INTEGER, origen TEXT, destino TEXT, fecha_hora_salida TEXT, cupos_totales INTEGER," +
                " cupos_disponibles INTEGER, estado TEXT, FOREIGN KEY (idVehiculos) REFERENCES vehiculos(idVehiculos))");

        // 3.2 RUTAS
        db.execSQL("CREATE TABLE rutas (idRutas INTEGER PRIMARY KEY AUTOINCREMENT, punto_subida TEXT, punto_bajada TEXT)");

        // 3.3 VIAJE_RUTA (Tabla de unión)
        db.execSQL("CREATE TABLE viaje_ruta (idViajes INTEGER, idRutas INTEGER, PRIMARY KEY (idViajes, idRutas)," +
                " FOREIGN KEY (idViajes) REFERENCES viajes(idViajes), FOREIGN KEY (idRutas) REFERENCES rutas(idRutas))");


        db.execSQL("CREATE TABLE usuario_viaje (idUsuarios INTEGER, idViajes INTEGER, idTarifas INTEGER, rol_en_viaje TEXT, " +
                "distancia_recorrida REAL, precio_final REAL, PRIMARY KEY (idUsuarios, idViajes)," +
                " FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (idViajes) " +
                "REFERENCES viajes(idViajes), FOREIGN KEY (idTarifas) REFERENCES tarifas(idTarifas))");

        // 4. CREACIÓN DE TABLAS DE PAGOS Y CALIFICACIONES

        // 4.1 PAGOS
        db.execSQL("CREATE TABLE pagos (idPagos INTEGER PRIMARY KEY AUTOINCREMENT, idMetodos_pago INTEGER, idViajes INTEGER, idUsuarios INTEGER, monto REAL, fecha_pago TEXT, estado TEXT, FOREIGN KEY (idMetodos_pago) REFERENCES metodos_pago_catalogo(idMetodos_pago), FOREIGN KEY (idViajes) REFERENCES viajes(idViajes), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios))");

        // 4.2 CALIFICACIONES
        db.execSQL("CREATE TABLE calificaciones " +
                "(idCalificacion INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "idUsuarios INTEGER, idViajes INTEGER, puntuacion INTEGER, comentario TEXT, " +
                "fecha_calificacion TEXT, FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios), " +
                "FOREIGN KEY (idViajes) REFERENCES viajes(idViajes))");

        // 4.3 USUARIO_ROL (Tabla de unión)
        db.execSQL("CREATE TABLE usuario_rol (idUsuarios INTEGER, idRol INTEGER," +
                " PRIMARY KEY (idUsuarios, idRol), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios)," +
                " FOREIGN KEY (idRol) REFERENCES roles(idRol))");

        db.execSQL("CREATE TABLE PublicarViaje(ORIGEN TEXT, DESTINO TEXT, HORA )");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {


        db.execSQL("DROP TABLE IF EXISTS Register");
        db.execSQL("DROP TABLE IF EXISTS usuario_rol");
        db.execSQL("DROP TABLE IF EXISTS calificaciones");
        db.execSQL("DROP TABLE IF EXISTS pagos");
        db.execSQL("DROP TABLE IF EXISTS usuario_viaje");
        db.execSQL("DROP TABLE IF EXISTS viaje_ruta");
        db.execSQL("DROP TABLE IF EXISTS rutas");
        db.execSQL("DROP TABLE IF EXISTS viajes");
        db.execSQL("DROP TABLE IF EXISTS usuario_vehiculo");
        db.execSQL("DROP TABLE IF EXISTS documentos_vehic");
        db.execSQL("DROP TABLE IF EXISTS documentos_catalogo");
        db.execSQL("DROP TABLE IF EXISTS vehiculos");
        db.execSQL("DROP TABLE IF EXISTS tarifas");
        db.execSQL("DROP TABLE IF EXISTS metodos_pago_catalogo");
        db.execSQL("DROP TABLE IF EXISTS USUARIOS");
        db.execSQL("DROP TABLE IF EXISTS roles");






        onCreate(db);
    }
}