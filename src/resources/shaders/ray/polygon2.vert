#version 430 compatibility

// camera
uniform vec3 camIn;
uniform vec3 camUp;
uniform vec3 camRight;
// viewport
uniform vec4 viewport;
// SAS/SES
uniform bool sas;
uniform float probeRadius;
// cavity clipping
uniform bool clipCavities;
uniform uint surfaceLabel;

/*
// ray-casting
out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out float radius;
out float RR;
out vec4 color;
// polygon
out flat uint index; // atom
out flat uint label;
out flat uint circleStart;
out flat uint circleEnd;
*/

out VertexData {
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

    // TODO: fix computation of bounding box
    // projected camera vector
    vec3 c2 = vec3(dot(vertex.camPos.xyz, camRight), dot(vertex.camPos.xyz, camUp), dot(vertex.camPos.xyz, camIn));

    vec3 cpj1 = camIn * c2.z + camRight * c2.x;
    vec3 cpm1 = camIn * c2.x - camRight * c2.z;

    vec3 cpj2 = camIn * c2.z + camUp * c2.y;
    vec3 cpm2 = camIn * c2.y - camUp * c2.z;
    
    vec2 d;
    d.x = length(cpj1);
    d.y = length(cpj2);

    vec2 dInv = vec2(1.0) / d;

    vec2 p = vertex.RR * dInv;
    vec2 q = d - p;
    vec2 h = sqrt(p * q);
    //h = vec2(0.0);
    
    p *= dInv;
    h *= dInv;

    cpj1 *= p.x;
    cpm1 *= h.x;
    cpj2 *= p.y;
    cpm2 *= h.y;

    vec3 testPos = vertex.objPos.xyz + cpj1 + cpm1;
    vec4 projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    vec2 mins = projPos.xy;
    vec2 maxs = projPos.xy;

    testPos -= 2.0 * cpm1;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    testPos = vertex.objPos.xyz + cpj2 + cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    testPos -= 2.0 * cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    vertex.color = gl_Color;

    vec2 window = 2.0 / viewport.zw;

    gl_Position = vec4((mins + maxs) * 0.5, 0.0, 1.0);
    gl_PointSize = max((maxs.x - mins.x + 0.1) * window.x, (maxs.y - mins.y + 0.1) * window.y) * 0.5;
}
