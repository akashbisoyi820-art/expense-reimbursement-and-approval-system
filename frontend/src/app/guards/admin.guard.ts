import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

// Only admins may open the policy page; everyone else is sent to their own page
export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);

  if (!localStorage.getItem('token')) {
    return router.createUrlTree(['/login']);
  }

  const role = localStorage.getItem('role');
  if (role === 'ADMIN') {
    return true;
  }
  return router.createUrlTree([role === 'MANAGER' ? '/approvals' : '/dashboard']);
};
