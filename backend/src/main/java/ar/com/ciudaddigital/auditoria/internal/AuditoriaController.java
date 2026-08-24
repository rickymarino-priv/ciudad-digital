package ar.com.ciudaddigital.auditoria.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de auditoría del municipio del request en curso (ADR 0013 §3).
 *
 * <p>Solo lectura: el registro lo genera el sistema al reaccionar a
 * eventos de dominio, no se edita a mano. Sin paginado ni filtros —fuera
 * de alcance de esta rebanada—, así que devuelve la lista completa.
 *
 * <p>No hay {@code DescriptorDeModulo} para {@code auditoria}: es canon
 * base, no un módulo contratable (ADR 0013 §4), así que este endpoint no
 * pasa por el gating de entitlement, solo por el permiso {@code
 * auditoria.ver}.
 */
@RestController
@RequestMapping("/api/auditoria")
class AuditoriaController {

    private final RegistroAuditoriaRepository registros;

    AuditoriaController(RegistroAuditoriaRepository registros) {
        this.registros = registros;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auditoria.ver')")
    List<RegistroResponse> listar() {
        return registros.findAllByOrderByOcurridoEnDesc().stream()
                .map(RegistroResponse::de)
                .toList();
    }

    record RegistroResponse(
            Long id,
            Instant ocurridoEn,
            String actorNombre,
            String actorEmail,
            String accion,
            String entidadTipo,
            String entidadId,
            String detalle) {

        static RegistroResponse de(RegistroAuditoriaEntity registro) {
            return new RegistroResponse(
                    registro.getId(),
                    registro.getOcurridoEn(),
                    registro.getActorNombre(),
                    registro.getActorEmail(),
                    registro.getAccion(),
                    registro.getEntidadTipo(),
                    registro.getEntidadId(),
                    registro.getDetalle());
        }
    }
}
