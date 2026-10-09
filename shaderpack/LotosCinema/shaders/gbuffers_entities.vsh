#version 120

// Lotos Cinema, stage 4: geometry pass for blocks (copies of this file serve block entities, entities and the hand).
#define USE_BLOCK_IDS 0

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 vpos;
varying float blockId;

#if USE_BLOCK_IDS == 1
attribute vec4 mc_Entity;
#endif

void main() {
    gl_Position = ftransform();
    vpos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
#if USE_BLOCK_IDS == 1
    blockId = mc_Entity.x;
#else
    blockId = 0.0;
#endif
}
