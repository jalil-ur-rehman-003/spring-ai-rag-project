// Production build, swapped in via angular.json's fileReplacements.
// Relative path: nginx (see nginx.conf) proxies /api/v1/* to the backend
// container, so this works regardless of the domain/IP the frontend is
// served from - no rebuild needed to point at a different host.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
};
