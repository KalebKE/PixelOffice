// Custom voxel fragment shader: per-pixel lighting + enhanced PCF soft shadows
// Eliminates Gouraud banding on large flat surfaces

#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#ifdef normalFlag
varying vec3 v_normal;
#endif

#if defined(colorFlag)
varying vec4 v_color;
#endif

#ifdef blendedFlag
varying float v_opacity;
#ifdef alphaTestFlag
varying float v_alphaTest;
#endif
#endif

#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag)
#define textureFlag
#endif

#ifdef diffuseTextureFlag
varying MED vec2 v_diffuseUV;
#endif

#ifdef specularTextureFlag
varying MED vec2 v_specularUV;
#endif

#ifdef emissiveTextureFlag
varying MED vec2 v_emissiveUV;
#endif

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif

#ifdef diffuseTextureFlag
uniform sampler2D u_diffuseTexture;
#endif

#ifdef specularColorFlag
uniform vec4 u_specularColor;
#endif

#ifdef specularTextureFlag
uniform sampler2D u_specularTexture;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef emissiveTextureFlag
uniform sampler2D u_emissiveTexture;
#endif

#ifdef lightingFlag
varying vec3 v_lightDiffuse;
varying vec3 v_worldPos;

#if defined(ambientLightFlag) || defined(ambientCubemapFlag) || defined(sphericalHarmonicsFlag)
#define ambientFlag
#endif

#ifdef shininessFlag
uniform float u_shininess;
#else
const float u_shininess = 20.0;
#endif

#if defined(specularFlag) || defined(fogFlag)
#define cameraPositionFlag
#endif

#ifdef cameraPositionFlag
uniform vec4 u_cameraPosition;
#endif

// ===== Per-pixel directional lights =====
#if numDirectionalLights > 0
struct DirectionalLight
{
    vec3 color;
    vec3 direction;
};
uniform DirectionalLight u_dirLights[numDirectionalLights];
#endif

// ===== Per-pixel point lights =====
#if numPointLights > 0
struct PointLight
{
    vec3 color;
    vec3 position;
};
uniform PointLight u_pointLights[numPointLights];
#endif

// ===== Shadow mapping with enhanced PCF =====
#ifdef shadowMapFlag
uniform sampler2D u_shadowTexture;
uniform float u_shadowPCFOffset;
varying vec3 v_shadowMapUv;
#define separateAmbientFlag

float getShadowness(vec2 offset)
{
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
    return step(v_shadowMapUv.z, dot(texture2D(u_shadowTexture, v_shadowMapUv.xy + offset), bitShifts));
}

float getShadow()
{
    // 9-tap 3x3 PCF for softer shadow edges (vs default 4-tap 2x2)
    float shadow = 0.0;
    float pcf = u_shadowPCFOffset;
    shadow += getShadowness(vec2(-pcf, -pcf));
    shadow += getShadowness(vec2( 0.0, -pcf));
    shadow += getShadowness(vec2( pcf, -pcf));
    shadow += getShadowness(vec2(-pcf,  0.0));
    shadow += getShadowness(vec2( 0.0,  0.0));
    shadow += getShadowness(vec2( pcf,  0.0));
    shadow += getShadowness(vec2(-pcf,  pcf));
    shadow += getShadowness(vec2( 0.0,  pcf));
    shadow += getShadowness(vec2( pcf,  pcf));
    return shadow / 9.0;
}
#endif //shadowMapFlag

#if defined(ambientFlag) && defined(separateAmbientFlag)
varying vec3 v_ambientLight;
#endif

#endif //lightingFlag

#ifdef fogFlag
uniform vec4 u_fogColor;
varying float v_fog;
#endif

