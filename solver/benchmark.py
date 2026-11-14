#!/usr/bin/env python3
"""Benchmark A* solver with various heuristics against a set of levels."""
from __future__ import annotations
import ctypes
import json
import os
import platform
import sys
import time

from engine import Direction, Destination, LevelData, Move, Pos, Seal, Wall, build_wall_set
from astar import AStarSolver, SolverResult
import heuristics

_DIR_FROM_ENUM = {Direction.UP: 0, Direction.DOWN: 1, Direction.LEFT: 2, Direction.RIGHT: 3}
_DIR_TO_ENUM = [Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT]

_native_lib = None

def _load_native():
    global _native_lib
    if _native_lib is not None:
        return _native_lib
    ext = ".dylib" if platform.system() == "Darwin" else ".so"
    path = os.path.join(os.path.dirname(__file__), f"solver_native{ext}")
    if not os.path.exists(path):
        path = os.path.join(os.path.dirname(__file__), "solver_native.so")
    _native_lib = ctypes.CDLL(path)
    _native_lib.solve_astar.restype = ctypes.c_int
    _native_lib.solve_astar.argtypes = [
        ctypes.c_int, ctypes.c_int,
        ctypes.c_int, ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
        ctypes.c_int, ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
        ctypes.c_int, ctypes.POINTER(ctypes.c_int),
        ctypes.c_int, ctypes.c_int,
        ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
        ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
    ]
    return _native_lib


class NativeSolver:
    def __init__(self, name: str, heuristic_type: int, max_nodes: int = 1_000_000):
        self.name = name
        self.heuristic_type = heuristic_type
        self.max_nodes = max_nodes

    def solve(self, level: LevelData) -> SolverResult | None:
        lib = _load_native()
        rows, cols = level.rows, level.cols

        color_map: dict[str, int] = {}
        def cid(c: str) -> int:
            if c not in color_map:
                color_map[c] = len(color_map)
            return color_map[c]

        seal_order = sorted(range(len(level.seals)), key=lambda i: level.seals[i].id)
        seal_ids = [level.seals[i].id for i in seal_order]
        n_seals = len(seal_order)

        sp = (ctypes.c_int * n_seals)(*(level.seals[i].pos.row * cols + level.seals[i].pos.col for i in seal_order))
        sc = (ctypes.c_int * n_seals)(*(cid(level.seals[i].color) for i in seal_order))

        n_dests = len(level.destinations)
        dp = (ctypes.c_int * n_dests)(*(d.pos.row * cols + d.pos.col for d in level.destinations))
        dc = (ctypes.c_int * n_dests)(*(cid(d.color) for d in level.destinations))

        raw_walls = build_wall_set(level)
        wl = [(w.pos.row * cols + w.pos.col) * 4 + _DIR_FROM_ENUM[w.side] for w in raw_walls]
        n_walls = len(wl)
        we = (ctypes.c_int * n_walls)(*wl)

        out_ms = (ctypes.c_int * 512)()
        out_md = (ctypes.c_int * 512)()
        out_nm = ctypes.c_int(0)
        out_ne = ctypes.c_int(0)

        start = time.perf_counter()
        status = lib.solve_astar(
            rows, cols,
            n_seals, sp, sc,
            n_dests, dp, dc,
            n_walls, we,
            self.max_nodes, self.heuristic_type,
            out_ms, out_md,
            ctypes.byref(out_nm), ctypes.byref(out_ne),
        )
        elapsed = (time.perf_counter() - start) * 1000

        if status == 0:
            moves = [Move(seal_ids[out_ms[i]], _DIR_TO_ENUM[out_md[i]]) for i in range(out_nm.value)]
            return SolverResult(moves, out_ne.value, elapsed)
        if status == 2:
            return SolverResult([], out_ne.value, elapsed)
        return None


def load_levels_from_json(path: str) -> list[LevelData]:
    with open(path) as f:
        data = json.load(f)
    levels = []
    for lv in data["levels"]:
        walls = [Wall(Pos(w["row"], w["col"]), Direction(w["side"])) for w in lv["walls"]]
        seals = [Seal(s["id"], s["color"], Pos(s["row"], s["col"])) for s in lv["seals"]]
        dests = [Destination(d["color"], Pos(d["row"], d["col"])) for d in lv["destinations"]]
        levels.append(LevelData(
            id=lv["id"], rows=lv["rows"], cols=lv["cols"],
            walls=walls, seals=seals, destinations=dests,
            optimal_moves=lv["optimalMoves"],
        ))
    return levels


def load_all_levels() -> list[LevelData]:
    assets = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "levels")
    all_levels = []
    for fname in sorted(os.listdir(assets)):
        if fname.endswith(".json"):
            all_levels.extend(load_levels_from_json(os.path.join(assets, fname)))
    return all_levels


def make_solvers() -> list:
    return [
        # AStarSolver("Min Axis Moves", heuristics.min_axis_moves(), max_nodes=20_000_000),
        # AStarSolver("Axis + Setup", heuristics.axis_with_setup(), max_nodes=20_000_000),
        NativeSolver("Native Min Axis", 0, max_nodes=20_000_000),
        NativeSolver("Native Axis+Setup", 1, max_nodes=20_000_000),
    ]


def run_benchmark(levels: list[LevelData] | None = None, solvers: list[AStarSolver] | None = None):
    if levels is None:
        levels = load_all_levels()
    if solvers is None:
        solvers = make_solvers()

    col_name = 24
    col_moves = 7
    col_nodes = 10
    col_time = 10

    header = f"  {'Heuristic':<{col_name}} {'Moves':>{col_moves}} {'Nodes':>{col_nodes}} {'Time':>{col_time}}"
    divider = f"  {'-' * (col_name + col_moves + col_nodes + col_time + 3)}"

    print(f"Loaded {len(levels)} levels, {len(solvers)} solvers\n")

    for li, level in enumerate(levels, 1):
        print(f"[{li}/{len(levels)}] === {level.id} ({level.rows}x{level.cols}, {len(level.seals)} seals, "
              f"{len(level.destinations)} dests, optimal={level.optimal_moves}) ===")
        print(header)
        print(divider)

        for si, solver in enumerate(solvers, 1):
            print(f"  Running {solver.name}...", end="", flush=True)
            result = solver.solve(level)
            print("\r", end="")
            if result is None:
                moves_str = "FAIL"
                nodes_str = "-"
                time_str = "-"
                ok = ""
            elif not result.moves and result.nodes_expanded >= solver.max_nodes:
                moves_str = "LIMIT"
                nodes_str = f"{result.nodes_expanded}"
                time_str = f"{result.time_ms:.1f}ms"
                ok = ""
            else:
                moves_str = str(len(result.moves))
                nodes_str = str(result.nodes_expanded)
                time_str = f"{result.time_ms:.1f}ms"
                ok = " *" if len(result.moves) == level.optimal_moves else " !"
            print(f"  {solver.name:<{col_name}} {moves_str:>{col_moves}} {nodes_str:>{col_nodes}} {time_str:>{col_time}}{ok}")

    print()


if __name__ == "__main__":
    run_benchmark()
