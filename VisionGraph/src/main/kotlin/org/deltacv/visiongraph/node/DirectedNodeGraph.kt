package org.deltacv.visiongraph.node

class DirectedNodeGraph {
    private val adjacencyList = mutableMapOf<Int, MutableSet<Int>>() // Node ID -> Linked Node IDs

    fun addEdge(fromNodeId: Int, toNodeId: Int) {
        adjacencyList.getOrPut(fromNodeId) { mutableSetOf() }.add(toNodeId)
    }

    fun removeEdge(fromNodeId: Int, toNodeId: Int) {
        adjacencyList[fromNodeId]?.remove(toNodeId)
        if (adjacencyList[fromNodeId]?.isEmpty() == true) {
            adjacencyList.remove(fromNodeId)
        }
    }

    fun removeNode(nodeId: Int) {
        adjacencyList.remove(nodeId)
        adjacencyList.values.forEach { it.remove(nodeId) }
    }

    /**
     * Top-Sort algorithmic cycle check.
     * Returns true if adding a directed edge from `fromNodeId` to `toNodeId` would create an exhaustless cycle mathematically.
     * Utilizes a highly optimized recursive DFS strictly parsing primitive integers caching structural dependencies.
     */
    fun hasCycleIfAdded(fromNodeId: Int, toNodeId: Int): Boolean {
        if (fromNodeId == toNodeId) return true // Self-loops mathematically are infinite
        
        val visited = mutableSetOf<Int>()
        
        fun dfs(current: Int): Boolean {
            if (current == fromNodeId) return true // Traced back to start = cycle!
            if (!visited.add(current)) return false // Already checked this path
            
            adjacencyList[current]?.forEach { next ->
                if (dfs(next)) return true
            }
            return false
        }
        
        return dfs(toNodeId)
    }

    fun clear() {
        adjacencyList.clear()
    }
}
