package ar.com.ciudaddigital.acceso.internal;

/**
 * El login no prosperó.
 *
 * <p>Un único error para todos los casos —email inexistente, contraseña
 * equivocada, usuario desactivado— a propósito: distinguirlos le confirma a
 * quien prueba credenciales cuáles de los emails que probó existen en este
 * municipio.
 */
class CredencialesInvalidas extends RuntimeException {

    CredencialesInvalidas() {
        super("El correo electrónico o la contraseña no son correctos.");
    }
}
