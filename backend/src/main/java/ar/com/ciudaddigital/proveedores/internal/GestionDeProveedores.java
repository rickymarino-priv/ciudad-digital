package ar.com.ciudaddigital.proveedores.internal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.seguimientoanonimo.TokenDeSeguimiento;

/**
 * Alta, listado, cambio de estado y consulta anónima por token de los
 * proveedores del municipio del request en curso (ADR 0014, ADR 0017).
 */
@Service
class GestionDeProveedores {

    private static final int LARGO_MAXIMO_RAZON_SOCIAL = 200;
    private static final int LARGO_MAXIMO_EMAIL_CONTACTO = 200;
    private static final int LARGO_MAXIMO_TELEFONO_CONTACTO = 50;
    private static final int LARGO_MAXIMO_DOMICILIO = 300;
    private static final int LARGO_MAXIMO_DOCUMENTACION_ADICIONAL = 500;

    /** Un CUIT normalizado tiene exactamente 11 dígitos (sin validar el dígito verificador, fuera de alcance). */
    private static final int LARGO_CUIT_NORMALIZADO = 11;

    /**
     * Transiciones válidas de la revisión, codificadas acá, no en la
     * entidad ni en un motor genérico de workflow (ADR 0014 §3): una
     * única decisión del municipio, sin pasos intermedios.
     */
    private static final Map<EstadoDeProveedor, Set<EstadoDeProveedor>> TRANSICIONES_VALIDAS =
            new EnumMap<>(Map.of(
                    EstadoDeProveedor.PENDIENTE,
                    EnumSet.of(EstadoDeProveedor.APROBADO, EstadoDeProveedor.RECHAZADO),
                    EstadoDeProveedor.APROBADO, EnumSet.noneOf(EstadoDeProveedor.class),
                    EstadoDeProveedor.RECHAZADO, EnumSet.noneOf(EstadoDeProveedor.class)));

    private final ProveedorRepository proveedores;

    GestionDeProveedores(ProveedorRepository proveedores) {
        this.proveedores = proveedores;
    }

    @Transactional("tenantTransactionManager")
    ProveedorCreado registrar(String razonSocial, String cuit, RubroProveedor rubro, String emailContacto,
            String telefonoContacto, String domicilio, boolean declaraConstanciaAfip,
            boolean declaraSeguroResponsabilidadCivil, boolean declaraCertificadoAntecedentes,
            String documentacionAdicional) {

        if (razonSocial == null || razonSocial.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la razón social.");
        }
        if (razonSocial.length() > LARGO_MAXIMO_RAZON_SOCIAL) {
            throw new SolicitudInvalida(
                    "La razón social no puede superar los " + LARGO_MAXIMO_RAZON_SOCIAL + " caracteres.");
        }

        String cuitNormalizado = normalizarCuit(cuit);
        if (proveedores.findByCuit(cuitNormalizado).isPresent()) {
            throw new SolicitudInvalida("Ya existe un proveedor registrado con ese CUIT.");
        }

        if (rubro == null) {
            throw new SolicitudInvalida("Hay que indicar un rubro.");
        }
        if (emailContacto == null || emailContacto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un email de contacto.");
        }
        if (emailContacto.length() > LARGO_MAXIMO_EMAIL_CONTACTO) {
            throw new SolicitudInvalida(
                    "El email de contacto no puede superar los " + LARGO_MAXIMO_EMAIL_CONTACTO + " caracteres.");
        }
        if (telefonoContacto == null || telefonoContacto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un teléfono de contacto.");
        }
        if (telefonoContacto.length() > LARGO_MAXIMO_TELEFONO_CONTACTO) {
            throw new SolicitudInvalida(
                    "El teléfono de contacto no puede superar los " + LARGO_MAXIMO_TELEFONO_CONTACTO
                            + " caracteres.");
        }
        if (domicilio == null || domicilio.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un domicilio.");
        }
        if (domicilio.length() > LARGO_MAXIMO_DOMICILIO) {
            throw new SolicitudInvalida(
                    "El domicilio no puede superar los " + LARGO_MAXIMO_DOMICILIO + " caracteres.");
        }
        if (documentacionAdicional != null && documentacionAdicional.length() > LARGO_MAXIMO_DOCUMENTACION_ADICIONAL) {
            throw new SolicitudInvalida(
                    "La documentación adicional no puede superar los "
                            + LARGO_MAXIMO_DOCUMENTACION_ADICIONAL + " caracteres.");
        }

        // El token en claro solo existe acá, entre que se genera y que el
        // record de retorno lo lleva hasta el controller (ADR 0017 §4): ni
        // la entidad ni el repositorio lo vuelven a ver.
        String tokenDeSeguimiento = TokenDeSeguimiento.generar();
        ProveedorEntity proveedor = ProveedorEntity.nuevo(razonSocial, cuitNormalizado, rubro, emailContacto,
                telefonoContacto, domicilio, declaraConstanciaAfip, declaraSeguroResponsabilidadCivil,
                declaraCertificadoAntecedentes, documentacionAdicional, TokenDeSeguimiento.hash(tokenDeSeguimiento));
        return new ProveedorCreado(proveedores.save(proveedor), tokenDeSeguimiento);
    }

