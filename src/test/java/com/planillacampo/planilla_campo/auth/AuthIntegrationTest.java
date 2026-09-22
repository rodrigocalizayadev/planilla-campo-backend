package com.planillacampo.planilla_campo.auth;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.auth.dto.UsuarioResponse;
import com.planillacampo.planilla_campo.auth.repositories.UsuarioRepository;
import com.planillacampo.planilla_campo.catalogo.repositories.CompradorRepository;
import com.planillacampo.planilla_campo.catalogo.repositories.CultivoRepository;
import com.planillacampo.planilla_campo.catalogo.repositories.InsumoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.lote.repositories.LoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    EstablecimientoRepository establecimientoRepository;

    @Autowired
    LoteRepository loteRepository;

    @Autowired
    CultivoRepository cultivoRepository;

    @Autowired
    InsumoRepository insumoRepository;

    @Autowired
    CompradorRepository compradorRepository;

    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        loteRepository.deleteAll();
        compradorRepository.deleteAll();
        insumoRepository.deleteAll();
        cultivoRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void registro_devuelve_token() {
        var request = new RegistroRequest(
                "productor@ejemplo.com", "secreto123",
                "Juan", "Perez", "La Esperanza", "Mendoza");

        TokenResponse response = client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TokenResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void registro_crea_establecimiento() {
        var request = new RegistroRequest(
                "productor2@ejemplo.com", "secreto123",
                "Maria", "Lopez", "El Paraiso", "San Juan");

        client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        var usuario = usuarioRepository.findByEmail("productor2@ejemplo.com").orElseThrow();
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId());
        assertThat(establecimiento).isPresent();
        assertThat(establecimiento.get().getNombre()).isEqualTo("El Paraiso");
    }

    @Test
    void login_con_credenciales_validas_devuelve_token() {
        registrar("login@ejemplo.com", "miClave456");

        var request = new LoginRequest("login@ejemplo.com", "miClave456");
        TokenResponse response = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TokenResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void login_con_clave_incorrecta_devuelve_401() {
        registrar("auth@ejemplo.com", "claveCorrecta");

        var requestSpec = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("auth@ejemplo.com", "claveIncorrecta"))
                .retrieve();

        assertThatThrownBy(requestSpec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void me_sin_token_devuelve_401() {
        var requestSpec = client.get()
                .uri("/api/auth/me")
                .retrieve();

        assertThatThrownBy(requestSpec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void me_con_token_valido_devuelve_datos_del_usuario() {
        registrar("me@ejemplo.com", "clave1234");
        String token = login("me@ejemplo.com", "clave1234");

        UsuarioResponse response = client.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(UsuarioResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("me@ejemplo.com");
        assertThat(response.establecimientoId()).isNotNull().isPositive();
    }

    @Test
    void establecimientoId_se_resuelve_del_token_no_del_request() {
        // La API no acepta establecimientoId en el request; viene siempre del contexto
        registrar("aislamiento@ejemplo.com", "clave1234");
        String token = login("aislamiento@ejemplo.com", "clave1234");

        UsuarioResponse response = client.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(UsuarioResponse.class);

        assertThat(response.establecimientoId()).isPositive();
    }

    @Test
    void registro_con_email_duplicado_devuelve_error() {
        registrar("dup@ejemplo.com", "clave1234");

        var requestSpec = client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest("dup@ejemplo.com", "otraClave", "Otro", "Usuario", "Otro Campo", null))
                .retrieve();

        assertThatThrownBy(requestSpec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode().value())
                        .isIn(400, 409, 422));
    }

    // helpers

    private void registrar(String email, String password) {
        client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, password, "Nombre", "Apellido", "Campo", null))
                .retrieve()
                .toBodilessEntity();
    }

    private String login(String email, String password) {
        TokenResponse response = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .body(TokenResponse.class);
        return response.token();
    }
}
