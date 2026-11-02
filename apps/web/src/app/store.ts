import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { tokenStore } from '../lib/api';

const auth = createSlice({
  name: 'auth',
  initialState: { signedIn: tokenStore.get() !== null },
  reducers: { setSignedIn: (s, a: PayloadAction<boolean>) => void (s.signedIn = a.payload) },
});
const ui = createSlice({
  name: 'ui',
  initialState: { dark: localStorage.getItem('pj.dark') === '1' },
  reducers: { toggleDark: (s) => { s.dark = !s.dark; localStorage.setItem('pj.dark', s.dark ? '1' : '0'); } },
});

export const { setSignedIn } = auth.actions;
export const { toggleDark } = ui.actions;
export const store = configureStore({ reducer: { auth: auth.reducer, ui: ui.reducer } });
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
