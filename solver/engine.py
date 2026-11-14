from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
from typing import NamedTuple


class Direction(Enum):
    UP = "UP"
    DOWN = "DOWN"
    LEFT = "LEFT"
    RIGHT = "RIGHT"

    def opposite(self) -> Direction:
        return _OPPOSITES[self]

    def delta(self) -> tuple[int, int]:
        return _DELTAS[self]


_OPPOSITES = {
    Direction.UP: Direction.DOWN,
    Direction.DOWN: Direction.UP,
    Direction.LEFT: Direction.RIGHT,
    Direction.RIGHT: Direction.LEFT,
}

_DELTAS = {
    Direction.UP: (-1, 0),
    Direction.DOWN: (1, 0),
    Direction.LEFT: (0, -1),
    Direction.RIGHT: (0, 1),
}


class Pos(NamedTuple):
    row: int
    col: int


class Wall(NamedTuple):
    pos: Pos
    side: Direction


@dataclass(frozen=True)
class Seal:
    id: str
    color: str
    pos: Pos


@dataclass(frozen=True)
class Destination:
    color: str
    pos: Pos


@dataclass(frozen=True)
class Move:
    seal_id: str
    direction: Direction


@dataclass
class LevelData:
    id: str
    rows: int
    cols: int
    walls: list[Wall]
    seals: list[Seal]
    destinations: list[Destination]
    optimal_moves: int


def build_wall_set(level: LevelData) -> frozenset[Wall]:
    walls: set[Wall] = set()
    for w in level.walls:
        walls.add(w)
        dr, dc = w.side.delta()
        neighbor = Pos(w.pos.row + dr, w.pos.col + dc)
        walls.add(Wall(neighbor, w.side.opposite()))
    for c in range(level.cols):
        walls.add(Wall(Pos(0, c), Direction.UP))
        walls.add(Wall(Pos(level.rows - 1, c), Direction.DOWN))
    for r in range(level.rows):
        walls.add(Wall(Pos(r, 0), Direction.LEFT))
        walls.add(Wall(Pos(r, level.cols - 1), Direction.RIGHT))
    return frozenset(walls)


def compute_slide(
    seal_id: str,
    direction: Direction,
    positions: dict[str, Pos],
    walls: frozenset[Wall],
) -> Pos | None:
    pos = positions[seal_id]
    others = {p for sid, p in positions.items() if sid != seal_id}
    dr, dc = direction.delta()

    cur = pos
    while True:
        if Wall(cur, direction) in walls:
            break
        nxt = Pos(cur.row + dr, cur.col + dc)
        if nxt in others:
            break
        cur = nxt

    return None if cur == pos else cur


class SolverContext:
    __slots__ = ("destinations", "walls", "rows", "cols", "seal_colors", "seal_ids")

    def __init__(
        self,
        destinations: list[Destination],
        walls: frozenset[Wall],
        rows: int,
        cols: int,
        seal_colors: dict[str, str],
        seal_ids: list[str],
    ):
        self.destinations = destinations
        self.walls = walls
        self.rows = rows
        self.cols = cols
        self.seal_colors = seal_colors
        self.seal_ids = seal_ids
