#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float VignetteStrength;
uniform float DesatStrength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    if (Intensity < 0.001) {
        fragColor = color;
        return;
    }

    // Desaturation
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 desaturated = mix(color.rgb, vec3(luminance), Intensity * DesatStrength * 0.8);

    // Vignette: darken edges, creating tunnel vision
    vec2 centered = texCoord - 0.5;
    float dist = length(centered) * 2.0;
    float vignette = 1.0 - smoothstep(0.2, 1.2, dist) * Intensity * VignetteStrength * 0.7;

    // Slight warm tint shift at high suppression (stress response)
    vec3 tinted = desaturated * vec3(1.0 + Intensity * 0.05, 1.0 - Intensity * 0.02, 1.0 - Intensity * 0.05);

    fragColor = vec4(tinted * vignette, color.a);
}
