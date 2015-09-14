#version 430 compatibility

uniform vec3 camIn;
uniform vec3 camUp;
uniform vec3 camRight;
uniform vec4 viewport;
uniform float probeRadius;
uniform bool sas;

out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out float radius;
out float RR;
out vec4 color;
out flat uint index;

void main() {
    objPos = vec4(gl_Vertex.xyz, 1.0);
    radius = gl_Vertex.w;
    if (sas) {
        radius += probeRadius;
    }
    RR = radius * radius;

    index = uint(floor(gl_MultiTexCoord0.x));

    // calculate camera position
    camPos = gl_ModelViewMatrixInverse[3]; // (C) by Christoph
    camPos.xyz -= objPos.xyz; // cam pos to glyph space

    lightPos = gl_ModelViewMatrixInverse * normalize(gl_LightSource[0].position);

    // camera coordinate system in object space
    /*vec4 tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[2];
    vec3 camIn = normalize(tmp.xyz);
    tmp = gl_ModelViewMatrixInverse[3] + gl_ModelViewMatrixInverse[1];
    vec3 camUp = tmp.xyz;
    vec3 camRight = normalize(cross(camIn, camUp));
    camUp = cross(camIn, camRight);*/

    // TODO: fix computation of bounding box
    // projected camera vector
    vec3 c2 = vec3(dot(camPos.xyz, camRight), dot(camPos.xyz, camUp), dot(camPos.xyz, camIn));

    vec3 cpj1 = camIn * c2.z + camRight * c2.x;
    vec3 cpm1 = camIn * c2.x - camRight * c2.z;

    vec3 cpj2 = camIn * c2.z + camUp * c2.y;
    vec3 cpm2 = camIn * c2.y - camUp * c2.z;
    
    vec2 d;
    d.x = length(cpj1);
    d.y = length(cpj2);

    vec2 dInv = vec2(1.0) / d;

    vec2 p = RR * dInv;
    vec2 q = d - p;
    vec2 h = sqrt(p * q);
    //h = vec2(0.0);
    
    p *= dInv;
    h *= dInv;

    cpj1 *= p.x;
    cpm1 *= h.x;
    cpj2 *= p.y;
    cpm2 *= h.y;

    vec3 testPos = objPos.xyz + cpj1 + cpm1;
    vec4 projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    vec2 mins = projPos.xy;
    vec2 maxs = projPos.xy;

    testPos -= 2.0 * cpm1;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    testPos = objPos.xyz + cpj2 + cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    testPos -= 2.0 * cpm2;
    projPos = gl_ModelViewProjectionMatrix * vec4(testPos, 1.0);
    projPos /= projPos.w;
    mins = min(mins, projPos.xy);
    maxs = max(maxs, projPos.xy);

    color = gl_Color;

    vec2 window = 2.0 / viewport.zw;

    gl_Position = vec4((mins + maxs) * 0.5, 0.0, 1.0);
    gl_PointSize = max((maxs.x - mins.x + 0.1) * window.x, (maxs.y - mins.y + 0.1) * window.y) * 0.5;
}
