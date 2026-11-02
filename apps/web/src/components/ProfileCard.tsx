import { Avatar, Box, Button, Card, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import VerifiedIcon from '@mui/icons-material/Verified';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import WorkOutlineIcon from '@mui/icons-material/WorkOutline';
import CompletenessRing from './CompletenessRing';
import { DIVISION_LABEL, type Division } from '../lib/uttarakhand';

export interface ProfileCardProps {
  name?: string; age?: number | null; district?: string; division?: Division | null; community?: string;
  occupation?: string; education?: string; village?: string; compatibility?: number; verified?: boolean;
  badge?: string; onView?: () => void; onInterest?: () => void; onShortlist?: () => void; compact?: boolean;
}

const initials = (n?: string) => (n ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((s) => s[0]!.toUpperCase()).join('') || '?';

export default function ProfileCard(p: ProfileCardProps) {
  const hues = p.division === 'KUMAON' ? ['#F2A33A', '#B9651B'] : ['#2A7A62', '#0F3D33'];
  return (
    <Card sx={{ overflow: 'hidden', transition: 'transform .25s, box-shadow .25s', '&:hover': { transform: 'translateY(-4px)' } }}>
      <Box sx={{ position: 'relative', height: p.compact ? 120 : 168, background: `linear-gradient(145deg, ${hues[0]}, ${hues[1]})`, display: 'grid', placeItems: 'center' }}>
        <svg viewBox="0 0 400 120" preserveAspectRatio="none" style={{ position: 'absolute', inset: 'auto 0 0 0', width: '100%', height: '55%', opacity: 0.22 }}>
          <path d="M0,120 L60,50 L110,90 L180,20 L250,85 L310,45 L400,100 L400,120 Z" fill="#fff" />
        </svg>
        <Avatar sx={{ width: p.compact ? 64 : 84, height: p.compact ? 64 : 84, fontSize: p.compact ? 24 : 32, fontFamily: '"Playfair Display", serif', fontWeight: 700,
          bgcolor: 'rgba(255,255,255,0.22)', border: '3px solid rgba(255,255,255,0.8)', color: '#fff', backdropFilter: 'blur(4px)' }}>{initials(p.name)}</Avatar>
        {p.badge && <Chip size="small" label={p.badge} sx={{ position: 'absolute', top: 12, left: 12, bgcolor: 'rgba(0,0,0,0.35)', color: '#fff', backdropFilter: 'blur(6px)' }} />}
        {p.compatibility !== undefined && (
          <Box sx={{ position: 'absolute', top: 10, right: 10, bgcolor: 'background.paper', borderRadius: '50%', p: 0.4, boxShadow: 2 }}>
            <CompletenessRing value={p.compatibility} size={52} label="match" />
          </Box>
        )}
      </Box>
      <Box sx={{ p: 2.5 }}>
        <Stack direction="row" alignItems="center" spacing={0.75}>
          <Typography variant="h6" noWrap sx={{ fontFamily: '"Playfair Display", serif', fontWeight: 700 }}>
            {p.name || 'Your name'}{p.age ? `, ${p.age}` : ''}
          </Typography>
          {p.verified && <Tooltip title="Verified profile"><VerifiedIcon color="primary" fontSize="small" /></Tooltip>}
        </Stack>
        <Stack spacing={0.5} sx={{ mt: 1, color: 'text.secondary' }}>
          {(p.district || p.village) && (
            <Stack direction="row" spacing={0.75} alignItems="center"><PlaceOutlinedIcon fontSize="small" />
              <Typography variant="body2">{[p.village, p.district].filter(Boolean).join(', ')}</Typography></Stack>)}
          {(p.occupation || p.education) && (
            <Stack direction="row" spacing={0.75} alignItems="center"><WorkOutlineIcon fontSize="small" />
              <Typography variant="body2" noWrap>{[p.occupation, p.education].filter(Boolean).join(' · ')}</Typography></Stack>)}
        </Stack>
        <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mt: 1.75, minHeight: 28 }}>
          {p.division && <Chip size="small" color={p.division === 'KUMAON' ? 'secondary' : 'primary'} label={`${DIVISION_LABEL[p.division]} region`} />}
          {p.community && <Chip size="small" variant="outlined" label={p.community} />}
        </Stack>
        {(p.onView || p.onInterest || p.onShortlist) && (
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
            {p.onInterest && <Button fullWidth variant="contained" startIcon={<FavoriteBorderIcon />} onClick={p.onInterest}>Send interest</Button>}
            {p.onShortlist && <Tooltip title="Shortlist"><IconButton onClick={p.onShortlist} sx={{ border: 1, borderColor: 'divider', borderRadius: '12px' }}><BookmarkBorderIcon /></IconButton></Tooltip>}
            {p.onView && <Button variant="outlined" onClick={p.onView}>View</Button>}
          </Stack>
        )}
      </Box>
    </Card>
  );
}
