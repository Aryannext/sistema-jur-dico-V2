import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Manda la cookie de sesión en cada petición a la API.
 *
 * <p>En desarrollo el proxy hace que todo sea del mismo origen y el navegador
 * la mandaría igual. En el VPS el frontend y la API pueden servirse desde
 * origenes distintos, y ahí `withCredentials` es la diferencia entre estar
 * dentro y recibir 401 sin entender por qué.
 *
 * <p>Se limita a las rutas propias a propósito: mandar credenciales a
 * cualquier URL que alguien escriba en un `http.get` sería exactamente cómo se
 * filtra una sesión a un tercero.
 */
export const conCredenciales: HttpInterceptorFn = (peticion, siguiente) => {
  const propia = peticion.url.startsWith('/api') || peticion.url.startsWith('/logout');

  return siguiente(propia ? peticion.clone({ withCredentials: true }) : peticion);
};
