package com.planillacampo.planilla_campo;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para tests de integración con MySQL 8.4 real via Testcontainers.
 * La imagen está fijada a mysql:8.4, misma versión que docker-compose.yml.
 *
 * El contenedor es estático a nivel de clase (no de instancia) y es compartido
 * entre todas las subclases gracias al campo static en esta clase base.
 * Esto evita que cada subclase levante su propio contenedor y que Spring Boot
 * reutilice el mismo ApplicationContext con una URL de BD diferente.
 *
 * El @BeforeEach aquí trunca todas las tablas en orden seguro (FK primero)
 * usando SET FOREIGN_KEY_CHECKS=0, para que los tests de cada suite no
 * interfieran entre sí sin importar el orden de ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("planilla_campo_test")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE cobro");
        jdbc.execute("TRUNCATE TABLE liquidacion_venta");
        jdbc.execute("TRUNCATE TABLE venta");
        jdbc.execute("TRUNCATE TABLE cosecha");
        jdbc.execute("TRUNCATE TABLE costo_indirecto_parte");
        jdbc.execute("TRUNCATE TABLE costo_indirecto");
        jdbc.execute("TRUNCATE TABLE aplicacion_insumo");
        jdbc.execute("TRUNCATE TABLE tarea");
        jdbc.execute("TRUNCATE TABLE lote_campania");
        jdbc.execute("TRUNCATE TABLE campania");
        jdbc.execute("TRUNCATE TABLE lote");
        jdbc.execute("TRUNCATE TABLE comprador");
        jdbc.execute("TRUNCATE TABLE insumo");
        jdbc.execute("TRUNCATE TABLE cultivo");
        jdbc.execute("TRUNCATE TABLE establecimiento");
        jdbc.execute("TRUNCATE TABLE usuario");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
