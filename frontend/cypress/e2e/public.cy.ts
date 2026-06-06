/// <reference types="cypress" />

describe("public smoke", () => {
  beforeEach(() => {
    cy.clearCookies();
    cy.clearLocalStorage();
  });

  it("loads public homepage and public navigation targets", () => {
    cy.visit("/");
    cy.contains("DotaOps").should("be.visible");

    cy.visit("/turnirji");
    cy.location("pathname").should("eq", "/turnirji");
    cy.get("body").should("be.visible");

    cy.visit("/login");
    cy.location("pathname").should("eq", "/login");
    cy.contains(/login to dotaops/i).should("be.visible");
    cy.get('input[type="email"]').should("be.visible");
    cy.get('input[type="password"]').should("be.visible");

    cy.visit("/register");
    cy.location("pathname").should("eq", "/register");
    cy.contains(/^Player$/).should("be.visible");
    cy.contains(/^Organizer$/).should("be.visible");
    cy.contains(/^Team Captain$/).should("not.exist");
  });
});
