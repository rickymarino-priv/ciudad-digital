package ar.com.ciudaddigital.seguimientoanonimo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y hash del token de seguimiento anónimo (ADR 0017).
 *
 * <p>Sin estado propio (mismo patrón que {@code RespuestasJson} en
 * {@code acceso.internal}): constructor privado, dos métodos estáticos.
 * Ningún módulo consumidor guarda el valor que devuelve {@link #generar()};
 * solo guardan el resultado de pasarlo por {@link #hash(String)} (ADR 0017
 * §2) — el token en claro no se persiste en ningún lado, existe únicamente
 * de paso entre que se genera y que la respuesta HTTP del alta lo devuelve.
 */
public final class TokenDeSeguimiento {

    /**
     * Instancia única: {@link SecureRandom} mantiene su propio estado
     * interno de entropía, así que crear una por llamada solo agregaría
     * costo sin ganar nada (ADR 0017 §1).
     */
    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();

    private TokenDeSeguimiento() {
    }

    /**
     * 32 bytes (256 bits) de {@link SecureRandom}, codificados Base64
     * URL-safe sin padding: 43 caracteres de {@code [A-Za-z0-9_-]}, seguros
     * para viajar en una URL o pegarse en un campo de texto sin escapar
     * (ADR 0017 §1).
     */
    public static String generar() {
        byte[] bytesAleatorios = new byte[32];
        GENERADOR_ALEATORIO.nextBytes(bytesAleatorios);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);
    }

    /**
     * SHA-256 del token en claro, en hexadecimal minúscula (64 caracteres),
     * para guardar y buscar por igualdad exacta sin persistir nunca el
     * valor en claro (ADR 0017 §2).
     *
     * <p>Lanza {@link IllegalArgumentException} si {@code tokenEnClaro} es
     * {@code null} o vacío: nadie debería llamar a este método sin un
     * token real, ni siquiera un llamador interno de {@code reclamos}/
     * {@code mesaentradas} — devolver en cambio un hash "vacío" arbitrario
     * escondería ese error de programación como si fuera una búsqueda
     * legítima que simplemente no encontró nada.
     */
    public static String hash(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw new IllegalArgumentException("No se puede hashear un token vacío.");
        }

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(tokenEnClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es un algoritmo estándar garantizado por el JDK
            // (java.security.MessageDigest), así que esto es un problema
            // del entorno de ejecución, no una condición que el llamador
            // pueda manejar.
            throw new IllegalStateException("SHA-256 no está disponible en este entorno.", e);
        }
    }
}
