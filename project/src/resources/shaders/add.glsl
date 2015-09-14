#version 430 core

uniform uint elementCount;

layout(std430) buffer A {
    int a[];
};

layout(std430) buffer B {
    int b[];
};

layout(std430) buffer C {
    int c[];
};

layout (local_size_x = 256) in;

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (idx >= elementCount) {
        return;
    }

    c[idx] = a[idx] + b[idx];
}
