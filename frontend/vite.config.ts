import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

// `npm run dev`   serves the app with hot reload; run the Java side with `mvn compile exec:exec -Dqueryexe.dev=true`.
// `npm run build` writes the app as separate files into src/main/resources/ui, which Java serves with loadResources.
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  build: { outDir: '../src/main/resources/ui', emptyOutDir: true, target: 'es2022' },
});
