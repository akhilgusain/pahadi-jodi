import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

/** Layered inline-SVG mountain range. The front layer takes the page background colour so sections blend seamlessly. */
export default function MountainRange({ height = 260 }: { height?: number | { xs?: number; sm?: number; md?: number } }) {
  const theme = useTheme();
  const bg = theme.palette.background.default;
  return (
    <Box sx={{ position: 'absolute', left: 0, right: 0, bottom: -1, height, pointerEvents: 'none', overflow: 'hidden' }}>
      <svg viewBox="0 0 1440 320" preserveAspectRatio="none" width="100%" height="100%" aria-hidden>
        <defs>
          <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stopColor="#F7C47A" /><stop offset="0.6" stopColor="#F2A33A" stopOpacity="0.55" /><stop offset="1" stopColor="#F2A33A" stopOpacity="0" /></radialGradient>
        </defs>
        <circle cx="1100" cy="120" r="130" fill="url(#sun)" />
        <circle cx="1100" cy="120" r="46" fill="#F7C47A" />
        <path d="M0,200 L120,130 L220,180 L360,90 L480,170 L620,70 L760,160 L900,100 L1040,180 L1180,110 L1320,170 L1440,130 L1440,320 L0,320 Z" fill="#6F93A8" opacity="0.55" />
        <path d="M335,112 L360,90 L385,112 L372,106 L360,115 L348,106 Z M592,96 L620,70 L648,96 L634,90 L620,101 L606,90 Z M875,122 L900,100 L925,122 L912,116 L900,125 L888,116 Z" fill="#fff" opacity="0.85" />
        <path d="M0,250 L140,190 L260,230 L420,150 L560,230 L720,170 L880,240 L1040,180 L1200,245 L1340,200 L1440,230 L1440,320 L0,320 Z" fill="#2F6B5A" opacity="0.9" />
        <path d="M0,290 L180,260 L360,285 L560,255 L760,288 L960,262 L1160,290 L1320,268 L1440,285 L1440,320 L0,320 Z" fill={bg} />
      </svg>
    </Box>
  );
}
