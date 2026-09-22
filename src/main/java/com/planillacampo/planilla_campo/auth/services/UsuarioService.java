package com.planillacampo.planilla_campo.auth.services;

import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.auth.dto.UsuarioResponse;
import com.planillacampo.planilla_campo.auth.entities.Rol;
import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.auth.repositories.UsuarioRepository;
import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Inyección por setter para evitar dependencia circular con SecurityConfig
    @Setter
    private AuthenticationManager authenticationManager;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            EstablecimientoRepository establecimientoRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        // username puede ser el ID (usado por el filtro JWT) o el email (usado por AuthenticationManager)
        try {
            Long id = Long.valueOf(username);
            return usuarioRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + id));
        } catch (NumberFormatException e) {
            return usuarioRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        }
    }

    @Transactional
    public TokenResponse registrar(@NonNull RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new NegocioException("El email ya está registrado");
        }
        var usuario = new Usuario(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nombre(),
                request.apellido(),
                Rol.OPERADOR
        );
        usuarioRepository.save(usuario);

        var establecimiento = new Establecimiento(usuario, request.nombreEstablecimiento(), request.ubicacion());
        establecimientoRepository.save(establecimiento);

        return new TokenResponse(jwtService.generarToken(usuario));
    }

    public TokenResponse login(@NonNull LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return new TokenResponse(jwtService.generarToken(usuario));
    }

    public UsuarioResponse me(@NonNull Usuario usuario) {
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado para el usuario"));
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getRol(),
                establecimiento.getId()
        );
    }
}
