import { defineConfig } from 'vite';
import path from 'node:path';

export default defineConfig({
  build: {
    outDir: 'src/main/resources/static/assets',
    emptyOutDir: true,
    assetsDir: '',
    manifest: true,
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'src/main/frontend/main.js'),
        receipts: path.resolve(__dirname, 'src/main/frontend/receipts.js'),
        receiptOverview: path.resolve(__dirname, 'src/main/frontend/receipt-overview.js'),
        receiptUploads: path.resolve(__dirname, 'src/main/frontend/receipt-uploads.js'),
        priceHistory: path.resolve(__dirname, 'src/main/frontend/price-history.js')
      }
    }
  }
});
