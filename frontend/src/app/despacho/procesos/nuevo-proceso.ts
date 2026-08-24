import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Autenticacion } from '../../nucleo/autenticacion';
import { Cuenta } from '../../nucleo/cuenta.servicio';
import { Cliente, Clientes } from '../clientes/clientes.servicio';
import { Usuario, ValorCatalogo } from '../../nucleo/modelos';
import { Procesos as ServicioProcesos } from './procesos.servicio';
import { mensajeDeError } from '../../nucleo/mensajes';

/**
 * Alta de un proceso. RF-11 · RF-13 · HU-11 · RN-15 · RN-31.
 *
 * <h2>Aquí es donde un proceso queda vinculado a su cliente</h2>
 *
 * <p>No hay «asignar proceso a cliente» en ninguna otra parte, y no falta:
 * <strong>crear el proceso ES vincularlo</strong>. El cliente titular es un
 * campo obligatorio del alta porque un proceso sin titular no existe en la
 * realidad que este sistema modela — y si se pudiera crear primero y asignar
 * después, un fallo entre los dos pasos dejaría procesos que ningún cliente
 * vería nunca en su portal.
 *
 * <p>Un cliente sí puede tener varios procesos (RN-15). La relación es de uno
 * a muchos, y por eso se elige el cliente desde el proceso y no al revés.
 *
 * <h2>Por qué el responsable casi nunca se elige</h2>
 *
 * <p>RN-31 exige que el responsable sea abogado. Quien está creando el proceso
 * normalmente lo es, así que se preselecciona a sí mismo: obligar a elegirse en
 * un desplegable de un solo nombre es fricción sin ninguna ganancia.
 *
 * <p>La lista de abogados solo se pide cuando el usuario es Administrador de
 * Despacho, que es el único que puede consultarla —`/api/usuarios` es suyo—.
 * Pedirla siempre haría que a un abogado raso le fallara la pantalla con un
 * 403 al abrirla, por un dato que además no necesita.
 */
@Component({
  selector: 'sgpj-nuevo-proceso',
  imports: [RouterLink],
  templateUrl: './nuevo-proceso.html',
  styleUrl: './nuevo-proceso.css',
})
export class NuevoProceso {

  private readonly servicio = inject(ServicioProcesos);
  private readonly servicioClientes = inject(Clientes);
  private readonly cuenta = inject(Cuenta);
  private readonly router = inject(Router);
  protected readonly autenticacion = inject(Autenticacion);

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly juzgados = signal<ValorCatalogo[]>([]);
  protected readonly tipos = signal<ValorCatalogo[]>([]);
  protected readonly estados = signal<ValorCatalogo[]>([]);
  protected readonly abogados = signal<Usuario[]>([]);

  protected readonly radicado = signal('');
  protected readonly clienteTitularId = signal<number | null>(null);
  protected readonly juzgadoId = signal<number | null>(null);
  protected readonly tipoProcesoId = signal<number | null>(null);
  protected readonly estadoProcesalId = signal<number | null>(null);
  protected readonly abogadoResponsableId = signal<number | null>(null);
  protected readonly descripcion = signal('');

  /**
   * El aviso de RN-17b, si el radicado no tiene forma de radicado.
   *
   * <p>Se consulta al salir del campo y no en cada tecla: mientras se escribe,
   * un radicado de 23 dígitos está incompleto casi todo el tiempo, y avisar en
   * cada pulsación sería gritarle al abogado por no haber terminado.
   */
  protected readonly avisoRadicado = signal<string | null>(null);

  /** Si el despacho todavía no tiene ningún cliente, no hay proceso que crear. */
  protected readonly sinClientes = computed(
    () => !this.cargando() && this.clientes().length === 0);

  /**
   * Cuáles de los tres catálogos están vacíos, con su nombre.
   *
   * <p>Nombrarlos importa. Antes la pantalla decía «falta al menos uno de los
   * tres» sin decir cuál, y el caso normal es tener dos llenos y uno vacío:
   * el abogado iba a Catálogos, veía siete tipos de proceso activos y concluía
   * que el sistema estaba roto.
   *
   * <p>El que casi siempre falta es <strong>Juzgado</strong>, y no por error:
   * **D-17** decidió no sembrarlo porque cada despacho trabaja con los suyos.
   * Un aviso que no puede señalar la causa convierte una decisión de diseño en
   * lo que parece un fallo.
   */
  protected readonly catalogosVacios = computed(() => {
    const faltan: string[] = [];
    if (this.juzgados().length === 0) faltan.push('Juzgado');
    if (this.tipos().length === 0) faltan.push('Tipo de proceso');
    if (this.estados().length === 0) faltan.push('Estado procesal');
    return faltan;
  });

