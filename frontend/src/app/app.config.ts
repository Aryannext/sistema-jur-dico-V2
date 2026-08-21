import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import {
  provideHttpClient,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';

import { routes } from './app.routes';
import { conCredenciales } from './nucleo/credenciales.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),

    provideHttpClient(
      // El backend usa CookieCsrfTokenRepository con la cookie XSRF-TOKEN y la
      // cabecera X-XSRF-TOKEN. Son exactamente los nombres que Angular espera,
      // así que la protección CSRF queda cubierta sin escribir código: basta
      // con nombrarla. Declararlo explícitamente —y no confiar en el defecto—
      // deja constancia de que los dos lados hablan del mismo mecanismo.
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
      withInterceptors([conCredenciales]),
    ),
  ],
};
