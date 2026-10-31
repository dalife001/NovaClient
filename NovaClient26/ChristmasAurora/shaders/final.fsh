#version 120
#include "/lib/settings.glsl"

#ifdef BLOOM
const bool colortex0MipmapEnabled = true;
#if BLOOM_QUALITY == 1
  #define BLOOM_LODS 3
#else
  #define BLOOM_LODS 5
#endif
#endif

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform ivec2 eyeBrightnessSmooth;
varying vec2 texcoord;

float hash(vec2 p){
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

#ifdef BLOOM
vec3 getBloom(vec2 uv){
    vec3 sum = vec3(0.0);
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    for(int i = 0; i < BLOOM_LODS; i++){
        float lod = float(i) + 2.0;
        vec2 t = px * exp2(lod);
        vec3 s = texture2D(colortex0, uv, lod).rgb;
#if BLOOM_QUALITY > 1
        s = (s * 2.0
           + texture2D(colortex0, uv + vec2( t.x, 0.0), lod).rgb
           + texture2D(colortex0, uv + vec2(-t.x, 0.0), lod).rgb
           + texture2D(colortex0, uv + vec2(0.0,  t.y), lod).rgb
           + texture2D(colortex0, uv + vec2(0.0, -t.y), lod).rgb) / 6.0;
#endif
        sum += max(s - 0.72, 0.0) / (1.0 + 0.3 * float(i));
    }
    vec3 haze = texture2D(colortex0, uv, 5.0).rgb * 0.05;
    return (sum * 1.6 + haze) * BLOOM_INTENSITY;
}
#endif

#if SNOW_FLAKES > 0
float flakeLayer(vec2 uv, float scale, float speed, float seed){
    uv.x *= viewWidth / viewHeight;
    uv *= scale;
    uv.y += frameTimeCounter * speed;
    uv.x += sin(frameTimeCounter * 0.5 + uv.y * 0.7 + seed) * 0.3;
    vec2 cell = floor(uv + seed * 17.0);
    vec2 f = fract(uv) - 0.5;
    float h = hash(cell);
    vec2 off = (vec2(h, hash(cell + 1.7)) - 0.5) * 0.5;
    float r = 0.05 + 0.05 * h;
    float keep = step(0.55, hash(cell + 4.2));
    return keep * (1.0 - smoothstep(r * 0.6, r, length(f - off)));
}
#endif

void main(){
    vec3 c = texture2D(colortex0, texcoord).rgb;

#ifdef BLOOM
    c += getBloom(texcoord);
#endif

#ifdef COLOR_GRADE
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, 1.08);
    c *= mix(vec3(0.97, 1.0, 1.05), vec3(1.05, 1.0, 0.95), smoothstep(0.2, 0.9, l));
#endif

    if(FROST_VIGNETTE > 0.0){
    vec2 q = (texcoord - 0.5) * vec2(viewWidth / viewHeight, 1.0);
    float v = smoothstep(0.45, 1.0, length(q));
    c = mix(c, vec3(0.80, 0.90, 1.0), v * float(FROST_VIGNETTE) * 0.25);
    }

#if SNOW_FLAKES > 0
    float expo = smoothstep(0.55, 0.95, float(eyeBrightnessSmooth.y) / 240.0);
    float fl = 0.0;
    for(int i = 0; i < SNOW_FLAKES; i++){
        float fi = float(i);
        fl = max(fl, flakeLayer(texcoord, 14.0 + fi * 9.0, 0.25 + fi * 0.15, fi * 3.3));
    }
    c = mix(c, vec3(1.0), fl * expo * (0.55 + 0.4 * rainStrength));
#endif

    gl_FragData[0] = vec4(c, 1.0);
}
