import { Box, Button, Chip, Container, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import MapIcon from '@mui/icons-material/MapOutlined';
import PublicIcon from '@mui/icons-material/PublicOutlined';
import GroupsIcon from '@mui/icons-material/Groups2Outlined';
import StarsIcon from '@mui/icons-material/AutoAwesomeOutlined';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUserOutlined';
import ChatIcon from '@mui/icons-material/ForumOutlined';
import PersonAddIcon from '@mui/icons-material/PersonAddAlt1Outlined';
import FavoriteIcon from '@mui/icons-material/FavoriteBorder';
import FormatQuoteIcon from '@mui/icons-material/FormatQuote';
import MountainRange from '../../components/MountainHero';
import SectionHeader from '../../components/SectionHeader';
import StatCard from '../../components/StatCard';
import ProfileCard from '../../components/ProfileCard';
import Logo from '../../components/Logo';
import UttarakhandMap from './UttarakhandMap';
import { SERIF } from '../../app/theme';

const reveal = { initial: { opacity: 0, y: 28 }, whileInView: { opacity: 1, y: 0 }, viewport: { once: true, margin: '-60px' }, transition: { duration: 0.6, ease: 'easeOut' as const } };

const FEATURES = [
  { icon: <VerifiedUserIcon />, title: 'Verified profiles', text: 'ID, selfie liveness and AI face-match checks earn the blue tick, so you can trust who you talk to.' },
  { icon: <StarsIcon />, title: 'Horoscope matching', text: 'Ashtakoot Guna Milan out of 36 points, Manglik check and kundli, built into every match.' },
  { icon: <GroupsIcon />, title: 'Pahadi communities', text: 'Garhwali, Kumaoni, Jaunsari, Bhotiya and more. Filter by gotra, village and native place.' },
  { icon: <PublicIcon />, title: 'Diaspora friendly', text: 'Living in Dubai, Delhi or Denver? Search by roots and by where you live today.' },
];
const STEPS = [
  { icon: <PersonAddIcon />, title: 'Create your profile', text: 'Tell us about your family, roots and what you are looking for. Takes about five minutes.' },
  { icon: <FavoriteIcon />, title: 'Discover matches', text: 'Daily recommendations ranked by compatibility, plus powerful village and district filters.' },
  { icon: <ChatIcon />, title: 'Connect safely', text: 'Send an interest, chat once both agree, and meet families you can trust.' },
];
const STORIES = [
  { names: 'Priya & Ankit', place: 'Dehradun × Almora', text: 'Our families found they were from villages just two valleys apart. We spoke in Garhwali and Kumaoni and laughed about it from day one.' },
  { names: 'Neha & Rohit', place: 'Pune × Pauri', text: 'Being far from the hills made roots matter more. The village filter led us to each other.' },
  { names: 'Kavita & Sandeep', place: 'Dubai × Nainital', text: 'Horoscope match, video call, then both families in one room. It all felt natural.' },
];

export default function LandingPage() {
  const { t } = useTranslation();
  return (
    <Box>
      {/* HERO */}
      <Box sx={{ position: 'relative', overflow: 'hidden', color: '#fff', pt: { xs: 14, md: 18 }, pb: { xs: 22, md: 28 },
        background: 'radial-gradient(1200px 500px at 85% -10%, rgba(242,163,58,0.35), transparent 60%), linear-gradient(180deg, #0B332B 0%, #1B5E4B 55%, #4A7A8F 100%)' }}>
        <Container maxWidth="lg" sx={{ position: 'relative', zIndex: 2 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.25fr 0.75fr' }, gap: 6, alignItems: 'center' }}>
            <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7 }}>
              <Chip label="Made for Uttarakhand, loved worldwide" sx={{ mb: 3, bgcolor: 'rgba(255,255,255,0.14)', color: '#fff', backdropFilter: 'blur(6px)', fontWeight: 500 }} />
              <Typography variant="h1" sx={{ fontSize: { xs: '2.6rem', sm: '3.4rem', md: '4.2rem' }, lineHeight: 1.08 }}>
                {t('hero.title1')} <Box component="span" sx={{ color: 'secondary.main', fontStyle: 'italic' }}>{t('hero.title2')}</Box>{t('hero.title3') && <><br />{t('hero.title3')}</>}
              </Typography>
              <Typography sx={{ mt: 3, fontSize: { xs: '1.05rem', md: '1.2rem' }, maxWidth: 560, color: 'rgba(255,255,255,0.86)' }}>{t('hero.sub')}</Typography>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 4.5 }}>
                <Button component={RouterLink} to="/auth?mode=register" size="large" variant="contained" color="secondary" sx={{ boxShadow: '0 10px 30px rgba(242,163,58,0.4)' }}>{t('hero.cta')}</Button>
                <Button component={RouterLink} to="/auth" size="large" variant="outlined" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}>{t('hero.cta2')}</Button>
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 4 }}>
                {['Garhwali', 'Kumaoni', 'Jaunsari', 'Bhotiya', 'Rajput', 'Brahmin'].map((c) => (
                  <Chip key={c} size="small" label={c} variant="outlined" sx={{ color: 'rgba(255,255,255,0.9)', borderColor: 'rgba(255,255,255,0.3)' }} />
                ))}
              </Stack>
            </motion.div>
            <Box sx={{ display: { xs: 'none', md: 'block' } }}>
              <motion.div initial={{ opacity: 0, scale: 0.94 }} animate={{ opacity: 1, scale: 1, y: [0, -10, 0] }} transition={{ opacity: { duration: 0.8 }, scale: { duration: 0.8 }, y: { duration: 6, repeat: Infinity, ease: 'easeInOut' } }}>
                <Box sx={{ transform: 'rotate(3deg)', maxWidth: 300, ml: 'auto' }}>
                  <ProfileCard name="Anjali Rawat" age={29} village="Kandai" district="Pauri Garhwal" division="GARHWAL" community="Garhwali" occupation="Software Engineer" education="B.Tech" compatibility={92} verified badge="Sample profile" compact />
                </Box>
              </motion.div>
            </Box>
          </Box>
        </Container>
        <MountainRange height={{ xs: 150, md: 260 }} />
      </Box>

      {/* TRUST STRIP */}
      <Container maxWidth="lg" sx={{ mt: { xs: -4, md: -6 }, position: 'relative', zIndex: 3 }}>
        <motion.div {...reveal}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 2 }}>
            <StatCard icon={<MapIcon />} value="13" label="Districts covered" />
            <StatCard icon={<GroupsIcon />} value="8" label="Pahadi communities" />
            <StatCard icon={<StarsIcon />} value="36" label="Point Guna Milan" />
            <StatCard icon={<PublicIcon />} value="Global" label="Diaspora ready" />
          </Box>
        </motion.div>
      </Container>

      {/* HOW IT WORKS */}
      <Container maxWidth="lg" sx={{ py: { xs: 9, md: 14 } }}>
        <SectionHeader eyebrow="How it works" title="From first hello to a family meeting" subtitle="Three simple steps, designed around how Pahadi families actually find each other." />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 3 }}>
          {STEPS.map((s, i) => (
            <motion.div key={s.title} {...reveal} transition={{ ...reveal.transition, delay: i * 0.1 }}>
              <Box sx={{ position: 'relative', p: 4, height: '100%', borderRadius: '20px', bgcolor: 'background.paper', border: 1, borderColor: 'divider', boxShadow: 2 }}>
                <Typography sx={{ position: 'absolute', top: 14, right: 22, fontFamily: SERIF, fontSize: '4rem', fontWeight: 700, color: 'primary.main', opacity: 0.08, lineHeight: 1 }}>{i + 1}</Typography>
                <Box sx={{ width: 56, height: 56, borderRadius: '14px', display: 'grid', placeItems: 'center', mb: 2.5, color: '#fff', background: 'linear-gradient(135deg,#2A7A62,#0F3D33)' }}>{s.icon}</Box>
                <Typography variant="h6" gutterBottom>{s.title}</Typography>
                <Typography color="text.secondary">{s.text}</Typography>
              </Box>
            </motion.div>
          ))}
        </Box>
      </Container>

      {/* FEATURES */}
      <Box sx={{ py: { xs: 9, md: 12 }, bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(255,255,255,0.025)' : 'rgba(27,94,75,0.045)') }}>
        <Container maxWidth="lg">
          <SectionHeader eyebrow="Why Pahadi Jodi" title="Built for the way we marry" />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' }, gap: 3 }}>
            {FEATURES.map((f, i) => (
              <motion.div key={f.title} {...reveal} transition={{ ...reveal.transition, delay: i * 0.08 }} style={{ height: '100%' }}>
                <Box sx={{ p: 3.5, height: '100%', borderRadius: '20px', bgcolor: 'background.paper', border: 1, borderColor: 'divider', transition: 'all .25s',
                  '&:hover': { transform: 'translateY(-6px)', boxShadow: 6, borderColor: 'secondary.main' } }}>
                  <Box sx={{ color: 'secondary.dark', mb: 2, '& svg': { fontSize: 38 } }}>{f.icon}</Box>
                  <Typography variant="h6" gutterBottom>{f.title}</Typography>
                  <Typography variant="body2" color="text.secondary">{f.text}</Typography>
                </Box>
              </motion.div>
            ))}
          </Box>
        </Container>
      </Box>

      {/* MAP */}
      <Container maxWidth="lg" sx={{ py: { xs: 9, md: 14 } }} id="map">
        <SectionHeader eyebrow="Roots mapping" title="Start from your village, your valley" subtitle="Explore the thirteen districts of Devbhoomi, from the Garhwal Himalaya to the Kumaon hills and the Terai." />
        <motion.div {...reveal}><UttarakhandMap /></motion.div>
      </Container>

      {/* STORIES */}
      <Box sx={{ py: { xs: 9, md: 12 }, bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(255,255,255,0.025)' : 'rgba(242,163,58,0.08)') }}>
        <Container maxWidth="lg">
          <SectionHeader eyebrow="Success stories" title="Stories from the hills" />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 3 }}>
            {STORIES.map((s, i) => (
              <motion.div key={s.names} {...reveal} transition={{ ...reveal.transition, delay: i * 0.1 }} style={{ height: '100%' }}>
                <Box sx={{ p: 4, height: '100%', borderRadius: '20px', bgcolor: 'background.paper', border: 1, borderColor: 'divider', boxShadow: 2, position: 'relative' }}>
                  <FormatQuoteIcon sx={{ fontSize: 48, color: 'secondary.main', opacity: 0.7 }} />
                  <Typography sx={{ fontFamily: SERIF, fontSize: '1.1rem', lineHeight: 1.6, mb: 3 }}>{s.text}</Typography>
                  <Typography fontWeight={700}>{s.names}</Typography>
                  <Typography variant="body2" color="text.secondary">{s.place}</Typography>
                  <Chip size="small" label="Sample story" sx={{ position: 'absolute', top: 16, right: 16 }} variant="outlined" />
                </Box>
              </motion.div>
            ))}
          </Box>
        </Container>
      </Box>

      {/* CTA + FOOTER */}
      <Box sx={{ background: 'linear-gradient(135deg,#0F3D33,#1B5E4B)', color: '#fff', textAlign: 'center', py: { xs: 9, md: 12 }, px: 2 }}>
        <Typography variant="h3" sx={{ fontSize: { xs: '1.9rem', md: '2.8rem' }, mb: 2 }}>Your story could begin today</Typography>
        <Typography sx={{ color: 'rgba(255,255,255,0.8)', mb: 4 }}>Create your free profile in minutes.</Typography>
        <Button component={RouterLink} to="/auth?mode=register" size="large" variant="contained" color="secondary">{t('hero.cta')}</Button>
      </Box>
      <Box sx={{ bgcolor: '#08241E', color: 'rgba(255,255,255,0.7)', py: 5, px: 2 }}>
        <Container maxWidth="lg" sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, gap: 2, justifyContent: 'space-between', alignItems: 'center' }}>
          <Logo light />
          <Typography variant="body2">© {new Date().getFullYear()} Pahadi Jodi · Made with love for Devbhoomi Uttarakhand</Typography>
        </Container>
      </Box>
    </Box>
  );
}
