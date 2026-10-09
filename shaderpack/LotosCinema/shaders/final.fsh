#version 120

// Lotos Cinema, stage 1: final grade. Cold violet shadows, warm highlights, soft bloom, vignette, light grain.

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;

varying vec2 texcoord;

#define GRADE_CONTRAST 1.20     //[0.8 0.9 1.0 1.1 1.2 1.3 1.4 1.5]
#define GRADE_SATURATION 0.90   //[0.5 0.6 0.7 0.8 0.9 1.0 1.1 1.2]
#define SHADOW_TINT 0.35        //[0.0 0.15 0.25 0.35 0.5 0.7]
#define BLOOM_STRENGTH 0.35     //[0.0 0.15 0.25 0.35 0.5 0.75 1.0]
#define VIGNETTE 0.45           //[0.0 0.2 0.3 0.45 0.6 0.8]
#define FILM_GRAIN 0.04         //[0.0 0.02 0.04 0.06 0.1]
#define CHROMA 0.0015           //[0.0 0.0008 0.0015 0.003]

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 fetch(vec2 uv) {
    return texture2D(colortex0, clamp(uv, 0.001, 0.999)).rgb;
}

void main() {
    vec2 px = vec2(1.0 / viewWidth, 1.0 / viewHeight);
    vec2 d = texcoord - 0.5;
    float r2 = dot(d, d);

    // slight chromatic split, stronger toward the edges
    vec2 ca = d * CHROMA * (1.0 + r2 * 4.0);
    vec3 col;
    col.r = fetch(texcoord + ca).r;
    col.g = fetch(texcoord).g;
    col.b = fetch(texcoord - ca).b;

    // bloom: golden-angle taps over the bright parts only
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float a = float(i) * 2.39996;
        float rad = sqrt(float(i) + 0.5) * 6.5;
        vec3 s = fetch(texcoord + vec2(cos(a), sin(a)) * rad * px);
        bloom += max(s - 0.6, 0.0);
    }
    col += bloom * (BLOOM_STRENGTH * 0.18);

    // grade
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, GRADE_SATURATION);
    col = max((col - 0.42) * GRADE_CONTRAST + 0.42, 0.0);

    luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float sh = 1.0 - smoothstep(0.0, 0.5, luma);
    col = mix(col, col * vec3(0.78, 0.82, 1.18) + vec3(0.008, 0.0, 0.025), sh * SHADOW_TINT);
    float hi = smoothstep(0.55, 1.0, luma);
    col = mix(col, col * vec3(1.08, 0.97, 0.94), hi * 0.35);

    // vignette
    col *= 1.0 - VIGNETTE * smoothstep(0.25, 0.85, length(d) * 1.4);

    // grain, lighter in the bright parts
    float g = hash(texcoord * vec2(viewWidth, viewHeight) + fract(frameTimeCounter) * vec2(37.0, 17.0)) - 0.5;
    col += g * FILM_GRAIN * (1.0 - luma * 0.5);

    gl_FragColor = vec4(max(col, 0.0), 1.0);
}
