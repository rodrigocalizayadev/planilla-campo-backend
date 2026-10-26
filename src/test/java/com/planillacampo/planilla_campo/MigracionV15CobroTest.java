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
 * V15 migra datos existentes: las ventas a precio cerrado históricas quedan COBRADAS con un
 * cobro por el total. Se prueba sobre una base aparte: esquema en V14, filas históricas y
 * migración hasta la última versión. No levanta el contexto de Spring.
 */
class MigracionV15CobroTest {

    private static final String BASE = "migracion_v15";

    @Test
    void ventas_historicas_a_precio_cerrado_quedan_cobradas_con_un_cobro_por_el_total() throws SQLException {
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

        Flyway.configure().dataSource(urlBase, usuario, clave).target("14").load().migrate();

        try (Connection con = DriverManager.getConnection(urlBase, usuario, clave);
             Statement st = con.createStatement()) {
            // Sin padres: lo único que importa acá son las columnas de venta
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            // 1: cerrada en ARS por 500.000
            st.execute(ventaHistorica(1, "PRECIO_CERRADO", "LIQUIDADA", "500000.00", "ARS", "1.0000",
                    "500.00", "500000.00"));
            // 2: cerrada en USD por 100 a TC 1000 (equivale a 100.000 ARS)
            st.execute(ventaHistorica(2, "PRECIO_CERRADO", "LIQUIDADA", "100.00", "USD", "1000.0000",
                    "100.00", "100000.00"));
            // 3: cerrada con neto negativo (gastos mayores al bruto): nada para cobrar
            st.execute(ventaHistorica(3, "PRECIO_CERRADO", "LIQUIDADA", "-50.00", "ARS", "1.0000",
                    "-0.05", "-50.00"));
            // 4: consignación sin liquidar
            st.execute(ventaHistorica(4, "CONSIGNACION", "ENTREGADA", null, null, null, null, null));
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        Flyway.configure().dataSource(urlBase, usuario, clave).load().migrate();

        try (Connection con = DriverManager.getConnection(urlBase, usuario, clave);
             Statement st = con.createStatement()) {
            assertThat(filas(st, "SELECT id, estado_cobro, cobrado_ars FROM venta ORDER BY id"))
                    .containsExactly(
                            "1|COBRADA|500000.00",
                            "2|COBRADA|100000.00",
                            "3|COBRADA|0.00",
                            "4|null|0.00");
            assertThat(filas(st, "SELECT venta_id, fecha, monto_importe, monto_moneda, monto_importe_ars "
                    + "FROM cobro ORDER BY venta_id"))
                    .containsExactly(
                            "1|2025-12-01|500000.00|ARS|500000.00",
                            "2|2025-12-01|100.00|USD|100000.00");
        }
    }

    private static String ventaHistorica(int id, String modalidad, String estado,
                                         String neto, String moneda, String tipoCambio,
                                         String netoUsd, String netoArs) {
        return """
                INSERT INTO venta (id, lote_campania_id, comprador_id, calidad, modalidad, estado,
                                   fecha_entrega, cantidad, unidad,
                                   importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
                                   importe_neto_importe_usd, importe_neto_importe_ars,
                                   created_at, updated_at)
                VALUES (%d, 1, 1, 'PRIMERA', '%s', '%s', '2025-12-01', 100.00, 'BOLSA',
                        %s, %s, %s, %s, %s, NOW(6), NOW(6))
                """.formatted(id, modalidad, estado,
                sql(neto), sql(moneda == null ? null : "'" + moneda + "'"), sql(tipoCambio),
                sql(netoUsd), sql(netoArs));
    }

    private static String sql(String valor) {
        return valor == null ? "NULL" : valor;
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
