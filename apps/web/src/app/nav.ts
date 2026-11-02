import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import type { SvgIconComponent } from '@mui/icons-material';
import PersonIcon from '@mui/icons-material/PersonOutline';
import SearchIcon from '@mui/icons-material/Search';
import FavoriteIcon from '@mui/icons-material/FavoriteBorder';
import MailIcon from '@mui/icons-material/MailOutline';
import ChatIcon from '@mui/icons-material/ChatBubbleOutline';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremiumOutlined';
import NotificationsIcon from '@mui/icons-material/NotificationsNone';
import AdminIcon from '@mui/icons-material/AdminPanelSettingsOutlined';

/**
 * Single source of truth for authenticated pages. To add a page:
 *   1. append an entry here (give it `component` for a real page, or `placeholder` for a "coming soon" page);
 *   2. that's it: routes, the top bar, the mobile bottom bar and the user menu are all generated from this list.
 */
export interface NavItem {
  id: string;
  path: string;
  label: string;                 // i18n key is `nav.<id>`; this is the fallback
  icon: SvgIconComponent;
  placement: 'primary' | 'menu';  // primary = top bar (desktop); menu = avatar menu
  bottom?: boolean;               // show in the mobile bottom navigation
  roles?: string[];               // visible/accessible only with one of these roles (UI hint; server enforces)
  component?: LazyExoticComponent<ComponentType>;
  placeholder?: { title: string; description: string };
}

export const NAV: NavItem[] = [
  { id: 'search', path: '/search', label: 'Search', icon: SearchIcon, placement: 'primary', bottom: true,
    placeholder: { title: 'Search is on its way', description: 'Filter by district, village, community, gotra and more. This page will connect to the search service.' } },
  { id: 'matches', path: '/matches', label: 'Matches', icon: FavoriteIcon, placement: 'primary', bottom: true,
    placeholder: { title: 'Your daily matches', description: 'Compatibility-scored recommendations will appear here every morning.' } },
  { id: 'interests', path: '/interests', label: 'Interests', icon: MailIcon, placement: 'primary', bottom: true,
    placeholder: { title: 'Interests & shortlist', description: 'Interests you sent and received, plus your private shortlist.' } },
  { id: 'chat', path: '/chat', label: 'Chat', icon: ChatIcon, placement: 'primary', bottom: true,
    placeholder: { title: 'Conversations', description: 'Real-time chat, voice notes and video calls with your mutual matches.' } },
  { id: 'profile', path: '/profile', label: 'Profile', icon: PersonIcon, placement: 'menu', bottom: true,
    component: lazy(() => import('../features/profile/ProfilePage')) },
  { id: 'plans', path: '/plans', label: 'Plans', icon: WorkspacePremiumIcon, placement: 'menu',
    placeholder: { title: 'Choose your plan', description: 'Free, Premium, Premium Plus and VIP plans with secure UPI, card and net-banking checkout.' } },
  { id: 'notifications', path: '/notifications', label: 'Notifications', icon: NotificationsIcon, placement: 'menu',
    placeholder: { title: 'Notifications', description: 'Interest requests, profile views and match alerts in one place.' } },
  { id: 'admin', path: '/admin', label: 'Admin console', icon: AdminIcon, placement: 'menu', roles: ['ADMIN'],
    placeholder: { title: 'Admin console', description: 'User management, moderation queue, verification workbench and revenue dashboards.' } },
];
