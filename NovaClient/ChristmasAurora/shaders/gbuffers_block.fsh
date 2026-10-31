#version 120


#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
uniform int blockEntityId;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 worldPos;
varying vec3 worldN;
varying float dist;

void main(){
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if(c.a < 0.1) discard;
    float glowAdd = 0.0;
#ifdef CHRISTMAS_CHESTS
    if(blockEntityId == 10010){
        vec4 pl = presentLook(c.rgb, worldPos, worldN);
        c.rgb = pl.rgb;
        glowAdd = pl.a * 0.12;
    }
#endif
    c.rgb *= getLight(lmcoord);
    c.rgb += glowAdd;
    c.rgb = applyFogMist(c.rgb, dist, worldPos.y);
    gl_FragData[0] = c;
}
