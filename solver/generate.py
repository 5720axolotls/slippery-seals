#!/usr/bin/env python3
"""Generate random Slippery Seals levels and filter by optimal move count."""
from __future__ import annotations
import json
import multiprocessing as mp
import random
import sys
import time
from dataclasses import dataclass

from engine import (
    Destination, Direction, LevelData, Move, Pos, Seal, Wall, build_wall_set,
)
from astar import AStarSolver
from heuristics import axis_with_setup

try:
    from benchmark import NativeSolver
    _HAS_NATIVE = True
except Exception:
    _HAS_NATIVE = False

COLORS = ["RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE"]
SIDES = [Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT]


@dataclass
class GeneratorConfig:
    rows: int = 8
    cols: int = 8
    n_seals: int = 3
    n_destinations: int = 1
    n_walls: int = 4
    min_moves: int = 3
    max_moves: int = 8
    max_attempts: int = 5000
    max_solver_nodes: int = 500_000
    time_budget_s: float = 300.0
    # Per-candidate solve timeout. A single solve runs in a child process so an
    # unsolvable board that would otherwise churn can be killed and skipped,
    # keeping the overall time budget responsive. None disables it.
    solve_timeout_s: float | None = None


def generate_level(config: GeneratorConfig, rng: random.Random) -> LevelData | None:
    rows, cols = config.rows, config.cols
    all_cells = [(r, c) for r in range(rows) for c in range(cols)]

    cells = rng.sample(all_cells, config.n_seals + config.n_destinations)
    seal_cells = cells[: config.n_seals]
    dest_cells = cells[config.n_seals :]

    colors = COLORS[: config.n_seals]
    seals = [
        Seal(f"s{i+1}", colors[i], Pos(r, c))
        for i, (r, c) in enumerate(seal_cells)
    ]

    dest_colors = rng.sample(colors, config.n_destinations)
    destinations = [
        Destination(color, Pos(r, c))
        for color, (r, c) in zip(dest_colors, dest_cells)
    ]

    # Make sure no destination starts already solved
    seal_map = {(s.color, s.pos) for s in seals}
    if any((d.color, d.pos) in seal_map for d in destinations):
        return None

    # Random internal walls (not on perimeter)
    walls = []
    used_wall_edges: set[tuple[int, int, Direction]] = set()
    for _ in range(config.n_walls):
        for _attempt in range(20):
            r = rng.randint(0, rows - 1)
            c = rng.randint(0, cols - 1)
            side = rng.choice(SIDES)
            dr, dc = side.delta()
            nr, nc = r + dr, c + dc
            if not (0 <= nr < rows and 0 <= nc < cols):
                continue
            if (r, c, side) in used_wall_edges:
                continue
            walls.append(Wall(Pos(r, c), side))
            used_wall_edges.add((r, c, side))
            used_wall_edges.add((nr, nc, side.opposite()))
            break

    return LevelData(
        id="candidate",
        rows=rows,
        cols=cols,
        walls=walls,
        seals=seals,
        destinations=destinations,
        optimal_moves=0,
    )


def solve_level(level: LevelData, max_nodes: int) -> list[Move] | None:
    """Returns the optimal solution (A* with an admissible heuristic, so the
    returned path is optimal), or None if no solution is found within max_nodes."""
    if _HAS_NATIVE:
        solver = NativeSolver("gen", 1, max_nodes=max_nodes)
    else:
        solver = AStarSolver("gen", axis_with_setup(), max_nodes=max_nodes)
    result = solver.solve(level)
    if result is None or not result.moves:
        return None
    return result.moves


def _solve_into_queue(level: LevelData, max_nodes: int, queue) -> None:
    try:
        queue.put(solve_level(level, max_nodes))
    except Exception:
        queue.put(None)


def solve_level_timed(
    level: LevelData, max_nodes: int, timeout_s: float | None
) -> list[Move] | None:
    """Like solve_level, but abandons the solve (returns None) if it exceeds
    timeout_s. The solve runs in a child process because a stuck native solve
    can't be interrupted within the calling thread."""
    if timeout_s is None:
        return solve_level(level, max_nodes)

    ctx = mp.get_context("fork")
    queue = ctx.Queue()
    proc = ctx.Process(target=_solve_into_queue, args=(level, max_nodes, queue))
    proc.start()
    proc.join(timeout_s)
    if proc.is_alive():
        proc.terminate()
        proc.join()
        return None
    try:
        return queue.get_nowait()
    except Exception:
        return None


def generate_batch(
    config: GeneratorConfig,
    count: int,
    seed: int | None = None,
) -> list[tuple[LevelData, list[Move]]]:
    rng = random.Random(seed)
    found: list[tuple[LevelData, list[Move]]] = []
    attempts = 0

    unsolvable = 0
    too_easy = 0
    too_hard = 0

    start = time.monotonic()
    while len(found) < count and attempts < config.max_attempts:
        if (elapsed := time.monotonic() - start) > config.time_budget_s:
            print(
                f"  ... time budget {config.time_budget_s:.0f}s reached after {elapsed:.0f}s: "
                f"stopping with {len(found)}/{count} levels",
                flush=True,
            )
            break
        attempts += 1
        if attempts % 500 == 0:
            print(
                f"  ... attempt {attempts}: {len(found)}/{count} found, "
                f"{unsolvable} unsolvable, {too_easy} too easy, {too_hard} too hard",
                flush=True,
            )

        level = generate_level(config, rng)
        if level is None:
            continue

        moves = solve_level_timed(level, config.max_solver_nodes, config.solve_timeout_s)
        if moves is None:
            unsolvable += 1
            continue

        n_moves = len(moves)
        if n_moves < config.min_moves:
            too_easy += 1
            continue
        if n_moves > config.max_moves:
            too_hard += 1
            continue

        level.optimal_moves = n_moves
        found.append((level, moves))
        print(
            f"  [{len(found)}/{count}] {n_moves} moves "
            f"({attempts} attempts)",
            flush=True,
        )

    return found


