import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Card, Chip, InputAdornment, MenuItem, Skeleton, Snackbar, Stack, Step, StepLabel, Stepper, TextField, Typography } from '@mui/material';
import PersonIcon from '@mui/icons-material/PersonOutline';
import ParkIcon from '@mui/icons-material/ForestOutlined';
import WorkIcon from '@mui/icons-material/WorkOutline';
import EditNoteIcon from '@mui/icons-material/EditNoteOutlined';
import { api, ApiError } from '../../lib/api';
import PageContainer from '../../components/PageContainer';
import CompletenessRing from '../../components/CompletenessRing';
import ProfileCard from '../../components/ProfileCard';
import { COMMUNITIES, COMMUNITY_OPTIONS, DISTRICT_LIST, DISTRICTS, GENDER_OPTIONS, communityLabel, districtInfo } from '../../lib/uttarakhand';

const ageOf = (dob: string) => {
  const d = new Date(dob); if (isNaN(d.getTime())) return null;
  const n = new Date(); let a = n.getFullYear() - d.getFullYear();
  if (n < new Date(n.getFullYear(), d.getMonth(), d.getDate())) a--;
  return a;
};

const schema = z.object({
  displayName: z.string().trim().min(1, 'Please enter your name').max(100),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Pick your date of birth').refine((v) => (ageOf(v) ?? 0) >= 18, 'You must be at least 18'),
  community: z.enum(COMMUNITIES),
  district: z.enum(DISTRICTS),
  village: z.string().max(120).optional(),
  caste: z.string().max(80).optional(),
  gotra: z.string().max(80).optional(),
  education: z.string().max(120).optional(),
  occupation: z.string().max(120).optional(),
  bio: z.string().max(2000).optional(),
});
type Form = z.infer<typeof schema>;
type Profile = Form & { id: string; division: 'GARHWAL' | 'KUMAON'; completeness: number };

const STEPS = [
  { label: 'Basics', icon: <PersonIcon />, fields: ['displayName', 'gender', 'dateOfBirth'] },
  { label: 'Heritage', icon: <ParkIcon />, fields: ['community', 'district', 'village', 'caste', 'gotra'] },
  { label: 'Career', icon: <WorkIcon />, fields: ['education', 'occupation'] },
  { label: 'About me', icon: <EditNoteIcon />, fields: ['bio'] },
] as const satisfies ReadonlyArray<{ label: string; icon: React.ReactNode; fields: ReadonlyArray<keyof Form> }>;

const EMPTY: Form = { displayName: '', gender: 'FEMALE', dateOfBirth: '', community: 'GARHWALI', district: 'DEHRADUN', village: '', caste: '', gotra: '', education: '', occupation: '', bio: '' };
const toForm = (p: Profile): Form => ({ ...EMPTY, ...Object.fromEntries(Object.entries(p).filter(([k]) => k in EMPTY).map(([k, v]) => [k, v ?? ''])) } as Form);

/** Mirrors the server completeness weights (profile-service Profile#computeCompleteness) for live feedback. */
function estimate(v: Partial<Form>) {
  const has = (s?: string) => !!s && s.trim().length > 0;
  let s = 40; // mandatory basics
  if (has(v.caste)) s += 5; if (has(v.gotra)) s += 5; if (has(v.village)) s += 10;
  if (has(v.education)) s += 8; if (has(v.occupation)) s += 7;
  s += (v.bio?.length ?? 0) >= 100 ? 15 : has(v.bio) ? 7 : 0;
  return Math.min(s, 100);
}