    List<ProveedorEntity> listar() {
        return proveedores.findAllByOrderByCreadoEnDesc();
    }

    /**
     * Consulta anónima por posesión del token (ADR 0017 §4): un
     * {@code token} vacío se trata igual que "no encontrado", nunca como
     * {@link SolicitudInvalida}, para no distinguirle a quien prueba
     * tokens al azar un formato inválido de un token que no existe.
     */
    ProveedorEntity consultarPorToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenNoEncontrado("No encontramos un proveedor con ese código.");
        }

        return proveedores.findByTokenHash(TokenDeSeguimiento.hash(token))
                .orElseThrow(() -> new TokenNoEncontrado("No encontramos un proveedor con ese código."));
    }

    @Transactional("tenantTransactionManager")
    ProveedorEntity cambiarEstado(Long id, EstadoDeProveedor nuevoEstado, String comentario) {
        ProveedorEntity proveedor = proveedores.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el proveedor " + id + "."));

        if (nuevoEstado == null) {
            throw new SolicitudInvalida("Hay que indicar el estado nuevo.");
        }

        EstadoDeProveedor estadoActual = proveedor.getEstado();
        if (!TRANSICIONES_VALIDAS.get(estadoActual).contains(nuevoEstado)) {
            throw new SolicitudInvalida("No se puede pasar de " + estadoActual + " a " + nuevoEstado + ".");
        }

        proveedor.cambiarEstado(nuevoEstado, comentario);
        return proveedores.save(proveedor);
    }

    /**
     * Quita todo carácter que no sea dígito y exige exactamente 11 (sin
     * validar el dígito verificador, fuera de alcance de esta rebanada),
     * para que dos altas con el mismo CUIT en formatos de entrada
     * distintos (con o sin guiones) no evadan la unicidad de abajo.
     */
    private static String normalizarCuit(String cuit) {
        if (cuit == null || cuit.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un CUIT.");
        }

        String soloDigitos = cuit.replaceAll("\\D", "");
        if (soloDigitos.length() != LARGO_CUIT_NORMALIZADO) {
            throw new SolicitudInvalida("El CUIT tiene que tener 11 dígitos.");
        }

        return soloDigitos.substring(0, 2) + "-" + soloDigitos.substring(2, 10) + "-" + soloDigitos.substring(10);
    }

    /**
     * Resultado del alta: además del proveedor, el token en claro para que
     * el controller lo devuelva en la respuesta HTTP —la única vez que
     * existe fuera de este método— sin forzarlo a volver a tocar el
     * servicio (ADR 0017 §4).
     */
    record ProveedorCreado(ProveedorEntity proveedor, String tokenDeSeguimiento) {
    }
}
