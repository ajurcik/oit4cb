#version 430 core

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

struct frag {
    uint color;
    float depth;
    float ao;
};

uniform uvec2 window;
uniform uint maxNumFragments;

const uint INVALID_INDEX = 0xffffffff;

out vec4 fragColor;

//layout(binding = 0) uniform atomic_uint overdrawCount;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

layout(std430) buffer CountersBuffer {
    uint pixelCount;
    uint overdrawCount;
    uint maxFragmentCount;
};

void main() {
    uvec2 coord = uvec2(floor(gl_FragCoord.xy));
    uint index = fragIndices[coord.y * window.x + coord.x];
    if (index == INVALID_INDEX) {
        discard;
    }

    atomicAdd(pixelCount, 1);

    // read (nearest) fragments
    frag ordered[24];
    int count = 0;
    while (index != INVALID_INDEX && count < maxNumFragments) {
        ordered[count].color = fragments[index].color;
        ordered[count].depth = fragments[index].depth;
        ordered[count].ao = fragments[index].ao;
        index = fragments[index].prev;
        count++;
    }
    if (count == maxNumFragments && index != INVALID_INDEX) {
        atomicAdd(overdrawCount, 1);
        //atomicCounterIncrement(overdrawCount);
    }

    atomicMax(maxFragmentCount, count);

    // sort fragments (bubble sort)
    for (int i = 0; i < count - 1; i++) {
        for (int j = 0; j < count - i - 1; j++) {
            if (ordered[j].depth > ordered[j+1].depth) {
                frag tmp = ordered[j];
                ordered[j] = ordered[j+1];
                ordered[j+1] = tmp;
            }
        }
    }

    // compose fragments front to back
    vec3 sumColor = vec3(0.0);
    float sumAlpha = 0.0;
    for (uint i = 0; i < count; i++) {
        vec4 color = unpackUnorm4x8(ordered[i].color);
        sumColor += (1.0 - sumAlpha) * color.a * color.xyz;
        // do not add fragments hidden by totally opaque fragments - clamp to 1.0
        sumAlpha = min(sumAlpha + (1.0 - sumAlpha) * color.a, 1.0);
    }
    // add backgroud color
    fragColor.xyz = sumColor + (1.0 - sumAlpha) * vec3(1.0);
    fragColor.a = 1.0;

    //fragColor = unpackUnorm4x8(ordered[0].color); // DEBUG sort
    //fragColor = vec4(fragCount / 16.0, 0.0, 0.0, 1.0); // DEBUG peaks
}
