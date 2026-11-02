import { useEffect, useState } from 'react';
import { Link as RouterLink, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import { AppBar, Avatar, BottomNavigation, BottomNavigationAction, Box, Button, Divider, IconButton, ListItemIcon, Menu, MenuItem,
  Paper, Toolbar, Tooltip, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DarkModeIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeIcon from '@mui/icons-material/LightModeOutlined';
import TranslateIcon from '@mui/icons-material/Translate';
import LogoutIcon from '@mui/icons-material/Logout';
import type { AppDispatch, RootState } from './store';
import { setSignedIn, toggleDark } from './store';
import { tokenStore } from '../lib/api';
import { currentRoles } from '../lib/session';
import { NAV } from './nav';
import Logo from '../components/Logo';

export default function Layout() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { t, i18n } = useTranslation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const signedIn = useSelector((s: RootState) => s.auth.signedIn);
  const dark = useSelector((s: RootState) => s.ui.dark);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const roles = signedIn ? currentRoles() : [];
  const visible = NAV.filter((n) => !n.roles || n.roles.some((r) => roles.includes(r)));
  const onLanding = pathname === '/' && !signedIn;

  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);

  const signOut = () => { setAnchor(null); tokenStore.set(null); dispatch(setSignedIn(false)); navigate('/'); };
  const toggleLang = () => { const l = i18n.language === 'en' ? 'hi' : 'en'; localStorage.setItem('pj.lang', l); i18n.changeLanguage(l); };
  const bottom = visible.filter((n) => n.bottom);
  const current = bottom.find((n) => pathname.startsWith(n.path))?.path ?? false;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', pb: signedIn && isMobile ? 9 : 0 }}>
      <AppBar position={onLanding ? 'absolute' : 'sticky'} elevation={0} color="transparent"
        sx={{ backdropFilter: onLanding ? 'none' : 'blur(14px)', bgcolor: onLanding ? 'transparent' : (t) => (t.palette.mode === 'dark' ? 'rgba(13,21,19,0.8)' : 'rgba(246,248,247,0.82)'),
          borderBottom: onLanding ? 0 : 1, borderColor: 'divider', color: onLanding ? '#fff' : 'text.primary' }}>
        <Toolbar sx={{ gap: 1, maxWidth: 1280, width: '100%', mx: 'auto' }}>
          <Box component={RouterLink} to="/" sx={{ textDecoration: 'none', mr: { xs: 0, md: 2 } }}><Logo light={onLanding} /></Box>
          {signedIn && !isMobile && (
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              {visible.filter((n) => n.placement === 'primary').map((n) => (
                <Button key={n.id} component={NavLink} to={n.path} color="inherit" startIcon={<n.icon />}
                  sx={{ color: 'text.secondary', '&.active': { color: 'primary.main', bgcolor: 'action.selected' } }}>
                  {t(`nav.${n.id}`, n.label)}
                </Button>
              ))}
            </Box>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Tooltip title="Language"><Button color="inherit" size="small" startIcon={<TranslateIcon />} onClick={toggleLang} sx={{ minWidth: 0, '& .MuiButton-startIcon': { mr: { xs: 0, sm: 1 } } }}><Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>{i18n.language === 'en' ? 'हिन्दी' : 'EN'}</Box></Button></Tooltip>
          <Tooltip title={dark ? 'Light mode' : 'Dark mode'}><IconButton color="inherit" onClick={() => dispatch(toggleDark())} aria-label="toggle theme">{dark ? <LightModeIcon /> : <DarkModeIcon />}</IconButton></Tooltip>
          {signedIn ? (
            <>
              <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="account menu" sx={{ ml: 0.5 }}>
                <Avatar sx={{ width: 36, height: 36, bgcolor: 'secondary.main', color: 'secondary.contrastText', fontWeight: 700 }}>P</Avatar>
              </IconButton>
              <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)} slotProps={{ paper: { sx: { mt: 1, minWidth: 220, borderRadius: '12px' } } }}>
                {visible.filter((n) => n.placement === 'menu').map((n) => (
                  <MenuItem key={n.id} component={RouterLink} to={n.path} onClick={() => setAnchor(null)}>
                    <ListItemIcon><n.icon fontSize="small" /></ListItemIcon>{t(`nav.${n.id}`, n.label)}
                  </MenuItem>
                ))}
                <Divider />
                <MenuItem onClick={signOut}><ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>{t('signout')}</MenuItem>
              </Menu>
            </>
          ) : pathname !== '/auth' && (
            <Button component={RouterLink} to="/auth" variant={onLanding ? 'contained' : 'outlined'} color={onLanding ? 'secondary' : 'primary'} sx={{ whiteSpace: 'nowrap', px: { xs: 2, sm: 2.5 } }}>{t('signin')}</Button>
          )}
        </Toolbar>
      </AppBar>

      <Box component="main" sx={{ flex: 1 }}><Outlet /></Box>

      {signedIn && isMobile && (
        <Paper elevation={8} sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1200, borderTopLeftRadius: '20px', borderTopRightRadius: '20px', overflow: 'hidden' }}>
          <BottomNavigation showLabels value={current} onChange={(_, v) => navigate(v)} sx={{ height: 68, bgcolor: 'background.paper' }}>
            {bottom.map((n) => (
              <BottomNavigationAction key={n.id} value={n.path} label={t(`nav.${n.id}`, n.label)} icon={<n.icon />}
                sx={{ minWidth: 0, '&.Mui-selected': { color: 'primary.main' }, '& .MuiBottomNavigationAction-label': { fontSize: '0.68rem' } }} />
            ))}
          </BottomNavigation>
        </Paper>
      )}
    </Box>
  );
}
