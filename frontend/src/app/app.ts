import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * La raíz. No pinta nada: solo aloja la ruta activa.
 *
 * <p>El marco con la barra lateral no vive aquí porque no todas las pantallas
 * lo llevan — el ingreso no, y el portal del cliente llevará otro distinto.
 * Poner el marco en la raíz obligaría a esconderlo con condicionales.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
}
