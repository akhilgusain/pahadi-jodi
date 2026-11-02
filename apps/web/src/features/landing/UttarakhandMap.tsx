import { useState } from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import TouchAppIcon from '@mui/icons-material/TouchAppOutlined';
import ExploreIcon from '@mui/icons-material/ExploreOutlined';
import { DISTRICT_LIST, DIVISION_LABEL, type DistrictInfo } from '../../lib/uttarakhand';

const TILE = 118, GAP = 10, COLS = 4, ROWS = 4;
const W = COLS * (TILE + GAP) + GAP, H = ROWS * (TILE + GAP) + GAP;

export default function UttarakhandMap({ onPick }: { onPick?: (d: DistrictInfo) => void }) {
  const theme = useTheme();
  const dark = theme.palette.mode === 'dark';
  const [hover, setHover] = useState<string | null>(null);
  const [picked, setPicked] = useState<DistrictInfo | null>(null);
  const active = DISTRICT_LIST.find((d) => d.code === (hover ?? picked?.code)) ?? null;
  const garhwal = DISTRICT_LIST.filter((d) => d.division === 'GARHWAL').length;
  const kumaon = DISTRICT_LIST.length - garhwal;

  const fill = (d: DistrictInfo, on: boolean) => d.division === 'GARHWAL'
    ? (on ? theme.palette.primary.light : theme.palette.primary.main)
    : (on ? '#F7C47A' : theme.palette.secondary.main);
  const ink = (d: DistrictInfo) => (d.division === 'GARHWAL' ? (dark ? '#06201A' : '#fff') : '#2B1D00');

  const lines = (label: string) => {
    const words = label.split(' ');
    if (words.length === 1) return [label];
    if (words.length === 3) return [words[0] + ' ' + words[1], words[2]];
    return words;
  };

  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.15fr 1fr' }, gap: { xs: 3, md: 6 }, alignItems: 'center' }}>
      <Box>
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="group" aria-label="Tile map of the 13 districts of Uttarakhand" style={{ display: 'block', maxWidth: 560, margin: '0 auto' }}>
          {DISTRICT_LIST.map((d) => {
            const x = GAP + d.col * (TILE + GAP), y = GAP + d.row * (TILE + GAP);
            const on = active?.code === d.code;
            const ls = lines(d.label);
            return (
              <g key={d.code} transform={`translate(${x},${y})`} tabIndex={0} role="button" aria-label={`${d.label}, ${DIVISION_LABEL[d.division]}`}
                style={{ cursor: 'pointer', outline: 'none', transition: 'transform .2s' }}
                onMouseEnter={() => setHover(d.code)} onMouseLeave={() => setHover(null)} onFocus={() => setHover(d.code)} onBlur={() => setHover(null)}
                onClick={() => { setPicked(d); onPick?.(d); }} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { setPicked(d); onPick?.(d); } }}>
                <rect width={TILE} height={TILE} rx={24} fill={fill(d, on)} stroke={picked?.code === d.code ? (dark ? '#fff' : '#16241F') : 'transparent'} strokeWidth={3}
                  style={{ filter: on ? `drop-shadow(0 10px 14px ${alpha(d.division === 'GARHWAL' ? '#0F3D33' : '#B9651B', 0.45)})` : 'none', transform: on ? 'translateY(-4px)' : 'none', transition: 'all .2s' }} />
                <path d={`M14 ${TILE - 14} L34 ${TILE - 44} L48 ${TILE - 26} L62 ${TILE - 40} L84 ${TILE - 14} Z`} fill={ink(d)} opacity={0.14} />
                {ls.map((l, i) => (
                  <text key={i} x={TILE / 2} y={TILE / 2 + (i - (ls.length - 1) / 2) * 15 - 6} textAnchor="middle" fontSize={ls.length > 1 ? 12.5 : 14} fontWeight={600} fill={ink(d)} fontFamily="Poppins, sans-serif" style={{ pointerEvents: 'none' }}>{l}</text>
                ))}
              </g>
            );
          })}
          <g transform={`translate(${GAP + 2 * (TILE + GAP) + TILE / 2},${GAP + 0 * (TILE + GAP) + TILE / 2})`} opacity={0.55} aria-hidden>
            <circle r="26" fill="none" stroke={theme.palette.text.secondary} strokeWidth="1.5" />
            <path d="M0 -20 L6 0 L0 20 L-6 0 Z" fill={theme.palette.accent.main} />
            <text y="-30" textAnchor="middle" fontSize="11" fontWeight="700" fill={theme.palette.text.secondary}>N</text>
          </g>
        </svg>
      </Box>

      <Box>
        <Stack direction="row" spacing={1} sx={{ mb: 3 }}>
          <Chip color="primary" label={`Garhwal · ${garhwal} districts`} />
          <Chip color="secondary" label={`Kumaon · ${kumaon} districts`} />
        </Stack>
        <Box sx={{ minHeight: 190, p: 3, borderRadius: '20px', bgcolor: 'background.paper', border: 1, borderColor: 'divider', boxShadow: 3, transition: 'all .25s' }}>
          {active ? (
            <>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <ExploreIcon color={active.division === 'GARHWAL' ? 'primary' : 'secondary'} />
                <Typography variant="overline" color="text.secondary">{DIVISION_LABEL[active.division]} division</Typography>
              </Stack>
              <Typography variant="h4" sx={{ mb: 1 }}>{active.label}</Typography>
              <Typography color="text.secondary">{active.blurb}</Typography>
              <Typography variant="body2" sx={{ mt: 2, color: 'primary.main', fontWeight: 600 }}>Find matches rooted in {active.label} →</Typography>
            </>
          ) : (
            <Stack alignItems="center" justifyContent="center" spacing={1.5} sx={{ minHeight: 140, color: 'text.secondary', textAlign: 'center' }}>
              <TouchAppIcon sx={{ fontSize: 36 }} />
              <Typography>Hover or tap a district to explore its roots.</Typography>
            </Stack>
          )}
        </Box>
      </Box>
    </Box>
  );
}
