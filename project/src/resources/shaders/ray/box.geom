#version 430 core

in vec4 quads[][3];
in vec2 quadsVertices[][3][4];
in vec4 quadsColor[][3];
in int quadCount[];

out vec4 color;
out float depth;

layout(points) in;
layout(triangle_strip, max_vertices = 12) out;

void main() {
    for (int q = 0; q < quadCount[0]; q++) {
        vec4 quad = quads[0][q];
        
        float quadDepth = 10.0;
        if (q == 1) {
            quadDepth = 11.0;
        } else if (q == 2) {
            quadDepth = 12.0;
        }

        color = quadsColor[0][q];
        depth = quadDepth;
        /*gl_Position = vec4(quad.xy - 0.5 * quad.zw, 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quad.xy + vec2(0.5, -0.5) * quad.zw, 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quad.xy + vec2(-0.5, 0.5) * quad.zw, 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quad.xy + 0.5 * quad.zw, 0.0, 1.0);    
        EmitVertex();*/
        
        gl_Position = vec4(quadsVertices[0][q][0], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quadsVertices[0][q][1], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quadsVertices[0][q][2], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(quadsVertices[0][q][3], 0.0, 1.0);    
        EmitVertex();

        EndPrimitive();
    }
}
