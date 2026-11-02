import { useEffect, useState } from 'react';
import { Alert, Button, SafeAreaView, Text, TextInput, View } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import { authenticate, myProfile, restoreSession } from './src/api';

export default function App() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [signedIn, setSignedIn] = useState(false);
  const [profile, setProfile] = useState<any>(null);

  // Biometric unlock: if a refresh token exists, require Face/Touch ID before restoring the session.
  useEffect(() => {
    (async () => {
      const ok = await LocalAuthentication.hasHardwareAsync() && await LocalAuthentication.isEnrolledAsync();
      if (ok) {
        const r = await LocalAuthentication.authenticateAsync({ promptMessage: 'Unlock Pahadi Jodi' });
        if (!r.success) return;
      }
      if (await restoreSession()) setSignedIn(true);
    })();
  }, []);

  useEffect(() => { if (signedIn) myProfile().then(setProfile).catch(() => {}); }, [signedIn]);

  const submit = async (mode: 'login' | 'register') => {
    try { await authenticate(mode, email, password); setSignedIn(true); }
    catch (e) { Alert.alert('Error', (e as Error).message); }
  };

  if (signedIn) {
    return <SafeAreaView style={{ padding: 24 }}><Text style={{ fontSize: 22 }}>Pahadi Jodi</Text>
      <Text>{profile ? `${profile.displayName} · ${profile.district}` : 'No profile yet'}</Text></SafeAreaView>;
  }
  return (
    <SafeAreaView style={{ padding: 24, gap: 12 }}>
      <Text style={{ fontSize: 22 }}>Pahadi Jodi</Text>
      <TextInput placeholder="Email" autoCapitalize="none" value={email} onChangeText={setEmail} style={{ borderWidth: 1, padding: 8 }} />
      <TextInput placeholder="Password" secureTextEntry value={password} onChangeText={setPassword} style={{ borderWidth: 1, padding: 8 }} />
      <View style={{ gap: 8 }}>
        <Button title="Sign in" onPress={() => submit('login')} />
        <Button title="Create account" onPress={() => submit('register')} />
      </View>
    </SafeAreaView>
  );
}
