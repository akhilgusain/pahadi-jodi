import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

i18n.use(initReactI18next).init({
  lng: localStorage.getItem('pj.lang') ?? 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  resources: {
    en: { translation: { title: 'Pahadi Jodi', tagline: 'Find your life partner from the hills', email: 'Email', password: 'Password', signin: 'Sign in', register: 'Create account', signout: 'Sign out', save: 'Save profile' } },
    hi: { translation: { title: 'पहाड़ी जोड़ी', tagline: 'पहाड़ों से अपना जीवनसाथी चुनें', email: 'ईमेल', password: 'पासवर्ड', signin: 'साइन इन', register: 'खाता बनाएँ', signout: 'साइन आउट', save: 'प्रोफ़ाइल सहेजें' } },
  },
});
export default i18n;
