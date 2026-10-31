#version 120
#define USE_BLOCKID
#include "/lib/settings.glsl"
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
#ifdef SHADOWS
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
#endif
#ifdef USE_BLOCKID
attribute vec4 mc_Entity;
varying float blockId;
#endif
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 worldPos;
varying vec3 worldN;
varying vec3 shadowPos;
varying float dist;

void main(){
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord  = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor  = gl_Color;
    vec4 vp  = gl_ModelViewMatrix * gl_Vertex;
    vec3 wn  = mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal);
    worldN   = wn;
    worldPos = (gbufferModelViewInverse * vp).xyz + cameraPosition;
    dist     = length(vp.xyz);
    shadowPos = vec3(0.0);
#ifdef SHADOWS
    vec4 feet = gbufferModelViewInverse * vp;
    feet.xyz += wn * 0.08;
    shadowPos = (shadowProjection * (shadowModelView * feet)).xyz;
#endif
#ifdef USE_BLOCKID
    blockId = mc_Entity.x;
#endif
    gl_Position = gl_ProjectionMatrix * vp;
}
