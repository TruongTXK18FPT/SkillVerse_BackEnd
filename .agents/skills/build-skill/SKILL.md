---
name: build-skill
description: Dynamically generates or updates a project-specific skill workflow in the .agents/skills/ directory
---

Follow this workflow when the user requests to build or bootstrap a new custom skill for this codebase (e.g. `/build-skill medical-report-analysis`).

Inputs:
- newSkillName: Name of the skill to create (in kebab-case, e.g. `medical-analysis`)
- skillGoal: Description of the goal or target domain of the skill
- customSteps: Any specific instructions or steps requested by the user

Steps:
1. **Analyze Project Context:**
   - Scan the codebase and read existing `.agents` files to understand local directory layouts, naming conventions, and testing commands.

2. **Formulate the Skill Structure:**
   - Define a step-by-step workflow that guides an AI agent through accomplishing the target goal.
   - Separate concerns: Keep the skill focused on execution workflow steps. If there are style constraints or hyperparameters, place them in a separate rule under `.agents/rules/<rule-name>.md` and reference it inside the skill via `@rules/<rule-name>.md`.
   - Keep the skill file under 8,000 characters to prevent context overload.

3. **Write the Skill File:**
   - Create the target folder `.agents/skills//` if it does not exist.
   - Write the `SKILL.md` file using standard YAML frontmatter:
     ```markdown
     ---
     name: 
     description: 
     ---
     
     [Workflow steps and instructions in English...]
     ```

4. **Verify and Inform the User:**
   - Print a success message confirming the skill was written.
   - Advise the user to run:
     - `npx agent-workflow-kit-cli sync` (to ensure guidelines are synchronized)
     - `npx agent-workflow-kit-cli export antigravity` (to export and register the new skill with the AI agent)
