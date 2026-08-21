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
      { path: '', pathMatch: 'full', redirectTo: 'vencimientos' },
    ],
  },
  // Lo que no exista lleva al panel, no a una pantalla en blanco.
  { path: '**', redirectTo: '' },
];
