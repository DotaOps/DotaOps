/// <reference types="cypress" />

const protectedRoutes = ["/dashboard", "/ekipe", "/analitika"];

function expectProtectedState() {
  cy.location("pathname").then((pathname) => {
    if (pathname === "/login") {
      cy.contains(/login/i).should("be.visible");
      return;
    }

    cy.get("body").should(($body) => {
      const text = $body.text();
      expect(text).to.match(/login|required|access|authentication|restricted/i);
    });
  });
}

describe("protected route smoke", () => {
  beforeEach(() => {
    cy.clearCookies();
    cy.clearLocalStorage();
  });

  protectedRoutes.forEach((route) => {
    it(`protects ${route} for logged-out visitors`, () => {
      cy.visit(route, { failOnStatusCode: false });
      expectProtectedState();
    });
  });
});
