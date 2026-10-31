#version 120


#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
uniform vec4 entityColor;
uniform int entityId;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 worldPos;
varying vec3 worldN;
varying float dist;

void main(){
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if(c.a < 0.1) discard;
#if defined(OVERWORLD) && defined(MOB_SNOW)
    if(entityId != 10100){
        // snow dusts upward-facing parts (backs, heads) of mobs that stand under open sky
        float top     = smoothstep(0.55, 0.85, worldN.y);
        float exposed = smoothstep(0.75, 0.93, lmcoord.y) * (1.0 - smoothstep(0.55, 0.85, lmcoord.x));
        vec2  cell    = floor(texcoord * 96.0);
        float m = top * exposed * step(hash12(cell), MOB_SNOW_AMOUNT);
        vec3 snow = vec3(0.92, 0.95, 1.0) * (0.94 + 0.06 * hash12(cell + 7.0));
        c.rgb = mix(c.rgb, snow, m);
    }
#endif
    c.rgb = mix(c.rgb, entityColor.rgb, entityColor.a);
    c.rgb *= getLight(lmcoord);
    c.rgb = applyFogMist(c.rgb, dist, worldPos.y);
    gl_FragData[0] = c;
}
