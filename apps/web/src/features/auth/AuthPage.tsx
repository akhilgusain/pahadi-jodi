import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useDispatch } from 'react-redux';
import { api, tokenStore, type Tokens } from '../../lib/api';
import { setSignedIn } from '../../app/store';

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(10, 'At least 10 characters'),
});
type Form = z.infer<typeof schema>;

export default function AuthPage() {
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (v) => {
    setError(null);
    try {
      const tokens = await api<Tokens>(`/api/v1/auth/${mode}`, { method: 'POST', body: JSON.stringify(v) });
      tokenStore.set(tokens);
      dispatch(setSignedIn(true));
    } catch (e) { setError((e as Error).message); }
  });

  return (
    <Box component="form" onSubmit={onSubmit} sx={{ maxWidth: 420, mx: 'auto', mt: 8, px: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h4">{t('title')}</Typography>
        <Typography color="text.secondary">{t('tagline')}</Typography>
        <ToggleButtonGroup exclusive value={mode} onChange={(_, v) => v && setMode(v)} size="small">
          <ToggleButton value="login">{t('signin')}</ToggleButton>
          <ToggleButton value="register">{t('register')}</ToggleButton>
        </ToggleButtonGroup>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label={t('email')} {...register('email')} error={!!errors.email} helperText={errors.email?.message} />
        <TextField label={t('password')} type="password" {...register('password')} error={!!errors.password} helperText={errors.password?.message} />
        <Button type="submit" variant="contained" disabled={isSubmitting}>{mode === 'login' ? t('signin') : t('register')}</Button>
      </Stack>
    </Box>
  );
}
