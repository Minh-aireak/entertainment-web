import Login from "../pages/Login/Login";
import Profile from "../pages/Profile/Profile";
import Message from "../pages/Message/Message";
import Groups from "../pages/Groups";
import Schedules from "../pages/Schedule/Schedules";
import Authenticate from "../pages/Authenticate";
import Friends from "../pages/Friend/Friends";
import { LoginLayout, ContentLayout } from "../components/Layout/CustomLayout";
import Home from "../pages/Home/Home";

const publicRoutes = [
  { path: "/", component: Home },
  { path: "/login", component: Login, layout: LoginLayout },
  { path: "/authenticate", component: Authenticate },
  { path: "/profile", component: Profile },
  { path: "/schedules", component: Schedules },
  { path: "/friends", component: Friends },
  { path: "/groups", component: Groups },
  { path: "/messages", component: Message, layout: ContentLayout },
];

export { publicRoutes };
