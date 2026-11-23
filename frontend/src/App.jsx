import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Login from './pages/Login';
import SuperAdminDashboard from './pages/SuperAdminDashboard';
import OrganizationManagement from './pages/OrganizationManagement';
import StaffManagement from './pages/StaffManagement';
import BranchesManagement from './pages/BranchesManagement';
import PosCashierDashboard from './pages/PosCashierDashboard';
import KdsKitchenScreen from './pages/KdsKitchenScreen';
import AnalyticsDashboard from './pages/AnalyticsDashboard';
import PickupDisplayScreen from './pages/PickupDisplayScreen';
import PublicQrMenu from './pages/PublicQrMenu';

const ProtectedRoute = ({ children }) => {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-slate-500 font-bold">جاري التحميل...</p>
      </div>
    );
  }
  if (!user) return <Navigate to="/login" />;
  return children;
};

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/m/:qrToken" element={<PublicQrMenu />} />
          <Route path="/menu/:branchId" element={<PublicQrMenu />} />
          <Route path="/pickup" element={<PickupDisplayScreen />} />

          <Route path="/super-admin" element={<ProtectedRoute><SuperAdminDashboard /></ProtectedRoute>} />
          <Route path="/organization" element={<ProtectedRoute><OrganizationManagement /></ProtectedRoute>} />
          <Route path="/staff" element={<ProtectedRoute><StaffManagement /></ProtectedRoute>} />
          <Route path="/branches" element={<ProtectedRoute><BranchesManagement /></ProtectedRoute>} />
          <Route path="/pos" element={<ProtectedRoute><PosCashierDashboard /></ProtectedRoute>} />
          <Route path="/kds" element={<ProtectedRoute><KdsKitchenScreen /></ProtectedRoute>} />
          <Route path="/analytics" element={<ProtectedRoute><AnalyticsDashboard /></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;