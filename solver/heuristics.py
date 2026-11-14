from __future__ import annotations
from typing import Protocol

_DIR_UP, _DIR_DOWN, _DIR_LEFT, _DIR_RIGHT = 0, 1, 2, 3


class SearchContext:
    """Precomputed data for heuristic evaluation using int-encoded positions."""

    __slots__ = (
        "n_seals", "cols", "seal_colors",
        "n_dests", "dest_colors", "dest_encoded",
        "wall_check", "deltas",
    )

    def __init__(
        self,
        n_seals: int,
        cols: int,
        seal_colors: list[str],
        dest_colors: list[str],
        dest_encoded: list[int],
        wall_check: set[int],
    ):
        self.n_seals = n_seals
        self.cols = cols
        self.seal_colors = seal_colors
        self.n_dests = len(dest_colors)
        self.dest_colors = dest_colors
        self.dest_encoded = dest_encoded
        self.wall_check = wall_check
        self.deltas = [-cols, cols, -1, 1]


class Heuristic(Protocol):
    def __call__(self, state: tuple[int, ...], ctx: SearchContext) -> int: ...


def _has_blocker(
    target: int, dir_int: int,
    all_pos: set[int], wall_check: set[int], deltas: list[int],
) -> bool:
    if target * 4 + dir_int in wall_check:
        return True
    return (target + deltas[dir_int]) in all_pos


def zero() -> Heuristic:
    def h(state: tuple[int, ...], ctx: SearchContext) -> int:
        return 0
    return h


def unmatched_count() -> Heuristic:
    def h(state: tuple[int, ...], ctx: SearchContext) -> int:
        count = 0
        for di in range(ctx.n_dests):
            color = ctx.dest_colors[di]
            dpos = ctx.dest_encoded[di]
            found = False
            for i in range(ctx.n_seals):
                if ctx.seal_colors[i] == color and state[i] == dpos:
                    found = True
                    break
            if not found:
                count += 1
        return count
    return h


def min_axis_moves() -> Heuristic:
    def h(state: tuple[int, ...], ctx: SearchContext) -> int:
        total = 0
        cols = ctx.cols
        for di in range(ctx.n_dests):
            color = ctx.dest_colors[di]
            dpos = ctx.dest_encoded[di]
            best = 2
            for i in range(ctx.n_seals):
                if ctx.seal_colors[i] != color:
                    continue
                sp = state[i]
                if sp == dpos:
                    best = 0
                    break
                if sp // cols == dpos // cols or sp % cols == dpos % cols:
                    best = 1
            total += best
        return total
    return h


def axis_with_setup() -> Heuristic:
    def h(state: tuple[int, ...], ctx: SearchContext) -> int:
        cols = ctx.cols
        n_seals = ctx.n_seals
        seal_colors = ctx.seal_colors
        dest_colors = ctx.dest_colors
        dest_encoded = ctx.dest_encoded
        n_dests = ctx.n_dests
        wc = ctx.wall_check
        dl = ctx.deltas
        all_pos = set(state)

        total = 0
        setup_corners: list[frozenset[int]] = []

        for di in range(n_dests):
            color = dest_colors[di]
            dpos = dest_encoded[di]
            best_cost = 2
            best_corners = None

            for i in range(n_seals):
                if seal_colors[i] != color:
                    continue
                sp = state[i]
                if sp == dpos:
                    best_cost = 0
                    best_corners = None
                    break

                sp_row = sp // cols
                dp_row = dpos // cols
                sp_col = sp % cols
                dp_col = dpos % cols

                if sp_row == dp_row or sp_col == dp_col:
                    # same axis — inline _same_axis_cost + _has_blocker
                    if sp_row == dp_row:
                        d = _DIR_RIGHT if dpos > sp else _DIR_LEFT
                    else:
                        d = _DIR_DOWN if dpos > sp else _DIR_UP
                    if dpos * 4 + d in wc or (dpos + dl[d]) in all_pos:
                        cost = 1
                    else:
                        cost = 2
                    if cost < best_cost:
                        best_cost = cost
                        best_corners = None
                else:
                    # off axis — inline _off_axis_cost + _has_blocker
                    h_dir = _DIR_RIGHT if dp_col > sp_col else _DIR_LEFT
                    v_dir = _DIR_DOWN if dp_row > sp_row else _DIR_UP

                    corner_a = sp_row * cols + dp_col
                    corner_b = dp_row * cols + sp_col

                    corners = None
                    if (corner_a * 4 + h_dir in wc or (corner_a + dl[h_dir]) in all_pos):
                        if (dpos * 4 + v_dir in wc or (dpos + dl[v_dir]) in all_pos):
                            corners = None
                        else:
                            corners = frozenset((corner_a, corner_b))
                    elif (corner_b * 4 + v_dir in wc or (corner_b + dl[v_dir]) in all_pos):
                        if (dpos * 4 + h_dir in wc or (dpos + dl[h_dir]) in all_pos):
                            corners = None
                        else:
                            corners = frozenset((corner_a, corner_b))
                    else:
                        corners = frozenset((corner_a, corner_b))

                    if corners is None and best_corners is not None:
                        best_corners = None

            total += best_cost
            if best_corners is not None:
                setup_corners.append(best_corners)

        if setup_corners:
            dest_set = set(dest_encoded)
            independent = [cs for cs in setup_corners if not (cs & dest_set)]
            if independent:
                groups: list[set[int]] = []
                for corners in independent:
                    merged = False
                    for g in groups:
                        if g & corners:
                            g |= corners
                            merged = True
                            break
                    if not merged:
                        groups.append(set(corners))
                total += len(groups)

        return total
    return h


def combined(*bases: Heuristic) -> Heuristic:
    def h(state: tuple[int, ...], ctx: SearchContext) -> int:
        return max(b(state, ctx) for b in bases)
    return h
