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

vec4 colors[] = {
    vec4(0.0, 0.0, 1.0, 1.0),
    vec4(1.0, 0.0, 0.0, 1.0),
    vec4(0.0, 1.0, 0.0, 1.0),
    vec4(0.0, 0.0, 1.0, 1.0),
    vec4(1.0, 0.0, 0.0, 1.0),
    vec4(0.0, 1.0, 0.0, 1.0)
};

uniform vec4 viewport;

out vec4 quads[3];
out vec2 quadsVertices[3][4];
out vec4 quadsColor[3];
out int quadCount;

void main() {
    vec3 objPos = gl_Vertex.xyz; // position
    
    vec3 rotMat1 = gl_MultiTexCoord0.xyz; // rotMat1
    vec3 rotMat2 = gl_MultiTexCoord1.xyz; // rotMat2
    vec3 rotMat3 = gl_MultiTexCoord2.xyz; // rotMat3

    vec3 scale = gl_MultiTexCoord3.xyz; // scale

    int q = 0;
    for (int f = 0; f < 6; f++) {
        ivec4 face = faces[f];
        vec3 v0 = vertices[face.x];
        vec3 v1 = vertices[face.y];
        vec3 v2 = vertices[face.z];
        vec3 v3 = vertices[face.w];

        vec4 pv0 = gl_ModelViewProjectionMatrix * vec4(objPos + v0, 1.0);
        pv0 /= pv0.w;
        vec4 pv1 = gl_ModelViewProjectionMatrix * vec4(objPos + v1, 1.0);
        pv1 /= pv1.w;
        vec4 pv2 = gl_ModelViewProjectionMatrix * vec4(objPos + v2, 1.0);
        pv2 /= pv2.w;
        vec4 pv3 = gl_ModelViewProjectionMatrix * vec4(objPos + v3, 1.0);
        pv3 /= pv3.w;
        
        vec3 n = cross(pv1.xyz - pv0.xyz, pv3.xyz - pv0.xyz);
        vec4 color = vec4(1.0, 1.0, 0.0, 1.0);
        if (n.z < 0) {
            continue;
            //color.rgb = vec3(0.5, 0.5, 0.5);
        }

        vec2 mins = pv0.xy;
        vec2 maxs = pv0.xy;

        mins = min(mins, pv1.xy);
        maxs = max(maxs, pv1.xy);

        mins = min(mins, pv2.xy);
        maxs = max(maxs, pv2.xy);

        mins = min(mins, pv3.xy);
        maxs = max(maxs, pv3.xy);

        vec2 window = 2.0 / viewport.zw;
        quads[q] = vec4((mins + maxs) * 0.5, maxs - mins);
        quadsColor[q] = colors[f]; //color;
        quadsVertices[q][0] = pv0.xy;
        quadsVertices[q][1] = pv1.xy;
        quadsVertices[q][2] = pv2.xy;
        quadsVertices[q][3] = pv3.xy;
        q++;
    }

    quadCount = q;

    // write something to pass vertex
    gl_Position = vec4(0.5, 0.5, 0.0, 1.0);
}
