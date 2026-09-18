import axios from 'axios';
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080', timeout: 5000 });
let accessToken = null;
export const setAccessToken = token => { accessToken = token; };
api.interceptors.request.use(config => {
  if (accessToken && config.url !== '/api/auth/login') config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});
api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && error.config?.url !== '/api/auth/login') {
    window.dispatchEvent(new Event('stockflow:unauthorized'));
  }
  return Promise.reject(error);
});
