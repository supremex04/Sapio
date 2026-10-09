export type Role = "STUDENT" | "TEACHER" | "ADMIN";

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: User;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput extends LoginInput {
  fullName: string;
  role: Exclude<Role, "ADMIN">;
}
