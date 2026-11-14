#!/usr/bin/env python3
"""Solve all levels with BFS and write optimal solutions back to the JSON files."""
from __future__ import annotations
import json
import os

from engine import Direction, Destination, LevelData, Pos, Seal, Wall
from astar import AStarSolver
import heuristics

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "levels")


def solve_and_update(path: str):
    with open(path) as f:
        data = json.load(f)

    solver = AStarSolver("BFS", heuristics.zero())
    changed = False

    for lv in data["levels"]:
        walls = [Wall(Pos(w["row"], w["col"]), Direction(w["side"])) for w in lv["walls"]]
        seals = [Seal(s["id"], s["color"], Pos(s["row"], s["col"])) for s in lv["seals"]]
        dests = [Destination(d["color"], Pos(d["row"], d["col"])) for d in lv["destinations"]]
        level = LevelData(
            id=lv["id"], rows=lv["rows"], cols=lv["cols"],
            walls=walls, seals=seals, destinations=dests,
            optimal_moves=999,
        )

        print(f"  Solving {lv['id']}...", end="", flush=True)
        result = solver.solve(level)

        if result is None:
            print(" FAILED (no solution found)")
            continue

        old_optimal = lv.get("optimalMoves")
        new_optimal = len(result.moves)
        lv["optimalMoves"] = new_optimal
        lv["optimalSolution"] = [
            {"sealId": m.seal_id, "direction": m.direction.value}
            for m in result.moves
        ]

        status = "unchanged" if old_optimal == new_optimal else f"was {old_optimal}"
        print(f" {new_optimal} moves ({status}, {result.nodes_expanded} nodes, {result.time_ms:.1f}ms)")

        if old_optimal != new_optimal:
            changed = True

    if changed:
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print(f"  Updated {os.path.basename(path)}")
    else:
        print(f"  {os.path.basename(path)} already correct")


if __name__ == "__main__":
    for fname in sorted(os.listdir(ASSETS_DIR)):
        if not fname.endswith(".json"):
            continue
        print(f"\n{fname}:")
        solve_and_update(os.path.join(ASSETS_DIR, fname))
    print()
