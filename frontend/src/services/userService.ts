import axios from "axios";
import { tokenManager } from "./tokenManager";
import type { UserRequestDTO } from "../Types";
import { clearVideoCache } from "./videoService";
import { clearMontageCache } from "./montageService";
import { errorMessage } from "./errors";

const backendURL = import.meta.env.VITE_BACKEND_URL;

async function generateToken(email: string, password: string): Promise<void> {
  const tokenResponse = await axios.post(
    `${backendURL}/generate-token`,
    { username: email, password: password },
    { withCredentials: true } // Send/receive the refresh token cookie
  );
  // The access token only ever lives in memory
  tokenManager.setToken(tokenResponse.headers.authorization);
}

export async function logInOrSignUp(
  isLogin: boolean,
  email: string,
  password: string,
  userName: string,
  turnstileToken: string = ""
): Promise<string> {
  if (isLogin) {
    try {
      await generateToken(email, password);
      return "";
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        return "Invalid username or password.";
      }
      return errorMessage(err, "Login failed. Please try again.");
    }
  }

  const request: UserRequestDTO = {
    username: email,
    password: password,
    displayedName: userName,
    turnstileToken: turnstileToken,
  };
  try {
    await axios.post(`${backendURL}/register`, request);
  } catch (err) {
    return errorMessage(err, "Sign up failed. Please try again.");
  }
  try {
    await generateToken(email, password);
    return "";
  } catch (err) {
    return errorMessage(err, "Your account was created, but logging in failed. Please log in.");
  }
}

// New function to restore session on app startup
export async function restoreSession(): Promise<boolean> {
  try {
    const tokenResponse = await axios.post(
      `${backendURL}/refresh-token`,
      {},
      { withCredentials: true } // Sends refresh token cookie
    );

    const accessToken = tokenResponse.headers.authorization;
    if (accessToken) {
      tokenManager.setToken(accessToken);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

export async function logout(): Promise<void> {
  try {
    // Backend clears the HttpOnly refresh token cookie
    await axios.post(`${backendURL}/refresh-token/logout`, {}, { withCredentials: true });
  } catch (err) {
    console.error("Logout request failed:", err);
  }
  tokenManager.clearToken();
  clearVideoCache();
  clearMontageCache();
}
