#version 430 compatibility

const uint ISOLATED = 0;
const uint AND = 1;
const uint OR = 2;
const uint DEBUG = 3;

uniform vec3 camIn;
uniform vec3 camUp;
uniform vec3 camRight;
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

    // TODO: fix computation of bounding box
    // projected camera vector
    vec3 c2 = vec3(dot(tmp.xyz, camRight), dot(tmp.xyz, camUp), dot(tmp.xyz, camIn));

    vec3 cpj1 = camIn * c2.z + camRight * c2.x;
    vec3 cpm1 = camIn * c2.x - camRight * c2.z;

    vec3 cpj2 = camIn * c2.z + camUp * c2.y;
    vec3 cpm2 = camIn * c2.y - camUp * c2.z;
    
    vec2 d;
    d.x = length(cpj1);
    d.y = length(cpj2);

    vec2 dInv = vec2(1.0) / d;

    vec2 p = inSphere.w*inSphere.w * dInv;
    vec2 q = d - p;
    vec2 h = sqrt(p * q);
    //h = vec2(0.0);
    
    p *= dInv;
    h *= dInv;

    cpj1 *= p.x;
    cpm1 *= h.x;
    cpj2 *= p.y;
    cpm2 *= h.y;

    //vec3 testPos = objPos.xyz + cpj1 + cpm1;
    vec3 testPos = inSphere.xyz + vertex.objPos.xyz + cpj1 + cpm1;
    vec4 projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    vec2 mins = projPos.xy;
    vec2 maxs = projPos.xy;

    testPos -= 2.0 * cpm1;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    //testPos = objPos.xyz + cpj2 + cpm2;
    testPos = inSphere.xyz + vertex.objPos.xyz + cpj2 + cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    testPos -= 2.0 * cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    vec2 window = 2.0 / viewport.zw;

    gl_Position = vec4((mins + maxs) * 0.5, 0.0, 1.0);
    gl_PointSize = max((maxs.x - mins.x + 0.1) * window.x, (maxs.y - mins.y + 0.1) * window.y) * 0.5;
}
