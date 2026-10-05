import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { enableButtonRipple } from './ripple';

enableButtonRipple();

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withViewTransitions({
        skipInitialTransition: true,
        onViewTransitionCreated: ({ to }) => {
          let route = to;
          while (route.firstChild) {
            route = route.firstChild;
          }
          document.documentElement.dataset['screen'] = route.routeConfig?.path ?? '';
        }
      })
    ),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
