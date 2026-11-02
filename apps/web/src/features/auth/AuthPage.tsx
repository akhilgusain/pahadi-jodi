import { useEffect, useState } from 'react';
import { Link as RouterLink, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useDispatch, useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, CircularProgress, IconButton, InputAdornment, LinearProgress, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import EmailIcon from '@mui/icons-material/EmailOutlined';
import LockIcon from '@mui/icons-material/LockOutlined';
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOffOutlined';
import { api, tokenStore, type Tokens } from '../../lib/api';
import { setSignedIn, type RootState } from '../../app/store';
import MountainRange from '../../components/MountainHero';
import Logo from '../../components/Logo';
import { SERIF } from '../../app/theme';

const schema = z.object({ email: z.string().email('Enter a valid email'), password: z.string().min(10, 'Use at least 10 characters') });
type Form = z.infer<typeof schema>;

function strength(p: string) {
  let s = 0;
  if (p.length >= 10) s++; if (p.length >= 14) s++;
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) s++;
  if (/\d/.test(p) && /[^A-Za-z0-9]/.test(p)) s++;
  return s; // 0..4
}

export default function AuthPage() {
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const loc = useLocation();
  const [params] = useSearchParams();
  const signedIn = useSelector((s: RootState) => s.auth.signedIn);
  const [mode, setMode] = useState<'login' | 'register'>(params.get('mode') === 'register' ? 'register' : 'login');
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });
  const pw = watch('password') ?? '';
  const sc = strength(pw);

  useEffect(() => { setError(null); }, [mode]);
  if (signedIn) return <Navigate to="/profile" replace />;

  const onSubmit = handleSubmit(async (v) => {
    setError(null);
    try {
      const tokens = await api<Tokens>(`/api/v1/auth/${mode}`, { method: 'POST', body: JSON.stringify(v) });
      tokenStore.set(tokens);
      dispatch(setSignedIn(true));
      navigate((loc.state as { from?: string } | null)?.from ?? '/profile', { replace: true });
    } catch (e) { setError((e as Error).message); reset({ email: v.email, password: '' }); }
  });

  return (
    <Box sx={{ minHeight: 'calc(100vh - 64px)', display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
      {/* Illustration panel */}
      <Box sx={{ position: 'relative', overflow: 'hidden', display: { xs: 'none', md: 'flex' }, flexDirection: 'column', justifyContent: 'space-between', p: 6, color: '#fff',
        background: 'radial-gradient(700px 400px at 80% 0%, rgba(242,163,58,0.35), transparent 60%), linear-gradient(180deg,#0B332B 0%, #1B5E4B 60%, #4A7A8F 100%)' }}>
        <Box component={RouterLink} to="/" sx={{ textDecoration: 'none', position: 'relative', zIndex: 2 }}><Logo light /></Box>
        <Box sx={{ position: 'relative', zIndex: 2, maxWidth: 440, mb: 18 }}>
          <Typography sx={{ fontFamily: SERIF, fontSize: '2.4rem', lineHeight: 1.2, fontWeight: 700 }}>
            “Rishte banate hain <Box component="span" sx={{ color: 'secondary.main', fontStyle: 'italic' }}>pahaad</Box>, jodte hain parivaar.”
          </Typography>
          <Typography sx={{ mt: 2, color: 'rgba(255,255,255,0.8)' }}>Join families from all thirteen districts of Uttarakhand.</Typography>
        </Box>
        <MountainRange height={220} />
      </Box>

      {/* Form panel */}
      <Box sx={{ display: 'grid', placeItems: 'center', p: { xs: 2.5, sm: 6 } }}>
        <Box component="form" onSubmit={onSubmit} noValidate sx={{ width: '100%', maxWidth: 420 }}>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h4">{mode === 'login' ? 'Welcome back' : 'Create your account'}</Typography>
              <Typography color="text.secondary" sx={{ mt: 0.5 }}>{mode === 'login' ? 'Sign in to see your matches.' : 'It is free and takes less than a minute.'}</Typography>
            </Box>
            <Tabs value={mode} onChange={(_, v) => setMode(v)} variant="fullWidth"
              sx={{ bgcolor: 'action.hover', borderRadius: '12px', minHeight: 44, p: 0.5, '& .MuiTabs-indicator': { display: 'none' },
                '& .MuiTab-root': { minHeight: 36, borderRadius: '10px', zIndex: 1 }, '& .Mui-selected': { bgcolor: 'background.paper', boxShadow: 1, color: 'primary.main !important' } }}>
              <Tab value="login" label={t('signin')} />
              <Tab value="register" label={t('register')} />
            </Tabs>
            {error && <Alert severity="error" variant="outlined">{error}</Alert>}
            <TextField label={t('email')} type="email" autoComplete="email" fullWidth {...register('email')} error={!!errors.email} helperText={errors.email?.message}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><EmailIcon fontSize="small" /></InputAdornment> } }} />
            <Box>
              <TextField label={t('password')} type={show ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} fullWidth {...register('password')}
                error={!!errors.password} helperText={errors.password?.message}
                slotProps={{ input: {
                  startAdornment: <InputAdornment position="start"><LockIcon fontSize="small" /></InputAdornment>,
                  endAdornment: <InputAdornment position="end"><IconButton onClick={() => setShow((s) => !s)} edge="end" aria-label="toggle password visibility">{show ? <VisibilityOffIcon /> : <VisibilityIcon />}</IconButton></InputAdornment>,
                } }} />
              {mode === 'register' && pw && (
                <Box sx={{ mt: 1 }}>
                  <LinearProgress variant="determinate" value={sc * 25} color={sc <= 1 ? 'error' : sc <= 2 ? 'warning' : 'success'} sx={{ height: 6, borderRadius: '12px' }} />
                  <Typography variant="caption" color="text.secondary">{['Too weak', 'Weak', 'Okay', 'Good', 'Strong'][sc]}</Typography>
                </Box>
              )}
            </Box>
            <Button type="submit" size="large" variant="contained" disabled={isSubmitting} sx={{ py: 1.5 }}>
              {isSubmitting ? <CircularProgress size={24} color="inherit" /> : mode === 'login' ? t('signin') : t('register')}
            </Button>
            <Typography variant="caption" color="text.secondary" textAlign="center">By continuing you agree to our Terms and Privacy Policy.</Typography>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
