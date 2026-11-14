from __future__ import annotations
import heapq
import time
from dataclasses import dataclass

from engine import Direction, LevelData, Move, build_wall_set
from heuristics import Heuristic, SearchContext

# Positions encoded as row * cols + col (single int).
# State = tuple of encoded positions in fixed seal order (sorted by id).
State = tuple[int, ...]

_DIR_UP, _DIR_DOWN, _DIR_LEFT, _DIR_RIGHT = 0, 1, 2, 3
_DIR_TO_ENUM = [Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT]
_DIR_FROM_ENUM = {d: i for i, d in enumerate(_DIR_TO_ENUM)}


@dataclass
class SolverResult:
    moves: list[Move]
    nodes_expanded: int
    time_ms: float


class AStarSolver:
    def __init__(self, name: str, heuristic: Heuristic, max_nodes: int = 1_000_000):
        self.name = name
        self.heuristic = heuristic
        self.max_nodes = max_nodes

    def solve(self, level: LevelData) -> SolverResult | None:
        rows, cols = level.rows, level.cols

        raw_walls = build_wall_set(level)
        wall_check = set()
        for w in raw_walls:
            wall_check.add((w.pos.row * cols + w.pos.col) * 4 + _DIR_FROM_ENUM[w.side])

        # Precompute wall-only stop for every (cell, direction).
        total_cells = rows * cols
        wall_stop = [0] * (total_cells * 4)

        for r in range(rows):
            stop = -1
            for c in range(cols - 1, -1, -1):
                pos = r * cols + c
                if pos * 4 + _DIR_RIGHT in wall_check:
                    stop = pos
                wall_stop[pos * 4 + _DIR_RIGHT] = stop

            stop = -1
            for c in range(cols):
                pos = r * cols + c
                if pos * 4 + _DIR_LEFT in wall_check:
                    stop = pos
                wall_stop[pos * 4 + _DIR_LEFT] = stop

        for c in range(cols):
            stop = -1
            for r in range(rows - 1, -1, -1):
                pos = r * cols + c
                if pos * 4 + _DIR_DOWN in wall_check:
                    stop = pos
                wall_stop[pos * 4 + _DIR_DOWN] = stop

            stop = -1
            for r in range(rows):
                pos = r * cols + c
                if pos * 4 + _DIR_UP in wall_check:
                    stop = pos
                wall_stop[pos * 4 + _DIR_UP] = stop

        # Seal metadata in fixed order (sorted by id).
        seal_order = sorted(range(len(level.seals)), key=lambda i: level.seals[i].id)
        seal_ids = [level.seals[i].id for i in seal_order]
        seal_colors = [level.seals[i].color for i in seal_order]
        n_seals = len(seal_ids)

        dest_colors = [d.color for d in level.destinations]
        dest_encoded = [d.pos.row * cols + d.pos.col for d in level.destinations]
        dest_enc = list(zip(dest_colors, dest_encoded))

        ctx = SearchContext(
            n_seals=n_seals,
            cols=cols,
            seal_colors=seal_colors,
            dest_colors=dest_colors,
            dest_encoded=dest_encoded,
            wall_check=wall_check,
        )

        initial: State = tuple(
            level.seals[i].pos.row * cols + level.seals[i].pos.col
            for i in seal_order
        )

        counter = 0
        h0 = self.heuristic(initial, ctx)
        open_set: list[tuple[int, int, int, State]] = [(h0, 0, counter, initial)]
        best_g: dict[State, int] = {initial: 0}
        parent: dict[State, tuple[State, int, int]] = {}
        closed: set[State] = set()
        nodes_expanded = 0
        start = time.perf_counter()

        while open_set:
            _, g, _, state = heapq.heappop(open_set)

            if state in closed:
                continue
            closed.add(state)
            nodes_expanded += 1

            if nodes_expanded > self.max_nodes:
                elapsed = (time.perf_counter() - start) * 1000
                return SolverResult([], nodes_expanded, elapsed)

            is_goal = True
            for color, dpos in dest_enc:
                found = False
                for i in range(n_seals):
                    if seal_colors[i] == color and state[i] == dpos:
                        found = True
                        break
                if not found:
                    is_goal = False
                    break

            if is_goal:
                elapsed = (time.perf_counter() - start) * 1000
                moves = _reconstruct(parent, state, seal_ids)
                return SolverResult(moves, nodes_expanded, elapsed)

            for i in range(n_seals):
                seal_pos = state[i]
                for d in range(4):
                    ws = wall_stop[seal_pos * 4 + d]
                    if ws == seal_pos:
                        continue

                    stop = ws
                    if d == _DIR_RIGHT:
                        for j in range(n_seals):
                            if j != i:
                                os = state[j]
                                if seal_pos < os <= stop:
                                    stop = os - 1
                    elif d == _DIR_LEFT:
                        for j in range(n_seals):
                            if j != i:
                                os = state[j]
                                if stop <= os < seal_pos:
                                    stop = os + 1
                    elif d == _DIR_DOWN:
                        pc = seal_pos % cols
                        for j in range(n_seals):
                            if j != i:
                                os = state[j]
                                if os % cols == pc and seal_pos < os <= stop:
                                    stop = os - cols
                    else:
                        pc = seal_pos % cols
                        for j in range(n_seals):
                            if j != i:
                                os = state[j]
                                if os % cols == pc and stop <= os < seal_pos:
                                    stop = os + cols

                    if stop == seal_pos:
                        continue

                    neighbor = state[:i] + (stop,) + state[i + 1:]

                    if neighbor in closed:
                        continue

                    tentative_g = g + 1
                    if tentative_g < best_g.get(neighbor, 999999):
                        best_g[neighbor] = tentative_g
                        parent[neighbor] = (state, i, d)
                        h = self.heuristic(neighbor, ctx)
                        counter += 1
                        heapq.heappush(
                            open_set,
                            (tentative_g + h, tentative_g, counter, neighbor),
                        )

        elapsed = (time.perf_counter() - start) * 1000
        return None


def _reconstruct(
    parent: dict[State, tuple[State, int, int]],
    goal: State,
    seal_ids: list[str],
) -> list[Move]:
    moves: list[Move] = []
    current = goal
    while current in parent:
        prev, seal_idx, dir_int = parent[current]
        moves.append(Move(seal_ids[seal_idx], _DIR_TO_ENUM[dir_int]))
        current = prev
    moves.reverse()
    return moves
