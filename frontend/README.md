# Frontend

React + Vite + TypeScript. `import.meta.glob('./features/*/index.tsx', { eager: true })` descubre features; cada módulo exporta `{ id, routes, nav, widgets? }`. Las pantallas usan tokens y componentes de `src/shared/ui`.

```bash
npm run dev
npm run lint
npm run check:contrast
npm run build
```

No hay pruebas automatizadas frontend por decisión explícita del propietario.
