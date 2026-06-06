/// <reference types="cypress" />

const demoPassword = String(Cypress.env("DEMO_PASSWORD"));
const demoPlayerEmail = String(Cypress.env("DEMO_PLAYER_EMAIL"));

function login(email: string) {
  cy.visit("/login");
  cy.get('input[type="email"]').clear().type(email);
  cy.get('input[type="password"]').clear().type(demoPassword, { log: false });
  cy.contains("button", /^Login$/).click();
  cy.contains("button", /^Skip$/, { timeout: 20_000 }).click({ force: true });
  cy.location("pathname", { timeout: 30_000 }).should("not.eq", "/login");
}

describe("player auth smoke", () => {
  beforeEach(() => {
    cy.clearCookies();
    cy.clearLocalStorage();
  });

  it("logs in as the seeded demo player and opens core player pages", () => {
    login(demoPlayerEmail);

    cy.visit("/dashboard");
    cy.get("body").should("contain.text", "DotaOps");

    cy.visit("/ekipe");
    cy.location("pathname").should("eq", "/ekipe");
    cy.get("body").should("be.visible");

    cy.visit("/analitika");
    cy.location("pathname").should("eq", "/analitika");
    cy.get("body").should("be.visible");
  });
});