  protected readonly catalogoIncompleto = computed(
    () => !this.cargando() && this.catalogosVacios().length > 0);

  /** Si lo único que falta son juzgados, hay 60 de Neiva listos para agregar. */
  protected readonly soloFaltanJuzgados = computed(() => {
    const faltan = this.catalogosVacios();
    return faltan.length === 1 && faltan[0] === 'Juzgado';
  });

  protected readonly puedeCrear = computed(() =>
    !this.guardando()
    && this.radicado().trim().length > 0
    && this.clienteTitularId() !== null
    && this.juzgadoId() !== null
    && this.tipoProcesoId() !== null
    && this.estadoProcesalId() !== null
    && this.abogadoResponsableId() !== null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [clientes, juzgados, tipos, estados] = await Promise.all([
        this.servicioClientes.listar(),
        this.servicio.catalogo('JUZGADO'),
        this.servicio.catalogo('TIPO_PROCESO'),
        this.servicio.catalogo('ESTADO_PROCESAL'),
      ]);

      this.clientes.set(clientes);
      this.juzgados.set(juzgados);
      this.tipos.set(tipos);
      this.estados.set(estados);

      // El estado inicial razonable es «Activo», que es un valor protegido
      // (RN-06a) y por tanto siempre está: un proceso que se acaba de abrir no
      // nace archivado.
      const activo = estados.find(e => e.nombre === 'Activo');
      if (activo) this.estadoProcesalId.set(activo.id);

      await this.cargarAbogados();

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Quién puede ser responsable. RN-31.
   *
   * <p>Si quien crea el proceso es abogado, queda él por defecto. Si además es
   * administrador, se ofrece la lista para poder asignárselo a otro.
   */
  private async cargarAbogados(): Promise<void> {
    const yo = this.autenticacion.sesion();

    if (this.autenticacion.esAbogado() && yo) {
      this.abogadoResponsableId.set(yo.usuarioId);
    }

    if (!this.autenticacion.esAdministradorDeDespacho()) return;

    // Solo activos y solo abogados: asignarle un proceso a alguien desactivado
    // dejaría un proceso cuyas alertas no las recibe nadie.
    const usuarios = await this.cuenta.usuarios();
    this.abogados.set(usuarios.filter(
      u => u.activo && u.roles.some(r => r.codigo === 'ABOGADO')));

    if (this.abogadoResponsableId() === null && this.abogados().length === 1) {
      this.abogadoResponsableId.set(this.abogados()[0].id);
    }
  }

  protected async crear(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeCrear()) return;

    this.guardando.set(true);
    this.error.set(null);
    try {
      const proceso = await this.servicio.crear({
        radicado: this.radicado().trim(),
        juzgadoId: this.juzgadoId()!,
        tipoProcesoId: this.tipoProcesoId()!,
        estadoProcesalId: this.estadoProcesalId()!,
        clienteTitularId: this.clienteTitularId()!,
        abogadoResponsableId: this.abogadoResponsableId()!,
        descripcion: this.descripcion().trim() || null,
      });

      // Se va al expediente y no a la lista: quien acaba de abrir un proceso
      // casi siempre tiene algo que guardar en él, y volver a la lista lo
      // obligaría a buscarlo entre todos los demás.
      await this.router.navigate(['/procesos', proceso.id]);

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.guardando.set(false);
    }
  }

  protected escribir(campo: 'radicado' | 'descripcion', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
    this.error.set(null);
    if (campo === 'radicado') {
      this.avisoRadicado.set(null);   // se recalcula al salir del campo
    }
  }

  /**
   * RN-17b: comprueba la forma del radicado al salir del campo.
   *
   * <p>Un fallo aquí <strong>no molesta al usuario</strong>: es un aviso de
   * cortesía, y si el backend no responde lo peor que pasa es que no lo vea.
   * Poner una franja roja porque no se pudo comprobar un formato sería
   * convertir una ayuda en un estorbo.
   */
  protected async comprobarRadicado(): Promise<void> {
    const valor = this.radicado().trim();
    if (!valor) {
      this.avisoRadicado.set(null);
      return;
    }
    try {
      const { aviso } = await this.servicio.avisoDeRadicado(valor);
      this.avisoRadicado.set(aviso);
    } catch {
      this.avisoRadicado.set(null);
    }
  }

  protected elegir(
    campo: 'clienteTitularId' | 'juzgadoId' | 'tipoProcesoId'
      | 'estadoProcesalId' | 'abogadoResponsableId',
    evento: Event,
  ): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this[campo].set(valor === '' ? null : Number(valor));
    this.error.set(null);
  }

}
