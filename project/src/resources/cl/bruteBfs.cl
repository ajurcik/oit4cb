#define UNLABELLED 0
#define INVALID_VERTEX -1

typedef struct {
    int count;
    int neighbors[3];
} row_t;

void connectedComponent(global const row_t * adjacency,
        //global int * vertexDistances,
        //global int * vertexLabels,
        global int2 * vertices,
        local int * iteration,
        const int vertexCount,
        const int label);
void unlabelledVertex(global int2 * vertices /*global int * vertexLabels*/, local int * start, const int vertexCount);

/**
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
kernel void bruteBfs(global const row_t * adjacency,
        //global int * vertexDistances,
        //global int * vertexLabels,
        global int2 * vertices,
        global int * outVertexLabels,
        global int * labels,
        global int * outerLabel,
        const int vertexCount) {
    int threadID = get_local_id(0); // only local ID!
    if (threadID >= vertexCount) {
        return;
    }
    
    local int start;
    local int iteration;
    local int label;
    local int indices[64];

    // initialization
    if (threadID == 0) {
        start = 0;
        iteration = 0;
        label = 1;
        // start vertex
        //vertexDistances[start] = iteration;
        //vertexLabels[start] = label;
        vertices[start] = (int2)(iteration, label);
    }

    // wait for control thread
    // sync vertexDistances, vertexLabels
    // sync iteration, label variables
    barrier(CLK_LOCAL_MEM_FENCE | CLK_GLOBAL_MEM_FENCE); 

    do {
        connectedComponent(adjacency, vertices /*vertexDistances, vertexLabels*/, &iteration, vertexCount, label);
        unlabelledVertex(vertices /*vertexLabels*/, &start, vertexCount);
        if (start != INVALID_VERTEX) {
            if (threadID == 0) {
                label++;
                //vertexDistances[start] = iteration;
                //vertexLabels[start] = label;
                vertices[start] = (int2)(iteration, label);
            }
            
            // wait for control thread
            // sync vertexDistances, vertexLabels
            // sync iteration, label variables
            barrier(CLK_LOCAL_MEM_FENCE | CLK_GLOBAL_MEM_FENCE);
        }
    } while (start != INVALID_VERTEX);

    // count labels
    int index = threadID;
    while (index < vertexCount) {
        int label = vertices[index].y; /*vertexLabels[index]*/;
        //outVertexLabels[index] = label;
        atomic_inc(&labels[label - 1]);
        index += get_local_size(0);
    }

    // find maximum
    barrier(CLK_GLOBAL_MEM_FENCE);

    if (threadID < 64) {
        indices[threadID] = threadID;
    }

    barrier(CLK_LOCAL_MEM_FENCE);

    for (int s = 64/2; s > 0; s >>= 1) {
        if (threadID < s) {
            if (labels[threadID] < labels[threadID + s]) {
                labels[threadID] = labels[threadID + s];
                indices[threadID] = indices[threadID + s];
            }
        }
        barrier(CLK_GLOBAL_MEM_FENCE);
    }

    if (threadID == 0) {
        *outerLabel = indices[0] + 1;
        /*printf("Components: ");
        for (int i = 0; i < 64; i++) {
            printf("%d ", labels[i]);
        }
        printf("\n");*/
    }

    // write labels
    //verticesLabel[index] = label; /*vertices[index].y*/ // bug???
}

void connectedComponent(global const row_t * adjacency,
        //global int * vertexDistances,
        //global int * vertexLabels,
        global int2 * vertices,
        local int * iteration,
        const int vertexCount,
        const int label) {
    local bool done;
    
    int threadID = get_local_id(0); // only local ID!
    do {
        // wait for other threads to go through the while condition
        // sync iteration variable
        barrier(CLK_LOCAL_MEM_FENCE);

        if (threadID == 0) {
            done = true;
        }

        // wait for control thread to set done
        barrier(CLK_LOCAL_MEM_FENCE);

        int index = threadID;
        while (index < vertexCount) {
            if (vertices[index].x /*vertexDistances[index]*/ == *iteration) {
                int v0 = adjacency[index].neighbors[0];
                int v1 = adjacency[index].neighbors[1];
                int v2 = adjacency[index].neighbors[2];
                if (/*vertexLabels[v0]*/ vertices[v0].y == UNLABELLED) {
                    //vertexDistances[v0] = *iteration + 1;
                    //vertexLabels[v0] = label;
                    vertices[v0] = (int2)(*iteration + 1, label);
                    done = false;
                }
                if (/*vertexLabels[v1]*/ vertices[v1].y == UNLABELLED) {
                    //vertexDistances[v1] = *iteration + 1;
                    //vertexLabels[v1] = label;
                    vertices[v1] = (int2)(*iteration + 1, label);
                    done = false;
                }
                if (/*vertexLabels[v2]*/ vertices[v2].y == UNLABELLED) {
                    //vertexDistances[v2] = *iteration + 1;
                    //vertexLabels[v2] = label;
                    vertices[v2] = (int2)(*iteration + 1, label);
                    done = false;
                }
            }
            index += get_local_size(0);
        }

        // wait for all threads to do iteration
        // sync vertices buffer
        // sync done variable
        barrier(CLK_LOCAL_MEM_FENCE | CLK_GLOBAL_MEM_FENCE);
        
        if (threadID == 0) {
            *iteration = *iteration + 1;
        }
    } while (!done);
}

void unlabelledVertex(global int2 * vertices /*global int * vertexLabels*/, local int * start, const int vertexCount) {
    int index = get_local_id(0); // only local ID!
    
    if (index == 0) {
        *start = INVALID_VERTEX;
    }

    // wait for control thread to reset start variable
    barrier(CLK_LOCAL_MEM_FENCE);

    // find next start vertex
    while (index < vertexCount) {
        if (vertices[index].y /*vertexLabels[index]*/ == UNLABELLED) {
            *start = index;
        }
        index += get_local_size(0);
    }

    // wait for all threads to scan vertices
    // sync start variable
    barrier(CLK_LOCAL_MEM_FENCE);
}