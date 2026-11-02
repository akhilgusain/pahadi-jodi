import { Box, CircularProgress, Typography } from '@mui/material';

export default function CompletenessRing({ value, size = 88, label = 'complete' }: { value: number; size?: number; label?: string }) {
  const color = value >= 80 ? 'success.main' : value >= 50 ? 'secondary.main' : 'accent.main';
  return (
    <Box sx={{ position: 'relative', width: size, height: size, display: 'inline-flex', flexShrink: 0 }}>
      <CircularProgress variant="determinate" value={100} size={size} thickness={4} sx={{ color: 'action.hover', position: 'absolute' }} />
      <CircularProgress variant="determinate" value={Math.min(value, 100)} size={size} thickness={4} sx={{ color, '& .MuiCircularProgress-circle': { strokeLinecap: 'round', transition: 'stroke-dashoffset .6s ease' } }} />
      <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center' }}>
        <Box>
          <Typography sx={{ fontWeight: 700, fontSize: size * 0.27, lineHeight: 1 }}>{Math.round(value)}%</Typography>
          {size >= 80 && <Typography variant="caption" color="text.secondary">{label}</Typography>}
        </Box>
      </Box>
    </Box>
  );
}
