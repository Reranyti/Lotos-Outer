#version 120

// Lotos Cinema, stage 4: geometry pass for blocks (copies of this file serve block entities, entities and the hand).
// Writes: colortex0 = vanilla-lit colour, colortex1 = view-space normal (with texture relief), colortex2 = material.
#define USE_ENTITY_COLOR 0

/* DRAWBUFFERS:012 */

uniform sampler2D texture;
uniform sampler2D lightmap;
uniform ivec2 atlasSize;
uniform vec4 entityColor;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 vpos;
varying float blockId;

#define BUMP_STRENGTH 1.2       //[0.0 0.6 1.0 1.2 1.6 2.2]

float lumaOf(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// height field = brightness of the texture; its slope tilts the normal, so pixel art catches the light
vec3 bumpNormal(vec3 N) {
    if (atlasSize.x <= 0 || BUMP_STRENGTH <= 0.0) return N;
    vec2 ts = 1.0 / vec2(atlasSize);
    float hx = lumaOf(texture2D(texture, texcoord + vec2(ts.x, 0.0)).rgb) - lumaOf(texture2D(texture, texcoord - vec2(ts.x, 0.0)).rgb);
    float hy = lumaOf(texture2D(texture, texcoord + vec2(0.0, ts.y)).rgb) - lumaOf(texture2D(texture, texcoord - vec2(0.0, ts.y)).rgb);

    vec3 dp1 = dFdx(vpos);
    vec3 dp2 = dFdy(vpos);
    vec2 duv1 = dFdx(texcoord);
    vec2 duv2 = dFdy(texcoord);
    vec3 dp2perp = cross(dp2, N);
    vec3 dp1perp = cross(N, dp1);
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
    float m = max(dot(T, T), dot(B, B));
    if (m < 1.0e-14) return N;
    float inv = inversesqrt(m);
    return normalize(N - (T * hx + B * hy) * inv * BUMP_STRENGTH * 0.6);
}

bool isId(float id, float want) {
    return abs(id - want) < 0.5;
}

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < 0.1) discard;
#if USE_ENTITY_COLOR == 1
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif

    vec3 N = bumpNormal(normalize(normal));

    // material: x = specular amount, y = smoothness, z = emissive
    float spec = 0.10;
    float smoothness = 0.30;
    float emis = 0.0;
    if (isId(blockId, 10001.0)) { spec = 0.85; smoothness = 0.85; }          // metals and gems
    else if (isId(blockId, 10002.0)) { emis = 1.0; }                          // light sources
    else if (isId(blockId, 10003.0)) { spec = 0.40; smoothness = 0.75; }     // polished stone, ice, quartz
    emis = max(emis, smoothstep(0.94, 0.99, lmcoord.x) * 0.5);                // anything that is itself a lamp

    vec3 lit = albedo.rgb * texture2D(lightmap, lmcoord).rgb;
    lit += albedo.rgb * emis * 0.6;                                           // pushes lamps past 1.0 so they bloom

    gl_FragData[0] = vec4(lit, albedo.a);
    gl_FragData[1] = vec4(N * 0.5 + 0.5, 1.0);
    gl_FragData[2] = vec4(spec, smoothness, emis, lmcoord.y);
}
