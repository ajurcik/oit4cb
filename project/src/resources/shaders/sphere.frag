#version 430 core

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

uniform uvec2 window;

in vec3 pos;
in vec3 normal;
in vec4 color;

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
    vec3 l = vec3(0.0, 0.0, 1.0);
    vec3 n = normalize(normal);
    vec3 e = normalize(-pos);
    vec3 r = normalize(-reflect(l, n));
    
    vec4 fragColor = vec4(0.0, 0.0, 0.0, color.a);
    fragColor.rgb += 0.2 * color.rgb;
    fragColor.rgb += 0.8 * max(0.0, dot(n, l)) * color.rgb;
    //fragColor.rgb += pow(max(0.0, dot(r, e)), 64.0) * vec3(1.0);

    storeFragment(fragColor, -pos.z /*gl_FragCoord.z*/, -1.0);
    discard;
}
