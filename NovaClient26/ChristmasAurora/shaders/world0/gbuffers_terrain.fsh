#version 120

#define OVERWORLD
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
uniform sampler2D texture;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 worldPos;
varying vec3 worldN;
varying vec3 shadowPos;
varying float blockId;
varying float dist;

#ifdef SHADOWS
uniform sampler2D shadowtex0;
uniform vec3 shadowLightPosition;
uniform mat4 gbufferModelViewInverse;

float shadowVis(vec3 sp, float ndl){
    float f = 0.15 + length(sp.xy) * 0.85;
    vec3 c = vec3(sp.xy / f, sp.z) * 0.5 + 0.5;
    if(c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
    float bias = 0.0006 + 0.0015 * (1.0 - clamp(ndl, 0.0, 1.0));
    float texel = 1.0 / float(shadowMapResolution);
    float vis = 0.0;
#if SHADOW_QUALITY == 0
    vis = texture2D(shadowtex0, c.xy).r < c.z - bias ? 0.0 : 1.0;
#elif SHADOW_QUALITY == 1
    for(int x = 0; x < 2; x++){
        for(int y = 0; y < 2; y++){
            vec2 o = (vec2(float(x), float(y)) - 0.5) * texel;
            vis += texture2D(shadowtex0, c.xy + o).r < c.z - bias ? 0.0 : 0.25;
        }
    }
#else
    for(int x = -1; x <= 1; x++){
        for(int y = -1; y <= 1; y++){
            vec2 o = vec2(float(x), float(y)) * texel;
            vis += texture2D(shadowtex0, c.xy + o).r < c.z - bias ? 0.0 : 1.0 / 9.0;
        }
    }
#endif
    return vis * smoothstep(0.0, 0.15, ndl);
}
#endif

void main(){
    vec4 tex = texture2D(texture, texcoord);
    if(tex.a < 0.1) discard;
    vec3 col = tex.rgb * glcolor.rgb;
    float night = getNight();
    float snowMask = 0.0;
    int id = int(blockId + 0.5);
    bool isLeaf = (id == 10001 || id == 10002);
    float lum0 = dot(tex.rgb, vec3(0.299, 0.587, 0.114));

#ifdef CHRISTMAS_CHESTS
    if(id == 10050) col = presentLook(col, worldPos, worldN).rgb;     // barrels
#endif
#ifdef CANDY_FENCES
    if(id == 10040){
        float st = step(0.5, fract((worldPos.x + worldPos.y + worldPos.z) * 2.5));
        col = mix(vec3(0.85, 0.08, 0.10), vec3(0.95), st) * (0.55 + 0.7 * lum0);
    }
#endif

#ifdef SNOW_COVER
    // snow on upward faces that can see the sky; melts near torches. work only happens where snow can be.
    float top = smoothstep(0.6, 0.9, worldN.y);
  #ifdef FLUFFY_LEAF_SNOW
    if(isLeaf) top = max(top, smoothstep(-0.3, 0.4, worldN.y) * 0.7);
  #endif
    float exposed = smoothstep(0.80, 0.93, lmcoord.y) * (1.0 - smoothstep(0.55, 0.85, lmcoord.x));
    float sm = top * exposed;
    if(sm > 0.01){
        vec2 cell = floor(worldPos.xz * 8.0 + vec2(worldPos.y * 8.0, worldPos.y * 3.0));
        snowMask = sm * step(hash12(cell), SNOW_COVERAGE * mix(0.7, 1.0, smoothstep(0.6, 0.9, worldN.y)));
        vec3 snow = vec3(0.92, 0.95, 1.0) * (0.94 + 0.06 * hash12(cell + 7.0));
  #ifdef SNOW_SPARKLE
        vec2 sc = floor(worldPos.xz * 16.0);
        snow += step(0.985, hash12(sc + floor(frameTimeCounter * 2.0))) * 0.6;
  #endif
        float ao = max(max(glcolor.r, glcolor.g), glcolor.b);
        col = mix(col, snow * ao, snowMask);
    }
#endif

    vec3 light = getLight(lmcoord);

#ifdef SHADOWS
    vec3 L = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
    float ndl = dot(normalize(worldN), L);
    float vis = shadowVis(shadowPos, ndl);
    float skyE = smoothstep(0.55, 0.95, lmcoord.y);
    light *= mix(1.0, mix(1.0 - SHADOW_STRENGTH, 1.0, vis), skyE);
#endif

    col *= light;

#ifdef TREE_LIGHTS
  #ifdef LIGHTS_ALL_TREES
    bool leaf = isLeaf;
  #else
    bool leaf = (id == 10001);
  #endif
    if(leaf){
        vec3 p = worldPos * 3.0 + 0.5;
        vec3 cl = floor(p);
        float present = step(1.0 - LIGHTS_DENSITY * (1.0 + 0.4 * festive()), hash12(cl.xz + cl.y * 17.3));
        if(present > 0.5){
            vec3 f = fract(p) - 0.5;
            vec3 off = (vec3(hash12(cl.xy + 3.7), hash12(cl.yz + 9.1), hash12(cl.xz + 5.3)) - 0.5) * 0.35;
            float d = length(f - off);
            float k = hash12(cl.xz + cl.y * 5.7 + 11.0);
            vec3 lc = k < 0.25 ? vec3(1.0, 0.15, 0.12)
                    : k < 0.50 ? vec3(0.15, 1.0, 0.30)
                    : k < 0.70 ? vec3(1.0, 0.80, 0.30)
                    : k < 0.85 ? vec3(0.25, 0.50, 1.0)
                    :            vec3(1.0, 0.95, 0.85);
            float tw = 0.7 + 0.3 * sin(frameTimeCounter * (2.0 + k * 4.0) + k * 40.0);
            float s  = LIGHTS_BRIGHTNESS * (0.5 + 0.5 * night) * (1.0 - snowMask);
            float core = 1.0 - smoothstep(0.07, 0.16, d);
            float glow = (1.0 - smoothstep(0.10, 0.45, d)) * 0.18;
            col = mix(col, lc * 1.6 * tw, clamp(core * s, 0.0, 1.0));
            col += lc * glow * tw * s;
        }
    }
#endif

#ifdef LANTERN_GLOW
    if(id >= 10021 && id <= 10023){
        float e = smoothstep(0.5, 0.85, lum0);         // bright pixels glow
        if(id == 10023) e = 0.6 + 0.4 * e;             // glowstone-type blocks glow all over
        vec3 tint = (id == 10022) ? vec3(0.50, 0.85, 1.10) : vec3(1.10, 0.90, 0.65);
        float fl = 0.92 + 0.08 * sin(frameTimeCounter * 7.0 + hash12(floor(worldPos.xz)) * 30.0);
        col += tex.rgb * tint * e * LANTERN_BRIGHTNESS * fl * 0.9;
    }
#endif

#ifdef ICE_SPARKLE
    if(id == 10030){
        col += step(0.985, hash12(floor(worldPos.xz * 16.0) + floor(worldPos.y * 16.0) * 13.7 + floor(frameTimeCounter * 2.0))) * 0.5;
    }
#endif

    col = applyFogMist(col, dist, worldPos.y);
    gl_FragData[0] = vec4(col, tex.a * glcolor.a);
}
