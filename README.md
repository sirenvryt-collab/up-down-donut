# Money Tracker (client-side, Fabric 1.21.11)

Mark a point in time in-game, and see how much money you've gained or lost
since then. Built for economy servers like Donut SMP. **Fully client-side** —
no server mod/plugin needed, it just reads chat/action-bar text that's already
sent to you.

## How it works

The mod watches every chat message, system message, and action bar message
your client receives, and looks for something matching a `$` amount (e.g.
`$1,234.56`). Whenever it finds one, it remembers it as your "current known
balance." So it works automatically the moment you run whatever balance
command your server uses (`/bal`, `/balance`, `/money`, etc.), or if the
server prints your balance somewhere on its own (join message, tab list
message, etc. — as long as it's sent as a chat/game message).

## Commands

- `/moneymark` – mark the current balance as your starting point
- `/moneycheck` – show current balance, marked balance, and the difference
- `/moneyreset` – clear the marked point
- `/moneyhud` – toggle the on-screen overlay (top-left corner)
- `/moneyset <amount>` – manually set the current balance if auto-detect misses it
- `/moneypattern <regex>` – change the detection regex if the default doesn't
  match your server's balance format. It must have exactly one capture group
  around the number, e.g. `\$(\d+(?:\.\d+)?)`

Data (marked point + last known balance) is saved to
`.minecraft/config/moneytracker.json` so it survives restarts.

## Turning this into a .jar using only the GitHub website (no Git required)

1. **Create the repo**
   - Go to https://github.com/new
   - Name it (e.g. `moneytracker`), keep it Public or Private, click **Create repository**.

2. **Upload the files**
   - On your new repo's page, click **Add file → Upload files**.
   - Drag in *the whole folder structure* from this project (or unzip the
     zip I gave you and drag all its contents in). GitHub's uploader supports
     dragging entire folders in most browsers — it will preserve the paths
     (`src/main/java/...`, `.github/workflows/build.yml`, etc.).
   - If your browser won't let you drag folders, upload the files one
     directory at a time — GitHub creates folders automatically based on the
     path you drop things into.
   - Scroll down, click **Commit changes** (commit directly to `main`).

3. **Let GitHub Actions build the jar**
   - Click the **Actions** tab at the top of the repo.
   - You should see a workflow run called "Build" already running (it
     triggers automatically on every push to `main`). If you don't see one,
     click **Build** in the left sidebar → **Run workflow** → **Run workflow**.
   - Wait for the green checkmark (takes a couple of minutes the first time).

4. **Download your jar**
   - Click into the finished workflow run.
   - Scroll down to **Artifacts** and click **moneytracker-jar** to download
     a zip containing your built `.jar` file.
   - Unzip it, and drop the `.jar` into your `.minecraft/mods` folder
     (alongside Fabric Loader 0.18.1+ and Fabric API 0.139.5+1.21.11).

Any time you want to change the code, just edit the file(s) again on the
GitHub website (click a file → pencil/edit icon → commit), and the Actions
workflow will automatically rebuild the jar for you.

## Requirements to run the mod in-game

- Minecraft 1.21.11
- Fabric Loader 0.18.1+
- Fabric API 0.139.5+1.21.11 (or newer, matching build)
