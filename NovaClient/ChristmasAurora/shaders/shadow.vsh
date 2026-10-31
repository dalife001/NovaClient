#version 120
#include "/lib/settings.glsl"
varying vec2 texcoord;
varying vec4 glcolor;
void main(){
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 pos = ftransform();
#ifdef SHADOWS
    float f = 0.15 + length(pos.xy) * 0.85;
    pos.xy /= f;
#else
    pos = vec4(2.0, 2.0, 2.0, 1.0); // shadows off: nothing is rasterized
#endif
    gl_Position = pos;
}
