# Security Policy

## Reporting a vulnerability

Use the repository's **Security** tab and select **Report a vulnerability**. Do not open a public issue for an unpatched vulnerability.

Include the affected version, device or Android version, reproduction steps, and any relevant logs with API keys and personal conversation data removed.

## Key handling

- Provider API keys and search keys must stay in the app or environment variables.
- Release keystores must stay outside this repository.
- GitHub Actions release builds must use the `RELEASE_*` repository secrets documented in `README.md`.
