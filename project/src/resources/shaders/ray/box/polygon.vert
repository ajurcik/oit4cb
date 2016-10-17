#version 430 compatibility

vec3 vertices[] = {
    vec3(-1.0, -1.0,  1.0),
    vec3( 1.0, -1.0,  1.0),
    vec3( 1.0,  1.0,  1.0),
    vec3(-1.0,  1.0,  1.0),
    vec3(-1.0, -1.0, -1.0),
    vec3( 1.0, -1.0, -1.0),
    vec3( 1.0,  1.0, -1.0),
    vec3(-1.0,  1.0, -1.0)
};

ivec4 faces[] = {
    ivec4(0, 1, 3, 2), // +Z
    ivec4(1, 5, 2, 6), // +X
    ivec4(3, 2, 7, 6), // +Y
    ivec4(5, 4, 6, 7), // -Z
    ivec4(4, 0, 7, 3), // -X
    ivec4(0, 4, 1, 5)  // -Y
};

const uint CW = 0;
const uint CCW = 1;

// viewport
uniform vec4 viewport;
// SAS/SES
uniform bool sas;
uniform float probeRadius;
// cavity clipping
uniform bool clipCavities;
uniform uint outerLabel;

uniform usamplerBuffer circlesTex;
uniform samplerBuffer edgesCircleTex;
uniform samplerBuffer edgesLineTex;

// globals
vec3 spherePoints[16];

layout(std430) buffer Debug {
    vec4 debug[];
};

out VertexData {
    // OBB
    vec3 faceVertices[3][4];
    int faceCount;
    // ray-casting
    vec4 objPos;
    vec4 camPos;
    vec4 lightPos;
    float radius;
    float RR;
    vec4 color;
    // polygon
    flat uint index; // atom
    flat uint label;
    flat uint circleStart;
    flat uint circleEnd;
    flat uint orientation;
} vertex;

vec4 minSphericalCircle(uint count);
vec4 minSphericalCircleWithPoint(uint count, vec3 q);
vec4 minSphericalCircleWithTwoPoints(uint count, vec3 q1, vec3 q2);
vec4 sphericalCircle(vec3 p0, vec3 p1);
vec4 sphericalCircle(vec3 p0, vec3 p1, vec3 p2);
bool sphericalCircleContains(vec4 c, vec3 p);

vec4 minCone(uint count);
vec4 minSphere(uint count);

