#version 430 compatibility

const uint ISOLATED = 0;
const uint AND = 1;
const uint OR = 2;
const uint DEBUG = 3;

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

uniform vec4 viewport;
uniform float probeRadius;

/*
out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out vec4 radii;
out vec4 visibilitySphere;
out vec4 plane1;
out vec4 plane2;
out flat uint operation;
//out flat uint index;
out vec4 color;

out vec3 rotMatT0;
out vec3 rotMatT1;
out vec3 rotMatT2;

out flat uint label;
*/

out VertexData {
    // OBB
    vec2 faceVertices[3][4];
    int faceCount;
    vec3 faceNormal[3]; // DEBUG
    // torus
    vec4 objPos;
    vec4 camPos;
    vec4 lightPos;
    vec4 radii;
    vec4 visibilitySphere;
    vec4 plane1;
    vec4 plane2;
    flat uint operation;
    //flat uint index;
    vec4 color;
    vec3 rotMatT0;
    vec3 rotMatT1;
    vec3 rotMatT2;
    flat uint label;
} vertex;

uniform usamplerBuffer edgesTex;
uniform usamplerBuffer labelsTex;

void main() {
    uint index = uint(gl_VertexID);

    vertex.objPos = vec4(gl_Vertex.xyz, 1.0);
    vertex.radii.x = probeRadius;
    vertex.radii.y = vertex.radii.x * vertex.radii.x;
    vertex.radii.z = gl_Vertex.w;
    vertex.radii.w = vertex.radii.z * vertex.radii.z;

    vertex.color = gl_Color;

    vec4 inTorusAxis = gl_MultiTexCoord0;
    vertex.rotMatT0 = inTorusAxis.xyz;
    if (inTorusAxis.w == 0.0) {
        vertex.operation = ISOLATED; // ISOLATED
    } else if (inTorusAxis.w > 2.0) {
        vertex.operation = DEBUG; // DEBUG
    } else {
        vertex.operation = inTorusAxis.w > 0.0 ? AND : OR;
    }

    vertex.rotMatT2 = ((vertex.rotMatT0.x > 0.9) || (vertex.rotMatT0.x < -0.9))
            ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0); // normal on tmp
    vertex.rotMatT1 = cross(vertex.rotMatT0, vertex.rotMatT2);
    vertex.rotMatT1 = normalize(vertex.rotMatT1);
    vertex.rotMatT2 = cross(vertex.rotMatT1, vertex.rotMatT0);

    vec3 ttmp1 = vertex.rotMatT2;
    vec3 ttmp2 = vertex.rotMatT1;
    vec3 ttmp3 = vertex.rotMatT0;
 
    vertex.rotMatT0 = vec3(ttmp1.x, ttmp2.x, ttmp3.x);
    vertex.rotMatT1 = vec3(ttmp1.y, ttmp2.y, ttmp3.y);
    vertex.rotMatT2 = vec3(ttmp1.z, ttmp2.z, ttmp3.z);

    // rotate and copy the visibility sphere
    vec4 inSphere = gl_MultiTexCoord1;
    vertex.visibilitySphere.xyz = vertex.rotMatT0 * inSphere.x + vertex.rotMatT1 * inSphere.y + vertex.rotMatT2 * inSphere.z;
    vertex.visibilitySphere.w = inSphere.w;
    
    // rotate and copy clipping planes
    vertex.plane1 = gl_MultiTexCoord2;
    vertex.plane2 = gl_MultiTexCoord3;

    if (vertex.operation != 0) {
        // get edge label
        uint vertexIdx = texelFetch(edgesTex, int(index)).r;
        vertex.label = texelFetch(labelsTex, int(vertexIdx)).r;
    } else {
        // polygon label is in plane1.x
        vertex.label = floatBitsToUint(vertex.plane1.x);
    }

    // calculate cam position
    vec4 tmp = gl_ModelViewMatrixInverse[3]; // (C) by Christoph
    tmp.xyz -= vertex.objPos.xyz; // cam move
    vertex.camPos.xyz = vertex.rotMatT0 * tmp.x + vertex.rotMatT1 * tmp.y + vertex.rotMatT2 * tmp.z;

    // calculate light position in glyph space
    vertex.lightPos = gl_ModelViewMatrixInverse * normalize(gl_LightSource[0].position);
    vertex.lightPos.xyz = vertex.rotMatT0 * vertex.lightPos.x + vertex.rotMatT1 * vertex.lightPos.y + vertex.rotMatT2 * vertex.lightPos.z;

    // camera coordinate system in object space
    /*vec4 tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[2];
    vec3 camIn = normalize(tmp.xyz);
    tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[1];
    vec3 camUp = tmp.xyz;
    vec3 camRight = normalize(cross(camIn, camUp));
    camUp = cross(camIn, camRight);*/

    // OBB bound for ray-casting
    vec4 obbUpX1 = gl_MultiTexCoord4;
    vec4 obbSize = gl_MultiTexCoord5;
    float x1 = obbUpX1.w;
    float x2 = obbSize.x;
    float y1 = obbSize.y;
    float y2 = obbSize.z;
    float z = obbSize.w;

    vec3 obbC = vertex.objPos.xyz + inSphere.xyz + (x1 - x2) / 2.0 * inTorusAxis.xyz;
    obbC += (y1 + y2) / 2.0 * obbUpX1.xyz;

    mat3 obbMat;
    obbMat[0] = inTorusAxis.xyz;
    obbMat[1] = obbUpX1.xyz;
    obbMat[2] = cross(obbMat[0], obbMat[1]);

    vec3 obbScale;
    obbScale.x = (x1 + x2) / 2.0;
    obbScale.y = abs(y1 - y2) / 2.0;
    obbScale.z = z;

    int of = 0;
    for (int f = 0; f < 6; f++) {
        ivec4 face = faces[f];
        vec3 v0 = vertices[face.x];
        vec3 v1 = vertices[face.y];
        vec3 v2 = vertices[face.z];
        vec3 v3 = vertices[face.w];

        vec4 pv0 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbMat * (obbScale * v0)), 1.0);
        pv0 /= pv0.w;
        vec4 pv1 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbMat * (obbScale * v1)), 1.0);
        pv1 /= pv1.w;
        vec4 pv2 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbMat * (obbScale * v2)), 1.0);
        pv2 /= pv2.w;
        vec4 pv3 = gl_ModelViewProjectionMatrix * vec4(obbC + (obbMat * (obbScale * v3)), 1.0);
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
        vertex.faceNormal[of] = n;
        of++;
    }

    vertex.faceCount = of;

    // write something to pass vertex
    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}
