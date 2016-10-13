#define LOCAL_SIZE 256
#define LOG_LOCAL_SIZE 8
#define WARP_COUNT 8
#define WARP_SIZE 32

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
        const int vertexCount,
        global int * visited,
        global int * labels,
        global int * outVertices,
        global int * outSize,
        global int * outLabel) {
    int index = get_local_id(0);
    int groupSize = get_local_size(0);
    
    //if (index == 0) printf("\nWork group size: %d\n", groupSize);
    
    local int size;
    local int frontiers[2][2 * LOCAL_SIZE];
    
    local int s;
    local int l;
    if (index == 0) {
        s = start;
        l = label;
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    
    int iter;
    while (s >= 0) {
    
    if (index == 0) {
        labels[s /*start*/] = l /*label*/;
        visited[s /*start*/ / 32] |= 1 << (s /*start*/ & 31);
        frontiers[0][0] = s /*start*/;
        size = 1;
    }
    
    barrier(CLK_LOCAL_MEM_FENCE);
    
    /*int*/ iter = 0;
    while (0 < size && size < groupSize) {
        local int neighbors[3 * LOCAL_SIZE];
        local int hashes[WARP_COUNT][256];
        
        int count = 0;
        if (index < size) {
//            if (index == 0) {
//                printf("Expanding: ");
//                for (int i = 0; i < size; i++) {
//                    printf("%d ", frontiers[iter & 1][i]);
//                }
//                printf("\n");
//            }
            int vertex = frontiers[iter & 1][index];
            count = expand(vertex, index, adjacency, visited, labels, hashes, neighbors);
        }
        
        // calc write positions
        local int sums[LOCAL_SIZE];
        sums[index] = count;
        
        // get last thread index
        int lastIndex = size - 1;
        
        barrier(CLK_LOCAL_MEM_FENCE);
        
//        if (index == 0) {
//            printf("Expanded counts: ");
//            for (int i = 0; i < 32; i++) {
//                printf("%d ", sums[i]);
//            }
//            printf("\n");
//        }
        
        // scan neighbor counts
        int offset = scan(sums, index);
        
        if (index == lastIndex) {
            size = offset + count;
        }
        
//        barrier(CLK_LOCAL_MEM_FENCE);
        
//        if (index == 0) {
//            printf("Scanned counts: ");
//            for (int i = 0; i < 32; i++) {
//                printf("%d ", sums[i]);
//            }
//            printf("\n");
//            printf("Expanded frontier (%d): ", size);
//        }
        
        // write new vertex frontier
        iter++;
        for (int i = 0; i < count; i++) {
            frontiers[iter & 1][offset + i] = neighbors[i * LOCAL_SIZE + index];
//            printf("%d %d %d %d %d\n", neighbors[i * LOCAL_SIZE + index], offset, i, count, sums[index]);
        }
        
//        if (index == 0) printf("\n");
        
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    
        if (index == 0) {
            s = -1;
        }
    
        barrier(CLK_LOCAL_MEM_FENCE);
    
        if (size == 0) {
            barrier(CLK_GLOBAL_MEM_FENCE);
            
            int vertexIdx = index;
            while (vertexIdx < vertexCount) {
                if (labels[vertexIdx] == 0) {
                    s = vertexIdx;
                }
                vertexIdx += groupSize;
            }
            
            barrier(CLK_LOCAL_MEM_FENCE);
            
            if (index == 0) {
                l++;
            }
            
            barrier(CLK_LOCAL_MEM_FENCE);
        }
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
        outLabel[0] = l;
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

//kernel void testScan() {
//    int index = get_local_id(0);
//    int size = get_local_size(0);
//    
//    int count = index % 2;
//    local int sums[LOCAL_SIZE];
//    sums[index] = count;
//
//    barrier(CLK_LOCAL_MEM_FENCE);
//
//    scan(sums, index);
//
//    barrier(CLK_LOCAL_MEM_FENCE);
//
//    if (index == 0) {
//        printf("Scanned counts: ");
//        for (int i = 0; i < size; i++) {
//            printf("%d ", sums[i]);
//        }
//        printf("\n");
//    }
//}

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

inline int scan(local int * data, const int threadId) {
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

//void printWarp(local int * data) {
//    printf("Warp scanned: ");
//    for (int i = 0; i < WARP_SIZE; i++) {
//        printf("%d ", data[i]);
//    }
//    printf("\n");
//}

inline int warpScanExclusive(local int * data, const int idx, const int laneId) {
    //barrier(CLK_LOCAL_MEM_FENCE);
    //if (idx == 0) printf("Threads: ");
    //barrier(CLK_LOCAL_MEM_FENCE);
    //printf("%d(%d) ", idx, laneId);
    //barrier(CLK_LOCAL_MEM_FENCE);
    //if (idx == 0) { printf("Input: "); printWarp(data); }
    //barrier(CLK_LOCAL_MEM_FENCE);
    //float tmp = (laneId > 0) ? data[idx - 1] : 0;
    //barrier(CLK_LOCAL_MEM_FENCE);
    if (laneId > 0) data[idx] += data[idx - 1];
    //barrier(CLK_LOCAL_MEM_FENCE); //if (idx == 0) printWarp(data);
    
    //tmp = (laneId > 1) ? data[idx - 2] : 0;
    //barrier(CLK_LOCAL_MEM_FENCE);
    if (laneId > 1) data[idx] += data[idx - 2];
    //barrier(CLK_LOCAL_MEM_FENCE); //if (idx == 0) printWarp(data);
    
    //tmp = (laneId > 3) ? data[idx - 4] : 0;
    //barrier(CLK_LOCAL_MEM_FENCE);
    if (laneId > 3) data[idx] += data[idx - 4];
    //barrier(CLK_LOCAL_MEM_FENCE); //if (idx == 0) printWarp(data);
    
    //tmp = (laneId > 7) ? data[idx - 8] : 0;
    //barrier(CLK_LOCAL_MEM_FENCE);
    if (laneId > 7) data[idx] += data[idx - 8];
    //barrier(CLK_LOCAL_MEM_FENCE); //if (idx == 0) printWarp(data);
    
    //tmp = (laneId > 15) ? data[idx - 16] : 0;
    //barrier(CLK_LOCAL_MEM_FENCE);
    if (laneId > 15) data[idx] += data[idx - 16];
    //if (idx < 32) printf("%d %d\n", laneId, data[idx]); //printWarp(data);
    
#if WARP_SIZE > 32
    if (laneId > 31) data[idx] += data[idx - 32];
#endif

    barrier(CLK_LOCAL_MEM_FENCE);
    
    return (laneId > 0) ? data[idx - 1] : 0;
}

inline int warpScanInclusive(local int * data, const int idx, const int laneId) {
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