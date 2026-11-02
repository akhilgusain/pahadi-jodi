import { Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { CircularProgress, Box } from '@mui/material';
import type { RootState } from './store';
import { NAV } from './nav';
import Layout from './Layout';
import LandingPage from '../features/landing/LandingPage';
import AuthPage from '../features/auth/AuthPage';
import ComingSoon from '../components/ComingSoon';
import { currentRoles } from '../lib/session';

function RequireAuth({ children, roles }: { children: ReactNode; roles?: string[] }) {
  const signedIn = useSelector((s: RootState) => s.auth.signedIn);
  const loc = useLocation();
  if (!signedIn) return <Navigate to="/auth" replace state={{ from: loc.pathname }} />;
  if (roles && !roles.some((r) => currentRoles().includes(r))) return <Navigate to="/profile" replace />;
  return <>{children}</>;
}

const Loading = () => <Box sx={{ display: 'grid', placeItems: 'center', py: 12 }}><CircularProgress /></Box>;

export default function AppRoutes() {
  const signedIn = useSelector((s: RootState) => s.auth.signedIn);
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={signedIn ? <Navigate to="/profile" replace /> : <LandingPage />} />
        <Route path="/auth" element={<AuthPage />} />
        {NAV.map((n) => {
          const page = n.component ? <n.component /> : <ComingSoon title={n.placeholder?.title ?? n.label} description={n.placeholder?.description ?? ''} />;
          return <Route key={n.id} path={n.path} element={<RequireAuth roles={n.roles}><Suspense fallback={<Loading />}>{page}</Suspense></RequireAuth>} />;
        })}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
