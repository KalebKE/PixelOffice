// Screen-space ambient occlusion fragment shader
// Samples camera-view depth buffer to darken areas where geometry is close together
#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_depthTexture;
uniform vec2 u_screenSize;
uniform float u_radius;
uniform float u_intensity;
uniform float u_near;
uniform float u_far;

varying vec2 v_texCoord;

// Unpack linear depth from DepthShaderProvider's RGBA encoding
float unpackDepth(vec4 rgba) {
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
    return dot(rgba, bitShifts);
}

// Convert packed depth to linear view-space depth
float getLinearDepth(vec2 uv) {
    float d = unpackDepth(texture2D(u_depthTexture, uv));
    return u_near * u_far / (u_far - d * (u_far - u_near));
}

void main() {
    float depth = getLinearDepth(v_texCoord);

    // Skip far-away pixels (sky/background)
    if (depth > u_far * 0.95) {
        gl_FragColor = vec4(1.0);
        return;
    }

    // 12-sample Poisson disk for occlusion testing
    float ao = 0.0;
    float pixelRadius = u_radius / depth;  // radius in screen pixels, scaled by depth

    vec2 samples[12];
    samples[0]  = vec2(-0.326, -0.406);
    samples[1]  = vec2(-0.840, -0.074);
    samples[2]  = vec2(-0.696,  0.457);
    samples[3]  = vec2(-0.203,  0.621);
    samples[4]  = vec2( 0.962, -0.195);
    samples[5]  = vec2( 0.473, -0.480);
    samples[6]  = vec2( 0.519,  0.767);
    samples[7]  = vec2( 0.185, -0.893);
    samples[8]  = vec2( 0.507,  0.064);
    samples[9]  = vec2(-0.321,  0.054);
    samples[10] = vec2(-0.138, -0.757);
    samples[11] = vec2( 0.061,  0.357);

    for (int i = 0; i < 12; i++) {
        vec2 sampleUV = v_texCoord + samples[i] * pixelRadius / u_screenSize;

        // Clamp to screen bounds
        sampleUV = clamp(sampleUV, 0.001, 0.999);

        float sampleDepth = getLinearDepth(sampleUV);
        float diff = depth - sampleDepth;

        // Occlude if sample is closer to camera (in front of current pixel)
        // but not too far away (range check prevents darkening distant background)
        if (diff > 0.01 && diff < u_radius * 2.0) {
            ao += 1.0;
        }
    }

    ao = 1.0 - (ao / 12.0) * u_intensity;
    ao = clamp(ao, 0.0, 1.0);

    gl_FragColor = vec4(ao, ao, ao, 1.0);
}
