uniform sampler2D lightmap;
uniform vec3 sunPosition;
uniform vec3 upPosition;
#if FESTIVE_MODE == 1
uniform ivec3 currentDate;
#endif
uniform float frameTimeCounter;
uniform float rainStrength;
uniform int isEyeInWater;

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float getNight(){
    float sunUp = dot(normalize(sunPosition), normalize(upPosition));
    return 1.0 - smoothstep(-0.25, 0.05, sunUp);
}

vec3 getLight(vec2 lm){
    vec3 l = texture2D(lightmap, lm).rgb;
    float warm = WARM_LIGHT * smoothstep(0.15, 0.9, lm.x) * (1.0 - lm.y);
    l *= mix(vec3(1.0), vec3(1.15, 0.92, 0.72), warm);
    return l * BRIGHTNESS;
}

vec3 applyFog(vec3 col, float dist){
    float f = clamp((dist - gl_Fog.start) * gl_Fog.scale, 0.0, 1.0);
    return mix(col, gl_Fog.color.rgb, f);
}

// vanilla fog + low-lying frosty mist (overworld only; OVERWORLD is set by the world0 folder)
vec3 applyFogMist(vec3 col, float dist, float wy){
    vec3 o = applyFog(col, dist);
#if defined(OVERWORLD) && defined(GROUND_MIST)
    if(isEyeInWater == 0){
        float h = exp(-max(wy - float(MIST_HEIGHT), 0.0) * 0.07);
        float m = (1.0 - exp(-dist * 0.012 * MIST_DENSITY * (1.0 + rainStrength))) * h;
        vec3 mc = mix(vec3(0.78, 0.86, 0.98), vec3(0.10, 0.14, 0.24), getNight());
        o = mix(o, mc, clamp(m, 0.0, 0.8));
    }
#endif
    return o;
}

// 1.0 during the festive period (20-26 Dec in auto mode), or always in "Always" mode
float festive(){
#if FESTIVE_MODE == 2
    return 1.0;
#elif FESTIVE_MODE == 1
    return (currentDate.y == 12 && currentDate.z >= 20 && currentDate.z <= 26) ? 1.0 : 0.0;
#else
    return 0.0;
#endif
}

float vnoise(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

vec3 auroraPalette(float e){
#if AURORA_COLOR == 0
    return mix(vec3(0.10, 1.00, 0.45), vec3(0.65, 0.25, 0.95), e);
#elif AURORA_COLOR == 1
    return mix(vec3(0.10, 1.00, 0.35), vec3(1.00, 0.15, 0.22), e);
#else
    return mix(vec3(0.20, 0.95, 1.00), vec3(0.40, 0.30, 1.00), e);
#endif
}

// red present with gold ribbon; rgb = colour, a = ribbon mask
vec4 presentLook(vec3 base, vec3 wp, vec3 n){
    float lum = dot(base, vec3(0.299, 0.587, 0.114));
    vec3 red  = vec3(0.85, 0.08, 0.10) * (0.55 + 0.9 * lum);
    vec3 gold = vec3(1.0, 0.78, 0.22) * (0.75 + 0.5 * lum);
    float bx = 1.0 - smoothstep(0.055, 0.075, abs(fract(wp.x) - 0.5));
    float bz = 1.0 - smoothstep(0.055, 0.075, abs(fract(wp.z) - 0.5));
    float top = step(0.7, abs(n.y));
    float rib = top > 0.5 ? max(bx, bz) : (abs(n.z) > abs(n.x) ? bx : bz);
    float sp = step(0.97, hash12(floor(wp.xz * 20.0 + wp.y * 20.0) + floor(frameTimeCounter * 3.0)));
    return vec4(mix(red, gold + sp * 0.5, rib), rib);
}
