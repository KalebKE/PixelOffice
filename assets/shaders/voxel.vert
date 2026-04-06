// Custom voxel vertex shader: passes normals + world position to fragment
// for per-pixel lighting (eliminates Gouraud banding on large flat surfaces)

#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag)
#define textureFlag
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#if defined(specularFlag) || defined(fogFlag)
#define cameraPositionFlag
#endif

attribute vec3 a_position;
uniform mat4 u_projViewTrans;

#if defined(colorFlag)
varying vec4 v_color;
attribute vec4 a_color;
#endif // colorFlag

#ifdef normalFlag
attribute vec3 a_normal;
uniform mat3 u_normalMatrix;
varying vec3 v_normal;
#endif // normalFlag

#ifdef textureFlag
attribute vec2 a_texCoord0;
#endif // textureFlag

#ifdef diffuseTextureFlag
uniform vec4 u_diffuseUVTransform;
varying vec2 v_diffuseUV;
#endif

#ifdef emissiveTextureFlag
uniform vec4 u_emissiveUVTransform;
varying vec2 v_emissiveUV;
#endif

#ifdef specularTextureFlag
uniform vec4 u_specularUVTransform;
varying vec2 v_specularUV;
#endif

// Bone weights (unchanged from default)
#ifdef boneWeight0Flag
#define boneWeightsFlag
attribute vec2 a_boneWeight0;
#endif
#ifdef boneWeight1Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight1;
#endif
#ifdef boneWeight2Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight2;
#endif
#ifdef boneWeight3Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight3;
#endif
#ifdef boneWeight4Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight4;
#endif
#ifdef boneWeight5Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight5;
#endif
#ifdef boneWeight6Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight6;
#endif
#ifdef boneWeight7Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight7;
#endif

#if defined(numBones) && defined(boneWeightsFlag)
#if (numBones > 0)
#define skinningFlag
#endif
#endif

uniform mat4 u_worldTrans;

#if defined(numBones)
#if numBones > 0
uniform mat4 u_bones[numBones];
#endif
#endif

#ifdef blendedFlag
uniform float u_opacity;
varying float v_opacity;
#ifdef alphaTestFlag
uniform float u_alphaTest;
varying float v_alphaTest;
#endif
#endif

#ifdef lightingFlag
// Per-pixel: we pass ambient only from vertex, lighting computed in fragment
varying vec3 v_lightDiffuse;

#ifdef ambientLightFlag
uniform vec3 u_ambientLight;
#endif

#ifdef ambientCubemapFlag
uniform vec3 u_ambientCubemap[6];
#endif

#ifdef sphericalHarmonicsFlag
uniform vec3 u_sphericalHarmonics[9];
#endif

#ifdef cameraPositionFlag
uniform vec4 u_cameraPosition;
#endif

#ifdef fogFlag
varying float v_fog;
#endif

#if defined(ambientLightFlag) || defined(ambientCubemapFlag) || defined(sphericalHarmonicsFlag)
#define ambientFlag
#endif

#ifdef shadowMapFlag
uniform mat4 u_shadowMapProjViewTrans;
varying vec3 v_shadowMapUv;
#define separateAmbientFlag
#endif

#if defined(ambientFlag) && defined(separateAmbientFlag)
varying vec3 v_ambientLight;
#endif

// World position for per-pixel lighting in fragment shader
varying vec3 v_worldPos;

#endif // lightingFlag