void main() {
    vertex.objPos = vec4(gl_Vertex.xyz, 1.0);
    vertex.radius = gl_Vertex.w;
    if (sas) {
        vertex.radius += probeRadius;
    }
    vertex.RR = vertex.radius * vertex.radius;

    uvec4 params = uvec4(floor(gl_MultiTexCoord0));
    vertex.index = params.x;
    vertex.label = params.y;
    vertex.circleStart = params.z;
    vertex.circleEnd = params.z + params.w;
    // isolated torus clipping plane
    //vertex.plane = gl_MultiTexCoord1;

    /*if (clipCavities) {
        if (label != outerLabel) {
            gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
            return;
        }
    }*/

    // calculate camera position
    vertex.camPos = gl_ModelViewMatrixInverse[3]; // (C) by Christoph
    vertex.camPos.xyz -= vertex.objPos.xyz; // cam pos to glyph space

    vertex.lightPos = gl_ModelViewMatrixInverse * normalize(gl_LightSource[0].position);

    // camera coordinate system in object space
    /*vec4 tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[2];
    vec3 camIn = normalize(tmp.xyz);
    tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[1];
    vec3 camUp = tmp.xyz;
    vec3 camRight = normalize(cross(camIn, camUp));
    camUp = cross(camIn, camRight);*/

    vertex.color = gl_Color;

    vec4 cap = vec4(0.0);
    vec3 points[32];
    for (uint c = vertex.circleStart; c < vertex.circleEnd; c++) {
        uint edgeIdx = texelFetch(circlesTex, int(c)).z;
        // compute small circle
        vec4 vs = texelFetch(edgesCircleTex, int(edgeIdx));
        vec3 relPos = vs.xyz - vertex.objPos.xyz;
        float dist = length(relPos);
        // intersection plane
        float ar = sas ? vertex.radius - probeRadius : vertex.radius;
        float r = (ar * ar) + (dist * dist) - (vs.w * vs.w);
        r = r / (2.0 * dist * dist);
        vec3 vec = relPos * r;
        vec4 circle;
        circle.w = sqrt((ar * ar) - dot(vec, vec));
        if (sas) {
            circle.w = circle.w * (ar + probeRadius) / ar;
            circle.xyz = (ar + probeRadius) / ar * vec;
        } else {
            circle.xyz = vec;
        }
        // compute line end point
        vec4 line = texelFetch(edgesLineTex, int(edgeIdx));
        vec3 zAxis = cross(relPos / dist, line.xyz);
        vec3 pc = vertex.objPos.xyz + circle.xyz;
        vec3 p = pc;
        pc += circle.w * line.xyz;
        p += circle.w * line.w * line.xyz;
        p += circle.w * sqrt(1.0 - line.w * line.w) * zAxis;
        points[2 * (c - vertex.circleStart)] = p;
        points[2 * (c - vertex.circleStart) + 1] = pc;
        // compute cap axis
        //cap.xyz += p - vertex.objPos.xyz /*line.xyz*/;
        cap.xyz += pc - vertex.objPos.xyz;
        //cap.xyz += line.xyz;
    }

    if (vertex.label == outerLabel) {
        cap.xyz = normalize(cap.xyz);
    } else {
        for (uint i = 0; i < vertex.circleEnd - vertex.circleStart; i++) {
            spherePoints[i] = (points[2 * i] - vertex.objPos.xyz) / vertex.radius;
            //if (vertex.index == 52) {
            //    debug[i] = vec4(spherePoints[i], 0.0);
            //}
        }
        cap.xyz = minSphericalCircle(vertex.circleEnd - vertex.circleStart).xyz;
    }
    cap.w = -dot(cap.xyz, vertex.objPos.xyz);

    // compute orientation
    /*vertex.orientation = CCW;
    if (vertex.circleEnd - vertex.circleStart >= 2) {
        vec3 dir = cross(vertex.objPos.xyz - points[0], vertex.objPos.xyz - points[1]);
        if (dot(dir, cap.xyz) < 0.0) {
            vertex.orientation = CW;
        }
    }*/

    float minDist = vertex.radius;
    for (uint i = 0; i < 2 * (vertex.circleEnd - vertex.circleStart); i++) {
        minDist = min(minDist, dot(cap.xyz, points[i]) + cap.w);
    }
    float minDistPos = max(minDist, 0.0);

    float sx = (vertex.radius - minDist) / 2.0;
    float syz = sqrt(vertex.radius * vertex.radius - minDistPos * minDistPos);

    vec3 obbC = vertex.objPos.xyz + (minDist + sx) * cap.xyz;

    mat3 obbRot;
    obbRot[0] = cap.xyz;
    obbRot[1] = ((obbRot[0].x > 0.9) || (obbRot[0].x < -0.9))
            ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0); // normal on tmp
    obbRot[2] = normalize(cross(obbRot[0], obbRot[1]));
    obbRot[1] = cross(obbRot[2], obbRot[0]);

    vec3 obbScale = vec3(sx, syz, syz);

    int of = 0;
    for (int f = 0; f < 6; f++) {
        ivec4 face = faces[f];
        vec3 v0 = vertices[face.x];
        vec3 v1 = vertices[face.y];
        vec3 v2 = vertices[face.z];
        vec3 v3 = vertices[face.w];

        vec4 pv0 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbRot * (obbScale * v0)), 1.0);
        pv0 /= pv0.w;
        vec4 pv1 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbRot * (obbScale * v1)), 1.0);
        pv1 /= pv1.w;
        vec4 pv2 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbRot * (obbScale * v2)), 1.0);
        pv2 /= pv2.w;
        vec4 pv3 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbRot * (obbScale * v3)), 1.0);
        pv3 /= pv3.w;
        
        vec3 n = normalize(cross(pv1.xyz - pv0.xyz, pv3.xyz - pv0.xyz));
        //vec4 color = vec4(1.0, 1.0, 0.0, 1.0);
        if (n.z < 0) {
            continue;
            //color.rgb = vec3(0.5, 0.5, 0.5);
        }
        
        if (of < 3) {
            vertex.faceVertices[of][0] = pv0.xyz;
            vertex.faceVertices[of][1] = pv1.xyz;
            vertex.faceVertices[of][2] = pv2.xyz;
            vertex.faceVertices[of][3] = pv3.xyz;
        }
        of++; // more than 3 faces indicate invalid box
    }

    vertex.faceCount = of;

    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}

vec4 minCone(uint count, vec3 q);
vec4 minCone(uint count, vec3 q1, vec3 q2);
vec4 minCone(uint count, vec3 q1, vec3 q2, vec3 q3);
vec4 cone(vec3 p0, vec3 p1, vec3 p2);
vec4 cone(vec3 p0, vec3 p1, vec3 p2, vec3 p3);
bool coneContains(vec4 s, vec3 p);

vec4 minCone(uint count) {
    vec4 c = cone(spherePoints[0], spherePoints[1], spherePoints[2]);
    for (uint i = 3; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!coneContains(c, pi)) {
            c = minCone(i, pi);
        }
    }
    return c;
}

