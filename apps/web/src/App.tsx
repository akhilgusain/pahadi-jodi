import { useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import { AppBar, Button, CssBaseline, ThemeProvider, Toolbar, Typography, createTheme } from '@mui/material';
import type { AppDispatch, RootState } from './app/store';
import { setSignedIn, toggleDark } from './app/store';
import { tokenStore } from './lib/api';
import AuthPage from './features/auth/AuthPage';
import ProfilePage from './features/profile/ProfilePage';

export default function App() {
  const dispatch = useDispatch<AppDispatch>();
  const { i18n, t } = useTranslation();
  const signedIn = useSelector((s: RootState) => s.auth.signedIn);
  const dark = useSelector((s: RootState) => s.ui.dark);
  const theme = useMemo(() => createTheme({ palette: { mode: dark ? 'dark' : 'light', primary: { main: '#2e7d32' } } }), [dark]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="static" color="default" elevation={0}>
        <Toolbar>
          <Typography sx={{ flexGrow: 1 }} variant="h6">{t('title')}</Typography>
          <Button onClick={() => { const l = i18n.language === 'en' ? 'hi' : 'en'; localStorage.setItem('pj.lang', l); i18n.changeLanguage(l); }}>
            {i18n.language === 'en' ? 'हिन्दी' : 'EN'}
          </Button>
          <Button onClick={() => dispatch(toggleDark())}>{dark ? 'Light' : 'Dark'}</Button>
          {signedIn && <Button onClick={() => { tokenStore.set(null); dispatch(setSignedIn(false)); }}>{t('signout')}</Button>}
        </Toolbar>
      </AppBar>
      {signedIn ? <ProfilePage /> : <AuthPage />}
    </ThemeProvider>
  );
}
