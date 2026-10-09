#version 120

// Lotos Cinema, stages 2-3 (part 1): sun/moon shadows, ambient occlusion and distance haze.
// Everything is deferred: normals are rebuilt from the depth buffer, so vanilla geometry programs stay untouched.

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform sampler2D shadowtex0;

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 shadowLightPosition;
uniform float near;
uniform float far;
uniform float viewWidth;
uniform float viewHeight;
uniform float rainStrength;

varying vec2 texcoord;

const int shadowMapResolution = 2048;
const float shadowDistance = 80.0;

#define SHADOW_STRENGTH 0.45    //[0.0 0.25 0.35 0.45 0.6 0.75]
#define AO_STRENGTH 0.55        //[0.0 0.25 0.4 0.55 0.75 1.0]
#define HAZE_DENSITY 0.35       //[0.0 0.2 0.35 0.5 0.75 1.0]

vec3 viewPos(vec2 uv, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

float linearDepth(float d) {
    return (2.0 * near * far) / (far + near - (d * 2.0 - 1.0) * (far - near));
}

float depthAt(vec2 uv) {
    return texture2D(depthtex0, clamp(uv, 0.001, 0.999)).r;
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
    float depth = depthAt(texcoord);
    if (depth >= 0.9999) {
        gl_FragData[0] = vec4(color, 1.0);
        return;
    }

    vec2 px = vec2(1.0 / viewWidth, 1.0 / viewHeight);
    vec3 p = viewPos(texcoord, depth);

    // normal from the depth buffer, picking the nearer neighbour on each axis to survive edges
    vec3 pR = viewPos(texcoord + vec2(px.x, 0.0), depthAt(texcoord + vec2(px.x, 0.0)));
    vec3 pL = viewPos(texcoord - vec2(px.x, 0.0), depthAt(texcoord - vec2(px.x, 0.0)));
    vec3 pU = viewPos(texcoord + vec2(0.0, px.y), depthAt(texcoord + vec2(0.0, px.y)));
    vec3 pD = viewPos(texcoord - vec2(0.0, px.y), depthAt(texcoord - vec2(0.0, px.y)));
    vec3 dx = abs(pR.z - p.z) < abs(p.z - pL.z) ? pR - p : p - pL;
    vec3 dy = abs(pU.z - p.z) < abs(p.z - pD.z) ? pU - p : p - pD;
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, p) > 0.0) n = -n;

    // shadow map lookup with a small PCF kernel
    vec3 lightDir = normalize(shadowLightPosition);
    float ndotl = dot(n, lightDir);
    vec3 biased = p + n * 0.06;
    vec4 world = gbufferModelViewInverse * vec4(biased, 1.0);
    vec4 sp = shadowProjection * (shadowModelView * world);
    sp.xyz = sp.xyz / sp.w * 0.5 + 0.5;
    float vis = 1.0;
    if (sp.x > 0.0 && sp.x < 1.0 && sp.y > 0.0 && sp.y < 1.0 && sp.z < 1.0) {
        float bias = 0.0007 + 0.0016 * (1.0 - clamp(ndotl, 0.0, 1.0));
        float texel = 1.0 / float(shadowMapResolution);
        float lit = 0.0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float sd = texture2D(shadowtex0, sp.xy + vec2(float(i), float(j)) * texel * 1.5).r;
                lit += sd >= sp.z - bias ? 1.0 : 0.0;
            }
        }
        vis = lit / 9.0;
    }
    float facing = smoothstep(0.0, 0.18, ndotl);
    float sunlit = vis * facing;
    float shade = mix(1.0 - SHADOW_STRENGTH * (1.0 - 0.5 * rainStrength), 1.0, sunlit);

    // ambient occlusion from depth
    float lc = linearDepth(depth);
    float occ = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 2.39996;
        float rad = 3.0 + 11.0 * float(i) / 8.0;
        float sd = linearDepth(depthAt(texcoord + vec2(cos(a), sin(a)) * rad * px));
        float diff = lc - sd;
        occ += smoothstep(0.05, 0.6, diff) * (1.0 - smoothstep(1.5, 4.0, diff));
    }
    float ao = 1.0 - AO_STRENGTH * occ / 8.0;

    color *= shade * ao;

    // cold haze that thickens with distance (and in rain)
    float dist = length(p);
    float haze = 1.0 - exp(-dist * 0.012 * HAZE_DENSITY * (1.0 + rainStrength));
    color = mix(color, vec3(0.055, 0.065, 0.12), clamp(haze, 0.0, 0.85));

    gl_FragData[0] = vec4(color, 1.0);
}
