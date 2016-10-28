#version 430 compatibility

const uint AREA = 0;
const uint MONO = 1;

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

// viewport
uniform vec4 viewport;
uniform uvec2 window;
// probe & surface
uniform float probeRadius;
uniform bool sas;

// small circles texture
uniform uint maxNumNeighbors;

// ambient occlusion & lighting
uniform bool phong;
uniform bool ao;
uniform float lambda;
uniform float volumeSize;
uniform float aoExponent;
uniform float aoThreshold;
uniform bool silhouettes;
uniform bool bfmod;

// cavity coloring
uniform uint outerLabel;
uniform uint cavityColoring;
uniform vec3 cavityColor1;
uniform vec3 cavityColor2;

// tunnel coloring
uniform float tunnelAOThreshold;
uniform vec3 tunnelColor;

// clipping by isolated tori
//uniform uint maxSphereIsolatedTori;

// clipping by cavities
uniform uint maxSphereCavities;

// debug
uniform bool obb;

in vec4 objPos;
in vec4 camPos;
in vec4 lightPos;
in float radius;
in float RR;
in vec4 color;

// sphere & surface
in flat uint index;
in flat uint label;

// plane
in vec4 plane;

// area
in flat float area;

uniform usamplerBuffer neighborsCountTex;
uniform samplerBuffer smallCirclesTex;
uniform usamplerBuffer sphereCavityCountsTex;
uniform samplerBuffer sphereCavityPlanesTex;
uniform sampler3D aoVolumeTex;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

layout(std430) buffer Debug {
    vec4 debug[];
};

void storeFragment(vec4 color, float depth, float ao) {
    uvec2 coord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].ao = ao;
    fragments[index].prev = atomicExchange(fragIndices[coord.y * window.x + coord.x], index);
}

float squaredLength(vec3 v);
void storeIntersection(vec3 position, vec3 normal, vec3 eye, vec4 color, float Ka, float Kd, bool bfmod);

void main() {
    // BV visualization, ray counting
    if (obb) {
        /*if (index == 0)*/ { storeFragment(vec4(1.0, 1.0, 0.0, 1.0), 0.0, 0.0); discard; }
    }
    
    /*if (index == 1668) {
        //discard;
        storeFragment(color, 10.0, 1.0); discard; // DEBUG
    } else {
        discard;
    }*/
    
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
        //storeFragment(vec4(1.0, 1.0, 0.0, 0.5), 10.0, 1.0); discard; // DEBUG
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

    // clip by cap plane, TODO SAS
    if (dot(plane.xyz, intPos1 - objPos.xyz) + plane.w < 0.0) {
        outer = false;
    }
    if (dot(plane.xyz, intPos2 - objPos.xyz) + plane.w < 0.0) {
        inner = false;
    }

    if (!inner && !outer) {
        //storeFragment(vec4(1.0, 1.0, 0.0, 0.5), 10.0, 1.0); discard; // DEBUG
        discard;
    }

    // cut by all small circles planes
    uint nCnt = texelFetch(neighborsCountTex, int(index)).r;
    for (uint j = 0; j < nCnt; j++) {
        vec4 circle = texelFetch(smallCirclesTex, int(index * maxNumNeighbors + j));
        if (circle.w < -10.0) {
            // circle was removed
            continue;
        }
        float t = length(circle.xyz);
        vec3 n = circle.xyz / t;
        vec3 p;
        if (sas) {
            p = circle.xyz; // SAS
        } else { 
            p = t / (radius + probeRadius) * radius * n; // SES
        }
        float d = -dot(n, objPos.xyz + p);
        if (circle.w < 0.0) {
            // circle center does not lie between atom centers
            n = -n;
            d = -d;
        }
        // DEBUG
        /*if (index == 14) {
            debug[2 * j] = circle;
            debug[2 * j + 1] = vec4(n, d);
        }*/
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
    
    if (!inner && !outer) {
        //storeFragment(vec4(1.0, 1.0, 0.0, 0.5), 10.0, 1.0); discard; // DEBUG
        discard;
    }

    vec3 eye = -normalize(ray);
    if (outer) {
        if (label == outerLabel) {
            storeIntersection(intPos1, normal1, eye, color, 0.2, 0.8, false);
        } else {
            storeIntersection(intPos1, normal1, eye, color /*vec4(1.0, 1.0, 1.0, 0.5)*/, 0.2, 0.8 /*0.4*/, bfmod);
        }
    }

    if (inner) {
        if (label == outerLabel) {
            storeIntersection(intPos2, normal2, eye, color /*vec4(1.0, 1.0, 1.0, 0.5)*/, 0.2, 0.8 /*0.4*/, bfmod);
        } else {
            storeIntersection(intPos2, normal2, eye, color, 0.2, 0.8, false);
        }
    }

    discard;
}

float squaredLength(vec3 v) {
    return dot(v, v);
}

void storeIntersection(vec3 position, vec3 normal, vec3 eye, vec4 color, float Ka, float Kd, bool bfmod) {
    if (label != outerLabel) {
        // color cavity
        if (cavityColoring == MONO) {
            color.rgb = cavityColor1;
        } else {
            color.rgb = mix(cavityColor1, cavityColor2, area);
        }
    }

    float aoFactor = texture3D(aoVolumeTex, (position + lambda * normal) / volumeSize).r;
    if (label == outerLabel && aoFactor > tunnelAOThreshold && !bfmod) {
        //color.rgb = tunnelColor;
        aoFactor = uintBitsToFloat(-2);
    }

    vec4 fragColor = color;
    if (phong) {
        float Id = max(0.0, dot(normal, eye));
        fragColor.rgb = Ka * fragColor.rgb + Kd * Id * fragColor.rgb; // ambient + diffuse term
    }

    if (ao /*&& !bfmod*/) {
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