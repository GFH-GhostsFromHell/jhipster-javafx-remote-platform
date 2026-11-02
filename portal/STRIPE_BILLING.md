# Stripe subscription billing

Electrical Workbench uses Stripe Checkout in subscription mode for Tier 2 and Tier 3. Paid features are granted only after a signed Stripe webhook confirms the subscription state.

## Required Render environment variables

- `APP_BASE_URL=https://remote-javafx-portal.onrender.com`
- `STRIPE_SECRET_KEY` — Stripe secret API key (`sk_test_...` while testing, then `sk_live_...` for production)
- `STRIPE_WEBHOOK_SECRET` — signing secret for the production webhook endpoint (`whsec_...`)
- `TIER_2_STRIPE_PRICE_ID` — recurring Stripe Price ID for Tier 2 (`price_...`)
- `TIER_3_STRIPE_PRICE_ID` — recurring Stripe Price ID for Tier 3 (`price_...`)
- `STRIPE_AUTOMATIC_TAX=false` — set to `true` only after Stripe Tax is configured for the account

Do not commit API keys or webhook secrets to GitHub.

## Stripe webhook

Endpoint:

`https://remote-javafx-portal.onrender.com/api/public/stripe/webhook`

Subscribe to these events:

- `checkout.session.completed`
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`

The webhook handler verifies the `Stripe-Signature` header against `STRIPE_WEBHOOK_SECRET`, logs processed event IDs for retry safety, and ignores older subscription-state events when a newer Stripe event has already been applied.

## Tier behaviour

- Tier 1: base tier; no Stripe checkout required.
- Tier 2: recurring subscription using `TIER_2_STRIPE_PRICE_ID`.
- Tier 3: recurring subscription using `TIER_3_STRIPE_PRICE_ID`.
- `active` and `trialing` Stripe subscriptions grant the paid tier.
- `canceled`, `unpaid`, `incomplete_expired`, and `paused` subscriptions revert the account to Tier 1.
- `past_due` and other non-terminal states retain the current entitlement while Stripe's collection/retry flow continues.

## Customer portal

Enable the Stripe Customer Portal in the Stripe Dashboard. Existing paid subscribers are routed to the portal for plan changes, payment-method updates, and cancellation rather than creating a second subscription.

If customers should be able to move directly between Tier 2 and Tier 3 in the portal, enable subscription plan switching and make both products/prices available in the portal configuration.

## Test mode first

Create recurring test-mode prices for Tier 2 and Tier 3, use the corresponding test Price IDs and test secret key in Render, create a test webhook endpoint, and confirm signup, upgrade, downgrade, cancellation, and webhook retries before switching to live-mode keys and live Price IDs.
