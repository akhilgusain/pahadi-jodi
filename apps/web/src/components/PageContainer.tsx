import type { ReactNode } from 'react';
import { Box, Container, Typography } from '@mui/material';

export default function PageContainer({ title, subtitle, actions, children, maxWidth = 'lg' }:
  { title?: string; subtitle?: string; actions?: ReactNode; children: ReactNode; maxWidth?: 'sm' | 'md' | 'lg' | 'xl' }) {
  return (
    <Container maxWidth={maxWidth} sx={{ py: { xs: 3, md: 5 }, px: { xs: 2, sm: 3 } }}>
      {(title || actions) && (
        <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, mb: 3, flexWrap: 'wrap' }}>
          <Box>
            {title && <Typography variant="h4" sx={{ fontSize: { xs: '1.6rem', md: '2.1rem' } }}>{title}</Typography>}
            {subtitle && <Typography color="text.secondary" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
          </Box>
          {actions}
        </Box>
      )}
      {children}
    </Container>
  );
}
