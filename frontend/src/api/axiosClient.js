import axios from 'axios';

const BASE_HOST = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const axiosClient = axios.create({
  baseURL: `${BASE_HOST}/api`,
  headers: {
    'Content-Type': 'application/json',
  },
});

const publicPaths = ['/auth/login', '/auth/register-workspace', '/public/'];

axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const isPublic = publicPaths.some((path) => config.url?.includes(path));

    if (token && !isPublic) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isPublic = publicPaths.some((path) => error.config?.url?.includes(path));
      if (!isPublic) {
        localStorage.removeItem('token');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default axiosClient;