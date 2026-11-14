# Slippery Seals

A sliding-puzzle game for Android, built with Kotlin and Jetpack Compose. Seals
slide across an icy board until they hit a wall or another seal — guide each seal
to its matching destination in as few moves as possible.

The repo also includes a Python/C solver (`solver/`) used to validate and
generate levels.

## Features

- Jetpack Compose UI with Material 3 theming
- Multiple level sets (tutorial, easy, medium, hard, expert, classic) loaded from
  JSON assets in `app/src/main/assets/levels/`
- Progress tracking across levels
- An A* solver (Kotlin in-app, plus a standalone Python/C toolchain) for
  verifying and generating puzzles
- **Live hints** — the in-app A* solver runs from the current board to suggest
  the next optimal move

## Requirements

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 11** (bundled with recent Android Studio releases)
- **Android SDK** with API level 35 (compile/target); minimum supported device
  API is 26 (Android 8.0)

The project uses the Gradle wrapper (Gradle 8.13, Android Gradle Plugin 8.7.3,
Kotlin 2.0.21), so no separate Gradle install is needed.

## Running in Android Studio

1. **Clone the repo**
   ```bash
   git clone https://github.com/5720axolotls/slippery-seals.git
   ```

2. **Open the project**
   - Launch Android Studio.
   - Choose **File → Open** (or **Open** from the welcome screen) and select the
     cloned `slippery-seals` folder.
   - Let Gradle sync finish. Android Studio will download the Gradle
     distribution and dependencies on first sync — this can take a few minutes.

3. **Install the SDK if prompted**
   - If Android Studio reports a missing SDK platform (API 35) or build tools,
     accept the prompt to install them, or add them via **Tools → SDK Manager**.
   - `local.properties` (holding `sdk.dir`) is generated automatically by
     Android Studio and is intentionally not committed.

4. **Pick a device**
   - Create an emulator via **Tools → Device Manager → Create Device** (any
     device running API 26+), or connect a physical device with USB debugging
     enabled.

5. **Run the app**
   - Select the `app` run configuration and the target device in the toolbar.
   - Click **Run ▶** (or press **Ctrl+R** / **Shift+F10**).

## Building from the command line

```bash
# Build a debug APK
./gradlew assembleDebug

# Install on a connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Project structure

```
app/src/main/java/com/calebnoble/slipperyseals/
├── model/        Game data types (Seal, Wall, GameState, LevelData, …)
├── engine/       Core game logic (sliding, collision, win detection)
├── solver/       In-app A* solver and heuristics
├── data/         Level loading and progress persistence
├── ui/           Compose screens, components, navigation, theme
├── util/         JSON parsing for level assets
├── MainActivity.kt
└── SlipperySealsApp.kt   Navigation host

app/src/main/assets/levels/   Level definitions (tutorial/easy/medium/hard/expert)
solver/                        Standalone Python/C solver & level generation tools
```

## Solver tooling (optional)

The `solver/` directory contains a standalone toolchain for generating and
verifying levels. It is independent of the Android build. See the scripts there
(`generate.py`, `benchmark.py`, `astar.py`, `solver_native.c`) for usage.

### Generating new levels

`generate.py` creates random, verified-solvable boards and writes them in the
same JSON format the app loads. For example, a 10×10 board with 4 seals, a single
goal, and ~7 walls (the "Classic" set was built this way):

```bash
cd solver
python3 generate.py medium --rows 10 --cols 10 --seals 4 --dests 1 --walls 7 \
  --min-moves 5 --max-moves 12 -n 10 -o ../app/src/main/assets/levels/classic.json
```

Key flags: `--rows`/`--cols` (board size), `--seals`, `--dests` (goals),
`--walls`, `--min-moves`/`--max-moves` (difficulty filter), `-n` (count),
`-s` (seed). To add a brand-new set, write a new `*.json` file and add a matching
entry to the `LevelSet` enum
(`app/src/main/java/com/calebnoble/slipperyseals/model/LevelSet.kt`); the home
screen picks it up automatically.
