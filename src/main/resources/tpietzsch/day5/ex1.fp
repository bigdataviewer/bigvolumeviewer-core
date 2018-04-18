out vec4 fragColor;

in vec3 texCoord;

uniform sampler3D ourTexture;

uniform float offset;
uniform float scale;

void main()
{
    fragColor = vec4( offset + scale * texture(ourTexture, texCoord).rrr, 1 );
}
