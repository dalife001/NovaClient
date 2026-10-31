#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform float viewWidth;
uniform float viewHeight;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform vec3 moonPosition;
uniform int moonPhase;
varying vec4 glcolor;
varying float starFlag;

#ifdef AURORA
vec3 aurora(vec3 d, float t){
    vec3 acc = vec3(0.0);
    for(int i = 0; i < AURORA_LAYERS; i++){
        float fi = float(i);
        vec2 p = d.xz / (d.y + 0.25) * (0.7 + 0.3 * fi) + vec2(fi * 7.3, fi * 3.1);
        float bend = vnoise(vec2(p.x * 0.6 + t * 0.05, fi)) * 2.0 + sin(p.x * 0.8 + t * 0.1) * 0.5;
        float v    = p.y - (0.6 + 0.5 * fi) - bend;
        float band = exp(-v * v * 2.5);
        float rays = 0.55 + 0.45 * vnoise(vec2(p.x * 7.0 + fi * 5.0, t * 0.4));
        float edge = clamp(v * 0.8 + 0.5, 0.0, 1.0);
        acc += auroraPalette(edge) * band * rays;
    }
    return acc * smoothstep(0.02, 0.3, d.y);
}
#endif

#ifdef SHOOTING_STARS
vec3 shootingStar(vec3 d, float t){
    float P = 20.0;
    float ct = floor(t / P);
    float ph = fract(t / P);
    if(ph > 0.05 || hash12(vec2(ct, 1.0)) < 0.45) return vec3(0.0);
    float p = ph / 0.05;
    vec3 s = normalize(vec3(hash12(vec2(ct, 2.0)) - 0.5, 0.35 + 0.5 * hash12(vec2(ct, 3.0)), hash12(vec2(ct, 4.0)) - 0.5));
    vec3 r = normalize(cross(s, vec3(hash12(vec2(ct, 5.0)) - 0.5, 0.3, hash12(vec2(ct, 6.0)) - 0.5)));
    vec3 a = normalize(s + r * max(p - 0.18, 0.0) * 0.5);
    vec3 b = normalize(s + r * p * 0.5);
    vec3 ab = b - a;
    float tt = clamp(dot(d - a, ab) / max(dot(ab, ab), 0.000001), 0.0, 1.0);
    vec3 q = d - a - ab * tt;
    return vec3(0.9, 0.95, 1.0) * exp(-dot(q, q) * 30000.0) * tt * sin(p * 3.14159) * 1.5;
}
#endif

#ifdef SANTA_SLEIGH
vec3 sleigh(vec3 d, float t){
    float period = mix(300.0, 120.0, festive());
    float ph = mod(t, period);
    if(ph > 30.0) return vec3(0.0);
    float p = ph / 30.0;
    float fade = smoothstep(0.0, 0.05, p) * (1.0 - smoothstep(0.95, 1.0, p));
    vec3 h = normalize(vec3(-1.0 + 2.0 * p, 0.45 + 0.25 * sin(p * 3.14159), 0.55));
    vec3 col = vec3(1.0, 0.1, 0.08) * smoothstep(0.99985, 0.99998, dot(d, h)) * 2.0;
    for(int k = 1; k < 5; k++){
        float pk = p - 0.012 * float(k);
        vec3 hk = normalize(vec3(-1.0 + 2.0 * pk, 0.45 + 0.25 * sin(pk * 3.14159), 0.55));
        col += vec3(1.0, 0.8, 0.35) * smoothstep(0.99995, 0.999995, dot(d, hk)) * (1.0 - 0.2 * float(k));
    }
    return col * fade;
}
#endif

void main(){
    vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec4 vp  = gbufferProjectionInverse * ndc;
    vec3 viewDir = normalize(vp.xyz / vp.w);
    vec3 d = normalize((gbufferModelViewInverse * vec4(viewDir, 0.0)).xyz);
    vec3 col;

    if(starFlag > 0.5){
        if(glcolor.a < 0.05) discard;
        col = glcolor.rgb * glcolor.a;
#ifdef TWINKLE_STARS
        vec3 cq = floor(d * 60.0);
        float h = hash12(cq.xz + cq.y * 31.7);
        float tw = 0.65 + 0.35 * sin(frameTimeCounter * (1.5 + h * 3.0) + h * 60.0);
        vec3 tint = h < 0.33 ? vec3(0.8, 0.9, 1.2) : (h < 0.66 ? vec3(1.1, 1.0, 0.85) : vec3(1.0));
        col *= tw * (1.0 + 0.3 * h) * mix(vec3(1.0), tint, 0.6);
#endif
    } else {
        float night = getNight();
        col = mix(fogColor, skyColor, smoothstep(0.0, 0.45, d.y));
        col += night * vec3(0.004, 0.012, 0.035) * (1.0 - rainStrength);

        if(night > 0.001 && d.y > 0.0){
#ifdef AURORA
            vec3 a = aurora(d, frameTimeCounter * AURORA_SPEED);
            col += a * 0.55 * AURORA_BRIGHTNESS * (1.0 + 0.4 * festive()) * night * (1.0 - rainStrength * 0.8);
#endif
            if(night > 0.6 && rainStrength < 0.5){
#ifdef SHOOTING_STARS
                col += shootingStar(d, frameTimeCounter);
#endif
#ifdef SANTA_SLEIGH
                col += sleigh(d, frameTimeCounter);
#endif
            }
        }

#ifdef MOON_HALO
        if(night > 0.001){
            vec3 md = normalize((gbufferModelViewInverse * vec4(moonPosition, 0.0)).xyz);
            float c = clamp(dot(d, md), -1.0, 1.0);
            float full = abs(float(moonPhase) - 4.0) / 4.0;
            float ang = acos(c);
            float glow = pow(max(c, 0.0), 24.0) * 0.5;
            float ring = exp(-pow((ang - 0.38) * 18.0, 2.0)) * 0.35;
            float up = smoothstep(-0.25, 0.1, md.y);
            float h = (glow + ring) * (0.35 + 0.65 * full) * night * up * (1.0 - rainStrength * 0.7);
            col += vec3(0.55, 0.70, 1.0) * h * MOON_HALO_BRIGHTNESS;
        }
#endif
    }
    gl_FragData[0] = vec4(col, 1.0);
}
