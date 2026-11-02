import { useMemo } from 'react';
import { useSelector } from 'react-redux';
import { CssBaseline, ThemeProvider } from '@mui/material';
import type { RootState } from './app/store';
import { buildTheme } from './app/theme';
import AppRoutes from './app/routes';

export default function App() {
  const dark = useSelector((s: RootState) => s.ui.dark);
  const theme = useMemo(() => buildTheme(dark ? 'dark' : 'light'), [dark]);
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppRoutes />
    </ThemeProvider>
  );
}
