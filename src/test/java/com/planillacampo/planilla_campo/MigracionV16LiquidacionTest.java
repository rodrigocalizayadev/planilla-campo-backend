package com.planillacampo.planilla_campo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V16 migra datos existentes: cada consignación ya liquidada queda con un único tramo por la
 * cantidad total y los mismos precio e importe neto. Se prueba sobre una base aparte: esquema
 * en V15, filas históricas y migración hasta la última versión.
 */
class MigracionV16LiquidacionTest {

    private static final String BASE = "migracion_v16";

    @Test
    void consignaciones_liquidadas_historicas_quedan_con_un_tramo_por_la_cantidad_total() throws SQLException {
        String host = IntegrationTestBase.MYSQL.getHost();
        int puerto = IntegrationTestBase.MYSQL.getMappedPort(3306);
        String params = "?allowPublicKeyRetrieval=true&useSSL=false";
        String urlServidor = "jdbc:mysql://%s:%d/%s".formatted(host, puerto, params);
        String urlBase = "jdbc:mysql://%s:%d/%s%s".formatted(host, puerto, BASE, params);
        String usuario = "root";
        String clave = IntegrationTestBase.MYSQL.getPassword();

        try (Connection admin = DriverManager.getConnection(urlServidor, usuario, clave);
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BASE);
            st.execute("CREATE DATABASE " + BASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        Flyway.configure().dataSource(urlBase, usuario, clave).target("15").load().migrate();

        try (Connection con = DriverManager.getConnection(urlBase, usuario, clave);
             Statement st = con.createStatement()) {
            // Sin padres: lo único que importa acá son las columnas de venta
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            // 1: consignación liquidada, 100 a 5.000 ARS con comisión de 10.000
            st.execute("""
                    INSERT INTO venta (id, lote_campania_id, comprador_id, calidad, modalidad, estado,
                                       fecha_entrega, cantidad, unidad, fecha_liquidacion,
                                       precio_unitario_importe, precio_unitario_moneda, precio_unitario_tipo_cambio,
                                       precio_unitario_importe_usd, precio_unitario_importe_ars,
                                       comision_importe, comision_moneda, comision_tipo_cambio,
                                       comision_importe_usd, comision_importe_ars,
                                       importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
                                       importe_neto_importe_usd, importe_neto_importe_ars,
                                       created_at, updated_at)
                    VALUES (1, 1, 1, 'PRIMERA', 'CONSIGNACION', 'LIQUIDADA',
                            '2025-12-01', 100.00, 'BOLSA', '2026-02-15',
                            5000.00, 'ARS', 1.0000, 5.00, 5000.00,
                            10000.00, 'ARS', 1.0000, 10.00, 10000.00,
                            490000.00, 'ARS', 1.0000, 490.00, 490000.00,
                            NOW(6), NOW(6))
                    """);
            // 2: consignación sin liquidar
            st.execute(ventaSinPrecio(2, "CONSIGNACION", "ENTREGADA"));
            // 3: precio cerrado ya liquidada (no es consignación: no lleva tramo)
            st.execute("""
                    INSERT INTO venta (id, lote_campania_id, comprador_id, calidad, modalidad, estado,
                                       fecha_entrega, cantidad, unidad, fecha_liquidacion,
                                       importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
                                       importe_neto_importe_usd, importe_neto_importe_ars,
                                       created_at, updated_at)
                    VALUES (3, 1, 1, 'PRIMERA', 'PRECIO_CERRADO', 'LIQUIDADA',
                            '2025-12-01', 100.00, 'BOLSA', '2025-12-01',
                            500000.00, 'ARS', 1.0000, 500.00, 500000.00,
                            NOW(6), NOW(6))
                    """);
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        Flyway.configure().dataSource(urlBase, usuario, clave).load().migrate();

        try (Connection con = DriverManager.getConnection(urlBase, usuario, clave);
             Statement st = con.createStatement()) {
            assertThat(filas(st, "SELECT id, cantidad_liquidada FROM venta ORDER BY id"))
                    .containsExactly("1|100.00", "2|0.00", "3|0.00");
            assertThat(filas(st, "SELECT venta_id, fecha, cantidad, precio_unitario_importe, "
                    + "comision_importe, flete_importe, importe_neto_importe, importe_neto_importe_ars "
                    + "FROM liquidacion_venta ORDER BY venta_id"))
                    .containsExactly("1|2026-02-15|100.00|5000.00|10000.00|null|490000.00|490000.00");
        }
    }

    private static String ventaSinPrecio(int id, String modalidad, String estado) {
        return """
                INSERT INTO venta (id, lote_campania_id, comprador_id, calidad, modalidad, estado,
                                   fecha_entrega, cantidad, unidad, created_at, updated_at)
                VALUES (%d, 1, 1, 'PRIMERA', '%s', '%s', '2025-12-01', 100.00, 'BOLSA', NOW(6), NOW(6))
                """.formatted(id, modalidad, estado);
    }

    private static List<String> filas(Statement st, String consulta) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (var rs = st.executeQuery(consulta)) {
            int columnas = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                var partes = new ArrayList<String>();
                for (int i = 1; i <= columnas; i++) {
                    partes.add(String.valueOf(rs.getObject(i)));
                }
                filas.add(String.join("|", partes));
            }
        }
        return filas;
    }
}