vec4 minCone(uint count, vec3 q) {
    vec4 c = cone(spherePoints[0], spherePoints[1], q);
    for (uint i = 2; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!coneContains(c, pi)) {
            c = minCone(i, pi, q);
        }
    }
    return c;
}

vec4 minCone(uint count, vec3 q1, vec3 q2) {
    vec4 c = cone(spherePoints[0], q1, q2);
    for (uint i = 1; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!coneContains(c, pi)) {
            c = minCone(i, pi, q1, q2);
        }
    }
    return c;
}

vec4 minCone(uint count, vec3 q1, vec3 q2, vec3 q3) {
    vec4 c = cone(q1, q2, q3);
    for (uint i = 0; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!coneContains(c, pi)) {
            c = cone(q1, q2, q3, pi);
        }
    }
    return c;
}

vec4 cone(vec3 p0, vec3 p1, vec3 p2) {
    return sphericalCircle(p0, p1, p2);
}

vec4 cone(vec3 p0, vec3 p1, vec3 p2, vec3 p3) {
    // find all four circles
    vec4 c0 = sphericalCircle(p0, p1, p2);
    vec4 c1 = sphericalCircle(p0, p1, p3);
    vec4 c2 = sphericalCircle(p0, p2, p3);
    vec4 c3 = sphericalCircle(p1, p2, p3);

    // make circles cones
    c0 = coneContains(c0, p3) ? c0 : -c0;
    c1 = coneContains(c1, p2) ? c1 : -c1;
    c2 = coneContains(c2, p1) ? c2 : -c2;
    c3 = coneContains(c3, p0) ? c3 : -c3;

    // choose smallest cone
    vec4 c = c0;
    c = (c1.w > c.w) ? c1 : c;
    c = (c2.w > c.w) ? c2 : c;
    c = (c3.w > c.w) ? c3 : c;

    return c;
}

bool coneContains(vec4 c, vec3 p) {
    return dot(c.xyz, p) >= c.w;
}

vec4 minSphere(uint count, vec3 q);
vec4 minSphere(uint count, vec3 q1, vec3 q2);
vec4 minSphere(uint count, vec3 q1, vec3 q2, vec3 q3);
vec4 sphere(vec3 p0, vec3 p1, vec3 p2);
vec4 sphere(vec3 p0, vec3 p1, vec3 p2, vec3 p3);
bool sphereContains(vec4 s, vec3 p);

vec4 minSphere(uint count) {
    vec4 s = sphere(spherePoints[0], spherePoints[1], spherePoints[2]);
    for (uint i = 3; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!sphereContains(s, pi)) {
            s = minSphere(i, pi);
        }
    }
    return s;
}

vec4 minSphere(uint count, vec3 q) {
    vec4 s = sphere(spherePoints[0], spherePoints[1], q);
    for (uint i = 2; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!sphereContains(s, pi)) {
            s = minSphere(i, pi, q);
        }
    }
    return s;
}

vec4 minSphere(uint count, vec3 q1, vec3 q2) {
    vec4 s = sphere(spherePoints[0], q1, q2);
    for (uint i = 1; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!sphereContains(s, pi)) {
            s = minSphere(i, pi, q1, q2);
        }
    }
    return s;
}

vec4 minSphere(uint count, vec3 q1, vec3 q2, vec3 q3) {
    vec4 s = sphere(q1, q2, q3);
    for (uint i = 0; i < count; i++) {
        vec3 pi = spherePoints[i];
        if (!sphereContains(s, pi)) {
            s = sphere(q1, q2, q3, pi);
        }
    }
    return s;
}

/*
 * http://stackoverflow.com/questions/8947151/smallest-sphere-to-encapsulate-a-triangle-in-3d
 */