export default function ProfilePage() {
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [saved, setSaved] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => api<Profile>('/api/v1/profiles/me').catch((e) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
  });
  const { control, register, handleSubmit, trigger, watch, formState: { errors } } = useForm<Form>({
    resolver: zodResolver(schema), mode: 'onTouched',
    values: data ? toForm(data) : undefined, defaultValues: EMPTY,
  });
  const v = watch();
  const info = districtInfo(v.district);
  const live = useMemo(() => estimate(v), [v]);

  const save = useMutation({
    mutationFn: (f: Form) => api<Profile>('/api/v1/profiles/me', { method: 'PUT',
      body: JSON.stringify(Object.fromEntries(Object.entries(f).map(([k, x]) => [k, x === '' ? null : x]))) }),
    onSuccess: (p) => { qc.setQueryData(['profile', 'me'], p); setSaved(true); },
  });

  const next = async () => { if (await trigger([...STEPS[step].fields])) setStep((s) => s + 1); };
  const last = step === STEPS.length - 1;

  if (isLoading) {
    return (
      <PageContainer title="My profile">
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.4fr 1fr' }, gap: 4 }}>
          <Skeleton variant="rounded" height={460} sx={{ borderRadius: '20px' }} /><Skeleton variant="rounded" height={460} sx={{ borderRadius: '20px' }} />
        </Box>
      </PageContainer>
    );
  }

  return (
    <PageContainer title={data ? 'My profile' : 'Create your profile'} subtitle={data ? 'Keep it fresh, complete profiles get far more interest.' : 'Four short steps, you can edit everything later.'}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.4fr 1fr' }, gap: 4, alignItems: 'start' }}>
        <Card sx={{ p: { xs: 2.5, sm: 4 } }}>
          <Stepper activeStep={step} alternativeLabel sx={{ mb: 4, '& .MuiStepLabel-label': { fontSize: { xs: '0.7rem', sm: '0.85rem' } } }}>
            {STEPS.map((s, i) => (<Step key={s.label} completed={i < step}><StepLabel onClick={() => i < step && setStep(i)} sx={{ cursor: i < step ? 'pointer' : 'default' }}>{s.label}</StepLabel></Step>))}
          </Stepper>

          <Box component="form" onSubmit={handleSubmit((f) => save.mutate(f))} noValidate>
            <Stack spacing={2.5} sx={{ minHeight: 330 }}>
              {step === 0 && (<>
                <TextField label="Full name" {...register('displayName')} error={!!errors.displayName} helperText={errors.displayName?.message} fullWidth autoFocus />
                <Controller name="gender" control={control} render={({ field }) => (
                  <TextField select label="I am looking to find a match as" {...field} fullWidth>{GENDER_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}</TextField>)} />
                <TextField label="Date of birth" type="date" {...register('dateOfBirth')} error={!!errors.dateOfBirth} helperText={errors.dateOfBirth?.message ?? (v.dateOfBirth && ageOf(v.dateOfBirth) ? `${ageOf(v.dateOfBirth)} years old` : ' ')}
                  slotProps={{ inputLabel: { shrink: true } }} fullWidth />
              </>)}
              {step === 1 && (<>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2.5 }}>
                  <Controller name="community" control={control} render={({ field }) => (
                    <TextField select label="Community" {...field} fullWidth>{COMMUNITY_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}</TextField>)} />
                  <Controller name="district" control={control} render={({ field }) => (
                    <TextField select label="Home district" {...field} fullWidth
                      slotProps={{ input: { endAdornment: info && <InputAdornment position="end" sx={{ mr: 2 }}><Chip size="small" color={info.division === 'GARHWAL' ? 'primary' : 'secondary'} label={info.division === 'GARHWAL' ? 'Garhwal' : 'Kumaon'} /></InputAdornment> } }}>
                      {DISTRICT_LIST.map((d) => <MenuItem key={d.code} value={d.code}>{d.label} <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>{d.division === 'GARHWAL' ? 'Garhwal' : 'Kumaon'}</Typography></MenuItem>)}
                    </TextField>)} />
                </Box>
                <TextField label="Native village" {...register('village')} helperText="Where your family comes from" fullWidth />
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2.5 }}>
                  <TextField label="Caste" {...register('caste')} fullWidth />
                  <TextField label="Gotra" {...register('gotra')} fullWidth />
                </Box>
              </>)}
              {step === 2 && (<>
                <TextField label="Highest education" {...register('education')} placeholder="e.g. B.Tech, MBBS, MA" fullWidth autoFocus />
                <TextField label="Occupation" {...register('occupation')} placeholder="e.g. Software Engineer, Army Officer, Teacher" fullWidth />
              </>)}
              {step === 3 && (
                <TextField label="About me" {...register('bio')} multiline minRows={7} fullWidth autoFocus
                  helperText={`${v.bio?.length ?? 0}/2000 · Write 100+ characters to earn the full completeness bonus`} />
              )}
            </Stack>
            {save.error && <Alert severity="error" sx={{ mt: 2 }}>{(save.error as Error).message}</Alert>}
            <Stack direction="row" justifyContent="space-between" sx={{ mt: 3 }}>
              <Button disabled={step === 0} onClick={() => setStep((s) => s - 1)}>Back</Button>
              {last
                ? <Button key="save" type="submit" variant="contained" size="large" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save profile'}</Button>
                : <Button key="next" type="button" variant="contained" size="large" onClick={next}>Continue</Button>}
            </Stack>
          </Box>
        </Card>

        <Stack spacing={3} sx={{ position: { md: 'sticky' }, top: { md: 88 } }}>
          <Card sx={{ p: 3, display: 'flex', alignItems: 'center', gap: 3 }}>
            <CompletenessRing value={data && !save.isPending ? Math.max(live, 0) : live} />
            <Box>
              <Typography fontWeight={700}>Profile strength</Typography>
              <Typography variant="body2" color="text.secondary">{live >= 80 ? 'Excellent, you will stand out.' : live >= 55 ? 'Good start. Add village, gotra and a bio.' : 'Fill in more details to get noticed.'}</Typography>
            </Box>
          </Card>
          <Box>
            <Typography variant="overline" color="text.secondary">Live preview</Typography>
            <ProfileCard name={v.displayName} age={v.dateOfBirth ? ageOf(v.dateOfBirth) : null} village={v.village} district={info?.label} division={info?.division}
              community={communityLabel(v.community)} occupation={v.occupation} education={v.education} verified={false} />
          </Box>
        </Stack>
      </Box>
      <Snackbar open={saved} autoHideDuration={3500} onClose={() => setSaved(false)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert severity="success" variant="filled" onClose={() => setSaved(false)}>Profile saved</Alert>
      </Snackbar>
    </PageContainer>
  );
}
