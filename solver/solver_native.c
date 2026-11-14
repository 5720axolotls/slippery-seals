/*
 * solver_native.c — A* solver for Slippery Seals, callable from Python via ctypes.
 * Compile: cc -O2 -shared -fPIC -o solver_native.so solver_native.c
 */
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>

#define DIR_UP    0
#define DIR_DOWN  1
#define DIR_LEFT  2
#define DIR_RIGHT 3

#define MAX_SEALS 8
#define MAX_DESTS 8
#define MAX_CELLS 256
#define MAX_SOLUTION 512

/* ── State: up to 8 positions packed in a uint64_t (8 bits each) ── */

static inline int sget(uint64_t s, int i) {
    return (int)((s >> (i * 8)) & 0xFF);
}

static inline uint64_t sset(uint64_t s, int i, int v) {
    return (s & ~((uint64_t)0xFF << (i * 8))) | ((uint64_t)(v & 0xFF) << (i * 8));
}

/* ── Open-addressing hash table (power-of-2 capacity) ── */

typedef struct {
    uint64_t key;
    uint64_t parent;
    int      g;
    uint8_t  mseal;
    uint8_t  mdir;
    uint8_t  used;
    uint8_t  closed;
} HTNode;

static inline uint64_t ht_mix(uint64_t x) {
    x ^= x >> 33; x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 33; x *= 0xc4ceb9fe1a85ec53ULL;
    x ^= x >> 33;
    return x;
}

static inline HTNode *ht_find(HTNode *tbl, int mask, uint64_t key) {
    int i = (int)(ht_mix(key) & (uint64_t)mask);
    while (tbl[i].used && tbl[i].key != key)
        i = (i + 1) & mask;
    return &tbl[i];
}

/* ── Binary min-heap (ordered by f, ties broken by higher g) ── */

typedef struct { int f; int g; uint64_t state; } HNode;

typedef struct {
    HNode *d;
    int    sz;
    int    cap;
} Heap;

#define HLESS(a, b) ((a).f < (b).f || ((a).f == (b).f && (a).g > (b).g))

static inline void heap_push(Heap *h, int f, int g, uint64_t state) {
    if (h->sz >= h->cap) {
        h->cap *= 2;
        h->d = (HNode *)realloc(h->d, (size_t)h->cap * sizeof(HNode));
    }
    int i = h->sz++;
    h->d[i] = (HNode){f, g, state};
    while (i > 0) {
        int p = (i - 1) >> 1;
        if (!HLESS(h->d[i], h->d[p])) break;
        HNode t = h->d[i]; h->d[i] = h->d[p]; h->d[p] = t;
        i = p;
    }
}

static inline HNode heap_pop(Heap *h) {
    HNode top = h->d[0];
    h->d[0] = h->d[--h->sz];
    int i = 0;
    for (;;) {
        int l = (i << 1) + 1, r = l + 1, s = i;
        if (l < h->sz && HLESS(h->d[l], h->d[s])) s = l;
        if (r < h->sz && HLESS(h->d[r], h->d[s])) s = r;
        if (s == i) break;
        HNode t = h->d[i]; h->d[i] = h->d[s]; h->d[s] = t;
        i = s;
    }
    return top;
}

/* ── Heuristics ── */

static inline int h_min_axis(
    const int *pos, int n_seals, int cols,
    const int *sc, int n_dests, const int *dc, const int *dp
) {
    int total = 0;
    for (int di = 0; di < n_dests; di++) {
        int color = dc[di], dpos = dp[di];
        int best = 2;
        for (int i = 0; i < n_seals; i++) {
            if (sc[i] != color) continue;
            int sp = pos[i];
            if (sp == dpos) { best = 0; break; }
            if (sp / cols == dpos / cols || sp % cols == dpos % cols)
                best = 1;
        }
        total += best;
    }
    return total;
}