void main() {
    #ifdef diffuseTextureFlag
        v_diffuseUV = u_diffuseUVTransform.xy + a_texCoord0 * u_diffuseUVTransform.zw;
    #endif

    #ifdef emissiveTextureFlag
        v_emissiveUV = u_emissiveUVTransform.xy + a_texCoord0 * u_emissiveUVTransform.zw;
    #endif

    #ifdef specularTextureFlag
        v_specularUV = u_specularUVTransform.xy + a_texCoord0 * u_specularUVTransform.zw;
    #endif

    #if defined(colorFlag)
        v_color = a_color;
    #endif

    #ifdef blendedFlag
        v_opacity = u_opacity;
        #ifdef alphaTestFlag
            v_alphaTest = u_alphaTest;
        #endif
    #endif

    #ifdef skinningFlag
        mat4 skinning = mat4(0.0);
        #ifdef boneWeight0Flag
            skinning += (a_boneWeight0.y) * u_bones[int(a_boneWeight0.x)];
        #endif
        #ifdef boneWeight1Flag
            skinning += (a_boneWeight1.y) * u_bones[int(a_boneWeight1.x)];
        #endif
        #ifdef boneWeight2Flag
            skinning += (a_boneWeight2.y) * u_bones[int(a_boneWeight2.x)];
        #endif
        #ifdef boneWeight3Flag
            skinning += (a_boneWeight3.y) * u_bones[int(a_boneWeight3.x)];
        #endif
        #ifdef boneWeight4Flag
            skinning += (a_boneWeight4.y) * u_bones[int(a_boneWeight4.x)];
        #endif
        #ifdef boneWeight5Flag
            skinning += (a_boneWeight5.y) * u_bones[int(a_boneWeight5.x)];
        #endif
        #ifdef boneWeight6Flag
            skinning += (a_boneWeight6.y) * u_bones[int(a_boneWeight6.x)];
        #endif
        #ifdef boneWeight7Flag
            skinning += (a_boneWeight7.y) * u_bones[int(a_boneWeight7.x)];
        #endif
    #endif

    #ifdef skinningFlag
        vec4 pos = u_worldTrans * skinning * vec4(a_position, 1.0);
    #else
        vec4 pos = u_worldTrans * vec4(a_position, 1.0);
    #endif

    gl_Position = u_projViewTrans * pos;

    #ifdef shadowMapFlag
        vec4 spos = u_shadowMapProjViewTrans * pos;
        v_shadowMapUv.xyz = (spos.xyz / spos.w) * 0.5 + 0.5;
        v_shadowMapUv.z = min(v_shadowMapUv.z, 0.998);
    #endif

    #if defined(normalFlag)
        #if defined(skinningFlag)
            vec3 normal = normalize((u_worldTrans * skinning * vec4(a_normal, 0.0)).xyz);
        #else
            vec3 normal = normalize(u_normalMatrix * a_normal);
        #endif
        v_normal = normal;
    #endif

    #ifdef fogFlag
        vec3 flen = u_cameraPosition.xyz - pos.xyz;
        float fog = dot(flen, flen) * u_cameraPosition.w;
        v_fog = min(fog, 1.0);
    #endif

    #ifdef lightingFlag
        // Pass world position for per-pixel lighting
        v_worldPos = pos.xyz;

        // Compute ambient in vertex shader (it's constant, no need for per-pixel)
        #if defined(ambientLightFlag)
            vec3 ambientLight = u_ambientLight;
        #elif defined(ambientFlag)
            vec3 ambientLight = vec3(0.0);
        #endif

        #ifdef ambientCubemapFlag
            vec3 squaredNormal = normal * normal;
            vec3 isPositive = step(0.0, normal);
            ambientLight += squaredNormal.x * mix(u_ambientCubemap[0], u_ambientCubemap[1], isPositive.x) +
                    squaredNormal.y * mix(u_ambientCubemap[2], u_ambientCubemap[3], isPositive.y) +
                    squaredNormal.z * mix(u_ambientCubemap[4], u_ambientCubemap[5], isPositive.z);
        #endif

        #ifdef sphericalHarmonicsFlag
            ambientLight += u_sphericalHarmonics[0];
            ambientLight += u_sphericalHarmonics[1] * normal.x;
            ambientLight += u_sphericalHarmonics[2] * normal.y;
            ambientLight += u_sphericalHarmonics[3] * normal.z;
            ambientLight += u_sphericalHarmonics[4] * (normal.x * normal.z);
            ambientLight += u_sphericalHarmonics[5] * (normal.z * normal.y);
            ambientLight += u_sphericalHarmonics[6] * (normal.y * normal.x);
            ambientLight += u_sphericalHarmonics[7] * (3.0 * normal.z * normal.z - 1.0);
            ambientLight += u_sphericalHarmonics[8] * (normal.x * normal.x - normal.y * normal.y);
        #endif

        #ifdef ambientFlag
            #ifdef separateAmbientFlag
                v_ambientLight = ambientLight;
                v_lightDiffuse = vec3(0.0);
            #else
                v_lightDiffuse = ambientLight;
            #endif
        #else
            v_lightDiffuse = vec3(0.0);
        #endif

        // NOTE: directional + point light loops REMOVED from vertex shader
        // They are now computed per-pixel in the fragment shader
    #endif // lightingFlag
}
