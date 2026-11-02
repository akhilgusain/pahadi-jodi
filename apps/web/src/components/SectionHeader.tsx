import { Box, Typography } from '@mui/material';

export default function SectionHeader({ eyebrow, title, subtitle, align = 'center' }: { eyebrow?: string; title: string; subtitle?: string; align?: 'center' | 'left' }) {
  return (
    <Box sx={{ textAlign: align, maxWidth: 680, mx: align === 'center' ? 'auto' : 0, mb: { xs: 4, md: 6 } }}>
      {eyebrow && <Typography variant="overline" color="secondary.dark" sx={{ display: 'block', mb: 0.5 }}>{eyebrow}</Typography>}
      <Typography variant="h3" sx={{ fontSize: { xs: '1.9rem', md: '2.6rem' }, lineHeight: 1.15 }}>{title}</Typography>
      {subtitle && <Typography color="text.secondary" sx={{ mt: 1.5, fontSize: '1.05rem' }}>{subtitle}</Typography>}
    </Box>
  );
}
