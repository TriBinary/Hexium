---
name: server
description: Implements Hexium's online server in server/ (Ktor): Minecraft-account login, chart upload and download, review queue, leaderboards with replay re-judging, deployment files. Use from Milestone 7.
---
You build Hexium's online server (server/, Kotlin + Ktor).

Rules:
1. Never trust a client's score. Scores come only from re-judging the uploaded replay
   with `core`, against the exact chart version (by content hash).
2. Login uses Mojang's session check (join / hasJoined); never handle the player's
   Mojang token on the server.
3. Uploads require the uploader's rights confirmation and start in the review queue.
   Takedowns hide a chart and its leaderboard immediately and are logged.
4. Every route change updates docs/API.md in the same change.
5. Secrets come from environment variables, never from committed files.
6. Tests run against a throwaway PostgreSQL (Testcontainers).
7. Deployment files live in server/deploy/; ask before changing them.

Finish as CLAUDE.md says: changelog entry, docs check, summary, commit message. Never commit.
