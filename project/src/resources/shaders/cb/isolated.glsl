#version 430 core

struct torus {
    vec4 position;
    vec4 axis;
    vec4 visibility;
    vec4 plane1;
    vec4 plane2;
};

uniform uint torusCount;

uniform usamplerBuffer circlesTex;
uniform usamplerBuffer circlesCountTex;
uniform usamplerBuffer labelsTex;
uniform usamplerBuffer polygonsTex;

layout(std430) buffer Tori {
    torus tori[];
};

layout(std430) buffer IsolatedTori {
    uint isolatedTori[];
};

layout(std430) buffer PolygonsPlanes {
    vec4 polygonsPlanes[];
};

layout (local_size_x = 64) in;

uint polygonLabel(uint index);
void clipPolygon(uint polyIdx, uint torusIdx);

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= torusCount) {
        return;
    }

    uint torusIdx = isolatedTori[index];

    uint ai = floatBitsToUint(tori[torusIdx].plane1.x);
    uint aj = floatBitsToUint(tori[torusIdx].plane1.y);
    
    uint count = texelFetch(circlesCountTex, int(ai)).r;
    if (count == 1) {
        uint label = polygonLabel(ai);
        tori[torusIdx].plane1.x = uintBitsToFloat(label);
        clipPolygon(ai, torusIdx);
        clipPolygon(aj, torusIdx);
        return;
    }
    
    count = texelFetch(circlesCountTex, int(aj)).r;
    if (count == 1) {
        uint label = polygonLabel(ai);
        tori[torusIdx].plane1.x = uintBitsToFloat(label);
        clipPolygon(ai, torusIdx);
        clipPolygon(aj, torusIdx);
        return;
    }
}

uint polygonLabel(uint index) {
    uint vertexIdx = texelFetch(circlesTex, int(index * 32)).x;
    return texelFetch(labelsTex, int(vertexIdx)).r;
}

void clipPolygon(uint polyIdx, uint torusIdx) {
    polygonsPlanes[polyIdx] = vec4(1.0, 2.0, 3.0, 4.0); // DEBUG
}