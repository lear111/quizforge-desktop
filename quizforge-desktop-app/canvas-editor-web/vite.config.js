import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [{
    name: 'webview-development-sockets',
    configureServer(server) {
      // WebKit may reset a pending socket when a WebView is disposed/navigated.
      // Keep that connection error from terminating the whole development server.
      server.httpServer?.on('connection', socket => {
        socket.on('error', error => {
          if (error.code !== 'ECONNRESET') server.config.logger.error(String(error));
        });
      });
    }
  }],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    hmr: { host: '127.0.0.1', port: 5173 }
  }
});
