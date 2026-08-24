package ar.com.ciudaddigital.tenants.internal;

/**
 * El login a la API de administración no prosperó.
 *
 * <p>Un único error para email inexistente, contraseña equivocada o
 * usuario desactivado, a propósito: distinguirlos le confirma a quien
 * prueba credenciales cuáles de los emails que probó existen.
 */
class CredencialesDePlataformaInvalidas extends RuntimeException {

    CredencialesDePlataformaInvalidas() {
        super("El correo electrónico o la contraseña no son correctos.");
    }
}
