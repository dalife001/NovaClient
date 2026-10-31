#version 120
#define USE_FOG

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;
varying float dist;

void main(){
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if(c.a < 0.02) discard;
#ifdef USE_FOG
    c.rgb = applyFog(c.rgb, dist);
#endif
    gl_FragData[0] = c;
}