def level_to_json(level: LevelData, moves: list[Move], level_id: str) -> dict:
    # Reuse the solution found during generation; re-solving here is wasteful and
    # on large boards can run for minutes past the generation time budget.
    return {
        "id": level_id,
        "rows": level.rows,
        "cols": level.cols,
        "walls": [
            {"row": w.pos.row, "col": w.pos.col, "side": w.side.value}
            for w in level.walls
        ],
        "seals": [
            {"id": s.id, "color": s.color, "row": s.pos.row, "col": s.pos.col}
            for s in level.seals
        ],
        "destinations": [
            {"color": d.color, "row": d.pos.row, "col": d.pos.col}
            for d in level.destinations
        ],
        "optimalMoves": len(moves),
        "optimalSolution": [
            {"sealId": m.seal_id, "direction": m.direction.value}
            for m in moves
        ],
    }


PRESETS = {
    "easy": GeneratorConfig(
        rows=5, cols=5, n_seals=2, n_destinations=1, n_walls=3,
        min_moves=2, max_moves=4,
    ),
    "medium": GeneratorConfig(
        rows=7, cols=7, n_seals=3, n_destinations=1, n_walls=5,
        min_moves=4, max_moves=7,
    ),
    "hard": GeneratorConfig(
        rows=8, cols=8, n_seals=4, n_destinations=2, n_walls=6,
        min_moves=6, max_moves=10,
    ),
    "expert": GeneratorConfig(
        rows=10, cols=10, n_seals=5, n_destinations=2, n_walls=10,
        min_moves=15, max_moves=20, max_attempts=50_000, max_solver_nodes=5_000_000,
    ),
}


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Generate Slippery Seals levels")
    parser.add_argument("preset", choices=list(PRESETS.keys()), help="Difficulty preset")
    parser.add_argument("-n", "--count", type=int, default=10, help="Number of levels to generate")
    parser.add_argument("-s", "--seed", type=int, default=None, help="Random seed")
    parser.add_argument("-o", "--output", type=str, default=None, help="Output JSON file")
    parser.add_argument("--min-moves", type=int, default=None)
    parser.add_argument("--max-moves", type=int, default=None)
    parser.add_argument("--rows", type=int, default=None)
    parser.add_argument("--cols", type=int, default=None)
    parser.add_argument("--seals", type=int, default=None)
    parser.add_argument("--dests", type=int, default=None)
    parser.add_argument("--walls", type=int, default=None)
    parser.add_argument("--max-time", type=float, default=None,
                        help="Wall-clock budget in seconds; stop and ship what was found")
    parser.add_argument("--max-nodes", type=int, default=None,
                        help="Per-candidate solver node cap; bounds each solve so the "
                             "time budget stays responsive (a candidate exceeding it is skipped)")
    parser.add_argument("--solve-timeout", type=float, default=None,
                        help="Per-candidate solve timeout in seconds; a slower candidate "
                             "is skipped (needed on large boards to bound unsolvable churn)")
    args = parser.parse_args()

    config = PRESETS[args.preset]
    if args.min_moves is not None:
        config.min_moves = args.min_moves
    if args.max_moves is not None:
        config.max_moves = args.max_moves
    if args.rows is not None:
        config.rows = args.rows
    if args.cols is not None:
        config.cols = args.cols
    if args.seals is not None:
        config.n_seals = args.seals
    if args.dests is not None:
        config.n_destinations = args.dests
    if args.walls is not None:
        config.n_walls = args.walls
    if args.max_time is not None:
        config.time_budget_s = args.max_time
    if args.max_nodes is not None:
        config.max_solver_nodes = args.max_nodes
    if args.solve_timeout is not None:
        config.solve_timeout_s = args.solve_timeout

    print(f"Generating {args.count} {args.preset} levels "
          f"({config.rows}x{config.cols}, {config.n_seals} seals, "
          f"{config.n_destinations} dests, {config.n_walls} walls, "
          f"{config.min_moves}-{config.max_moves} moves)")

    results = generate_batch(config, args.count, args.seed)

    if not results:
        print("No levels found!")
        sys.exit(1)

    print(f"\nGenerated {len(results)} levels")

    levels_json = [
        level_to_json(level, moves, f"{args.preset}_{i+1:02d}")
        for i, (level, moves) in enumerate(results)
    ]

    output = {"levels": levels_json}

    if args.output:
        with open(args.output, "w") as f:
            json.dump(output, f, indent=2)
            f.write("\n")
        print(f"Saved to {args.output}")
    else:
        print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
