#version 120


#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
uniform vec4 entityColor;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 worldPos;
varying vec3 worldN;
varying float dist;
#ifdef IS_WATER
varying float blockId;
#endif

void main(){
    vec4 c = texture2D(texture, texcoord) * glcolor;
    if(c.a < 0.1) discard;
    c.rgb = mix(c.rgb, entityColor.rgb, entityColor.a);
    float winGlow = 0.0;
    vec3 winCol = vec3(0.0);
    vec3 refl = vec3(0.0);
#ifdef IS_WATER
    int id = int(blockId + 0.5);
    vec3 n = normalize(worldN);
    if(id == 10020){
  #ifdef FROSTED_GLASS
        // frost creeps in from the edges of each glass face
        vec2 q = abs(n.y) > 0.7 ? fract(worldPos.xz) : (abs(n.x) > abs(n.z) ? fract(worldPos.zy) : fract(worldPos.xy));
        float e  = min(min(q.x, 1.0 - q.x), min(q.y, 1.0 - q.y));
        float nz = hash12(floor(worldPos.xz * 16.0) + floor(worldPos.y * 16.0) * 7.0);
        float frost = 1.0 - smoothstep(0.02, 0.2, e + (nz - 0.5) * 0.12);
        c.rgb = mix(c.rgb, vec3(0.85, 0.93, 1.0), frost * 0.8);
        c.a = max(c.a, frost * 0.85);
  #endif
  #ifdef WINDOW_GLOW
        winGlow = clamp(smoothstep(0.45, 0.85, lmcoord.x) * (0.1 + 0.9 * getNight()) * WINDOW_BRIGHTNESS, 0.0, 1.0);
        winCol = mix(vec3(1.0, 0.72, 0.32), c.rgb * 1.5, 0.35);
        c.a = mix(c.a, max(c.a, 0.8), winGlow * 0.6);
  #endif
    } else if(id == 10031){
  #ifdef ICE_SPARKLE
        float sp = step(0.985, hash12(floor(worldPos.xz * 16.0) + floor(worldPos.y * 16.0) * 13.7 + floor(frameTimeCounter * 2.0)));
        c.rgb += sp * 0.5;
  #endif
    } else {
  #ifdef COLD_WATER
        c.rgb *= vec3(0.78, 0.95, 1.08);
  #endif
  #if defined(OVERWORLD) && defined(AURORA_REFLECTION) && defined(AURORA)
        float nt = getNight();
        if(nt > 0.01 && n.y > 0.7){
            float t = frameTimeCounter * AURORA_SPEED;
            float band = vnoise(worldPos.xz * 0.03 + vec2(t * 0.02, 0.0));
            float rays = 0.6 + 0.4 * vnoise(vec2(worldPos.x * 0.5 + t * 0.1, worldPos.z * 0.05));
            float v = smoothstep(0.45, 0.75, band) * rays;
            float skyE = smoothstep(0.7, 0.95, lmcoord.y);
            refl = auroraPalette(clamp(band * 1.5 - 0.4, 0.0, 1.0)) * v * nt * skyE * 0.35 * AURORA_BRIGHTNESS * (1.0 - rainStrength * 0.8);
        }
  #endif
    }
#endif
    c.rgb *= getLight(lmcoord);
    c.rgb = mix(c.rgb, winCol * 1.1, clamp(winGlow * 0.55, 0.0, 1.0));
    c.rgb += refl;
    c.rgb = applyFogMist(c.rgb, dist, worldPos.y);
    gl_FragData[0] = c;
}
