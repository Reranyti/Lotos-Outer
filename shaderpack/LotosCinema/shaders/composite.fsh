#version 120

// Lotos Cinema, stages 2-4: sun/moon shadows, relief lighting, specular, rim light, AO and haze.
// Normals and materials come from the geometry passes (colortex1/2); where they are missing the normal is rebuilt from depth.

/* DRAWBUFFERS:0 */

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform sampler2D shadowtex0;

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 shadowLightPosition;
uniform vec3 sunPosition;
uniform vec3 upPosition;
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
#define SPECULAR_STRENGTH 1.0   //[0.0 0.5 1.0 1.5 2.0]
#define RELIEF_LIGHT 0.8        //[0.0 0.4 0.8 1.2 1.6]

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

    // normal and material: from the geometry passes when present, else rebuilt from depth
    vec4 nd = texture2D(colortex1, texcoord);
    vec4 md = texture2D(colortex2, texcoord);
    float haveMat = nd.a > 0.5 ? 1.0 : 0.0;
    vec3 n;
    float spec = 0.08;
    float smoothness = 0.3;
    float emis = 0.0;
    float skyLight = 1.0;
    if (haveMat > 0.5) {
        n = normalize(nd.rgb * 2.0 - 1.0);
        spec = md.r;
        smoothness = md.g;
        emis = md.b;
        skyLight = md.a;
    } else {
        vec3 pR = viewPos(texcoord + vec2(px.x, 0.0), depthAt(texcoord + vec2(px.x, 0.0)));
        vec3 pL = viewPos(texcoord - vec2(px.x, 0.0), depthAt(texcoord - vec2(px.x, 0.0)));
        vec3 pU = viewPos(texcoord + vec2(0.0, px.y), depthAt(texcoord + vec2(0.0, px.y)));
        vec3 pD = viewPos(texcoord - vec2(0.0, px.y), depthAt(texcoord - vec2(0.0, px.y)));
        vec3 dx = abs(pR.z - p.z) < abs(p.z - pL.z) ? pR - p : p - pL;
        vec3 dy = abs(pU.z - p.z) < abs(p.z - pD.z) ? pU - p : p - pD;
        n = normalize(cross(dx, dy));
        if (dot(n, p) > 0.0) n = -n;
    }
    float skyGate = haveMat > 0.5 ? smoothstep(0.15, 0.85, skyLight) : 1.0;     // caves and interiors get no sun

    float dayness = smoothstep(-0.1, 0.2, dot(normalize(sunPosition), normalize(upPosition)));
    vec3 lightTint = mix(vec3(0.55, 0.66, 1.0), vec3(1.0, 0.86, 0.66), dayness);

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
    float sunlit = vis * facing * skyGate;
    float shade = mix(1.0 - SHADOW_STRENGTH * (1.0 - 0.5 * rainStrength), 1.0, vis * facing);
    shade = mix(1.0, shade, skyGate);

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

    // far things (clouds, distant hills) lie outside the shadow map: fade shadows, AO and relief with distance
    float nearAmount = 1.0 - smoothstep(55.0, 105.0, length(p));
    color *= mix(1.0, mix(shade * ao, 1.0, emis), nearAmount);

    if (haveMat > 0.5) {
        // per-pixel relief: the textured normal modulates the sunlight, so every block face has depth
        float diffuse = clamp(ndotl, 0.0, 1.0);
        color *= mix(1.0, 0.78 + 0.5 * diffuse, sunlit * RELIEF_LIGHT * 0.6 * nearAmount * (1.0 - emis));

        // specular highlight; metals tint it with their own colour
        vec3 V = normalize(-p);
        vec3 H = normalize(lightDir + V);
        float shin = mix(10.0, 220.0, smoothness * smoothness);
        float sp0 = pow(max(dot(n, H), 0.0), shin) * (shin + 8.0) / 60.0;
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        vec3 metalCol = clamp(color / max(luma, 0.03), 0.0, 1.7);
        vec3 specCol = mix(vec3(1.0), metalCol, step(0.5, spec));
        color += lightTint * specCol * sp0 * spec * SPECULAR_STRENGTH * sunlit * nearAmount * (1.0 - 0.8 * rainStrength);

        // faint rim light so edges separate from the dark
        float rim = pow(1.0 - max(dot(n, V), 0.0), 3.0);
        color += lightTint * rim * 0.05 * skyGate * nearAmount * (1.0 - emis);
    }

    // cold haze that thickens with distance (and in rain)
    float dist = length(p);
    float haze = 1.0 - exp(-dist * 0.012 * HAZE_DENSITY * (1.0 + rainStrength));
    color = mix(color, vec3(0.055, 0.065, 0.12), clamp(haze, 0.0, 0.85));

    gl_FragData[0] = vec4(color, 1.0);
}
