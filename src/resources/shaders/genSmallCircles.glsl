#version 430

#extension GL_ARB_compute_shader : require
#extension GL_ARB_shader_storage_buffer_object : require

const float PI = 3.1415926;

uniform uint atomsCount;
uniform uint maxNumNeighbors;

layout(binding = 0) uniform atomic_uint circlesCount;

layout(std430) buffer Atoms {
    vec4 positions[];
};

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

layout(std430) buffer SmallCircles {
    vec4 smallCircles[];
};

layout(std430) buffer SmallCirclesVisible {
    uint smallCirclesVisible[];
};

layout(std430) buffer CirclesVertices {
    vec4 vertices[];
};

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= atomsCount) {
        return;
    }

    vec4 atomi = positions[index];
    uint neighborCount = neighborCounts[index];

    for (uint j = 0; j < neighborCount; j++) {
        uint visible = smallCirclesVisible[index * maxNumNeighbors + j];
        if (visible == 1) {
            uint count = atomicCounterIncrement(circlesCount);
            // compute i-j arc
            vec4 circle = smallCircles[index * maxNumNeighbors + j];
            // draw small circle
            vec3 back = normalize(circle.xyz);
            // get two vectors in the circle plane
            vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), back));
            vec3 up = normalize(cross(back, right));
            // compute start angle and increment
            //float start = asin(dot((arc.xyz - atomi.xyz) / atomi.w, up));
            //float inc = 2 * (1 - start) / 12.0;
            for (uint v = 0; v <= 12; v++) {
                //float ang = start + a * inc;
                float ang = 2 * PI * v / 12.0f;
                // offset from center of point
                vec3 offset = circle.w * (cos(ang) * right - sin(ang) * up);
                vertices[13 * count + v] = vec4(atomi.xyz, 0.0) + circle + vec4(offset, 0.0);
            }
        }
    }
}
