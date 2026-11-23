import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { jwtDecode } from 'jwt-decode';
import axiosClient from '../api/axiosClient';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [branchesData, setBranchesData] = useState([]);
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [loading, setLoading] = useState(true);

  const parseToken = (token) => {
    const decoded = jwtDecode(token);
    return {
      token,
      username: decoded.sub,
      userId: decoded.userId,
      organizationId: decoded.organizationId,
      branchId: decoded.branchId,
      role: decoded.primaryRole,
      isOwner: Boolean(decoded.isOwner),
      roles: decoded.roles || [],
    };
  };

  const loadBranchesForUser = useCallback(async (parsedUser) => {
    if (!parsedUser || parsedUser.role === 'SUPER_ADMIN' || parsedUser.role === 'CUSTOMER') {
      setBranchesData([]);
      return;
    }
    try {
      const res = await axiosClient.get('/branches/my');
      const items = res.data || [];
      setBranchesData(items);

      if (items.length > 0) {
        const savedBranch = Number(localStorage.getItem('selectedBranchId'));
        const matched = items.find((b) => b.id === savedBranch) || items[0];
        const branchIdStr = String(matched.id);
        setSelectedBranchId(branchIdStr);
        localStorage.setItem('selectedBranchId', branchIdStr);
      } else {
        setSelectedBranchId('');
      }
    } catch (error) {
      console.error('Failed to load user branches:', error);
    }
  }, []);

  useEffect(() => {
    const init = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const decoded = jwtDecode(token);
          if (decoded.exp && decoded.exp * 1000 < Date.now()) {
            localStorage.removeItem('token');
            setLoading(false);
            return;
          }
          const parsed = parseToken(token);
          setUser(parsed);
          await loadBranchesForUser(parsed);
        } catch {
          localStorage.removeItem('token');
        }
      }
      setLoading(false);
    };
    init();
  }, [loadBranchesForUser]);

  const login = async (username, password) => {
    const res = await axiosClient.post('/auth/login', { username, password });
    localStorage.setItem('token', res.data.token);
    const parsed = parseToken(res.data.token);
    setUser(parsed);
    await loadBranchesForUser(parsed);
    return parsed;
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('selectedBranchId');
    setUser(null);
    setBranchesData([]);
    setSelectedBranchId('');
  };

  const selectBranch = (branchId) => {
    const idStr = String(branchId);
    setSelectedBranchId(idStr);
    localStorage.setItem('selectedBranchId', idStr);
  };

  const refreshBranches = async () => {
    if (user) await loadBranchesForUser(user);
  };

  const currentBranch = branchesData.find((b) => String(b.id) === String(selectedBranchId)) || null;

  return (
    <AuthContext.Provider
      value={{
        user,
        login,
        logout,
        loading,
        branchesData,
        currentBranch,
        selectedBranchId: selectedBranchId ? Number(selectedBranchId) : null,
        selectBranch,
        refreshBranches,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);