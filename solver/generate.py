#!/usr/bin/env python3
"""Generate random Slippery Seals levels and filter by optimal move count."""
from __future__ import annotations
import json
import random
import sys
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


def solve_level(level: LevelData, max_nodes: int) -> int | None:
    if _HAS_NATIVE:
        solver = NativeSolver("gen", 1, max_nodes=max_nodes)
    else:
        solver = AStarSolver("gen", axis_with_setup(), max_nodes=max_nodes)
    result = solver.solve(level)
    if result is None or not result.moves:
        return None
    return len(result.moves)


def generate_batch(
    config: GeneratorConfig,
    count: int,
    seed: int | None = None,
) -> list[tuple[LevelData, int]]:
    rng = random.Random(seed)
    found: list[tuple[LevelData, int]] = []
    attempts = 0

    unsolvable = 0
    too_easy = 0
    too_hard = 0

    while len(found) < count and attempts < config.max_attempts:
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

        moves = solve_level(level, config.max_solver_nodes)
        if moves is None:
            unsolvable += 1
            continue

        if moves < config.min_moves:
            too_easy += 1
            continue
        if moves > config.max_moves:
            too_hard += 1
            continue

        level.optimal_moves = moves
        found.append((level, moves))
        print(
            f"  [{len(found)}/{count}] {moves} moves "
            f"({attempts} attempts)",
            flush=True,
        )

    return found


def level_to_json(level: LevelData, level_id: str) -> dict:
    if _HAS_NATIVE:
        solver = NativeSolver("gen", 1, max_nodes=5_000_000)
    else:
        solver = AStarSolver("gen", axis_with_setup())
    result = solver.solve(level)
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
        "optimalMoves": len(result.moves) if result else 0,
        "optimalSolution": [
            {"sealId": m.seal_id, "direction": m.direction.value}
            for m in result.moves
        ] if result else [],
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
        level_to_json(level, f"{args.preset}_{i+1:02d}")
        for i, (level, _) in enumerate(results)
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
