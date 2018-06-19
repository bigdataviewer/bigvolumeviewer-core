out vec4 FragColor;

uniform vec3 rgb;

void main()
{
//    FragColor = vec4( rgb, 1 );
    FragColor = vec4(
        convertR( rgb.r ),
        convertG( rgb.g ),
        convertB( rgb.b ),
        1 );
}
