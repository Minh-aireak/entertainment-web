import { Route, Routes } from "react-router-dom";
import { publicRoutes } from "./PublicRoutes";
import DefaultLayout from "../components/Layout/DefaultLayout/DefaultLayout";

export default function AppRoutes() {
  return (
    <Routes>
      {publicRoutes.map((route, index) => {
        let Layout = DefaultLayout;

        if (route.layout) {
          Layout = route.layout;
        }

        return (
          <Route
            key={index}
            path={route.path}
            element={
              <Layout>
                <route.component />
              </Layout>
            }
          />
        );
      })}
    </Routes>
  );
}
