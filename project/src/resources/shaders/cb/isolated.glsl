#version 430 core

struct torus {
    vec4 position;
    vec4 axis;
    vec4 visibility;
    vec4 plane1;
    vec4 plane2;
};

uniform uint torusCount;

uniform samplerBuffer atomsTex;
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
        uint label = polygonLabel(aj);
        tori[torusIdx].plane1.x = uintBitsToFloat(label);
        clipPolygon(ai, torusIdx);
        clipPolygon(aj, torusIdx);
        return;
    }

    tori[torusIdx].plane1.x = uintBitsToFloat(999); // DEBUG
}

uint polygonLabel(uint index) {
    uint vertexIdx = texelFetch(circlesTex, int(index * 32)).x;
    return texelFetch(labelsTex, int(vertexIdx)).r;
}

void clipPolygon(uint atomIdx, uint torusIdx) {
    vec4 atom = texelFetch(atomsTex, int(atomIdx));
    vec4 vs = tori[torusIdx].visibility;
    // compute intersection plane (taken from neighbors.glsl)
    vec3 relPos = atom.xyz - vs.xyz;
    float dist = length(relPos);
    float r = atom.w * atom.w + dist * dist - vs.w * vs.w;
    r = r / (2.0 * dist * dist);
    vec3 vec = relPos * r;
    // set isolated torus plane
    vec4 plane;
    plane.xyz = vec;
    plane.w = -dot(plane.xyz, atom.xyz + vec);
    // write isolated torus plane
    polygonsPlanes[atomIdx] = plane;
}