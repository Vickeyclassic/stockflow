import { createContext, useContext, useEffect, useState } from 'react';
import { api, setAccessToken } from './api/client';
const AuthContext = createContext(null);
const storageKey = 'stockflow.session';
function restoredSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey));
    return value?.token && Date.parse(value.expiresAt) > Date.now() ? value : null;
  } catch { return null; }
}
export function AuthProvider({ children }) {
  const [session, setSession] = useState(restoredSession);
  const [checking, setChecking] = useState(true);
  const [message, setMessage] = useState('');
  setAccessToken(session?.token);
  function logout(reason = '') {
    setAccessToken(null); sessionStorage.removeItem(storageKey); setSession(null); setMessage(reason);
    window.location.hash = 'login';
  }
  useEffect(() => {
    const expire = () => logout('Your session expired. Please sign in again.');
    window.addEventListener('stockflow:unauthorized', expire);
    return () => window.removeEventListener('stockflow:unauthorized', expire);
  }, []);
  useEffect(() => {
    if (!session) { setChecking(false); return; }
    let active = true;
    setChecking(true);
    api.get('/api/auth/me').then(({ data }) => {
      if (active) setSession(previous => previous ? { ...previous, user: data } : null);
    }).catch(() => { if (active) logout('Could not validate your session. Please sign in again.'); })
      .finally(() => { if (active) setChecking(false); });
    const timer = setTimeout(() => logout('Your session expired. Please sign in again.'), Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    return () => { active = false; clearTimeout(timer); };
  }, [session?.token]);
  async function login(username, password) {
    const { data } = await api.post('/api/auth/login', { username, password });
    sessionStorage.setItem(storageKey, JSON.stringify(data)); setAccessToken(data.token);
    setChecking(true); setSession(data); setMessage(''); window.location.hash = 'dashboard';
  }
  return <AuthContext.Provider value={{ user: session?.user, checking, message, login, logout }}>{children}</AuthContext.Provider>;
}
export const useAuth = () => useContext(AuthContext);
