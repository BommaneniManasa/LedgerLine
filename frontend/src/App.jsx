import { Layout } from './components/Layout.jsx';
import { useAuth } from './context/AuthContext.jsx';
import { AdminAuditPage, AdminDashboardPage } from './pages/AdminPages.jsx';
import { AuditorDashboardPage } from './pages/AuditorPage.jsx';
import { LoginPage, NotFoundPage, RegisterPage } from './pages/AuthPages.jsx';
import { AccountDetailPage, AccountsPage, DashboardPage, HistoryPage } from './pages/CustomerPages.jsx';
import { DepositWithdrawPage, TransferPage } from './pages/MoneyPages.jsx';
import { ProfilePage } from './pages/ProfilePage.jsx';
import { Navigate, ProtectedRoute, Routes, homeFor } from './router.jsx';

// Wraps a page: role check + shared layout. The backend enforces the same rules again on every API call.
function guarded(roles, page) {
  return (
    <ProtectedRoute roles={roles}>
      <Layout>{page}</Layout>
    </ProtectedRoute>
  );
}

function Home() {
  const { user, status } = useAuth();
  if (status === 'loading') return null;
  return <Navigate to={user ? homeFor(user.role) : '/login'} />;
}

const CUSTOMER = ['CUSTOMER'];
const ADMIN = ['ADMIN'];
const AUDITOR = ['AUDITOR'];
const ANY = ['CUSTOMER', 'ADMIN', 'AUDITOR'];

const routes = [
  { path: '/', element: <Home /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/dashboard', element: guarded(CUSTOMER, <DashboardPage />) },
  { path: '/accounts', element: guarded(CUSTOMER, <AccountsPage />) },
  { path: '/accounts/:id', element: guarded(CUSTOMER, <AccountDetailPage />) },
  { path: '/transactions', element: guarded(CUSTOMER, <HistoryPage />) },
  { path: '/deposit', element: guarded(CUSTOMER, <DepositWithdrawPage kind="deposit" />) },
  { path: '/withdraw', element: guarded(CUSTOMER, <DepositWithdrawPage kind="withdraw" />) },
  { path: '/transfer', element: guarded(CUSTOMER, <TransferPage />) },
  { path: '/profile', element: guarded(ANY, <ProfilePage />) },
  { path: '/admin', element: guarded(ADMIN, <AdminDashboardPage />) },
  { path: '/admin/audit', element: guarded(ADMIN, <AdminAuditPage />) },
  { path: '/auditor', element: guarded(AUDITOR, <AuditorDashboardPage />) },
  { path: '*', element: <NotFoundPage /> },
];

export default function App() {
  return <Routes routes={routes} />;
}
