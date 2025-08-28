import Login from "../pages/Login/Login";
import Home from "../pages/Home/Home";
import Profile from "../pages/Profile/Profile";
import Messages from "../pages/Messages/Message";
import Groups from "../pages/Groups/Groups";
import Schedules from "../pages/Schedules/Schedules";
import Authenticate from "../pages/Authenticate/Authenticate";
import Friends from "../pages/Friends/Friends";
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