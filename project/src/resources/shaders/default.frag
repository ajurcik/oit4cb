#version 430 core

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

uniform uvec2 window;

in vec4 color;
in float depth;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

void storeFragment(vec4 color, float depth, float ao) {
    uvec2 ucoord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].ao = ao;
    fragments[index].prev = atomicExchange(fragIndices[ucoord.y * window.x + ucoord.x], index);
}

void main() {
    storeFragment(color, depth /*gl_FragCoord.z*/, 0.0);
    discard;
}
