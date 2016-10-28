#version 430 core

#ifndef MAX_CAP_POINTS
#define MAX_CAP_POINTS 32
#endif

const uint maxCapPoints = MAX_CAP_POINTS;

struct cap {
    vec4 position;
    vec4 plane; // in (circleStart, circleLen, ?, ?)
    uint atomIdx;
    uint label;
    uint padding0; // std430 aligns structs using biggest element
    uint padding1;
};

uniform uint capCount;
uniform uint maxSphereCapCount;
//uniform float probeRadius;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer circlesTex;
uniform samplerBuffer edgesCircleTex;
uniform samplerBuffer edgesLineTex;

// globals
vec3 points[maxCapPoints];

layout(std430) buffer Caps {
    cap caps[];
};

layout(std430) buffer SphereCapCounts {
    uint sphereCapCounts[];
};

layout(std430) buffer SphereCapPlanes {
    vec4 sphereCapPlanes[];
};

layout(std430) buffer Debug {
    vec4 debug[];
};

vec4 minSphericalCircle(uint count);

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= capCount) {
        return;
    }

    // add new cap plane
    uint atomIdx = caps[index].atomIdx;
    uint capPlaneIdx = atomicAdd(sphereCapCounts[atomIdx], 1);

    vec4 atom = texelFetch(atomsTex, int(atomIdx));

    vec2 circle = caps[index].plane.xy;
    uint circleStart = floatBitsToUint(circle.x);
    uint circleEnd = circleStart + floatBitsToUint(circle.y);

    for (uint c = circleStart; c < circleEnd; c++) {
        uint edgeIdx = texelFetch(circlesTex, int(c)).z;
        // compute small circle
        vec4 vs = texelFetch(edgesCircleTex, int(edgeIdx));
        vec3 relPos = vs.xyz - atom.xyz;
        float dist = length(relPos);
        // intersection plane
        float ar = /*sas ? atom.w - probeRadius :*/ atom.w;
        float r = (ar * ar) + (dist * dist) - (vs.w * vs.w);
        r = r / (2.0 * dist * dist);
        vec3 vec = relPos * r;
        vec4 circle;
        circle.w = sqrt(max(0.0, (ar * ar) - dot(vec, vec))); // max(...) needed due to floats
        //if (sas) {
        //    circle.w = circle.w * (ar + probeRadius) / ar;
        //    circle.xyz = (ar + probeRadius) / ar * vec;
        //} else {
            circle.xyz = vec;
        //}
        // compute line end point
        vec4 line = texelFetch(edgesLineTex, int(edgeIdx));
        vec3 zAxis = cross(relPos / dist, line.xyz);
        vec3 pc = atom.xyz + circle.xyz;
        vec3 p = pc;
        pc += circle.w * line.xyz;
        p += circle.w * line.w * line.xyz;
        p += circle.w * sqrt(max(0.0, 1.0 - line.w * line.w)) * zAxis; // max(...) needed due to floats
        points[c - circleStart] = (p - atom.xyz) / atom.w;
    }

    // compute smallest enclosing cap plane
    vec4 plane = minSphericalCircle(circleEnd - circleStart);
    plane.w = -atom.w * plane.w;

    caps[index].plane = plane;
    sphereCapPlanes[atomIdx * maxSphereCapCount + capPlaneIdx] = plane;
}

vec4 minSphericalCircleWithPoint(uint count, vec3 q);
vec4 minSphericalCircleWithTwoPoints(uint count, vec3 q1, vec3 q2);
vec4 sphericalCircle(vec3 p0, vec3 p1);
vec4 sphericalCircle(vec3 p0, vec3 p1, vec3 p2);
bool sphericalCircleContains(vec4 c, vec3 p);

vec4 minSphericalCircle(uint count) {
    // compute a random permutation p1, . . . ,pn of the points in S.
    // let c2 be the smallest spherical circle enclosing {p1,p2}.
    vec4 c = sphericalCircle(points[0], points[1]);
    for (uint i = 2; i < count; i++) {
        vec3 pi = points[i];
        if (!sphericalCircleContains(c, pi)) {
            c = minSphericalCircleWithPoint(i, pi);
        }
    }
    return c;
}

/**
 * Input: A set S of n points on S2, and a point q s.t. there exists an
 * enclosing spherical circle of S that passes through q.
 * Output: The minimum-radius spherical circle on S2 that fully contains
 * S and that passes through q.
 */
vec4 minSphericalCircleWithPoint(uint count, vec3 q) {
    // compute a random permutation p1, . . . ,pn of the points in S.
    // let c1 be the smallest spherical circle enclosing {p1,q}.
    vec4 c = sphericalCircle(points[0], q);
    for (uint i = 1; i < count; i++) {
        vec3 pi = points[i];
        if (!sphericalCircleContains(c, pi)) {
            c = minSphericalCircleWithTwoPoints(i, pi, q);
        }
    }
    return c;
}

/**
 * Input: A set S of n points on S2, and two points q1,q2 s.t. there exists
 * an enclosing spherical circle of S that passes through q1 and q2.
 * Output: The minimum-radius spherical circle on S2 that fully contains S
 * and that passes through q1 and q2.
 */
vec4 minSphericalCircleWithTwoPoints(uint count, vec3 q1, vec3 q2) {
    // compute a random permutation p1, . . . ,pn of the points in S.
    // let c0 be the smallest spherical circle enclosing {q1,q2}.
    vec4 c = sphericalCircle(q1, q2);
    for (uint i = 0; i < count; i++) {
        vec3 pi = points[i];
        if (!sphericalCircleContains(c, pi)) {
            c = sphericalCircle(q1, q2, pi);
        }
    }
    return c;
}

vec4 sphericalCircle(vec3 p0, vec3 p1) {
    vec4 c;
    // normal
    c.xyz = normalize(p0 + p1);
    // radius
    c.w = dot(c.xyz, p0);
    return c;
}

vec4 sphericalCircle(vec3 p0, vec3 p1, vec3 p2) {
    // circle plane
    vec4 c;
    c.xyz = normalize(cross(p1 - p0, p2 - p0));
    // radius
    c.w = dot(c.xyz, p0);
    if (c.w < 0.0) {
        // make the normal face the same direction as input points
        c = -c;
    }
    return c;
}

bool sphericalCircleContains(vec4 c, vec3 p) {
    return dot(c.xyz, p) >= c.w;
}