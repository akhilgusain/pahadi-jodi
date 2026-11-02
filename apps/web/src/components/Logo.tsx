import { Box, Typography } from '@mui/material';
import { SERIF } from '../app/theme';

export function LogoMark({ size = 36 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" aria-hidden>
      <defs>
        <linearGradient id="lm-g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#2A7A62" /><stop offset="1" stopColor="#0F3D33" /></linearGradient>
      </defs>
      <rect width="48" height="48" rx="13" fill="url(#lm-g)" />
      <circle cx="34" cy="14" r="5" fill="#F2A33A" />
      <path d="M4 38 L18 16 L25 27 L30 20 L44 38 Z" fill="#fff" opacity="0.95" />
      <path d="M18 16 L21.5 21.5 L18 20 L14.5 21.5 Z" fill="#CFE5DD" />
    </svg>
  );
}

export default function Logo({ light = false, compact = false }: { light?: boolean; compact?: boolean }) {
  return (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.25 }}>
      <LogoMark size={34} />
      {!compact && (
        <Typography sx={{ fontFamily: SERIF, fontWeight: 700, fontSize: { xs: '1.1rem', sm: '1.35rem' }, lineHeight: 1, whiteSpace: 'nowrap', color: light ? '#fff' : 'text.primary' }}>
          Pahadi <Box component="span" sx={{ color: 'secondary.main' }}>Jodi</Box>
        </Typography>
      )}
    </Box>
  );
}
