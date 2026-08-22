import { Routes } from '@angular/router';

import {
  exigeAdministrador, exigeAnonimo, exigeCliente, exigeDespacho, exigePlataforma,
} from './nucleo/sesion.guard';

/**
 * Las rutas del despacho.
 *
 * <p>Se cargan de forma diferida (`loadComponent`): quien está en el ingreso
 * no necesita descargarse el panel, y quien no es administrador nunca pedirá
 * las pantallas de configuración. En un despacho con conexión de Neiva, eso se
 * nota.
 */
export const routes: Routes = [
  {
    path: 'ingreso',
    canActivate: [exigeAnonimo],
    title: 'Entrar · Iuris',
    loadComponent: () => import('./ingreso/ingreso').then(m => m.Ingreso),
  },
  {
    path: '',
    canActivate: [exigeDespacho],
    loadComponent: () => import('./despacho/marco/marco').then(m => m.Marco),
    children: [
      {
        path: 'vencimientos',
        title: 'Vencimientos · Iuris',
        loadComponent: () =>
          import('./despacho/vencimientos/vencimientos').then(m => m.Vencimientos),
      },
      {
        path: 'procesos',
        title: 'Procesos · Iuris',
        loadComponent: () =>
          import('./despacho/procesos/procesos').then(m => m.ListaProcesos),
      },
      {
        // ANTES de 'procesos/:id'. Angular resuelve por orden, y al revés
        // «nuevo» se leería como un identificador: la pantalla intentaría
        // abrir el expediente del proceso número «nuevo».
        path: 'procesos/nuevo',
        title: 'Nuevo proceso · Iuris',
        loadComponent: () =>
          import('./despacho/procesos/nuevo-proceso').then(m => m.NuevoProceso),
      },
      {
        path: 'procesos/:id',
        title: 'Expediente · Iuris',
        loadComponent: () =>
          import('./despacho/expediente/expediente').then(m => m.Expediente),
      },
      {
        path: 'procesos/:id/nueva',
        title: 'Agregar al expediente · Iuris',
        loadComponent: () =>
          import('./despacho/expediente/nueva-pieza').then(m => m.NuevaPieza),
      },
      {
        path: 'procesos/:id/terminos',
        title: 'Términos · Iuris',
        loadComponent: () =>
          import('./despacho/terminos/terminos').then(m => m.Terminos),
      },
      {
        path: 'audiencias',
        title: 'Audiencias · Iuris',
        loadComponent: () =>
          import('./despacho/audiencias/calendario').then(m => m.Calendario),
      },
      {
        path: 'procesos/:id/audiencias',
        title: 'Audiencias del proceso · Iuris',
        loadComponent: () =>
          import('./despacho/audiencias/audiencias-proceso').then(m => m.AudienciasDeProceso),
      },
      {
        path: 'clientes',
        title: 'Clientes · Iuris',
        loadComponent: () =>
          import('./despacho/clientes/clientes').then(m => m.ListaClientes),
      },
      {
        path: 'clientes/:id',
        title: 'Ficha del cliente · Iuris',
        loadComponent: () =>
          import('./despacho/clientes/ficha-cliente').then(m => m.FichaCliente),
      },
      {
        path: 'usuarios',
        canActivate: [exigeAdministrador],
        title: 'Usuarios y roles · Iuris',
        loadComponent: () => import('./cuenta/usuarios').then(m => m.Usuarios),
      },
      {
        path: 'reportes',
        title: 'Reportes · Iuris',
        loadComponent: () => import('./despacho/reportes/reportes').then(m => m.Reportes),
      },
      {
        path: 'configuracion/catalogos',
        title: 'Catálogos · Iuris',
        loadComponent: () => import('./configuracion/catalogos').then(m => m.Catalogos),
      },
      {
        path: 'configuracion/alertas',
        title: 'Configuración de alertas · Iuris',
        loadComponent: () => import('./configuracion/alertas').then(m => m.Alertas),
      },
      { path: 'configuracion', pathMatch: 'full', redirectTo: 'configuracion/catalogos' },
      {
        path: 'alertas',
        title: 'Historial de alertas · Iuris',
        loadComponent: () => import('./despacho/alertas/historial').then(m => m.HistorialAlertas),
      },
      {
        path: 'bitacora',
        canActivate: [exigeAdministrador],
        title: 'Bitácora de auditoría · Iuris',
        loadComponent: () =>
          import('./despacho/bitacora/bitacora').then(m => m.BitacoraDeAuditoria),
      },
      {
        path: 'mi-cuenta',
        title: 'Mi cuenta · Iuris',
        loadComponent: () => import('./cuenta/mi-cuenta').then(m => m.MiCuenta),
      },
      { path: '', pathMatch: 'full', redirectTo: 'vencimientos' },
    ],
  },
  {
    path: 'portal',
    canActivate: [exigeCliente],
    loadComponent: () => import('./portal/marco-portal').then(m => m.MarcoPortal),
    children: [
      {
        path: 'procesos',
        title: 'Mis procesos · Iuris',
        loadComponent: () => import('./portal/mis-procesos').then(m => m.MisProcesos),
      },
      {
        path: 'procesos/:id',
        title: 'Mi proceso · Iuris',
        loadComponent: () => import('./portal/mi-proceso').then(m => m.MiProceso),
      },
      {
        path: 'mi-cuenta',
        title: 'Mi cuenta · Iuris',
        loadComponent: () => import('./cuenta/mi-cuenta').then(m => m.MiCuenta),
      },
      { path: 'audiencias', pathMatch: 'full', redirectTo: 'procesos' },
      { path: '', pathMatch: 'full', redirectTo: 'procesos' },
    ],
  },

  /**
   * La zona de la plataforma. RF-01 · RF-02 · RN-10.
   *
   * <p>Cuelga de la raíz y no de `/plataforma/...` para que el Administrador
   * de Plataforma aterrice en `/despachos`, que es SU pantalla principal, y no
   * en una subcarpeta. Las tres zonas del sistema son hermanas, no una
   * principal con dos anexos.
   */
  {
    path: 'despachos',
    canActivate: [exigePlataforma],
    loadComponent: () => import('./plataforma/marco-plataforma').then(m => m.MarcoPlataforma),
    children: [
      {
        path: '',
        title: 'Despachos · Iuris',
        loadComponent: () => import('./plataforma/despachos').then(m => m.ListaDespachos),
      },
    ],
  },
  {
    path: 'plataforma',
    canActivate: [exigePlataforma],
    loadComponent: () => import('./plataforma/marco-plataforma').then(m => m.MarcoPlataforma),
    children: [
      {
        path: 'mi-cuenta',
        title: 'Mi cuenta · Iuris',
        loadComponent: () => import('./cuenta/mi-cuenta').then(m => m.MiCuenta),
      },
      { path: '', pathMatch: 'full', redirectTo: '/despachos' },
    ],
  },

  // Lo que no exista lleva al panel, no a una pantalla en blanco.
  { path: '**', redirectTo: '' },
];
