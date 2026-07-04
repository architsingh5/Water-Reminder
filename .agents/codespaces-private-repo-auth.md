# Codespaces Private Repo Git Auth

Use this when `git clone` or `git fetch` returns `403` in Codespaces for a private GitHub repository.

## Why this happens

Codespaces can inject a `GITHUB_TOKEN` and a global Git credential helper.
If that token does not have access to the target private repo, Git operations fail even when the user account has access.

## Known symptom

```bash
git clone https://github.com/<owner>/<repo>.git
# remote: Write access to repository not granted.
# fatal: ... The requested URL returned error: 403
```

## One-command workaround

```bash
env -u GITHUB_TOKEN git -c credential.helper= clone https://github.com/<owner>/<repo>.git
```

## Reusable pattern for other Git commands

Run private-repo Git commands with both overrides:

- remove injected token: `env -u GITHUB_TOKEN`
- bypass global helper: `-c credential.helper=`

Examples:

```bash
env -u GITHUB_TOKEN git -c credential.helper= -C <repo-dir> fetch --all
env -u GITHUB_TOKEN git -c credential.helper= -C <repo-dir> pull
env -u GITHUB_TOKEN git -c credential.helper= -C <repo-dir> push
```

## Optional: verify current auth context

```bash
gh auth status
git config --show-origin --get-all credential.helper
env | grep -E 'GITHUB_TOKEN|GH_TOKEN|GIT_ASKPASS|CODESPACES' || true
```

## Notes for agents

- Do not print or request secrets in chat.
- If access still fails after overrides, confirm the authenticated GitHub identity actually has read permission on the private repo.
- If needed, switch to a PAT flow with `gh auth login --with-token` in a terminal where secrets can be entered directly by the user.
