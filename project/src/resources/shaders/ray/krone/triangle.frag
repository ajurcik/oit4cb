#version 430 compatibility

const uint AREA = 0;
const uint MONO = 1;

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

uniform vec4 viewport;
uniform uvec2 window;
uniform uint maxNumNeighbors;

// clipping
uniform bool clipCavities;
uniform bool clipSurface;
uniform uint surfaceLabel;
uniform float threshold;

// ambient occlusion & lighting
uniform bool phong;
uniform bool ao;
uniform float lambda;
uniform float volumeSize;
uniform float aoExponent;
uniform float aoThreshold;
uniform bool silhouettes;
uniform bool bfmod;

// coloring by cavity area
layout(shared) uniform MinMaxCavityArea {
    float minArea;
    float maxArea;
    float max2Area;
};
uniform uint cavityColoring;
uniform vec3 cavityColor1;
uniform vec3 cavityColor2;

// tunnel coloring
uniform vec3 tunnelColor;

in flat vec4 objPos;
in flat vec4 camPos;
in flat vec4 lightPos;
in float radius;
in float RR;
in vec4 color;
in flat uint index;

in vec3 atomPos1;
in vec3 atomPos2;
in vec3 atomPos3;
in vec4 plane1;
in vec4 plane2;
in vec4 plane3;

uniform usamplerBuffer neighborCountsTex;
uniform samplerBuffer neighborProbesTex;
uniform usamplerBuffer labelsTex;
uniform sampler1D areasTex;
uniform sampler3D aoVolumeTex;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

float squaredLength(vec3 v);
void storeIntersection(vec3 position, vec3 normal, vec3 eye, vec4 color, float Ka, float Kd, bool bfmod, uint label, float area);

void storeFragment(vec4 color, float depth, float ao) {
    uvec2 coord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].ao = ao;
    fragments[index].prev = atomicExchange(fragIndices[coord.y * window.x + coord.x], index);
}

void main() {
    // discard fragment if cavity and clipping enabled
    uint label = texelFetch(labelsTex, int(index)).r;
    float area;
    if (label != surfaceLabel) {
        if (clipCavities) {
            discard;
        }
        // get cavity area for fragment
        area = texelFetch(areasTex, int(label - 1), 0).r;
        // discard fragment if cavity area is less then threshold
        if (area < threshold) {
            discard;
        }
        if (abs(max2Area - minArea) > 0.005) {
            area = (area - minArea) / (max2Area - minArea);
        } else {
            area = 1.0;
        }
    } else {
        if (clipSurface) {
            discard;
        }
    }

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

    // cut by three great circles
    float dist1 = dot(plane1, vec4(intPos1, 1.0));
    float dist2 = dot(plane1, vec4(atomPos3, 1.0));
    if (dist1 * dist2 < 0.0) {
        outer = false;
    }
    dist1 = dot(plane1, vec4(intPos2, 1.0));
    if (dist1 * dist2 < 0.0) {
        inner = false;
    }

    dist1 = dot(plane2, vec4(intPos1, 1.0));
    dist2 = dot(plane2, vec4(atomPos1, 1.0));
    if (dist1 * dist2 < 0.0) {
        outer = false;
    }
    dist1 = dot(plane2, vec4(intPos2, 1.0));
    if (dist1 * dist2 < 0.0) {
        inner = false;
    }

    dist1 = dot(plane3, vec4(intPos1, 1.0));
    dist2 = dot(plane3, vec4(atomPos2, 1.0));
    if (dist1 * dist2 < 0.0) {
        outer = false;
    }
    dist1 = dot(plane3, vec4(intPos2, 1.0));
    if (dist1 * dist2 < 0.0) {
        inner = false;
    }

    // cut by all probe small circles planes
    uint nCnt = texelFetch(neighborCountsTex, int(index)).r;
    for (uint j = 0; j < nCnt; j++) {
        vec3 probe = texelFetch(neighborProbesTex, int(index * maxNumNeighbors + j)).rgb;
        if (squaredLength(objPos.xyz - probe) > 0.1 && squaredLength(intPos1 - probe) < RR) {
            outer = false;
            if (!inner) {
                break;
            }
        }
        if (squaredLength(objPos.xyz - probe) > 0.1 && squaredLength(intPos2 - probe) < RR) {
            inner = false;
            if (!outer) {
                break;
            }
        }
    }

    if (!inner && !outer) {
        discard;
    }


    vec3 eye = -normalize(ray);
    if (false /*outer*/) {
        if (label == surfaceLabel) {
            storeIntersection(intPos1, normal1, eye, color /*vec4(1.0, 1.0, 1.0, 0.5)*/, 0.2, 0.8 /*0.4*/, bfmod, label, area);
        } else {
            storeIntersection(intPos1, normal1, eye, color, 0.2, 0.8, false, label, area);
        }
    }

    if (inner) {
        if (label == surfaceLabel) {
            storeIntersection(intPos2, normal2, eye, color, 0.2, 0.8, false, label, area);
        } else {
            storeIntersection(intPos2, normal2, eye, color /*vec4(1.0, 1.0, 1.0, 0.5)*/, 0.2, 0.8 /*0.4*/, bfmod, label, area);
        }
    }

    discard;
}

void storeIntersection(vec3 position, vec3 normal, vec3 eye, vec4 color, float Ka, float Kd, bool bfmod, uint label, float area) {
    if (label != surfaceLabel) {
        if (cavityColoring == MONO) {
            area = 0.0;
        }
        color.rgb = mix(cavityColor1, cavityColor2, area); // color cavity
    }

    float aoFactor = texture3D(aoVolumeTex, (position + lambda * normal) / volumeSize).r;
    if (label == surfaceLabel && aoFactor > 0.9) {
        //color.rgb = tunnelColor;
    }

    vec4 fragColor = color;
    if (phong) {
        float Id = max(0.0, dot(normal, eye));
        fragColor.rgb = Ka * fragColor.rgb + Kd * Id * fragColor.rgb; // ambient + diffuse term
    }

    if (ao) {
        //fragColor.rgb *= max(1.0 - aoFactor, 0.0);
        //fragColor.a = min(color.a + aoFactor, 1.0);
        fragColor.a = min(pow(aoFactor / aoThreshold, aoExponent), 1.0);
    }

    if (silhouettes) {
        //fragColor.a = min(1.0, color.a / abs(dot(normal1, normalize(ray))));
        fragColor.a = pow(fragColor.a, dot(normal, eye) + 1.0);
    }

    if (bfmod) {
        fragColor.a = fragColor.a * (1.0 - dot(normal, eye /* + C */));
    }

    vec4 Ding = vec4(position, 1.0);
    float depth = dot(gl_ModelViewProjectionMatrixTranspose[2], Ding);
    float depthW = dot(gl_ModelViewProjectionMatrixTranspose[3], Ding);
    float fragDepth = ((depth / depthW) + 1.0) * 0.5;

    // store front fragment to A-buffer
    if (0.0 <= fragDepth && fragDepth <= 1.0) {
        storeFragment(fragColor, depthW /*fragDepth*/, aoFactor);
    }
}

float squaredLength(vec3 v) {
    return dot(v, v);
}