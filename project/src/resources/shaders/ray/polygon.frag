#version 430 compatibility

struct fragment {
    uint color;
    float depth;
    uint prev;
};

const int EMPTY = 0;
const int LINE = 2;
const int PLANE = 3;

uniform vec4 viewport;
uniform uvec2 window;

uniform vec4 circle1;
uniform vec4 arc1;
uniform vec4 circle2;
uniform vec4 arc2;
uniform vec4 circle3;
uniform vec4 arc3;
uniform vec4 circle4;
uniform vec4 arc4;
uniform vec4 circle5;
uniform vec4 arc5;
uniform vec3 outside;

in vec4 objPos;
in vec4 camPos;
in vec4 lightPos;
in float radius;
in float RR;
in vec4 color;
//in flat uint index;

out vec4 fragColor;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

void storeFragment(vec4 color, float depth) {
    uvec2 coord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].prev = atomicExchange(fragIndices[coord.y * window.x + coord.x], index);
}

int planePlaneIntersection(vec4 plane1, vec4 plane2, out vec3 p1, out vec3 p2);
vec3 rotate(vec3 v, vec3 axis, float angle);
bool sphericalLineArcIntersection(vec4 sphere, vec4 circle, vec4 arc, vec3 p, vec3 o);

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

    vec3 intPos1 = sphereint1 + objPos.xyz;
    vec3 intPos2 = sphereint2 + objPos.xyz;

    bool inner = false;
    bool outer = false;
    vec4 sphere = vec4(objPos.xyz, radius);
    
    int count = 0; // DEBUG
    if (sphericalLineArcIntersection(sphere, circle1, arc1, intPos1, outside)) { count++; outer = !outer; }
    if (sphericalLineArcIntersection(sphere, circle2, arc2, intPos1, outside)) { count++; outer = !outer; }
    if (sphericalLineArcIntersection(sphere, circle3, arc3, intPos1, outside)) { count++; outer = !outer; }
    if (sphericalLineArcIntersection(sphere, circle4, arc4, intPos1, outside)) { count++; outer = !outer; }
    if (sphericalLineArcIntersection(sphere, circle5, arc5, intPos1, outside)) { count++; outer = !outer; }

    if (sphericalLineArcIntersection(sphere, circle1, arc1, intPos2, outside)) inner = !inner;
    if (sphericalLineArcIntersection(sphere, circle2, arc2, intPos2, outside)) inner = !inner;
    if (sphericalLineArcIntersection(sphere, circle3, arc3, intPos2, outside)) inner = !inner;
    if (sphericalLineArcIntersection(sphere, circle4, arc4, intPos2, outside)) inner = !inner;
    if (sphericalLineArcIntersection(sphere, circle5, arc5, intPos2, outside)) inner = !inner;

    if (!inner && !outer) {
        //discard;
    }

    if (true/*outer*/) {
        vec4 fragColor1 = 0.2 * color;
        fragColor1 += 0.8 * -dot(normal1, normalize(ray)) * color;
        fragColor1.a = min(1.0, color.a / abs(dot(normal1, normalize(ray))));

        if (!outer) {
            fragColor1.rgb = vec3(0.0, 0.0, 0.0);
        }

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

int planePlaneIntersection(vec4 plane1, vec4 plane2, out vec3 p, out vec3 dir) {
    const float EPSILON = 0.001;
    float n1n2 = dot(plane1.xyz, plane2.xyz);
    if (abs(n1n2) >= 1.0 - EPSILON) {
        // The planes are parallel. Check if they are coplanar.
        float diff = plane1.w - n1n2 * plane2.w;
        /*if (n1n2 >= 0.0) {
            // Normals are in same direction, need to look at c0-c1.
            diff = plane1.d - plane2.d;
        } else {
            // Normals are in opposite directions, need to look at c0+c1.
            diff = plane1.d + plane2.d;
        }*/

        if (abs(diff) < EPSILON) {
            return PLANE;
        } else {
            return EMPTY;
        }
    }

    float invDet = 1.0 / (1.0 - n1n2 * n1n2);
    float c1 = (-plane1.w + n1n2 * plane2.w) * invDet;
    float c2 = (-plane2.w + n1n2 * plane1.w) * invDet;

    p = c1 * plane1.xyz + c2 * plane2.xyz;
    dir = normalize(cross(plane1.xyz, plane2.xyz));

    return LINE;
}

vec3 rotate(vec3 v, vec3 axis, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

bool sphericalLineArcIntersection(vec4 sphere, vec4 circle, vec4 arc, vec3 p, vec3 o) {
    // test p and o lie on opposite sides of circle plane
    vec3 circlePos = sphere.xyz + circle.xyz;
    vec4 circlePlane;
    circlePlane.xyz = normalize(circle.xyz);
    circlePlane.w = -dot(circlePlane.xyz, circlePos);
    if (dot(circlePlane, vec4(p, 1.0)) > 0 && dot(circlePlane, vec4(o, 1.0)) > 0) {
        return false;
    }

    // find intersection of line circle and arc circle
    vec4 linePlane;
    linePlane.xyz = normalize(cross(o - sphere.xyz, p - sphere.xyz));
    linePlane.w = -dot(linePlane.xyz, sphere.xyz);
    
    vec3 a, dir;
    if (planePlaneIntersection(circlePlane, linePlane, a, dir) != LINE) {
        return false;
    }
    
    // project sphere center onto line and compute distance to intersections
    vec3 projPos = a + (dot(sphere.xyz - a, dir) / dot(dir, dir)) * dir;
    if (dot(projPos, projPos) > RR) {
        return false;
    }
    vec3 projVec = projPos - sphere.xyz;
    float d = sqrt(RR - dot(projVec, projVec));
    
    // choose intersection point between p and o
    int count = 0;
    vec3 line = sphere.w * normalize(p + o - 2.0 * sphere.xyz);
    float lineAngleDist = dot(p - sphere.xyz, line);
    float arcAngleDist = cos(arc.w / 2.0) * dot(arc.xyz, arc.xyz);
    
    vec3 intPos = projPos - d * dir;
    vec3 lineIntVec = intPos - sphere.xyz;
    if (dot(lineIntVec, line) >= lineAngleDist) {
        // test whether inersection lies within arc
        vec3 arcIntVec = intPos - circlePos;
        if (dot(arcIntVec, arc.xyz) >= arcAngleDist) {
            count++;
        }
    }
    
    intPos = projPos + d * dir;
    lineIntVec = intPos - sphere.xyz;
    if (dot(lineIntVec, line) >= lineAngleDist) {
        // test whether inersection lies within arc
        vec3 arcIntVec = intPos - circlePos;
        if (dot(arcIntVec, arc.xyz) >= arcAngleDist) {
            count++;
        }
    }
    
    return count == 1;
}