out vec4 fragColor;

in vec3 texCoord;

uniform usampler3D ourTexture;

void main()
{
    uvec4 t = texture(ourTexture, texCoord);
//    fragColor = texture(ourTexture, texCoord);
    float g = 0;
    if ( t.r != 0 )
        g = 1;
    fragColor = vec4( 0.5, g, 0.5, 1 );
}
