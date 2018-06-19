out vec4 FragColor;

uniform vec3 rgb;

void main()
{
    FragColor = vec4( rgb, 1 );
//    FragColor = convert( rgb.b );
}
