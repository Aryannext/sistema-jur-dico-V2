package co.iuris.sgpj.usuario.dominio;

import java.lang.reflect.Field;

/**
 * Construye roles del catálogo para las pruebas.
 *
 * <p>Vive en este paquete <strong>a propósito</strong>: el constructor de
 * {@link Rol} es {@code protected} porque los cuatro roles los inserta la
 * migración V2 y no se crean desde la aplicación (RN-07). Colocar esta utilidad
 * aquí permite construirlos en pruebas sin abrir un constructor público que el
 * código de producción no necesita — y que alguien podría acabar usando.
 */
public final class RolesDePrueba {

    private RolesDePrueba() {
    }

    public static Rol de(CodigoRol codigo) {
        try {
            Rol rol = new Rol();
            asignar(rol, "codigo", codigo);
            asignar(rol, "nombre", codigo.nombre());
            return rol;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("No se pudo construir el rol de prueba " + codigo, error);
        }
    }

    private static void asignar(Rol rol, String nombreCampo, Object valor)
            throws ReflectiveOperationException {
        Field campo = Rol.class.getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(rol, valor);
    }
}
