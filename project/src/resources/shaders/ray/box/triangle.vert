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

// viewport
uniform vec4 viewport;
// surface
uniform float probeRadius;

out VertexData {
    // OBB
    vec3 faceVertices[3][4];
    int faceCount;
    // sphere
    flat vec4 objPos;
    flat vec4 camPos;
    flat vec4 lightPos;
    float radius;
    float RR;
    vec4 color;
    flat uint index;
    // triangle
    vec3 atomPos1;
    vec3 atomPos2;
    vec3 atomPos3;
    vec4 plane1;
    vec4 plane2;
    vec4 plane3;
} vertex;

void main() {
    vertex.objPos = vec4(gl_Vertex.xyz, 1.0);
    vertex.radius = probeRadius;
    vertex.RR = probeRadius * probeRadius;

    vertex.index = uint(floor(gl_Vertex.w));

    vec3 atomVec1 = gl_MultiTexCoord0.xyz;
    vec3 atomVec2 = gl_MultiTexCoord1.xyz;
    vec3 atomVec3 = gl_MultiTexCoord2.xyz;

    vertex.atomPos1 = atomVec1 + vertex.objPos.xyz;
    vertex.atomPos2 = atomVec2 + vertex.objPos.xyz;
    vertex.atomPos3 = atomVec3 + vertex.objPos.xyz;

    vertex.plane1.xyz = normalize(cross(atomVec1, atomVec2));
    vertex.plane1.w = -dot(vertex.plane1.xyz, vertex.objPos.xyz);
    vertex.plane2.xyz = normalize(cross(atomVec2, atomVec3));
    vertex.plane2.w = -dot(vertex.plane2.xyz, vertex.objPos.xyz);
    vertex.plane3.xyz = normalize(cross(atomVec1, atomVec3));
    vertex.plane3.w = -dot(vertex.plane3.xyz, vertex.objPos.xyz);

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
    gl_Position = vec4(0.5, 0.5, 0.0, 1.0);
    
    vec3 p1 = vertex.objPos.xyz + probeRadius * normalize(atomVec1);
    vec3 p2 = vertex.objPos.xyz + probeRadius * normalize(atomVec2);
    vec3 p3 = vertex.objPos.xyz + probeRadius * normalize(atomVec3);

    vec4 plane;
    plane.xyz = normalize(cross(p2 - p1, p3 - p1));
    plane.w = -dot(plane.xyz, p1);
    float dist = dot(plane.xyz, vertex.objPos.xyz) + plane.w;
    if (dist > 0.0) {
        plane = -plane;
        dist = -dist;
    }

    // shrink OBB
    /*vec3 o = vertex.objPos.xyz + (probeRadius - dist) * plane.xyz;
    vec3 pp1 = p1 - dot(p1 - o, plane.xyz) * plane.xyz;
    vec3 pp2 = p2 - dot(p2 - o, plane.xyz) * plane.xyz;
    vec3 pp3 = p3 - dot(p3 - o, plane.xyz) * plane.xyz;
    vec3 pv1 = normalize(pp1 - o);
    vec3 pv2 = normalize(pp2 - o);
    vec3 pv3 = normalize(pp3 - o);
    float d12 = dot(pv1, pv2);
    float d13 = dot(pv1, pv3);
    float d23 = dot(pv2, pv3);
    vec3 zAxis;
    if (d12 < d13) {
        if (d12 < d23) {
            zAxis = pp1 - pp2;
        } else { // d12 >= d23
            zAxis = pp2 - pp3;
        }
    } else { // d12 >= d13
        if (d13 < d23) {
            zAxis = pp1 - pp3;
        } else { // d13 >= d23
            zAxis = pp2 - pp3;
        }
    }

    zAxis = normalize(zAxis);
    vec3 yAxis = cross(zAxis, plane.xyz);
    vec3 yv1 = probeRadius * normalize(p1 - dot(p1 - o, zAxis) * zAxis - vertex.objPos.xyz);
    vec3 yv2 = probeRadius * normalize(p2 - dot(p2 - o, zAxis) * zAxis - vertex.objPos.xyz);
    vec3 yv3 = probeRadius * normalize(p3 - dot(p3 - o, zAxis) * zAxis - vertex.objPos.xyz);
    float sy1 = min(min(dot(yv1, yAxis), dot(yv2, yAxis)), dot(yv3, yAxis));
    float sy2 = max(max(dot(yv1, yAxis), dot(yv2, yAxis)), dot(yv3, yAxis));*/
    /*float sy1 = min(min(dot(pp1 - o, yAxis), dot(pp2 - o, yAxis)), dot(pp3 - o, yAxis));
    float sy2 = max(max(dot(pp1 - o, yAxis), dot(pp2 - o, yAxis)), dot(pp3 - o, yAxis));*/

    float sx = (probeRadius + dist) / 2.0;
    float syz = sqrt(probeRadius * probeRadius - dist * dist);

    vec3 obbC = vertex.objPos.xyz + (probeRadius - sx) * plane.xyz;
    //obbC += (sy2 + sy1) / 2.0 * yAxis;

    mat3 obbRot;
    obbRot[0] = plane.xyz;
    obbRot[1] = ((obbRot[0].x > 0.9) || (obbRot[0].x < -0.9))
            ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0); // normal on tmp
    obbRot[2] = normalize(cross(obbRot[0], obbRot[1]));
    obbRot[1] = cross(obbRot[2], obbRot[0]);
    /*obbRot[1] = yAxis;
    obbRot[2] = zAxis;*/

    vec3 obbScale = vec3(sx, /*(sy2 - sy1) / 2.0*/ syz, syz);

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
            //vertex.color.rgb = vec3(0.5, 0.5, 0.5);
        }
        
        vertex.faceVertices[of][0] = pv0.xyz;
        vertex.faceVertices[of][1] = pv1.xyz;
        vertex.faceVertices[of][2] = pv2.xyz;
        vertex.faceVertices[of][3] = pv3.xyz;
        of++;
    }

    vertex.faceCount = of;
}
