# Decisions to explain in a walkthrough

## Atomic stock changes

`InventoryService` locks a product before calculating its new quantity, validates the result and writes the movement record in the transaction. Purchase/sales services lock order state, then advance the workflow and apply the stock movements together. If an operation fails, the transaction rolls back rather than leaving a partial stock change.

Receiving an already received purchase or fulfilling an already fulfilled sale cannot apply the movement a second time. Sales confirmation does not reserve stock; fulfillment checks actual available stock. These are important distinctions to show using the order and inventory screens.

## Authentication

Spring Security validates bearer JWTs and role rules. The current user is checked on requests; the password credential version invalidates older tokens after a password change. Browser logout clears the local session but is not per-token server revocation. Describe that limitation instead of claiming logout revokes every copied token.

## Schema and verification

Flyway applies three current migrations; production Hibernate validates the schema. Integration tests use an isolated H2 database, while the separate Docker and browser CI jobs exercise MySQL and the production images. H2 results alone do not prove MySQL deployment readiness.

## Demonstrate, then explain

Follow [DEMO.md](DEMO.md): receive a purchase, fulfill a sale, show previous/new quantities and the linked history, then open the calculated invoice. Explain rejected transitions, insufficient stock and rollback behaviour while pointing to the service/repository code.
