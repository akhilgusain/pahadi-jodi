import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

i18n.use(initReactI18next).init({
  lng: localStorage.getItem('pj.lang') ?? 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  resources: {
    en: { translation: {
      title: 'Pahadi Jodi', tagline: 'Find your life partner from the hills', email: 'Email', password: 'Password',
      signin: 'Sign in', register: 'Create account', signout: 'Sign out', save: 'Save profile',
      hero: { title1: 'Find your', title2: 'Saathi', title3: 'from the hills', sub: 'A matrimonial home built for Garhwali, Kumaoni, Jaunsari and Bhotiya families, in Uttarakhand and across the world.', cta: 'Create free profile', cta2: 'I already have an account' },
      nav: { search: 'Search', matches: 'Matches', interests: 'Interests', chat: 'Chat', profile: 'Profile', plans: 'Plans', notifications: 'Notifications', admin: 'Admin console' },
    } },
    hi: { translation: {
      title: 'पहाड़ी जोड़ी', tagline: 'पहाड़ों से अपना जीवनसाथी चुनें', email: 'ईमेल', password: 'पासवर्ड',
      signin: 'साइन इन', register: 'खाता बनाएँ', signout: 'साइन आउट', save: 'प्रोफ़ाइल सहेजें',
      hero: { title1: 'पहाड़ों से चुनिए अपना', title2: 'साथी', title3: '', sub: 'गढ़वाली, कुमाऊँनी, जौनसारी और भोटिया परिवारों के लिए बना वैवाहिक मंच, उत्तराखंड और दुनिया भर में।', cta: 'मुफ़्त प्रोफ़ाइल बनाएँ', cta2: 'मेरा खाता पहले से है' },
      nav: { search: 'खोजें', matches: 'मैच', interests: 'रुचि', chat: 'चैट', profile: 'प्रोफ़ाइल', plans: 'योजनाएँ', notifications: 'सूचनाएँ', admin: 'एडमिन' },
    } },
  },
});
export default i18n;
