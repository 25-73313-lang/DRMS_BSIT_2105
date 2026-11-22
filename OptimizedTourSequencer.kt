package com.example.deliveryroutesystem

class OptimizedTourSequencer(private val exactLimit: Int = 10) : StopSequencer() {

    override fun sequence(
        cost: Array<DoubleArray>,
        urgentFlags: List<Boolean>,
        startIndex: Int,
        endIndex: Int
    ): List<Int> {
        val customers = validIndices(cost.size, startIndex, endIndex)
        if (customers.isEmpty()) return emptyList()
        return if (customers.size <= exactLimit) exactOrder(cost, startIndex, endIndex, customers)
        else heuristicOrder(cost, startIndex, endIndex, customers)
    }

    private fun exactOrder(
        cost: Array<DoubleArray>,
        start: Int,
        end: Int,
        c: List<Int>
    ): List<Int> {
        val m = c.size
        val full = (1 shl m) - 1
        val inf = Double.MAX_VALUE / 4

        val dp = Array(1 shl m) { DoubleArray(m) { inf } }
        val parent = Array(1 shl m) { IntArray(m) { -1 } }

        for (j in 0 until m) dp[1 shl j][j] = cost[start][c[j]]

        for (mask in 1..full) {
            for (j in 0 until m) {
                if ((mask and (1 shl j)) == 0) continue
                val cur = dp[mask][j]
                if (cur >= inf) continue
                for (k in 0 until m) {
                    if ((mask and (1 shl k)) != 0) continue
                    val nextMask = mask or (1 shl k)
                    val value = cur + cost[c[j]][c[k]]
                    if (value < dp[nextMask][k]) {
                        dp[nextMask][k] = value
                        parent[nextMask][k] = j
                    }
                }
            }
        }

        var bestJ = 0
        var best = inf
        for (j in 0 until m) {
            val value = dp[full][j] + cost[c[j]][end]
            if (value < best) {
                best = value
                bestJ = j
            }
        }

        val order = ArrayList<Int>()
        var mask = full
        var j = bestJ
        while (j != -1) {
            order.add(c[j])
            val previous = parent[mask][j]
            mask = mask xor (1 shl j)
            j = previous
        }
        order.reverse()
        return order
    }

    private fun heuristicOrder(
        cost: Array<DoubleArray>,
        start: Int,
        end: Int,
        customers: List<Int>
    ): List<Int> {
        var bestTour: List<Int> = emptyList()
        var bestCost = Double.MAX_VALUE

        for (first in customers) {
            val tour = mutableListOf(start, first)
            val remaining = customers.filter { it != first }.toMutableList()
            var current = first
            while (remaining.isNotEmpty()) {
                val next = remaining.minByOrNull { cost[current][it] }!!
                tour.add(next)
                remaining.remove(next)
                current = next
            }
            tour.add(end)

            improve(cost, tour)
            val total = tourCost(cost, tour)
            if (total < bestCost) {
                bestCost = total
                bestTour = tour.toList()
            }
        }
        return bestTour.subList(1, bestTour.size - 1).toList()
    }

    private fun tourCost(cost: Array<DoubleArray>, t: List<Int>): Double {
        var sum = 0.0
        for (k in 0 until t.size - 1) sum += cost[t[k]][t[k + 1]]
        return sum
    }

    private fun improve(cost: Array<DoubleArray>, tour: MutableList<Int>) {
        var best = tourCost(cost, tour)
        var improved = true
        var rounds = 0
        while (improved && rounds < 50) {
            improved = false
            rounds++

            for (i in 1 until tour.size - 2) {
                for (j in i + 1 until tour.size - 1) {
                    val candidate = tour.toMutableList()
                    candidate.subList(i, j + 1).reverse()
                    val c = tourCost(cost, candidate)
                    if (c < best - 1e-6) {
                        tour.clear()
                        tour.addAll(candidate)
                        best = c
                        improved = true
                    }
                }
            }

            for (i in 1 until tour.size - 1) {
                for (j in 1 until tour.size - 1) {
                    if (i == j) continue
                    val candidate = tour.toMutableList()
                    val node = candidate.removeAt(i)
                    candidate.add(j, node)
                    val c = tourCost(cost, candidate)
                    if (c < best - 1e-6) {
                        tour.clear()
                        tour.addAll(candidate)
                        best = c
                        improved = true
                    }
                }
            }
        }
    }
}
