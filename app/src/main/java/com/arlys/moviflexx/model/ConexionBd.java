package com.arlys.moviflexx.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ConexionBd extends SQLiteOpenHelper {

    public ConexionBd(Context context) {
        super(context, Constantes.NAME_BD, null, Constantes.VERSION_BD);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // === ROLES (Fase 1 DomiFlex) ===
        db.execSQL("CREATE TABLE roles (idRol INTEGER PRIMARY KEY AUTOINCREMENT, nombreRol TEXT UNIQUE)");

        // === USUARIOS ===
        db.execSQL("CREATE TABLE USUARIOS (idUsuarios INTEGER PRIMARY KEY AUTOINCREMENT," +
                " NOMBRE TEXT, APELLIDO TEXT, CORREO TEXT UNIQUE, TELEFONO TEXT, CONTRASEÑA TEXT, idRol INTEGER, fotoPerfil TEXT)");

        // === METODOS PAGO / TARIFAS (legacy Moviflexx, se mantienen) ===
        db.execSQL("CREATE TABLE metodos_pago_catalogo (idMetodos_pago INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, tipo TEXT)");
        db.execSQL("CREATE TABLE tarifas (idTarifas INTEGER PRIMARY KEY AUTOINCREMENT, costo_base REAL, costo_por_km REAL, fecha_vigencia TEXT)");

        // === VEHICULOS (migrado: capacidadKg + tipo MOTO/BICI/CARRO) ===
        db.execSQL("CREATE TABLE vehiculos (idVehiculos INTEGER PRIMARY KEY AUTOINCREMENT," +
                " idUsuario INTEGER, tipoVehicular TEXT DEFAULT 'MOTO', placa TEXT UNIQUE, modelo TEXT, color TEXT," +
                " capacidadKg INTEGER DEFAULT 10, placaValidada INTEGER DEFAULT 0, fotoPlaca TEXT," +
                " FOREIGN KEY (idUsuario) REFERENCES USUARIOS(idUsuarios))");

        db.execSQL("CREATE TABLE documentos_catalogo (idDocumento_catalogo INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, aplica_e_p TEXT, requiere_vigencia INTEGER)");
        db.execSQL("CREATE TABLE documentos_vehic (idRegistro INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "idDocumento_catalogo INTEGER, idVehiculos INTEGER, url_archivo TEXT, fecha_vencimiento TEXT, " +
                "estado TEXT, estado_final TEXT, fecha_subida TEXT, FOREIGN KEY (idDocumento_catalogo) " +
                "REFERENCES documentos_catalogo(idDocumento_catalogo), FOREIGN KEY (idVehiculos) REFERENCES vehiculos(idVehiculos))");
        db.execSQL("CREATE TABLE usuario_vehiculo (idUsuarios INTEGER, idVehiculos INTEGER," +
                " PRIMARY KEY (idUsuarios, idVehiculos), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (idVehiculos) REFERENCES vehiculos(idVehiculos))");

        // === RUTAS / PARADAS (reutilizadas para recorrido del repartidor) ===
        db.execSQL("CREATE TABLE rutas (idRutas INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, descripcion TEXT, origen TEXT, estado TEXT, scoreIa REAL)");
        db.execSQL("CREATE TABLE paradas (idParada INTEGER PRIMARY KEY AUTOINCREMENT, idRuta INTEGER, nombre TEXT, lat REAL, lng REAL, orden INTEGER, tipo TEXT, FOREIGN KEY (idRuta) REFERENCES rutas(idRutas))");

        // === VIAJES (legacy Moviflexx - se mantiene para migración, deprecado en DomiFlex) ===
        db.execSQL("CREATE TABLE viajes (idViajes INTEGER PRIMARY KEY AUTOINCREMENT," +
                " idVehiculos INTEGER, origen TEXT, destino TEXT, fecha_hora_salida TEXT, cupos_totales INTEGER," +
                " cupos_disponibles INTEGER, estado TEXT, FOREIGN KEY (idVehiculos) REFERENCES vehiculos(idVehiculos))");
        db.execSQL("CREATE TABLE viaje_ruta (idViajes INTEGER, idRutas INTEGER, PRIMARY KEY (idViajes, idRutas)," +
                " FOREIGN KEY (idViajes) REFERENCES viajes(idViajes), FOREIGN KEY (idRutas) REFERENCES rutas(idRutas))");
        db.execSQL("CREATE TABLE usuario_viaje (idUsuarios INTEGER, idViajes INTEGER, idTarifas INTEGER, rol_en_viaje TEXT, " +
                "distancia_recorrida REAL, precio_final REAL, PRIMARY KEY (idUsuarios, idViajes)," +
                " FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (idViajes) REFERENCES viajes(idViajes), FOREIGN KEY (idTarifas) REFERENCES tarifas(idTarifas))");

        // === DOMIFLEX FASE 2: PEDIDOS (reemplaza Viajes con cupos por domicilio) ===
        db.execSQL("CREATE TABLE pedidos (idPedido INTEGER PRIMARY KEY AUTOINCREMENT," +
                " idCliente INTEGER, idRepartidor INTEGER, idComercio INTEGER, idRuta INTEGER, idVehiculo INTEGER, negocioId INTEGER," +
                " nombreRecogida TEXT, dirRecogida TEXT, latRecogida REAL, lngRecogida REAL," +
                " nombreEntrega TEXT, dirEntrega TEXT, latEntrega REAL, lngEntrega REAL," +
                " detallePedido TEXT, distanciaKm REAL, subtotal REAL, comisionPlataforma REAL, total REAL," +
                " tipoPago TEXT DEFAULT 'EFECTIVO', estado TEXT DEFAULT 'CREADO', creadoEn TEXT," +
                " FOREIGN KEY (idCliente) REFERENCES USUARIOS(idUsuarios)," +
                " FOREIGN KEY (idRepartidor) REFERENCES USUARIOS(idUsuarios)," +
                " FOREIGN KEY (negocioId) REFERENCES negocios(id))");

        db.execSQL("CREATE TABLE pedido_paradas (idPedido INTEGER, idParada INTEGER, orden INTEGER, tipo TEXT, completada INTEGER DEFAULT 0," +
                " PRIMARY KEY (idPedido, idParada), FOREIGN KEY (idPedido) REFERENCES pedidos(idPedido), FOREIGN KEY (idParada) REFERENCES paradas(idParada))");

        // === DOMIFLEX FASE 3: NEGOCIOS / PRODUCTO / PEDIDO_ITEM ===
        db.execSQL("CREATE TABLE categorias_negocio (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT UNIQUE, icono TEXT, activo INTEGER DEFAULT 1)");
        // Seed categorías
        db.execSQL("INSERT INTO categorias_negocio (nombre, icono) VALUES ('Restaurantes','🍔'),('Farmacias','💊'),('Supermercados','🛒'),('Tiendas','🏪'),('Paquetería','📦'),('Electrónica','📱'),('Ropa','👕'),('Mascotas','🐕')");

        db.execSQL("CREATE TABLE negocios (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, descripcion TEXT, tipo TEXT DEFAULT 'COMIDA'," +
                " direccion TEXT, latitud REAL, longitud REAL, telefono TEXT, imagen TEXT, banner TEXT," +
                " calificacion REAL DEFAULT 0, totalCalificaciones INTEGER DEFAULT 0, tiempoEstimadoMin INTEGER DEFAULT 15," +
                " costoEnvio INTEGER DEFAULT 2000, envioMinimo INTEGER DEFAULT 0, activo INTEGER DEFAULT 1," +
                " ownerId INTEGER, categoriaId INTEGER," +
                " FOREIGN KEY (ownerId) REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (categoriaId) REFERENCES categorias_negocio(id))");

        db.execSQL("CREATE TABLE producto (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, descripcion TEXT, precio INTEGER," +
                " imagen TEXT, categoria TEXT, disponible INTEGER DEFAULT 1, restauranteId INTEGER," +
                " FOREIGN KEY (restauranteId) REFERENCES negocios(id))");

        db.execSQL("CREATE TABLE pedido_item (id INTEGER PRIMARY KEY AUTOINCREMENT, cantidad INTEGER DEFAULT 1, precio INTEGER," +
                " pedidoId INTEGER, menuItemId INTEGER," +
                " FOREIGN KEY (pedidoId) REFERENCES pedidos(idPedido), FOREIGN KEY (menuItemId) REFERENCES producto(id))");

        // === PAGOS Y CALIFICACIONES (adaptados a pedidos) ===
        db.execSQL("CREATE TABLE pagos (idPagos INTEGER PRIMARY KEY AUTOINCREMENT, idMetodos_pago INTEGER, idViajes INTEGER, idPedido INTEGER, idUsuarios INTEGER, monto REAL, fecha_pago TEXT, estado TEXT," +
                " FOREIGN KEY (idPedido) REFERENCES pedidos(idPedido), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios))");
        db.execSQL("CREATE TABLE calificaciones (idCalificacion INTEGER PRIMARY KEY AUTOINCREMENT, idUsuarios INTEGER, idViajes INTEGER, idPedido INTEGER, puntuacion INTEGER, comentario TEXT, fecha_calificacion TEXT," +
                " FOREIGN KEY (idPedido) REFERENCES pedidos(idPedido), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios))");
        db.execSQL("CREATE TABLE usuario_rol (idUsuarios INTEGER, idRol INTEGER," +
                " PRIMARY KEY (idUsuarios, idRol), FOREIGN KEY (idUsuarios) REFERENCES USUARIOS(idUsuarios), FOREIGN KEY (idRol) REFERENCES roles(idRol))");

        // Seed roles DomiFlex
        db.execSQL("INSERT INTO roles (nombreRol) VALUES ('ADMIN'),('REPARTIDOR'),('CLIENTE'),('COMERCIO')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Migración Fase 1-3 DomiFlex: crear nuevas tablas sin borrar datos de usuarios/vehículos
            db.execSQL("CREATE TABLE IF NOT EXISTS pedidos (idPedido INTEGER PRIMARY KEY AUTOINCREMENT," +
                    " idCliente INTEGER, idRepartidor INTEGER, idComercio INTEGER, idRuta INTEGER, idVehiculo INTEGER, negocioId INTEGER," +
                    " nombreRecogida TEXT, dirRecogida TEXT, latRecogida REAL, lngRecogida REAL," +
                    " nombreEntrega TEXT, dirEntrega TEXT, latEntrega REAL, lngEntrega REAL," +
                    " detallePedido TEXT, distanciaKm REAL, subtotal REAL, comisionPlataforma REAL, total REAL," +
                    " tipoPago TEXT DEFAULT 'EFECTIVO', estado TEXT DEFAULT 'CREADO', creadoEn TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS pedido_paradas (idPedido INTEGER, idParada INTEGER, orden INTEGER, tipo TEXT, completada INTEGER DEFAULT 0, PRIMARY KEY (idPedido, idParada))");
            db.execSQL("CREATE TABLE IF NOT EXISTS categorias_negocio (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT UNIQUE, icono TEXT, activo INTEGER DEFAULT 1)");
            db.execSQL("INSERT OR IGNORE INTO categorias_negocio (nombre, icono) VALUES ('Restaurantes','🍔'),('Farmacias','💊'),('Supermercados','🛒'),('Tiendas','🏪'),('Paquetería','📦'),('Electrónica','📱'),('Ropa','👕'),('Mascotas','🐕')");
            db.execSQL("CREATE TABLE IF NOT EXISTS negocios (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, descripcion TEXT, tipo TEXT DEFAULT 'COMIDA'," +
                    " direccion TEXT, latitud REAL, longitud REAL, telefono TEXT, imagen TEXT, banner TEXT," +
                    " calificacion REAL DEFAULT 0, totalCalificaciones INTEGER DEFAULT 0, tiempoEstimadoMin INTEGER DEFAULT 15," +
                    " costoEnvio INTEGER DEFAULT 2000, envioMinimo INTEGER DEFAULT 0, activo INTEGER DEFAULT 1, ownerId INTEGER, categoriaId INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS producto (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT, descripcion TEXT, precio INTEGER, imagen TEXT, categoria TEXT, disponible INTEGER DEFAULT 1, restauranteId INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS pedido_item (id INTEGER PRIMARY KEY AUTOINCREMENT, cantidad INTEGER DEFAULT 1, precio INTEGER, pedidoId INTEGER, menuItemId INTEGER)");
            // Actualizar roles: agregar COMERCIO si no existe
            db.execSQL("INSERT OR IGNORE INTO roles (nombreRol) VALUES ('COMERCIO')");
            // Renombrar CONDUCTOR -> REPARTIDOR, PASAJERO -> CLIENTE si existen con nombre antiguo
            db.execSQL("UPDATE roles SET nombreRol='REPARTIDOR' WHERE nombreRol='CONDUCTOR'");
            db.execSQL("UPDATE roles SET nombreRol='CLIENTE' WHERE nombreRol='PASAJERO'");
            // Actualizar Vehiculos para DomiFlex (agregar columnas si no existen - SQLite no soporta ADD COLUMN IF NOT EXISTS, se ignora error)
            try { db.execSQL("ALTER TABLE vehiculos ADD COLUMN capacidadKg INTEGER DEFAULT 10"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE vehiculos ADD COLUMN placaValidada INTEGER DEFAULT 0"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE vehiculos ADD COLUMN fotoPlaca TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE vehiculos ADD COLUMN idUsuario INTEGER"); } catch (Exception e) {}
        } else {
            // Fallback: recrear todo (desarrollo)
            db.execSQL("DROP TABLE IF EXISTS pedido_item");
            db.execSQL("DROP TABLE IF EXISTS producto");
            db.execSQL("DROP TABLE IF EXISTS negocios");
            db.execSQL("DROP TABLE IF EXISTS categorias_negocio");
            db.execSQL("DROP TABLE IF EXISTS pedido_paradas");
            db.execSQL("DROP TABLE IF EXISTS pedidos");
            onCreate(db);
        }
    }
}
