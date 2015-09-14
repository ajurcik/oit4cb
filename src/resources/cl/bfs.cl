#define LOCAL_SIZE 256
//#define LOG_LOCAL_SIZE 8
#define WARP_COUNT 4
#define WARP_SIZE 64

typedef struct {
    int count;
    int neighbors[3];
} row_t;

int expand(const int vertex, const int threadId, global const row_t * adjacency,
        global int * visited, global int * labels, local int hashes[][256], local int * neighbors);

int scan(local int * data, const int threadId);

bool warpCull(const int vertex, const int threadId, local int hashes[][256]);

int warpScanExclusive(local int * data, const int idx, const int laneId);
int warpScanInclusive(local int * data, const int idx, const int laneId);

/**
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
kernel void iterate(global const int * vertices,
        global const row_t * adjacency,
        global int * visited,
        global int * labels,
        const int size,
        global int * outVertices,
        global int * outSize) {
    int index = get_global_id(0);
    int threadId = get_local_id(0);
    
    int count = 0;
    local int neighbors[3 * LOCAL_SIZE];
    local int hashes[WARP_COUNT][256];
    
    if (index < size) {
        int vertex = vertices[index];
        count = expand(vertex, threadId, adjacency, visited, labels, hashes, neighbors);
    }
    
    // calc write positions
    local int sums[LOCAL_SIZE];
    sums[threadId] = count;
    
    barrier(CLK_LOCAL_MEM_FENCE);
    
    /*
    // Ladner-Fisher based scan
    for (int i = 0; i < LOG_LOCAL_SIZE; i++) {
        int src = (1 << i) + ((threadId >> i) << (i + 1)) - 1;
        int dst = (1 << i) + ((threadId >> i) << i) + threadId;
        
        sums[dst] += sums[src];
        
        barrier(CLK_LOCAL_MEM_FENCE);
    }*/
    
    // Kogge-Stone based block scan
    /*for (int s = 1; s < LOCAL_SIZE; s <<= 1) {
        int left = (threadId >= s) ? sums[threadId - s] : 0;
        barrier(CLK_LOCAL_MEM_FENCE);
        //if (threadId >= s) {
            sums[threadId] += left;//sums[threadId - s];
        //}
        barrier(CLK_LOCAL_MEM_FENCE);
    }*/
    
    int offset = scan(sums, threadId);
    
    // get global offset to outVertices
    local int groupOffset;
    //int offset = (threadId > 0) ? sums[threadId - 1] : 0;
    int lastThreadId = min((int) get_local_size(0), size) - 1;
    if (threadId == lastThreadId) {
        groupOffset = atomic_add(outSize, offset + count);
    }
    
    barrier(CLK_LOCAL_MEM_FENCE);
    
    // write new vertex frontier
    offset += groupOffset;
    for (int i = 0; i < count; i++) {
        outVertices[offset + i] = neighbors[i * LOCAL_SIZE + threadId];
    }
}

