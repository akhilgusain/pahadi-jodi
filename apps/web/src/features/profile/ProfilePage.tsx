import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { api, ApiError } from '../../lib/api';

export const COMMUNITIES = ['GARHWALI', 'KUMAONI', 'JAUNSARI', 'BHOTIYA', 'RAJPUT', 'BRAHMIN', 'SCHEDULED', 'OTHER_PAHADI'] as const;
export const DISTRICTS = ['DEHRADUN', 'HARIDWAR', 'PAURI_GARHWAL', 'TEHRI_GARHWAL', 'CHAMOLI', 'RUDRAPRAYAG', 'UTTARKASHI',
  'ALMORA', 'NAINITAL', 'BAGESHWAR', 'PITHORAGARH', 'CHAMPAWAT', 'UDHAM_SINGH_NAGAR'] as const;

const schema = z.object({
  displayName: z.string().min(1).max(100),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'YYYY-MM-DD'),
  community: z.enum(COMMUNITIES),
  district: z.enum(DISTRICTS),
  caste: z.string().max(80).optional(),
  gotra: z.string().max(80).optional(),
  village: z.string().max(120).optional(),
  education: z.string().max(120).optional(),
  occupation: z.string().max(120).optional(),
  bio: z.string().max(2000).optional(),
});
type Form = z.infer<typeof schema>;
type Profile = Form & { id: string; division: string; completeness: number };

export default function ProfilePage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => api<Profile>('/api/v1/profiles/me').catch((e) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
  });
  const { control, register, handleSubmit, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema),
    values: data ?? undefined,
    defaultValues: { gender: 'FEMALE', community: 'GARHWALI', district: 'DEHRADUN' },
  });
  const save = useMutation({
    mutationFn: (v: Form) => api<Profile>('/api/v1/profiles/me', { method: 'PUT', body: JSON.stringify(v) }),
    onSuccess: (p) => qc.setQueryData(['profile', 'me'], p),
  });

  const select = (name: 'gender' | 'community' | 'district', options: readonly string[]) => (
    <Controller name={name} control={control} render={({ field }) => (
      <TextField select label={name} {...field}>{options.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}</TextField>
    )} />
  );

  return (
    <Box component="form" onSubmit={handleSubmit((v) => save.mutate(v))} sx={{ maxWidth: 560, mx: 'auto', mt: 4, px: 2 }}>
      <Stack spacing={2}>
        <Typography variant="h5">{t('title')}: Profile</Typography>
        {data && <><Typography variant="caption">Completeness {data.completeness}% · {data.division}</Typography>
          <LinearProgress variant="determinate" value={data.completeness} /></>}
        {save.error && <Alert severity="error">{(save.error as Error).message}</Alert>}
        <TextField label="Display name" {...register('displayName')} error={!!errors.displayName} />
        {select('gender', ['MALE', 'FEMALE', 'OTHER'])}
        <TextField label="Date of birth (YYYY-MM-DD)" {...register('dateOfBirth')} error={!!errors.dateOfBirth} helperText={errors.dateOfBirth?.message} />
        {select('community', COMMUNITIES)}
        {select('district', DISTRICTS)}
        <TextField label="Village" {...register('village')} />
        <TextField label="Caste" {...register('caste')} />
        <TextField label="Gotra" {...register('gotra')} />
        <TextField label="Education" {...register('education')} />
        <TextField label="Occupation" {...register('occupation')} />
        <TextField label="About me" multiline minRows={3} {...register('bio')} />
        <Button type="submit" variant="contained" disabled={save.isPending}>{t('save')}</Button>
      </Stack>
    </Box>
  );
}