void main() {
    #if defined(normalFlag)
        vec3 normal = normalize(v_normal);
    #endif

    // ===== Diffuse color =====
    #if defined(diffuseTextureFlag) && defined(diffuseColorFlag) && defined(colorFlag)
        vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor * v_color;
    #elif defined(diffuseTextureFlag) && defined(diffuseColorFlag)
        vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor;
    #elif defined(diffuseTextureFlag) && defined(colorFlag)
        vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * v_color;
    #elif defined(diffuseTextureFlag)
        vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV);
    #elif defined(diffuseColorFlag) && defined(colorFlag)
        vec4 diffuse = u_diffuseColor * v_color;
    #elif defined(diffuseColorFlag)
        vec4 diffuse = u_diffuseColor;
    #elif defined(colorFlag)
        vec4 diffuse = v_color;
    #else
        vec4 diffuse = vec4(1.0);
    #endif

    // ===== Emissive =====
    #if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
        vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV) * u_emissiveColor;
    #elif defined(emissiveTextureFlag)
        vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV);
    #elif defined(emissiveColorFlag)
        vec4 emissive = u_emissiveColor;
    #else
        vec4 emissive = vec4(0.0);
    #endif

    // ===== Per-pixel lighting =====
    #if defined(lightingFlag)
        // Start with ambient from vertex shader
        vec3 lightDiffuse = v_lightDiffuse;

        #ifdef specularFlag
            vec3 lightSpecular = vec3(0.0);
            vec3 viewVec = normalize(u_cameraPosition.xyz - v_worldPos);
        #endif

        // Per-pixel directional lights
        #if (numDirectionalLights > 0) && defined(normalFlag)
            for (int i = 0; i < numDirectionalLights; i++) {
                vec3 lightDir = -u_dirLights[i].direction;
                float NdotL = clamp(dot(normal, lightDir), 0.0, 1.0);
                vec3 value = u_dirLights[i].color * NdotL;
                lightDiffuse += value;
                #ifdef specularFlag
                    float halfDotView = max(0.0, dot(normal, normalize(lightDir + viewVec)));
                    lightSpecular += value * pow(halfDotView, u_shininess);
                #endif
            }
        #endif

        // Per-pixel point lights
        #if (numPointLights > 0) && defined(normalFlag)
            for (int i = 0; i < numPointLights; i++) {
                vec3 lightDir = u_pointLights[i].position - v_worldPos;
                float dist2 = dot(lightDir, lightDir);
                lightDir *= inversesqrt(dist2);
                float NdotL = clamp(dot(normal, lightDir), 0.0, 1.0);
                vec3 value = u_pointLights[i].color * (NdotL / (1.0 + dist2));
                lightDiffuse += value;
                #ifdef specularFlag
                    float halfDotView = max(0.0, dot(normal, normalize(lightDir + viewVec)));
                    lightSpecular += value * pow(halfDotView, u_shininess);
                #endif
            }
        #endif

        // ===== Combine lighting =====
        #if (!defined(specularFlag))
            #if defined(ambientFlag) && defined(separateAmbientFlag)
                #ifdef shadowMapFlag
                    gl_FragColor.rgb = (diffuse.rgb * (v_ambientLight + getShadow() * lightDiffuse)) + emissive.rgb;
                #else
                    gl_FragColor.rgb = (diffuse.rgb * (v_ambientLight + lightDiffuse)) + emissive.rgb;
                #endif
            #else
                #ifdef shadowMapFlag
                    gl_FragColor.rgb = getShadow() * (diffuse.rgb * lightDiffuse) + emissive.rgb;
                #else
                    gl_FragColor.rgb = (diffuse.rgb * lightDiffuse) + emissive.rgb;
                #endif
            #endif
        #else
            #if defined(specularTextureFlag) && defined(specularColorFlag)
                vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * u_specularColor.rgb * lightSpecular;
            #elif defined(specularTextureFlag)
                vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * lightSpecular;
            #elif defined(specularColorFlag)
                vec3 specular = u_specularColor.rgb * lightSpecular;
            #else
                vec3 specular = lightSpecular;
            #endif

            #if defined(ambientFlag) && defined(separateAmbientFlag)
                #ifdef shadowMapFlag
                    gl_FragColor.rgb = (diffuse.rgb * (getShadow() * lightDiffuse + v_ambientLight)) + specular + emissive.rgb;
                #else
                    gl_FragColor.rgb = (diffuse.rgb * (lightDiffuse + v_ambientLight)) + specular + emissive.rgb;
                #endif
            #else
                #ifdef shadowMapFlag
                    gl_FragColor.rgb = getShadow() * ((diffuse.rgb * lightDiffuse) + specular) + emissive.rgb;
                #else
                    gl_FragColor.rgb = (diffuse.rgb * lightDiffuse) + specular + emissive.rgb;
                #endif
            #endif
        #endif
    #else
        // No lighting
        gl_FragColor.rgb = diffuse.rgb + emissive.rgb;
    #endif //lightingFlag

    #ifdef fogFlag
        gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor.rgb, v_fog);
    #endif

    #ifdef blendedFlag
        gl_FragColor.a = diffuse.a * v_opacity;
        #ifdef alphaTestFlag
            if (gl_FragColor.a <= v_alphaTest)
                discard;
        #endif
    #else
        gl_FragColor.a = 1.0;
    #endif
}
