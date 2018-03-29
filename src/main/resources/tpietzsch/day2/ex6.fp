out vec4 fragColor;

in vec3 vertexColor;
in vec2 texCoord;

uniform sampler2D ourTexture;

void main()
{
    fragColor = texture(ourTexture, texCoord) * vec4(vertexColor, 1);
}
