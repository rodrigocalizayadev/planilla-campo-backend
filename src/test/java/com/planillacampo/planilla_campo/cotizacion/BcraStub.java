package com.planillacampo.planilla_campo.cotizacion;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP local que imita la API Estadísticas Cambiarias del BCRA.
 * Los tests nunca salen a internet.
 */
public class BcraStub implements AutoCloseable {

    private final HttpServer server;
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String cuerpo = vacio();
    private volatile long demoraMs = 0;

    public BcraStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            consultas.add(exchange.getRequestURI().toString());
            try {
                if (demoraMs > 0) {
                    Thread.sleep(demoraMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Responde 200 con las cotizaciones dadas como pares fecha/valor: "2026-11-13", "1543.50", ... */
    public void responderCotizaciones(String... fechaValor) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < fechaValor.length; i += 2) {
            if (!results.isEmpty()) {
                results.append(',');
            }
            results.append("""
                    {"fecha":"%s","detalle":[{"codigoMoneda":"USD","descripcion":"DOLAR E.E.U.U.",\
                    "tipoPase":0.00000000,"tipoCotizacion":%s}]}""".formatted(fechaValor[i], fechaValor[i + 1]));
        }
        responder(200, """
                {"status":200,"metadata":{"resultset":{"count":%d,"offset":0,"limit":1000}},"results":[%s]}"""
                .formatted(fechaValor.length / 2, results));
    }

    public void responder(int status, String cuerpo) {
        this.status = status;
        this.cuerpo = cuerpo;
    }

    public void demorar(long ms) {
        this.demoraMs = ms;
    }

    public List<String> consultas() {
        return consultas;
    }

    public void reiniciar() {
        consultas.clear();
        status = 200;
        cuerpo = vacio();
        demoraMs = 0;
    }

    private static String vacio() {
        return """
                {"status":200,"metadata":{"resultset":{"count":0,"offset":0,"limit":1000}},"results":[]}""";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
