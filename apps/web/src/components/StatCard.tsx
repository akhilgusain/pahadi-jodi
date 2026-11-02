import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

export default function StatCard({ icon, value, label }: { icon: ReactNode; value: string; label: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 2.5, borderRadius: '16px', bgcolor: 'background.paper', border: 1, borderColor: 'divider' }}>
      <Box sx={{ width: 48, height: 48, borderRadius: '12px', display: 'grid', placeItems: 'center', color: 'secondary.contrastText',
        background: 'linear-gradient(135deg,#F7C47A,#F2A33A)', flexShrink: 0 }}>{icon}</Box>
      <Box>
        <Typography sx={{ fontFamily: '"Playfair Display", serif', fontWeight: 700, fontSize: '1.6rem', lineHeight: 1.1 }}>{value}</Typography>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
      </Box>
    </Box>
  );
}
