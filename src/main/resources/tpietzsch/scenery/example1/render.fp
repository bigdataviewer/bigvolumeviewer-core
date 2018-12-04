layout (location = 0) out vec4 fragColor;


layout (location = 0) in vec3 texCoord;


uniform usampler3D texture1;


void main()
{
    uvec4 col = texture( texture1, texCoord );
    fragColor = vec4( col.xyz / 255.0, 1 );
}
