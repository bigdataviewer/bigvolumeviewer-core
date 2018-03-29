out vec4 fragColor;

in vec3 vertexColor;
in vec2 texCoord;

uniform sampler2D texture1;
uniform sampler2D texture2;

void main()
{
    fragColor = mix(
        texture(texture1, texCoord),
        texture(texture2, vec2(1.0 - texCoord.s, texCoord.t)),
        0.2);
}
