import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * La página de presentación. Sin backend, y a propósito.
 *
 * <h2>Qué hace y qué no</h2>
 *
 * <p>Explica el producto y lleva a una conversación. <strong>No registra a
 * nadie</strong>: el alta de un despacho la hace el Administrador de Plataforma
 * (RF-01), y esa decisión no cambia por tener una página pública. Por eso aquí
 * no hay formulario, ni entidad nueva, ni un solo dato que guardar.
 *
 * <h2>Por qué abre con un miedo y no con una lista de funciones</h2>
 *
 * <p>Un consultorio de Neiva no compra un gestor documental: compra
 * <strong>no volver a perder un término</strong>. La propuesta que originó este
 * sistema lo dice en su primera línea —«los términos judiciales vencen por falta
 * de seguimiento»— y ese es el argumento, no el calendario ni los reportes, que
 * los tiene cualquiera.
 *
 * <h2>Por qué hay una sección entera sobre desconfianza</h2>
 *
 * <p>Porque es la objeción real. Un abogado que pone expedientes bajo reserva
 * profesional en un sistema ajeno va a preguntarse quién más los ve, y esa
 * pregunta se responde en la página o se responde en la primera reunión — y
 * para entonces ya decidió que no.
 *
 * <p>Lo que se afirma ahí <strong>está construido y verificado</strong>: el
 * cifrado en reposo (CA-15.2), la bitácora inalterable (RF-08) y el aislamiento
 * entre despachos (41 pruebas, CA-41.3). No se promete nada que el sistema no
 * haga — es la misma regla que obligó a corregir el «6:00 a.m.» del panel.
 */
@Component({
  selector: 'sgpj-presentacion',
  imports: [RouterLink],
  templateUrl: './presentacion.html',
  styleUrl: './presentacion.css',
})
export class Presentacion {

  /**
   * Los datos de contacto. <strong>Hay que reemplazarlos.</strong>
   *
   * <p>Se dejan aquí arriba y marcados en vez de repartidos por la plantilla:
   * son lo único de esta página que cambia, y buscarlos entre el HTML sería la
   * forma más segura de publicarla con un número que no es.
   */
  protected readonly contacto = {
    whatsapp: '573000000000',            // ← REEMPLAZAR por el número real
    correo: 'contacto@iuris.co',         // ← REEMPLAZAR por el correo real
    ciudad: 'Neiva, Huila',
  };

  protected get enlaceWhatsapp(): string {
    const texto = encodeURIComponent(
      'Buen día. Vi Iuris y quisiera saber cómo funciona para mi despacho.');
    return `https://wa.me/${this.contacto.whatsapp}?text=${texto}`;
  }

  protected get enlaceCorreo(): string {
    const asunto = encodeURIComponent('Quiero conocer Iuris');
    return `mailto:${this.contacto.correo}?subject=${asunto}`;
  }
}
