#version 430 core

uniform uint elementCount;
uniform uint operationCount;

layout(std430) buffer Result {
    float result;
};

layout(local_size_x = 256) in;

//shared float array[32 * 380];

void main() {
    if (gl_GlobalInvocationID.x >= elementCount) {
        return;
    }
    
    uint offset = 0;
    //uint offset = gl_LocalInvocationID.x * 380;
    float array[768];
    for (uint i = 0; i < operationCount; i++) {
        array[offset + i] = 1.0;
    }
    
    float sum;
    for (uint i = 0; i < operationCount; i++) {
        sum += array[offset + i];
    }

    result = sum;
}
