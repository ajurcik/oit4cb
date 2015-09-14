#version 430 core

uniform uint labelCount;

layout(std430) buffer MinMax {
    float minArea;
    float maxArea;
    float max2Area;
};

layout(r32f) readonly uniform image1D areasImg;

layout (local_size_x = 64) in;

shared float minAreas[gl_WorkGroupSize.x];
shared float maxAreas[gl_WorkGroupSize.x];
shared float max2Areas[gl_WorkGroupSize.x];

void reduceMin(inout float areas[gl_WorkGroupSize.x], out float value);
float reduceMax(float areas[gl_WorkGroupSize.x]);

void main() {
    uint threadID = gl_LocalInvocationIndex;

    minAreas[threadID] = 1000000000.0;
    maxAreas[threadID] = 0.0;
    max2Areas[threadID] = 0.0;

    if (threadID < labelCount) {
        float area = imageLoad(areasImg, int(threadID)).r;
        minAreas[threadID] = area;
        maxAreas[threadID] = area;
        max2Areas[threadID] = area;
    }

    // unrolled parallel reduction
    if (threadID < 32) {
        minAreas[threadID] = min(minAreas[threadID], minAreas[threadID + 32]);
        maxAreas[threadID] = max(maxAreas[threadID], maxAreas[threadID + 32]);
    }
    if (threadID < 16) {
        minAreas[threadID] = min(minAreas[threadID], minAreas[threadID + 16]);
        maxAreas[threadID] = max(maxAreas[threadID], maxAreas[threadID + 16]);
    }
    if (threadID < 8) {
        minAreas[threadID] = min(minAreas[threadID], minAreas[threadID + 8]);
        maxAreas[threadID] = max(maxAreas[threadID], maxAreas[threadID + 8]);
    }
    if (threadID < 4) {
        minAreas[threadID] = min(minAreas[threadID], minAreas[threadID + 4]);
        maxAreas[threadID] = max(maxAreas[threadID], maxAreas[threadID + 4]);
    }
    if (threadID < 2) {
        minAreas[threadID] = min(minAreas[threadID], minAreas[threadID + 2]);
        maxAreas[threadID] = max(maxAreas[threadID], maxAreas[threadID + 2]);
    }
    if (threadID == 0) {
        minArea = min(minAreas[0], minAreas[1]);
        maxArea = max(maxAreas[0], maxAreas[1]);
    }
    
    /*reduceMin(minAreas, minArea);
    float maxResult = reduceMax(maxAreas);
    if (threadID == 0) {
        //minArea = minResult;
        maxArea = maxResult;
    }*/

    barrier();
    memoryBarrierBuffer();

    if (threadID < labelCount) {
        if (max2Areas[threadID] == maxArea) {
            max2Areas[threadID] = 0.0;
        }
    }

    // unrolled parallel reduction
    if (threadID < 32) {
        max2Areas[threadID] = max(max2Areas[threadID], max2Areas[threadID + 32]);
    }
    if (threadID < 16) {
        max2Areas[threadID] = max(max2Areas[threadID], max2Areas[threadID + 16]);
    }
    if (threadID < 8) {
        max2Areas[threadID] = max(max2Areas[threadID], max2Areas[threadID + 8]);
    }
    if (threadID < 4) {
        max2Areas[threadID] = max(max2Areas[threadID], max2Areas[threadID + 4]);
    }
    if (threadID < 2) {
        max2Areas[threadID] = max(max2Areas[threadID], max2Areas[threadID + 2]);
    }
    if (threadID == 0) {
        max2Area = max(max2Areas[0], max2Areas[1]);
    }

    /*max2Area = reduceMax(max2Areas);*/
}

/*void reduceMin(inout float areas[gl_WorkGroupSize.x], out float value) {
    uint threadID = gl_LocalInvocationIndex;
    if (threadID < 32) {
        areas[threadID] = min(areas[threadID], areas[threadID + 32]);
    }
    if (threadID < 16) {
        areas[threadID] = min(areas[threadID], areas[threadID + 16]);
    }
    if (threadID < 8) {
        areas[threadID] = min(areas[threadID], areas[threadID + 8]);
    }
    if (threadID < 4) {
        areas[threadID] = min(areas[threadID], areas[threadID + 4]);
    }
    if (threadID < 2) {
        areas[threadID] = min(areas[threadID], areas[threadID + 2]);
    }
    if (threadID == 0) {
        value = min(areas[0], areas[1]);
    }
}

float reduceMax(float areas[gl_WorkGroupSize.x]) {
    uint threadID = gl_LocalInvocationIndex;
    if (threadID < 32) {
        areas[threadID] = max(areas[threadID], areas[threadID + 32]);
    }
    if (threadID < 16) {
        areas[threadID] = max(areas[threadID], areas[threadID + 16]);
    }
    if (threadID < 8) {
        areas[threadID] = max(areas[threadID], areas[threadID + 8]);
    }
    if (threadID < 4) {
        areas[threadID] = max(areas[threadID], areas[threadID + 4]);
    }
    if (threadID < 2) {
        areas[threadID] = max(areas[threadID], areas[threadID + 2]);
    }
    return max(areas[0], areas[1]);
}*/