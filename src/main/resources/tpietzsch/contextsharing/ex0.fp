out vec4 fragColor;

in vec3 texCoord;

uniform sampler2D ourTexture;

void main()
{
    fragColor = texture(ourTexture, texCoord.xy);
}
