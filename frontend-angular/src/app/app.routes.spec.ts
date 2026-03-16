import { appRoutes } from "./app.routes";

describe("appRoutes", () => {
  it("should expose login route", () => {
    const loginRoute = appRoutes.find((route) => route.path === "login");
    expect(loginRoute).toBeDefined();
    expect(typeof loginRoute?.loadComponent).toBe("function");
  });

  it("should expose authenticated shell route", () => {
    const shellRoute = appRoutes.find((route) => route.path === "");
    expect(shellRoute).toBeDefined();
    expect(shellRoute?.canActivate?.length).toBeGreaterThan(0);
    expect(shellRoute?.children?.length).toBeGreaterThan(0);
  });

  it("should keep wildcard as the last route", () => {
    expect(appRoutes.at(-1)?.path).toBe("**");
  });
});
