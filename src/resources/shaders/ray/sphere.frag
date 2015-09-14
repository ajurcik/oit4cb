#version 430 compatibility

struct fragment {
    uint color;
    float depth;
    uint prev;
};

uniform vec4 viewport;
uniform uvec2 window;
uniform uint maxNumNeighbors;
uniform float probeRadius;
uniform bool sas;

in vec4 objPos;
in vec4 camPos;
in vec4 lightPos;
in float radius;
in float RR;
in vec4 color;
in flat uint index;

out vec4 fragColor;

uniform usamplerBuffer neighborCountsTex;
uniform samplerBuffer smallCirclesTex;
uniform usamplerBuffer smallCirclesVisibleTex;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

layout(std430) buffer Debug {
    uint frags;
    uint passed;
};

void storeFragment(vec4 color, float depth) {
    uvec2 coord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].prev = atomicExchange(fragIndices[coord.y * window.x + coord.x], index);
}

void main() {
    // transform fragment coordinates from window coordinates to view coordinates.
    vec4 coord = gl_FragCoord
        * vec4(viewport.z, viewport.w, 2.0, 0.0) 
        + vec4(-1.0, -1.0, -1.0, 1.0);

    // transform fragment coordinates from view coordinates to object coordinates.
    coord = gl_ModelViewProjectionMatrixInverse * coord;
    coord /= coord.w;
    coord -= objPos; // ... and to glyph space

    // calc the viewing ray
    vec3 ray = normalize(coord.xyz - camPos.xyz);

    // calculate the geometry-ray-intersection
    float d1 = -dot(camPos.xyz, ray);                  // projected length of the cam-sphere-vector onto the ray
    float d2s = dot(camPos.xyz, camPos.xyz) - d1 * d1; // off axis of cam-sphere-vector and ray
    float radicand = RR - d2s;                         // square of difference of projected length and lambda

    if (radicand < 0.0) {
        discard;
    }

    float sqrtRad = sqrt(radicand);
    float lambda1 = d1 - sqrtRad; // - for outer
    float lambda2 = d1 + sqrtRad; // + for inner
    vec3 sphereint1 = lambda1 * ray + camPos.xyz; // outer intersection point
    vec3 sphereint2 = lambda2 * ray + camPos.xyz; // inner intersection point
    // "calc" normal at intersection point
    vec3 normal1 = sphereint1 / radius; // + for outer
    vec3 normal2 = -sphereint2 / radius; // - for inner

    bool inner = true;
    bool outer = true;
    vec3 intPos1 = sphereint1 + objPos.xyz;
    vec3 intPos2 = sphereint2 + objPos.xyz;
    // cut by all small circles planes
    uint nCnt = texelFetch(neighborCountsTex, int(index)).r;
    for (uint j = 0; j < nCnt; j++) {
        //uint visible = texelFetch(smallCirclesVisibleTex, int(index * maxNumNeighbors + j)).r;
        if (true/*visible > 0*/) {
            vec4 circle = texelFetch(smallCirclesTex, int(index * maxNumNeighbors + j));
            float t = length(circle.xyz);
            vec3 n = circle.xyz / t;
            vec3 p;
            if (sas) {
                p = circle.xyz; // SAS
            } else {
                p = t / (radius + probeRadius) * radius * n; // SES
            }
            float d = -dot(objPos.xyz + p, n);
            if (dot(n, intPos1) + d > 0.0) {
                outer = false;
                if (!inner) {
                    break;
                }
            }
            if (dot(n, intPos2) + d > 0.0) {
                inner = false;
                if (!outer) {
                    break;
                }
            }
        }
    }

    //if (index == 203) atomicAdd(frags, 1); // DEBUG
    
    if (!inner && !outer) {
        discard;
    }

    if (outer) {
        //if (index == 203) atomicAdd(passed, 1); // DEBUG
        
        vec4 fragColor1 = 0.2 * color;
        fragColor1 += 0.8 * -dot(normal1, normalize(ray)) * color;
        fragColor1.a = min(1.0, color.a / abs(dot(normal1, normalize(ray))));
        
        vec4 Ding = vec4(intPos1.xyz, 1.0);
        float depth = dot(gl_ModelViewProjectionMatrixTranspose[2], Ding);
        float depthW = dot(gl_ModelViewProjectionMatrixTranspose[3], Ding);
        float fragDepth = ((depth / depthW) + 1.0) * 0.5;

        // store front fragment to A-buffer
        if (0.0 <= fragDepth && fragDepth <= 1.0) {
            storeFragment(fragColor1, fragDepth);
        }
    }

    if (inner) {
        vec4 fragColor2 = vec4(0.2, 0.2, 0.2, 0.5);
        fragColor2 += 0.4 * -dot(normal2, normalize(ray)) * vec4(1.0, 1.0, 1.0, 0.5);
        fragColor2.a = min(1.0, color.a / abs(dot(normal2, normalize(ray))));

        vec4 Ding = vec4(intPos2.xyz, 1.0);
        float depth = dot(gl_ModelViewProjectionMatrixTranspose[2], Ding);
        float depthW = dot(gl_ModelViewProjectionMatrixTranspose[3], Ding);
        float fragDepth = ((depth / depthW) + 1.0) * 0.5;

        // store back fragment to A-buffer
        if (0.0 <= fragDepth && fragDepth <= 1.0) {
            storeFragment(fragColor2, fragDepth);
        }
    }

    discard;
}
