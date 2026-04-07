---
name: conventional-commits
description: Generates conventional commit messages using AI. Analyzes code changes and suggests a commit message adhering to the conventional commits specification with a custom verb vocabulary (Add, Fix, Harden, Extract, DRY, Rework, Replace, Security). Use this skill whenever the user needs help writing clear, standardized commit messages, especially after making code changes and preparing to commit. Trigger aggressively with phrases like "create commit", "generate commit message", "write commit", "commit this", "what should my commit say", "help me commit", or any time the user shares a diff, staged changes, or describes what they just coded.
---

# Conventional Commits Skill

Generates clear, standardized commit messages following the Conventional Commits specification, using a curated verb vocabulary designed for professional codebases.

---

## Verb Vocabulary

Use **exactly one** of these verbs as the commit type. Do not invent new ones.

| Verb | When to use |
|------|-------------|
| `Add` | Introducing something new: file, class, route, feature, dependency |
| `Fix` | Correcting broken behavior, wrong output, crash, or typo |
| `Harden` | Making code more resilient: error handling, validation, edge cases, logs, timeouts |
| `Extract` | Pulling logic out of one place into a new isolated function, class, or module |
| `DRY` | Removing duplicated code and centralizing it in one place |
| `Rework` | Rewriting internal logic for clarity/modernization without changing behavior |
| `Replace` | Swapping one library, method, variable, or approach for another |
| `Security` | Patching vulnerabilities, sanitizing input, updating unsafe deps, adding encryption |

---

## Commit Message Format

```
<Verb>: <concise imperative description>

[optional body: what changed and why, not how]

[optional footer: breaking changes, issue refs like (#123)]
```

### Rules
- **Subject line**: 50 chars or less, imperative mood, no period at the end
- **Verb**: Capitalized, from the vocabulary above
- **Body**: Only when the "why" isn't obvious from the subject. Wrap at 72 chars.
- **Footer**: Issue/ticket refs go here — e.g., `Closes #42`, `Refs #88`

---

## Step-by-Step Process

### 1. Gather context
Ask for (or look at) any of the following — more context = better message:
- `git diff --staged` output
- Description of what changed
- Ticket/issue number (optional)
- Whether it's a breaking change (optional)

### 2. Identify the primary intent
Pick the **single most important** thing this commit does. A commit should do one thing. If the diff covers multiple unrelated changes, flag this and suggest splitting.

### 3. Select the verb
Match the primary intent to the vocabulary table above. When in doubt:
- Something new → `Add`
- Something broken → `Fix`
- Same behavior, better code → `Rework`, `Extract`, or `DRY`
- Safer code → `Harden`
- Swapped out A for B → `Replace`
- Attack surface reduced → `Security`

### 4. Write the subject line
Format: `Verb: what was done to what`

Good examples:
```
Add email validation to registration form
Fix null pointer in user profile loader
Harden API timeout handling in payment gateway
Extract auth middleware to separate module
DRY product discount calculation
Rework date formatting using Intl.DateTimeFormat
Replace Axios with native Fetch API
Security: Sanitize file upload paths to prevent traversal
```

Bad examples (avoid these patterns):
```
fix stuff              ← too vague
Added some code        ← past tense, not imperative
Update: misc changes   ← meaningless
feat: add login        ← wrong format (this skill doesn't use feat/fix/chore)
```

### 5. Add body/footer if needed
- Body: explain *why* if the subject doesn't make it obvious
- Footer: add issue refs or `BREAKING CHANGE:` note

---

## Output Format

Always present the commit message in a code block, ready to copy:

```
Fix: correct null check in cart total calculation

Cart total returned NaN when discount code was applied to an empty cart.
Added early return when items array is empty.

Closes #204
```

Then offer a brief one-line explanation of *why* you chose that verb — this helps the user learn and catch mistakes.

If the diff is ambiguous or covers multiple concerns, offer **2–3 alternatives** ranked by best fit.

---

## Edge Cases

**Multiple changes in one diff**: If the diff touches unrelated things (e.g., a bug fix AND a new feature), recommend splitting into separate commits. Suggest messages for each.

**Breaking changes**: Add `BREAKING CHANGE: <description>` in the footer. Mention it clearly when flagging.

**Ticket numbers**: If the user provides one, append `Closes #N` or `Refs #N` in the footer.

**Unclear diffs**: Ask one targeted question to clarify intent before generating. Don't guess wildly.

**Security commits**: Always use `Security:` prefix (capitalized) for any security-related change, even if it's also a fix.