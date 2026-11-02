import { alpha, createTheme, type PaletteMode } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface Palette { accent: Palette['primary']; mountain: Palette['primary']; saffron: Palette['primary']; }
  interface PaletteOptions { accent?: PaletteOptions['primary']; mountain?: PaletteOptions['primary']; saffron?: PaletteOptions['primary']; }
}

/** Pahadi palette: pine forest, marigold/saffron, snow, slate mountain blue, rhododendron pink. */
export const brand = {
  pine: '#1B5E4B', pineDeep: '#0F3D33', pineLight: '#5FBF9F',
  saffron: '#F2A33A', saffronDeep: '#D9822B',
  snow: '#F6F8F7', slate: '#4A6274', slateLight: '#7C9AAE',
  rhododendron: '#D6336C',
};

export const SERIF = '"Playfair Display", "Georgia", serif';
export const SANS = '"Poppins", "Inter", "Segoe UI", Roboto, sans-serif';

export function buildTheme(mode: PaletteMode) {
  const dark = mode === 'dark';
  const softShadow = dark ? '0 10px 30px rgba(0,0,0,0.45)' : '0 10px 32px rgba(15,61,51,0.09)';
  return createTheme({
    palette: {
      mode,
      primary: dark ? { main: brand.pineLight, dark: '#3E9B7C', light: '#8FD6BD', contrastText: '#06201A' }
                    : { main: brand.pine, dark: brand.pineDeep, light: '#3F8A73', contrastText: '#fff' },
      secondary: { main: brand.saffron, dark: brand.saffronDeep, light: '#F7C47A', contrastText: '#2B1D00' },
      saffron: { main: brand.saffron, dark: brand.saffronDeep, light: '#F7C47A', contrastText: '#2B1D00' },
      accent: { main: dark ? '#F06A9A' : brand.rhododendron, contrastText: '#fff' },
      mountain: { main: dark ? brand.slateLight : brand.slate, contrastText: '#fff' },
      background: dark ? { default: '#0D1513', paper: '#141F1C' } : { default: brand.snow, paper: '#FFFFFF' },
      text: dark ? { primary: '#E8F0ED', secondary: '#A5B8B1' } : { primary: '#16241F', secondary: '#506059' },
      divider: dark ? 'rgba(255,255,255,0.09)' : 'rgba(15,61,51,0.10)',
    },
    shape: { borderRadius: 14 },
    typography: {
      fontFamily: SANS,
      h1: { fontFamily: SERIF, fontWeight: 700, letterSpacing: '-0.02em' },
      h2: { fontFamily: SERIF, fontWeight: 700, letterSpacing: '-0.015em' },
      h3: { fontFamily: SERIF, fontWeight: 700 },
      h4: { fontFamily: SERIF, fontWeight: 700 },
      h5: { fontFamily: SERIF, fontWeight: 600 },
      h6: { fontWeight: 600 },
      subtitle1: { fontWeight: 500 },
      button: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
      overline: { fontWeight: 600, letterSpacing: '0.14em' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { WebkitFontSmoothing: 'antialiased' },
          '::selection': { background: alpha(brand.saffron, 0.4) },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 12, padding: '9px 20px' },
          sizeLarge: { padding: '13px 28px', fontSize: '1rem', borderRadius: 14 },
          containedPrimary: { backgroundImage: dark ? 'none' : `linear-gradient(135deg, ${brand.pine}, #2A7A62)`,
            '&:hover': { boxShadow: `0 8px 20px ${alpha(brand.pine, 0.35)}` } },
          containedSecondary: { '&:hover': { boxShadow: `0 8px 20px ${alpha(brand.saffron, 0.45)}` } },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: { borderRadius: 22, border: `1px solid ${dark ? 'rgba(255,255,255,0.08)' : 'rgba(15,61,51,0.08)'}`,
            boxShadow: softShadow, backgroundImage: 'none' },
        },
      },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiOutlinedInput: {
        styleOverrides: {
          root: { borderRadius: 12, backgroundColor: dark ? 'rgba(255,255,255,0.03)' : '#fff',
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: dark ? brand.pineLight : brand.pine },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderWidth: 2 } },
        },
      },
      MuiChip: { styleOverrides: { root: { fontWeight: 500, borderRadius: 10 } } },
      MuiAppBar: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiStepIcon: { styleOverrides: { root: { '&.Mui-completed': { color: brand.pine }, '&.Mui-active': { color: brand.saffron },
        '&.Mui-active .MuiStepIcon-text': { fill: '#2B1D00', fontWeight: 700 } } } },
    },
  });
}
