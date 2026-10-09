#version 120

// Lotos Cinema, stages 2-3 (part 2): volumetric light shafts, screen-space, from the sun by day and the moon by night.

uniform sampler2D colortex0;
uniform sampler2D depthtex0;

uniform mat4 gbufferProjection;
uniform vec3 shadowLightPosition;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform float viewWidth;
uniform float viewHeight;
uniform float rainStrength;

varying vec2 texcoord;

#define GODRAYS 0.55            //[0.0 0.25 0.4 0.55 0.8 1.2]

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;

    vec4 lp = gbufferProjection * vec4(shadowLightPosition, 1.0);
    if (lp.w <= 0.0 || GODRAYS <= 0.0) {
        gl_FragData[0] = vec4(color, 1.0);
        return;
    }
    vec2 lightUV = lp.xy / lp.w * 0.5 + 0.5;

    float dayness = smoothstep(-0.1, 0.2, dot(normalize(sunPosition), normalize(upPosition)));
    vec3 tint = mix(vec3(0.55, 0.66, 1.0), vec3(1.0, 0.82, 0.60), dayness);
    float power = mix(0.45, 1.0, dayness) * (1.0 - 0.8 * rainStrength);

    // march toward the light; only sky pixels (and the light itself) feed the shafts
    const int STEPS = 28;
    vec2 delta = (lightUV - texcoord) / float(STEPS) * 0.9;
    vec2 uv = texcoord + delta * hash(texcoord * vec2(viewWidth, viewHeight));
    float acc = 0.0;
    float weight = 1.0;
    for (int i = 0; i < STEPS; i++) {
        uv += delta;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
        float sky = texture2D(depthtex0, uv).r >= 0.9999 ? 1.0 : 0.0;
        acc += sky * weight;
        weight *= 0.965;
    }
    acc /= float(STEPS);

    float fade = clamp(1.0 - length(lightUV - 0.5) * 0.85, 0.0, 1.0);
    // capped, and weaker on the sky itself, so the horizon never burns out
    float onSky = texture2D(depthtex0, texcoord).r >= 0.9999 ? 0.25 : 1.0;
    float add = min(acc * fade * power * GODRAYS * 0.5, 0.28) * onSky;
    color += tint * add;

    gl_FragData[0] = vec4(color, 1.0);
}