vec4 sphere(vec3 p0, vec3 p1, vec3 p2) {
    // check sphere definition
    if (distance(p0, p1) < 0.01) {
        // coincidence
        vertex.color = vec4(1.0, 1.0, 0.0, gl_Color.a);
        return vec4(p0, 0.0);
    } else {
        float d = length(cross(p0 - p1, p0 - p2)) / length(p2 - p1);
        if (d < 0.01) {
            // colinearity
            vertex.color = vec4(0.0, 1.0, 0.0, gl_Color.a);
            float A = distance(p0, p1);
            float B = distance(p1, p2);
            float C = distance(p2, p0);
            vec4 s;
            if (A > B) {
                if (A > C) {
                    s.xyz = (p0 + p1) / 2.0;
                    s.w = A / 2.0;
                } else {
                    s.xyz = (p2 + p0) / 2.0;
                    s.w = C / 2.0;
                }
            } else {
                if (B > C) {
                    s.xyz = (p1 + p2) / 2.0;
                    s.w = B / 2.0;
                } else {
                    s.xyz = (p2 + p0) / 2.0;
                    s.w = C / 2.0;
                }
            }
            return s;
        }
    }
    
    vec4 s;
    // Calculate relative distances
    float A = distance(p0, p1);
    float B = distance(p1, p2);
    float C = distance(p2, p0);

    // Re-orient triangle (make A longest side)
    vec3 a = p2, b = p0, c = p1;
    if (B < C) {
        //swap(B, C), swap(b, c);
        float TMP = B; B = C; C = TMP;
        vec3 tmp = b; b = c; c = tmp;
    }
    if (A < B) {
        //swap(A, B), swap(a, b);
        float TMP = A; A = B; B = TMP;
        vec3 tmp = a; a = b; b = tmp;
    }

    // If obtuse, just use longest diameter, otherwise circumscribe
    if ((B*B) + (C*C) <= (A*A)) {
        s.w = A / 2.0;
        s.xyz = (b + c) / 2.0;
    } else {
        // http://en.wikipedia.org/wiki/Circumscribed_circle
        float cos_a = (B*B + C*C - A*A) / (B*C*2.0);
        s.w = A / (sqrt(1 - cos_a*cos_a) * 2.0);
        vec3 alpha = a - c, beta = b - c;
        s.xyz = cross(beta * dot(alpha, alpha) - alpha * dot(beta, beta), cross(alpha, beta)) /
            (dot(cross(alpha, beta), cross(alpha, beta)) * 2.0) + c;
    }
    return s;
}

/*
 * http://steve.hollasch.net/cgindex/geometry/sphere4pts.html
 */
vec4 sphere(vec3 p0, vec3 p1, vec3 p2, vec3 p3) {
    // check sphere definition
    if (distance(p0, p1) < 0.01) {
        // coincidence
        vertex.color = vec4(1.0, 1.0, 0.0, gl_Color.a);
        return vec4(p0, 0.0);
    } else {
        float d = length(cross(p0 - p1, p0 - p2)) / length(p2 - p1);
        if (d < 0.01) {
            // colinearity
            vertex.color = vec4(0.0, 1.0, 0.0, gl_Color.a);
            return sphere(p0, p1, p3);
        } else {
            vec4 p;
            p.xyz = normalize(cross(p0 - p1, p0 - p2));
            p.w = -dot(p.xyz, p0);
            if (abs(dot(p.xyz, p3) + p.w) < 0.01) {
                // coplanarity
                vertex.color = vec4(0.0, 0.0, 1.0, gl_Color.a);
                return sphere(p0, p1, p2);
            }
        }
    }

    vec4 s0 = sphere(p0, p1, p2);
    // circle plane
    vec4 cp;
    cp.xyz = normalize(cross(p1 - p0, p2 - p0));
    cp.w = -dot(cp.xyz, p0);
    
    // compute e
    vec3 e;
    float d1 = length(cross(p3 - s0.xyz, p3 - s0.xyz + cp.xyz));
    if (d1 < 0.001) {
        // p3 lies on cp.xyz
        e = p0;
    } else {
        // project p3 on cp
        vec3 p3cp = p3 - dot(p3 - p0, cp.xyz) * cp.xyz;
        e = s0.xyz + s0.w * normalize(p3cp - s0.xyz);
    }
    
    vec3 ep3 = (e + p3) / 2.0;
    vec4 ep3p;
    ep3p.xyz = normalize(p3 - e);
    ep3p.w = -dot(ep3p.xyz, ep3);

    // intersection of cp.xyz with ep3p
    float d2 = dot(ep3 - s0.xyz, ep3p.xyz) / dot(cp.xyz, ep3p.xyz);
    
    vec4 s;
    s.xyz = s0.xyz + d2 * cp.xyz;
    s.w = distance(s.xyz, p0);

    return s;
}

bool sphereContains(vec4 s, vec3 p) {
    vec3 v = p - s.xyz;
    return dot(v, v) <= s.w * s.w;
}

vec4 minSphericalCircle(uint count) {
    // compute a random permutation p1, . . . ,pn of the points in S.
    // let c2 be the smallest spherical circle enclosing {p1,p2}.
    vec4 c = sphericalCircle(spherePoints[0], spherePoints[1]);
    for (uint i = 2; i < count; i++) {
        vec3 pi = spherePoints[i];
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
    vec4 c = sphericalCircle(spherePoints[0], q);
    for (uint i = 1; i < count; i++) {
        vec3 pi = spherePoints[i];
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
        vec3 pi = spherePoints[i];
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