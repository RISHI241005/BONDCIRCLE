# Project scope

BondCircle is currently a Flutter frontend prototype. Do not implement a backend unless explicitly requested.

If backend development is requested in the future, use Java Spring Boot, not Node.js/Express.

Authentication UI order: Sign Up → email → demo email code → create and confirm password → profile setup. Sign In → email → new demo email code → existing password → Discover. Forgot Password is accessible from the sign-in password step. Demo codes are displayed locally, not emailed, and are not real account verification. Passwords are not stored or authenticated.

Password recovery is a UI demonstration: no email is sent, no account is verified, and no password is stored or changed. The displayed code 123456 is only for the frontend demo and must never be used as real authentication.

Blind Bond: Join Circle → Enter Blind Bond → Search → Anonymous Partner Found → 15-minute Chat or 10-minute Voice Demo → Connection Check → Mutual Reveal → Normal Chat. Circle cards expose the entry point after joining. The Blind Bond hub also supports joining Coffee, Gaming, Books (Readers & Stories), Fitness and existing circles.

Pairing uses a random eligible alias from a small local demo pool, excluding previously revealed or blocked aliases for the running app session. All partners and shared interests are fictional. No real pairing, messages, calls, reports or consent are transmitted. Voice controls only change their UI state. Other-person agree/decline controls are explicitly presentation-only. Neither a single yes, a decline nor a withdrawal reveals a profile. Timers use a deadline, recheck on resume, and close interaction before the decision screen. Leaving ends the session without revealing identities.

Privacy UI hides profile fields until mutual consent and warns against sharing private information. A basic chat guard rejects obvious links/email/long digit strings, but is not comprehensive personal-information filtering; real anonymity and authorization will require backend enforcement. Do not treat this prototype as a privacy or security guarantee.
