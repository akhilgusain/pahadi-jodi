import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

export default function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <Box sx={{ textAlign: 'center', py: { xs: 6, md: 9 }, px: 2, maxWidth: 520, mx: 'auto' }}>
      {icon && (
        <Box sx={{ width: 84, height: 84, mx: 'auto', mb: 3, borderRadius: '28px', display: 'grid', placeItems: 'center',
          color: 'primary.main', bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(95,191,159,0.12)' : 'rgba(27,94,75,0.08)'),
          '& svg': { fontSize: 40 } }}>{icon}</Box>
      )}
      <Typography variant="h5" gutterBottom>{title}</Typography>
      {description && <Typography color="text.secondary" sx={{ mb: action ? 3 : 0 }}>{description}</Typography>}
      {action}
    </Box>
  );
}
