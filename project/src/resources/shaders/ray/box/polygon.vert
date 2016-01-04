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
uniform uint surfaceLabel;

uniform usamplerBuffer circlesTex;
uniform samplerBuffer edgesCircleTex;
uniform samplerBuffer edgesLineTex;

out VertexData {
    // OBB
    vec2 faceVertices[3][4];
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
        if (label != surfaceLabel) {
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
    vec3 points[16];
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
        vec3 p = vertex.objPos.xyz + circle.xyz;
        p += circle.w * line.w * line.xyz;
        p += circle.w * sqrt(1.0 - line.w * line.w) * zAxis;
        points[c - vertex.circleStart] = p;
        // compute cap axis
        cap.xyz += line.xyz;
    }
    
    cap.xyz = normalize(cap.xyz);
    cap.w = -dot(cap.xyz, vertex.objPos.xyz);

    // compute orientation
    vertex.orientation = CCW;
    if (vertex.circleEnd - vertex.circleStart >= 2) {
        vec3 dir = cross(vertex.objPos.xyz - points[0], vertex.objPos.xyz - points[1]);
        if (dot(dir, cap.xyz) < 0.0) {
            vertex.orientation = CW;
        }
    }

    float minDist = vertex.radius;
    for (uint i = 0; i < vertex.circleEnd - vertex.circleStart; i++) {
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
        
        vertex.faceVertices[of][0] = pv0.xy;
        vertex.faceVertices[of][1] = pv1.xy;
        vertex.faceVertices[of][2] = pv2.xy;
        vertex.faceVertices[of][3] = pv3.xy;
        of++;
    }

    vertex.faceCount = of;

    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}
