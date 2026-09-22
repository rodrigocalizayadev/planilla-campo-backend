package com.planillacampo.planilla_campo.establecimiento.entities;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "establecimiento")
@Getter
@Setter
@NoArgsConstructor
public class Establecimiento extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 200)
    private String ubicacion;

    public Establecimiento(Usuario usuario, String nombre, String ubicacion) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
    }
}
