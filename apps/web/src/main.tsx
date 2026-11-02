import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { store } from './app/store';
import './app/i18n';
import App from './App';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}><QueryClientProvider client={qc}><App /></QueryClientProvider></Provider>
  </StrictMode>,
);
