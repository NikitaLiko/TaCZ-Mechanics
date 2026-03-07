#version 150

uniform sampler2D DiffuseSampler;
uniform float Radius;
uniform float Intensity;

in vec2 texCoord;
in vec2 sampleStep;

out vec4 fragColor;

void main() {
    if (Intensity < 0.001) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    float effectiveRadius = Radius * Intensity;
    if (effectiveRadius < 0.5) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    // Radial factor: blur more at edges, less in center (tunnel vision)
    vec2 centered = texCoord - 0.5;
    float radialDist = length(centered) * 2.0;
    float radialFactor = smoothstep(0.1, 0.9, radialDist);
    float adjustedRadius = effectiveRadius * mix(0.3, 1.0, radialFactor);

    vec4 blurred = vec4(0.0);
    float totalWeight = 0.0;

    // 13-tap gaussian kernel
    for (float i = -6.0; i <= 6.0; i += 1.0) {
        float offset = i;
        float weight = exp(-0.5 * (i * i) / (adjustedRadius * adjustedRadius * 0.25 + 0.01));
        blurred += texture(DiffuseSampler, texCoord + sampleStep * offset * adjustedRadius * 0.15) * weight;
        totalWeight += weight;
    }

    fragColor = blurred / totalWeight;
}