static inline int h_axis_setup(
    const int *pos, int n_seals, int cols, int total_cells,
    const int *sc, int n_dests, const int *dc, const int *dp,
    const bool *wc, const int *deltas
) {
    bool occ[MAX_CELLS + 20];
    memset(occ, 0, (total_cells + cols + 2) * sizeof(bool));
    for (int i = 0; i < n_seals; i++)
        occ[pos[i]] = true;

    int total = 0;
    for (int di = 0; di < n_dests; di++) {
        int color = dc[di], dpos = dp[di];
        int best = 2;
        for (int i = 0; i < n_seals; i++) {
            if (sc[i] != color) continue;
            int sp = pos[i];
            if (sp == dpos) { best = 0; break; }

            int sr = sp / cols, dr = dpos / cols;
            int scl = sp % cols, dcl = dpos % cols;

            if (sr == dr || scl == dcl) {
                int d;
                if (sr == dr) d = (dpos > sp) ? DIR_RIGHT : DIR_LEFT;
                else          d = (dpos > sp) ? DIR_DOWN  : DIR_UP;
                if (wc[dpos * 4 + d] || occ[dpos + deltas[d]])
                    best = 1;
            }
        }
        total += best;
    }
    return total;
}

/* ── Exported solver ── */

int solve_astar(
    int rows, int cols,
    int n_seals, const int *init_pos, const int *seal_colors,
    int n_dests, const int *dest_pos, const int *dest_colors,
    int n_walls, const int *wall_entries,
    int max_nodes, int heuristic_type,
    int *out_move_seals, int *out_move_dirs,
    int *out_n_moves, int *out_nodes
) {
    int total_cells = rows * cols;
    if (total_cells > MAX_CELLS || n_seals > MAX_SEALS || n_dests > MAX_DESTS)
        return -1;

    /* Build wall_check boolean array */
    bool wc[MAX_CELLS * 4];
    memset(wc, 0, sizeof(wc));
    for (int i = 0; i < n_walls; i++)
        wc[wall_entries[i]] = true;

    int deltas[4] = { -cols, cols, -1, 1 };

    /* Precompute wall-only stop positions */
    int ws[MAX_CELLS * 4];

    for (int r = 0; r < rows; r++) {
        int stop = -1;
        for (int c = cols - 1; c >= 0; c--) {
            int p = r * cols + c;
            if (wc[p * 4 + DIR_RIGHT]) stop = p;
            ws[p * 4 + DIR_RIGHT] = stop;
        }
        stop = -1;
        for (int c = 0; c < cols; c++) {
            int p = r * cols + c;
            if (wc[p * 4 + DIR_LEFT]) stop = p;
            ws[p * 4 + DIR_LEFT] = stop;
        }
    }
    for (int c = 0; c < cols; c++) {
        int stop = -1;
        for (int r = rows - 1; r >= 0; r--) {
            int p = r * cols + c;
            if (wc[p * 4 + DIR_DOWN]) stop = p;
            ws[p * 4 + DIR_DOWN] = stop;
        }
        stop = -1;
        for (int r = 0; r < rows; r++) {
            int p = r * cols + c;
            if (wc[p * 4 + DIR_UP]) stop = p;
            ws[p * 4 + DIR_UP] = stop;
        }
    }

    /* Pack initial state */
    uint64_t init = 0;
    for (int i = 0; i < n_seals; i++)
        init = sset(init, i, init_pos[i]);

    /* Allocate hash table (power-of-2, load factor ~0.5) */
    int ht_cap = 1;
    while (ht_cap < max_nodes * 2) ht_cap <<= 1;
    int ht_mask = ht_cap - 1;
    HTNode *ht = (HTNode *)calloc((size_t)ht_cap, sizeof(HTNode));
    if (!ht) return -1;

    /* Seed initial state */
    HTNode *hi = ht_find(ht, ht_mask, init);
    hi->key = init; hi->parent = init; hi->g = 0; hi->used = 1;

    /* Allocate heap */
    Heap heap;
    heap.cap = 1 << 16;
    heap.sz  = 0;
    heap.d   = (HNode *)malloc((size_t)heap.cap * sizeof(HNode));
    if (!heap.d) { free(ht); return -1; }

    /* Compute h0 and push */
    int pos_buf[MAX_SEALS];
    for (int i = 0; i < n_seals; i++) pos_buf[i] = init_pos[i];
    int h0 = (heuristic_type == 0)
        ? h_min_axis(pos_buf, n_seals, cols, seal_colors, n_dests, dest_colors, dest_pos)
        : h_axis_setup(pos_buf, n_seals, cols, total_cells, seal_colors, n_dests, dest_colors, dest_pos, wc, deltas);
    heap_push(&heap, h0, 0, init);

    int nodes_expanded = 0;
    int result = 1;  /* NO_SOLUTION */
    uint64_t goal_state = 0;

    while (heap.sz > 0) {
        HNode hn = heap_pop(&heap);
        uint64_t state = hn.state;
        int g = hn.g;

        HTNode *entry = ht_find(ht, ht_mask, state);
        if (entry->closed) continue;
        entry->closed = 1;
        nodes_expanded++;

        if (nodes_expanded > max_nodes) {
            result = 2;  /* NODE_LIMIT */
            break;
        }

        /* Unpack state once */
        for (int i = 0; i < n_seals; i++)
            pos_buf[i] = sget(state, i);

        /* Goal check */
        bool is_goal = true;
        for (int di = 0; di < n_dests; di++) {
            int color = dest_colors[di], dpos = dest_pos[di];
            bool found = false;
            for (int i = 0; i < n_seals; i++) {
                if (seal_colors[i] == color && pos_buf[i] == dpos) {
                    found = true; break;
                }
            }
            if (!found) { is_goal = false; break; }
        }

        if (is_goal) {
            result = 0;
            goal_state = state;
            break;
        }

        /* Expand */
        int ng = g + 1;
        for (int i = 0; i < n_seals; i++) {
            int sp = pos_buf[i];
            for (int d = 0; d < 4; d++) {
                int stop = ws[sp * 4 + d];
                if (stop == sp) continue;

                /* Adjust for seal blockers */
                if (d == DIR_RIGHT) {
                    for (int j = 0; j < n_seals; j++) {
                        if (j == i) continue;
                        int os = pos_buf[j];
                        if (os > sp && os <= stop) stop = os - 1;
                    }
                } else if (d == DIR_LEFT) {
                    for (int j = 0; j < n_seals; j++) {
                        if (j == i) continue;
                        int os = pos_buf[j];
                        if (os < sp && os >= stop) stop = os + 1;
                    }
                } else if (d == DIR_DOWN) {
                    int pc = sp % cols;
                    for (int j = 0; j < n_seals; j++) {
                        if (j == i) continue;
                        int os = pos_buf[j];
                        if (os % cols == pc && os > sp && os <= stop)
                            stop = os - cols;
                    }
                } else {
                    int pc = sp % cols;
                    for (int j = 0; j < n_seals; j++) {
                        if (j == i) continue;
                        int os = pos_buf[j];
                        if (os % cols == pc && os < sp && os >= stop)
                            stop = os + cols;
                    }
                }

                if (stop == sp) continue;

                uint64_t neighbor = sset(state, i, stop);
                HTNode *ne = ht_find(ht, ht_mask, neighbor);

                if (ne->used && ne->closed) continue;

                if (!ne->used || ng < ne->g) {
                    ne->key    = neighbor;
                    ne->parent = state;
                    ne->g      = ng;
                    ne->mseal  = (uint8_t)i;
                    ne->mdir   = (uint8_t)d;
                    ne->used   = 1;

                    /* Compute heuristic for neighbor */
                    int npos[MAX_SEALS];
                    for (int k = 0; k < n_seals; k++) npos[k] = pos_buf[k];
                    npos[i] = stop;

                    int h = (heuristic_type == 0)
                        ? h_min_axis(npos, n_seals, cols, seal_colors, n_dests, dest_colors, dest_pos)
                        : h_axis_setup(npos, n_seals, cols, total_cells, seal_colors, n_dests, dest_colors, dest_pos, wc, deltas);

                    heap_push(&heap, ng + h, ng, neighbor);
                }
            }
        }
    }

    /* Reconstruct path */
    *out_n_moves = 0;
    *out_nodes = nodes_expanded;

    if (result == 0) {
        int ms[MAX_SOLUTION], md[MAX_SOLUTION];
        int n = 0;
        uint64_t cur = goal_state;
        while (cur != init && n < MAX_SOLUTION) {
            HTNode *e = ht_find(ht, ht_mask, cur);
            ms[n] = e->mseal;
            md[n] = e->mdir;
            cur = e->parent;
            n++;
        }
        *out_n_moves = n;
        for (int i = 0; i < n; i++) {
            out_move_seals[i] = ms[n - 1 - i];
            out_move_dirs[i]  = md[n - 1 - i];
        }
    }

    free(heap.d);
    free(ht);
    return result;
}
