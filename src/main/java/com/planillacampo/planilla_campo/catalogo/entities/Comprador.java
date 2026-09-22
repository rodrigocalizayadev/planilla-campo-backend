package com.planillacampo.planilla_campo.catalogo.entities;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comprador")
@Getter
@Setter
@NoArgsConstructor
public class Comprador extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 13)
    private String cuit;

    @Column(length = 40)
    private String telefono;

    @Column(length = 120)
    private String contacto;

    @Column(length = 120)
    private String localidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_habitual", length = 20)
    private ModalidadVenta modalidadHabitual;

    @Column(nullable = false)
    private boolean activo = true;

    public Comprador(Establecimiento establecimiento, String nombre, String cuit,
                     String telefono, String contacto, String localidad,
                     ModalidadVenta modalidadHabitual) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.cuit = cuit;
        this.telefono = telefono;
        this.contacto = contacto;
        this.localidad = localidad;
        this.modalidadHabitual = modalidadHabitual;
    }
}
