import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource/poppins/400.css';
import '@fontsource/poppins/500.css';
import '@fontsource/poppins/600.css';
import '@fontsource/poppins/700.css';
import '@fontsource/playfair-display/600.css';
import '@fontsource/playfair-display/700.css';
import { store } from './app/store';
import './app/i18n';
import App from './App';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}><QueryClientProvider client={qc}><BrowserRouter><App /></BrowserRouter></QueryClientProvider></Provider>
  </StrictMode>,
);
