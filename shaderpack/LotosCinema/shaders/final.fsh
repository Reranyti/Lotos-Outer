#version 120

// Lotos Cinema, final pass: bloom, filmic tonemap, moody grade (the 13th-ending palette), vignette, grain.

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;

varying vec2 texcoord;

#define EXPOSURE 0.70           //[0.5 0.6 0.7 0.85 1.0 1.2]
#define GRADE_CONTRAST 1.30     //[0.8 1.0 1.1 1.2 1.3 1.4 1.5]
#define GRADE_SATURATION 0.78   //[0.5 0.6 0.7 0.78 0.9 1.0 1.1]
#define SHADOW_TINT 0.55        //[0.0 0.25 0.35 0.55 0.7 0.9]
#define SKY_MOOD 0.6            //[0.0 0.3 0.6 0.85 1.0]
#define BLOOM_STRENGTH 0.45     //[0.0 0.25 0.35 0.45 0.6 0.8 1.0]
#define VIGNETTE 0.55           //[0.0 0.3 0.45 0.55 0.7 0.9]
#define FILM_GRAIN 0.05         //[0.0 0.03 0.05 0.08 0.12]
#define CHROMA 0.0015           //[0.0 0.0008 0.0015 0.003]
#define LETTERBOX 0             //[0 1]

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 fetch(vec2 uv) {
    return texture2D(colortex0, clamp(uv, 0.001, 0.999)).rgb;
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 px = vec2(1.0 / viewWidth, 1.0 / viewHeight);
    vec2 d = texcoord - 0.5;
    float r2 = dot(d, d);

    vec2 ca = d * CHROMA * (1.0 + r2 * 4.0);
    vec3 col;
    col.r = fetch(texcoord + ca).r;
    col.g = fetch(texcoord).g;
    col.b = fetch(texcoord - ca).b;

    // bloom from the genuinely bright parts only
    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float a = float(i) * 2.39996;
        float rad = sqrt(float(i) + 0.5) * 6.5;
        bloom += max(fetch(texcoord + vec2(cos(a), sin(a)) * rad * px) - 0.72, 0.0);
    }
    col += bloom * (BLOOM_STRENGTH * 0.16);

    // the sky is pulled toward a cold violet-teal overcast so day does not look like a postcard
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float sky = texture2D(depthtex0, texcoord).r >= 0.9999 ? 1.0 : 0.0;
    col = mix(col, vec3(luma) * vec3(0.70, 0.84, 1.12) * 0.82, sky * SKY_MOOD);

    // tonemap in linear light: this is what gives deep blacks instead of a milky lift
    col = pow(max(col, 0.0), vec3(2.2));
    col = aces(col * EXPOSURE * 1.35);
    col = pow(col, vec3(1.0 / 2.2));

    luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, GRADE_SATURATION);
    col = max((col - 0.45) * GRADE_CONTRAST + 0.45, 0.0);

    luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float sh = 1.0 - smoothstep(0.0, 0.55, luma);
    col = mix(col, col * vec3(0.72, 0.80, 1.22) + vec3(0.010, 0.0, 0.030), sh * SHADOW_TINT);
    float hi = smoothstep(0.5, 0.95, luma);
    col = mix(col, col * vec3(1.10, 0.96, 0.92), hi * 0.4);

    col *= 1.0 - VIGNETTE * smoothstep(0.22, 0.80, length(d) * 1.4);

    float g = hash(texcoord * vec2(viewWidth, viewHeight) + fract(frameTimeCounter) * vec2(37.0, 17.0)) - 0.5;
    col += g * FILM_GRAIN * (1.0 - luma * 0.5);

#if LETTERBOX == 1
    float shown = (viewWidth / 2.39) / viewHeight;
    float bar = max(0.0, (1.0 - shown) * 0.5);
    if (texcoord.y < bar || texcoord.y > 1.0 - bar) col = vec3(0.0);
#endif

    gl_FragColor = vec4(max(col, 0.0), 1.0);
}
