/// <reference types="cypress" />

const demoPassword = String(Cypress.env("DEMO_PASSWORD"));
const demoOrganizerEmail = String(Cypress.env("DEMO_ORGANIZER_EMAIL"));

function login(email: string) {
  cy.visit("/login");
  cy.get('input[type="email"]').clear().type(email);
  cy.get('input[type="password"]').clear().type(demoPassword, { log: false });
  cy.contains("button", /^Login$/).click();
  cy.contains("button", /^Skip$/, { timeout: 20_000 }).click({ force: true });
  cy.location("pathname", { timeout: 30_000 }).should("not.eq", "/login");
}

describe("organizer auth smoke", () => {
  beforeEach(() => {
    cy.clearCookies();
    cy.clearLocalStorage();
  });

  it("logs in as the seeded demo organizer and opens organizer pages", () => {
    login(demoOrganizerEmail);

    cy.visit("/dashboard");
    cy.get("body").should("contain.text", "DotaOps");

    cy.visit("/organizator");
    cy.location("pathname").should("eq", "/organizator");
    cy.get("body").should("be.visible");

    cy.visit("/analitika");
    cy.location("pathname").should("eq", "/analitika");
    cy.get("body").should("be.visible");
  });
});
