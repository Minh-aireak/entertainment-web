import Login from "../pages/Login";
import Home from "../pages/Home";
import Profile from "../pages/Profile/Profile";
import Messages from "../pages/Message";
import Groups from "../pages/Groups";
import Schedules from "../pages/Schedules";
import Authenticate from "../pages/Authenticate";
import Friends from "../pages/Friends";
import HeaderContentLayout from "../components/Layout/HeaderContentLayout/HeaderContentLayout";
import LoginLayout from "../components/Layout/LoginLayout/LoginLayout";

const publicRoutes = [
  { path: "/login", component: Login, layout: LoginLayout },
  { path: "/authenticate", component: Authenticate },
  { path: "/", component: Home },
  { path: "/profile", component: Profile },
  { path: "/schedules", component: Schedules },
  { path: "/friends", component: Friends },
  { path: "/groups", component: Groups },
  { path: "/messages", component: Messages },
];

export { publicRoutes };
