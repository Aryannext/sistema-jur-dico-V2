import { Routes } from '@angular/router';

import { exigeAnonimo, exigeSesion } from './nucleo/sesion.guard';

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
    canActivate: [exigeSesion],
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
        path: 'mi-cuenta',
        title: 'Mi cuenta · Iuris',
        loadComponent: () => import('./cuenta/mi-cuenta').then(m => m.MiCuenta),
      },
      {
        path: 'usuarios',
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
      { path: '', pathMatch: 'full', redirectTo: 'vencimientos' },
    ],
  },
  // Lo que no exista lleva al panel, no a una pantalla en blanco.
  { path: '**', redirectTo: '' },
];
