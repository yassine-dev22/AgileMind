import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { SprintProvider, useSprint } from './context/SprintContext';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Layout = lazy(() => import('./components/layout/Layout'));
const Landing = lazy(() => import('./pages/Landing'));
const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));

const DashboardRoute = () => {
  const { currentProject } = useSprint();
  return <Dashboard key={currentProject?.id ?? 'home'} />;
};

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
};

const App: React.FC = () => {
  return (
    <AuthProvider>
      <SprintProvider>
        <BrowserRouter>
          <Suspense fallback={<div className="flex h-screen w-full items-center justify-center text-gray-500">Loading...</div>}>
            <Routes>
              <Route path="/" element={<Landing />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              
              <Route path="/dashboard" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
                <Route index element={<DashboardRoute />} />
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Route>
              
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </SprintProvider>
    </AuthProvider>
  );
};

export default App;