kernel void groupIterate(global const row_t * adjacency,
        const int start,
        const int label,
        global int * visited,
        global int * labels,
        global int * outVertices,
        global int * outSize) {
    int index = get_local_id(0);
    int groupSize = get_local_size(0);
    
    local int size;
    local int frontiers[2][2 * LOCAL_SIZE];
    
    if (index == 0) {
        labels[start] = label;
        visited[start / 32] |= 1 << (start & 31);
        frontiers[0][0] = start;
        size = 1;
    }
    
    barrier(CLK_LOCAL_MEM_FENCE);
    
    int iter = 0;
    while (0 < size && size < groupSize) {
        local int neighbors[3 * LOCAL_SIZE];
        local int hashes[WARP_COUNT][256];
        
        int count = 0;
        if (index < size) {
            int vertex = frontiers[iter & 1][index];
            count = expand(vertex, index, adjacency, visited, labels, hashes, neighbors);
        }
        
        // calc write positions
        local int sums[LOCAL_SIZE];
        sums[index] = count;
        
        barrier(CLK_LOCAL_MEM_FENCE);
        
        // scan neighbor counts
        int offset = scan(sums, index);
        int lastIndex = size - 1;
        if (index == lastIndex) {
            size = offset + count;
        }
        
        // write new vertex frontier
        iter++;
        for (int i = 0; i < count; i++) {
            frontiers[iter & 1][offset + i] = neighbors[i * LOCAL_SIZE + index];
        }
        
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    
    // write next frontier to global memory
    if (index < size) {
        outVertices[index] = frontiers[iter & 1][index];
    }
    int index2 = index + groupSize;
    if (index2 < size) {
        outVertices[index2] = frontiers[iter & 1][index2];
    }
    // frontier size
    if (index == 0) {
        outSize[0] = size;
    }
}

kernel void unlabelled(global const int * labels, const int size, global int * vertex) {
    int index = get_global_id(0);
    if (index >= size || *vertex >= 0) {
        return;
    }
    if (labels[index] == 0) {
        *vertex = index;
    }
}

int expand(const int vertex, const int threadId, global const row_t * adjacency,
        global int * visited, global int * labels, local int hashes[][256], local int * neighbors) {
    int count = 0;
    // cull duplicate vertices in a warp
    if (!warpCull(vertex, threadId, hashes)) {
        int label = labels[vertex];
        
        for (int i = 0; i < 3; i++) {
            int neighbor = adjacency[vertex].neighbors[i];
            
            int bit = 1 << (neighbor & 31);
            if ((visited[neighbor / 32] & bit) != 0) {
                // neighbor is visited
                continue;
            }

            if (labels[neighbor] > 0) {
                // neighbor is labelled
                continue;
            }
            
            // mark neighbor in global memory
            visited[neighbor / 32] |= bit;
            labels[neighbor] = label;
            
            // cache neighbor
            neighbors[count * LOCAL_SIZE + threadId] = neighbor;
            count++;
        }
    }
    
    return count;
}

bool warpCull(const int vertex, const int threadId, local int hashes[][256]) {
    int warpId = threadId / WARP_SIZE;
    int hash = vertex % 251;
    
    hashes[warpId][hash] = vertex;
    int retrieved = hashes[warpId][hash];
    if (retrieved == vertex) {
        // vie to be the "unique" item
        hashes[warpId][hash] = threadId;
        if (hashes[warpId][hash] != threadId) {
            // someone else is unique
            return true;
        }
    }
    
    return false;
}

int scan(local int * data, const int threadId) {
    int laneId = threadId % WARP_SIZE;
    int warpId = threadId / WARP_SIZE;
    
    int sum = warpScanExclusive(data, threadId, laneId);
    barrier(CLK_LOCAL_MEM_FENCE);
    
    if (laneId == WARP_SIZE - 1) {
        data[warpId] = data[threadId];
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    
    if (warpId == 0) {
        warpScanInclusive(data, threadId, laneId);
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    
    if (warpId > 0) {
        sum += data[warpId - 1];
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    
    data[threadId] = sum;
    barrier(CLK_LOCAL_MEM_FENCE);
    
    return sum;
}

int warpScanExclusive(local int * data, const int idx, const int laneId) {
    if (laneId > 0) data[idx] += data[idx - 1];
    if (laneId > 1) data[idx] += data[idx - 2];
    if (laneId > 3) data[idx] += data[idx - 4];
    if (laneId > 7) data[idx] += data[idx - 8];
    if (laneId > 15) data[idx] += data[idx - 16];
    
#if WARP_SIZE > 32
    if (laneId > 31) data[idx] += data[idx - 32];
#endif
    
    return (laneId > 0) ? data[idx - 1] : 0;
}

int warpScanInclusive(local int * data, const int idx, const int laneId) {
    if (laneId > 0) data[idx] += data[idx - 1];
    if (laneId > 1) data[idx] += data[idx - 2];
    if (laneId > 3) data[idx] += data[idx - 4];
    if (laneId > 7) data[idx] += data[idx - 8];
    if (laneId > 15) data[idx] += data[idx - 16];
    
#if WARP_SIZE > 32
    if (laneId > 31) data[idx] += data[idx - 32];
#endif
    
    return data[idx];
}