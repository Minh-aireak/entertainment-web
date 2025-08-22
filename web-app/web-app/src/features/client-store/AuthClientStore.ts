const ACCESS_TOKEN_KEY = "access_token";

class AuthClientStore {
  static setAccessToken(token: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
  }

  static getAccessToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  static removeAccessToken() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }
}

export default AuthClientStore;
