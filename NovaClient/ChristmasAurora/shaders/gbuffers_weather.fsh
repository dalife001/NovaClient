#version 120


#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main(){
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if(c.a < 0.02) discard;
#ifdef SNOW_WEATHER
    c.rgb = vec3(0.95, 0.97, 1.0);
#endif
    gl_FragData[0] = c;
}
