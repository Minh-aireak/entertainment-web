import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import AuthClientStore from "../features/client-store/AuthClientStore";

export default function Authenticate() {
  const navigate = useNavigate();
  const [isLoggedin, setLoggedin] = useState(false);

  useEffect(() => {
    const authCodeRegex = /code=([^&]+)/;
    const isMath = window.location.href.match(authCodeRegex);

    if (isMath) {
      const authCode = isMath[1];

      fetch(
        `http://localhost:8888/identity/auth/outbound/authentication?code=${authCode}`,
        {
          method: "POST",
        }
      )
        .then((response) => {
          return response.json();
        })
        .then((data) => {
          AuthClientStore.setAccessToken(data.result?.token);
          setLoggedin(true);
        });
    }
  }, []);

  useEffect(() => {
    if (isLoggedin) {
      navigate("/");
    }
  }, [isLoggedin, navigate]);
}
